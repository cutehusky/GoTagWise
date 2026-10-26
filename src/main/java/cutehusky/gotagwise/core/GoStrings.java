package cutehusky.gotagwise.core;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Go quoted strings, with UTF-16 source offsets preserved for editor highlighting. */
public final class GoStrings {
    private GoStrings() {}
    public record Decoded(String text, int[] source) {
        public int offset(int index) { return source[Math.min(index, source.length - 1)]; }
    }
    public static Decoded literal(String literal) {
        if (literal.length() < 2) throw new IllegalArgumentException("Unclosed tag literal");
        char quote = literal.charAt(0);
        if ((quote != '`' && quote != '"') || literal.charAt(literal.length() - 1) != quote)
            throw new IllegalArgumentException("Unclosed tag literal");
        return decode(literal.substring(1, literal.length() - 1), 1, quote == '`');
    }
    public static Decoded decode(String input, int base, boolean raw) {
        StringBuilder out = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        BitSet byteEscapes = new BitSet();
        for (int i = 0; i < input.length();) {
            int start = i;
            char c = input.charAt(i++);
            if (raw) {
                if (c == '\r') continue; // Go discards CR in raw string literals.
                out.append(c); offsets.add(base + start); continue;
            }
            if (c == '\n' || c == '\r' || c == '"') throw new IllegalArgumentException("Invalid quoted string");
            if (c != '\\') { out.append(c); offsets.add(base + start); continue; }
            if (i == input.length()) throw new IllegalArgumentException("Incomplete Go escape");
            char e = input.charAt(i++);
            String simple = switch (e) {
                case 'a' -> "\u0007"; case 'b' -> "\b"; case 'f' -> "\f";
                case 'n' -> "\n"; case 'r' -> "\r"; case 't' -> "\t";
                case 'v' -> "\u000b"; case '\\' -> "\\"; case '"' -> "\"";
                default -> null;
            };
            if (simple != null) {
                out.append(simple); offsets.add(base + start); continue;
            }
            int digits = e == 'x' ? 2 : e == 'u' ? 4 : e == 'U' ? 8 : 3;
            int radix = e == 'x' || e == 'u' || e == 'U' ? 16 : 8;
            if (radix == 8 && (e < '0' || e > '7')) throw new IllegalArgumentException("Invalid Go escape");
            if (radix == 8) i--;
            if (i + digits > input.length()) throw new IllegalArgumentException("Incomplete Go escape");
            String number = input.substring(i, i + digits);
            if (!number.matches(radix == 8 ? "[0-7]+" : "[0-9a-fA-F]+"))
                throw new IllegalArgumentException("Invalid Go escape");
            long value;
            try { value = Long.parseLong(number, radix); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid Go escape"); }
            if (value > 0x10ffff || value >= 0xd800 && value <= 0xdfff || radix == 8 && value > 255)
                throw new IllegalArgumentException("Invalid Go code point");
            i += digits;
            if (e == 'x' || radix == 8) byteEscapes.set(out.length());
            char[] chars = Character.toChars((int) value);
            out.append(chars);
            for (char ignored : chars) offsets.add(base + start);
        }
        offsets.add(base + input.length());
        if (byteEscapes.isEmpty()) return new Decoded(out.toString(), offsets.stream().mapToInt(Integer::intValue).toArray());
        // Hex/octal escapes encode bytes, unlike Go's Unicode scalar escapes.
        StringBuilder normalized = new StringBuilder();
        List<Integer> mapped = new ArrayList<>();
        for (int i = 0; i < out.length();) {
            int lead = out.charAt(i);
            int count = lead >= 0xc2 && lead <= 0xdf ? 2 : lead >= 0xe0 && lead <= 0xef ? 3 : lead >= 0xf0 && lead <= 0xf4 ? 4 : 1;
            boolean valid = count > 1 && i + count <= out.length() && byteEscapes.get(i);
            int cp = lead & (0x7f >> count);
            for (int j = 1; valid && j < count; j++) {
                int next = out.charAt(i + j);
                valid = byteEscapes.get(i + j) && next >= 0x80 && next <= 0xbf;
                cp = cp << 6 | next & 0x3f;
            }
            valid &= cp <= 0x10ffff && !(cp >= 0xd800 && cp <= 0xdfff) && cp >= (count == 2 ? 0x80 : count == 3 ? 0x800 : 0x10000);
            if (valid) {
                for (char c : Character.toChars(cp)) { normalized.append(c); mapped.add(offsets.get(i)); }
                i += count;
            } else { normalized.append(out.charAt(i)); mapped.add(offsets.get(i++)); }
        }
        mapped.add(base + input.length());
        return new Decoded(normalized.toString(), mapped.stream().mapToInt(Integer::intValue).toArray());
    }
}
