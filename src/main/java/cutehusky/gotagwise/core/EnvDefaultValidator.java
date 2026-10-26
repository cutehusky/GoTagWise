package cutehusky.gotagwise.core;

import java.math.*;
import java.util.*;
import java.util.regex.Pattern;
import static cutehusky.gotagwise.core.FieldType.Kind.*;

/** env/v11 defaults use its Go parsers, not JSON or validator condition syntax. */
public final class EnvDefaultValidator {
    private EnvDefaultValidator() {}
    private static final Set<String> BOOLS = Set.of("1", "t", "T", "TRUE", "true", "True", "0", "f", "F", "FALSE", "false", "False");
    private static final String DIGITS = "[0-9](?:_?[0-9])*", HEX = "[0-9a-fA-F](?:_?[0-9a-fA-F])*";
    private static final Pattern FLOAT_VALUE = Pattern.compile("[+-]?(?:(?:" + DIGITS + "(?:\\.(?:" + DIGITS + ")?)?|\\." + DIGITS + ")(?:[eE][+-]?" + DIGITS + ")?|0[xX]_?(?:" + HEX + "(?:\\.(?:" + HEX + ")?)?|\\." + HEX + ")[pP][+-]?" + DIGITS + ")");
    private static final Pattern DURATION = Pattern.compile("([0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(ns|us|µs|μs|ms|s|m|h)");
    private static final Map<String, Long> UNITS = Map.of("ns", 1L, "us", 1000L, "µs", 1000L, "μs", 1000L, "ms", 1000000L, "s", 1000000000L, "m", 60000000000L, "h", 3600000000000L);
    public static String example(FieldType declared) {
        FieldType type = declared.dereference();
        if (type.envParser().equals("time.Duration")) return "5s";
        return switch (type.kind()) {
            case BOOLEAN -> "true";
            case INTEGER, UNSIGNED -> "1";
            case FLOAT -> "1.5";
            case SLICE -> type.element() == null ? "value" : example(type.element());
            case MAP -> (type.key() == null ? "key" : example(type.key())) + ":" + (type.element() == null ? "value" : example(type.element()));
            default -> "value";
        };
    }
    public static String problem(String value, FieldType declared, List<TagParser.Entry> tags) {
        var env = tags.stream().filter(e -> e.key().equals("env")).findFirst();
        if (env.isPresent()) {
            List<String> options = TagValidator.split(env.get().value(), ',');
            if (options.get(0).equals("-") || options.subList(1, options.size()).contains("file")) return null;
            if (options.subList(1, options.size()).contains("expand") && value.contains("$")) return null;
        }
        // env skips conversion altogether for an empty default.
        if (value.isEmpty() || custom(declared)) return null;
        FieldType type = declared.dereference();
        if (type.unknown()) return null;
        if (type.kind() == SLICE) {
            if (type.element() == null) return null;
            String[] parts = value.split(Pattern.quote(separator(tags, "envSeparator", ",")), -1);
            for (int i = 0; i < parts.length; i++) {
                String problem = scalar(parts[i], type.element());
                if (problem != null) return "slice element " + (i + 1) + ": " + problem;
            }
            return null;
        }
        if (type.kind() == MAP) {
            String keyValue = separator(tags, "envKeyValSeparator", ":");
            String[] entries = value.split(Pattern.quote(separator(tags, "envSeparator", ",")), -1);
            for (int i = 0; i < entries.length; i++) {
                int at = entries[i].indexOf(keyValue);
                if (at < 0) return "map entry " + (i + 1) + " must use key" + keyValue + "value format";
                String keyProblem = type.key() == null ? null : scalar(entries[i].substring(0, at), type.key());
                if (keyProblem != null) return "map key " + (i + 1) + ": " + keyProblem;
                String valueProblem = type.element() == null ? null : scalar(entries[i].substring(at + keyValue.length()), type.element());
                if (valueProblem != null) return "map value " + (i + 1) + ": " + valueProblem;
            }
            return null;
        }
        return scalar(value, declared);
    }
    private static boolean custom(FieldType type) { return type.has("UnmarshalText") || type.dereference().has("UnmarshalText"); }
    private static String separator(List<TagParser.Entry> tags, String key, String fallback) {
        return tags.stream().filter(e -> e.key().equals(key) && !e.value().isEmpty()).map(TagParser.Entry::value).findFirst().orElse(fallback);
    }
    private static String scalar(String value, FieldType declared) {
        FieldType type = declared.dereference();
        if (custom(declared) || type.unknown()) return null;
        if (type.envParser().equals("time.Duration")) return duration(value);
        // URL and location parsing depends on Go's parsers and the runtime zone database.
        if (!type.envParser().isEmpty() || type.kind() == STRUCT) return null;
        if (type.kind() == STRING) return null;
        if (type.kind() == BOOLEAN) return BOOLS.contains(value) ? null : "expected a Go boolean (true/false, 1/0, t/f or their supported uppercase forms)";
        if (type.kind() == INTEGER || type.kind() == UNSIGNED) {
            boolean unsigned = type.kind() == UNSIGNED;
            if (type.name().equals("uintptr")) return "env has no built-in parser for uintptr";
            if (!value.matches(unsigned ? "[0-9]+" : "[+-]?[0-9]+")) return "expected a decimal integer for " + type.name();
            String width = type.name().replaceAll("[^0-9]", "");
            int bits = Set.of("int", "uint").contains(type.name()) ? 32 : width.isEmpty() ? 64 : Integer.parseInt(width);
            BigInteger number = new BigInteger(value);
            BigInteger min = unsigned ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
            BigInteger max = BigInteger.ONE.shiftLeft(unsigned ? bits : bits - 1).subtract(BigInteger.ONE);
            return number.compareTo(min) < 0 || number.compareTo(max) > 0 ? "integer is outside the env parser range for " + type.name() + " (" + bits + " bits)" : null;
        }
        if (type.kind() == FLOAT) {
            if (Set.of("nan", "inf", "+inf", "-inf", "infinity", "+infinity", "-infinity").contains(value.toLowerCase(Locale.ROOT))) return null;
            if (!FLOAT_VALUE.matcher(value).matches()) return "expected a Go floating-point literal for " + type.name();
            try {
                String normalized = value.replace("_", "");
                boolean overflow = type.name().equals("float32") ? Float.isInfinite(Float.parseFloat(normalized)) : Double.isInfinite(Double.parseDouble(normalized));
                return overflow ? "floating-point value is outside the range of " + type.name() : null;
            } catch (NumberFormatException error) { return "expected a Go floating-point literal for " + type.name(); }
        }
        return "env has no built-in scalar parser for " + type.name();
    }
    private static String duration(String value) {
        String number = value.startsWith("-") || value.startsWith("+") ? value.substring(1) : value;
        if (number.equals("0")) return null;
        var matcher = DURATION.matcher(number);
        int at = 0;
        BigInteger nanos = BigInteger.ZERO;
        while (matcher.find()) {
            if (matcher.start() != at) break;
            nanos = nanos.add(new BigDecimal(matcher.group(1)).multiply(BigDecimal.valueOf(UNITS.get(matcher.group(2)))).toBigInteger());
            at = matcher.end();
        }
        if (at == 0 || at != number.length()) return "expected a Go duration such as 300ms, 1.5h or 2h45m";
        BigInteger max = BigInteger.ONE.shiftLeft(63).subtract(value.startsWith("-") ? BigInteger.ZERO : BigInteger.ONE);
        return nanos.compareTo(max) > 0 ? "duration is outside the time.Duration range" : null;
    }
}
