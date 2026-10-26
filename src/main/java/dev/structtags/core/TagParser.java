package dev.structtags.core;

import java.util.*;

public final class TagParser {
    private TagParser() {}
    public record Entry(String key, String value, int start, int end, int valueStart, int valueEnd) {}
    public record Issue(int start, int end, String message) {}
    public record Parsed(List<Entry> entries, List<Issue> issues) {}

    public static Parsed parse(String literal) {
        List<Entry> entries = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        GoStrings.Decoded decoded;
        try { decoded = GoStrings.literal(literal); }
        catch (IllegalArgumentException ex) {
            return new Parsed(entries, List.of(new Issue(0, Math.max(1, literal.length()), ex.getMessage())));
        }
        String text = decoded.text();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && text.charAt(i) == ' ') i++;
            if (i == text.length()) break;
            int start = i;
            while (i < text.length() && validKey(text.charAt(i))) i++;
            if (i == start || i + 1 >= text.length() || text.charAt(i) != ':' || text.charAt(i + 1) != '"') {
                issues.add(new Issue(decoded.offset(start), decoded.offset(text.length()), "Expected key:\"value\" separated by spaces"));
                break;
            }
            String key = text.substring(start, i);
            int valueStart = i + 2;
            i = valueStart;
            boolean escaped = false;
            for (; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' && !escaped) break;
                if (c == '\\' && !escaped) escaped = true; else escaped = false;
            }
            if (i == text.length()) {
                issues.add(new Issue(decoded.offset(start), decoded.offset(i), "Unclosed struct tag value")); break;
            }
            try {
                String value = GoStrings.decode(text.substring(valueStart, i), 0, false).text();
                entries.add(new Entry(key, value, decoded.offset(start), decoded.offset(i + 1),
                        decoded.offset(valueStart), decoded.offset(i)));
            } catch (IllegalArgumentException ex) {
                issues.add(new Issue(decoded.offset(valueStart), decoded.offset(i), ex.getMessage()));
            }
            i++;
            if (i < text.length() && text.charAt(i) != ' ') {
                issues.add(new Issue(decoded.offset(i), decoded.offset(i + 1), "Separate struct tags with a space"));
            }
        }
        return new Parsed(List.copyOf(entries), List.copyOf(issues));
    }
    public static boolean validKey(char c) { return c > ' ' && c != ':' && c != '"' && c != 0x7f; }
}
