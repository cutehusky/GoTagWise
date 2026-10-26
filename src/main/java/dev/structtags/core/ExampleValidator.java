package dev.structtags.core;

import java.util.*;
import java.util.regex.Pattern;
import static dev.structtags.core.FieldType.Kind.*;

/** Huma example tags describe schema values, rather than env/Go conversion literals. */
public final class ExampleValidator {
    private ExampleValidator() {}
    private enum Shape { ANY, STRING, BOOLEAN, INTEGER, NUMBER, ARRAY, OBJECT, UNSUPPORTED }
    private static Shape shape(FieldType declared) {
        FieldType type = declared.dereference();
        if (type.unknown() || declared.has("Schema") || declared.has("TransformSchema")) return Shape.ANY;
        if (Set.of("time.Time", "net/url.URL", "net.IP", "net/netip.Addr").contains(type.name()) || declared.has("UnmarshalText")) return Shape.STRING;
        if (declared.customSchema() || type.customSchema()) return Shape.ANY;
        if (type.collection() && type.element() != null && type.element().name().equals("uint8")) return Shape.STRING;
        return switch (type.kind()) {
            case STRING -> Shape.STRING;
            case BOOLEAN -> Shape.BOOLEAN;
            case INTEGER, UNSIGNED -> Shape.INTEGER;
            case FLOAT -> Shape.NUMBER;
            case SLICE, ARRAY -> Shape.ARRAY;
            case MAP, STRUCT -> Shape.OBJECT;
            default -> Shape.UNSUPPORTED;
        };
    }
    public static String keyProblem(FieldType type) {
        return shape(type) == Shape.UNSUPPORTED ? "Huma example has no schema for this field datatype (" + type.dereference().name() + ")" : null;
    }
    public static String problem(String value, FieldType type) {
        Shape shape = shape(type);
        if (shape == Shape.ANY || shape == Shape.STRING) return null;
        if (shape == Shape.UNSUPPORTED) return keyProblem(type);
        FieldType element = type.dereference().element();
        if (shape == Shape.ARRAY && element != null && shape(element) == Shape.STRING && !value.isEmpty() && value.charAt(0) != '[') return null;
        Object parsed;
        try { parsed = new Json(value).parse(); }
        catch (TooDeep ignored) { return null; }
        catch (IllegalArgumentException error) { return "expected valid JSON for " + type.dereference().name() + ": " + error.getMessage(); }
        return check(parsed, type, "");
    }
    private static String check(Object value, FieldType declared, String path) {
        Shape shape = shape(declared);
        if (shape == Shape.ANY) return null;
        boolean valid = switch (shape) {
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
            case INTEGER -> value instanceof Double number && number == Math.rint(number);
            case NUMBER -> value instanceof Double;
            case ARRAY -> value instanceof List<?>;
            case OBJECT -> value instanceof Map<?, ?>;
            default -> false;
        };
        if (!valid) return path + "expected " + switch (shape) {
            case STRING -> "a JSON string";
            case BOOLEAN -> "a JSON boolean (true or false)";
            case INTEGER -> "an integral JSON number";
            case NUMBER -> "a JSON number";
            case ARRAY -> "a JSON array";
            case OBJECT -> "a JSON object";
            default -> "a supported Huma schema datatype";
        };
        FieldType type = declared.dereference();
        if (shape == Shape.ARRAY && type.element() != null) {
            List<?> items = (List<?>) value;
            for (int i = 0; i < items.size(); i++) {
                String problem = check(items.get(i), type.element(), path + "[" + i + "]: ");
                if (problem != null) return problem;
            }
        }
        if (shape == Shape.OBJECT && type.kind() == MAP && type.element() != null) {
            for (var entry : ((Map<?, ?>) value).entrySet()) {
                String problem = check(entry.getValue(), type.element(), path + "[" + entry.getKey() + "]: ");
                if (problem != null) return problem;
            }
        }
        return null;
    }
    public static String example(FieldType type) {
        if (shape(type) == Shape.STRING || shape(type) == Shape.ANY) return "value";
        return jsonExample(type);
    }
    private static String jsonExample(FieldType declared) {
        return switch (shape(declared)) {
            case BOOLEAN -> "true";
            case INTEGER -> "1";
            case NUMBER -> "1.5";
            case STRING -> "\"value\"";
            case ARRAY -> declared.dereference().element() == null ? "[]" : "[" + jsonExample(declared.dereference().element()) + "]";
            case OBJECT -> "{}";
            default -> "null";
        };
    }
    private static final class TooDeep extends RuntimeException {}
    /** Small strict JSON reader keeps the core independent of IDE or third-party JSON libraries. */
    private static final class Json {
        private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
        private final String text;
        private int at;
        Json(String text) { this.text = text; }
        Object parse() {
            Object value = value(0);
            space();
            if (at != text.length()) throw error("unexpected trailing content");
            return value;
        }
        private Object value(int depth) {
            if (depth > 128) throw new TooDeep();
            space();
            if (at == text.length()) throw error("missing JSON value");
            char c = text.charAt(at);
            if (c == '"') return string();
            if (c == '[') {
                at++;
                List<Object> items = new ArrayList<>();
                if (take(']')) return items;
                do { items.add(value(depth + 1)); } while (take(','));
                require(']'); return items;
            }
            if (c == '{') {
                at++;
                Map<String, Object> items = new LinkedHashMap<>();
                if (take('}')) return items;
                do {
                    space();
                    if (at == text.length() || text.charAt(at) != '"') throw error("object key must be a JSON string");
                    String key = string(); require(':'); items.put(key, value(depth + 1));
                } while (take(','));
                require('}'); return items;
            }
            for (String token : List.of("true", "false", "null")) if (text.startsWith(token, at)) {
                at += token.length();
                return token.equals("null") ? null : token.equals("true");
            }
            var number = NUMBER.matcher(text); number.region(at, text.length());
            if (number.lookingAt()) {
                at = number.end();
                double parsed = Double.parseDouble(number.group());
                if (!Double.isFinite(parsed)) throw error("JSON number exceeds float64 range");
                return parsed;
            }
            throw error("invalid JSON value");
        }
        private String string() {
            at++;
            StringBuilder out = new StringBuilder();
            while (at < text.length()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c < 32) throw error("unescaped control character in JSON string");
                if (c != '\\') { out.append(c); continue; }
                if (at == text.length()) throw error("incomplete JSON escape");
                char escaped = text.charAt(at++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > text.length()) throw error("incomplete JSON unicode escape");
                        String hex = text.substring(at, at + 4);
                        if (!hex.matches("[0-9a-fA-F]{4}")) throw error("invalid JSON unicode escape");
                        out.append((char) Integer.parseInt(hex, 16)); at += 4;
                    }
                    default -> throw error("invalid JSON escape");
                }
            }
            throw error("unclosed JSON string");
        }
        private boolean take(char c) { space(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        private void require(char c) { if (!take(c)) throw error("expected '" + c + "'"); }
        private void space() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at offset " + at); }
    }
}
