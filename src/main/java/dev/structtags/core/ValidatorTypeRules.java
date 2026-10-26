package dev.structtags.core;

import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

/** validator/v10 and Gin binding share these rules and traversal semantics. */
public final class ValidatorTypeRules {
    private ValidatorTypeRules() {}
    private static final Set<String> ANY = Set.of("required", "isdefault", "omitempty", "-", "eqfield", "nefield", "eqcsfield", "necsfield");
    private static final Set<String> LENGTH = Set.of("len", "min", "max", "gt", "gte", "lt", "lte");
    private static final Set<String> KNOWN = new HashSet<>();
    static { for (String word : TagCatalog.get("validate").words()) KNOWN.add(word.replace("=", "")); }
    public static String problem(String rule, FieldType declared) {
        String name = rule.split("=", 2)[0];
        FieldType type = declared.dereference();
        if (type.unknown() || !KNOWN.contains(name) || ANY.contains(name) || name.startsWith("required_") || name.startsWith("excluded_")) return null;
        boolean valid;
        String expected;
        if (name.equals("omitnil")) {
            valid = declared.kind() == POINTER || Set.of(SLICE, MAP, INTERFACE, CHANNEL, FUNCTION).contains(type.kind());
            expected = "a nil-capable field";
        } else if (name.equals("structonly") || name.equals("nostructlevel")) {
            valid = type.kind() == STRUCT; expected = "a struct field";
        } else if (Set.of("dive", "unique").contains(name)) {
            valid = type.collection() || type.kind() == MAP; expected = "a slice, array or map";
            if (valid && name.equals("unique") && type.element() != null && !type.element().dereference().unknown()) {
                FieldType element = type.element().dereference();
                if (rule.contains("=") && !rule.endsWith("=") && type.collection() && element.kind() != STRUCT)
                    return "Rule 'unique=Field' requires struct elements";
                if ((!rule.contains("=") || rule.endsWith("=")) && Set.of(MAP, SLICE, FUNCTION).contains(element.kind()))
                    return "Rule 'unique' requires comparable elements or values";
            }
        } else if (name.equals("validateFn")) {
            String method = rule.contains("=") && !rule.endsWith("=") ? rule.substring(rule.indexOf('=') + 1) : "Validate";
            valid = declared.has(method); expected = "a field with a '" + method + "' validation method";
        } else if (name.equals("keys") || name.equals("endkeys")) {
            return null; // Checked by traversal state, not just the current kind.
        } else if (LENGTH.contains(name) || Set.of("gtfield", "gtefield", "ltfield", "ltefield", "gtcsfield", "gtecsfield", "ltcsfield", "ltecsfield").contains(name)) {
            valid = type.kind() == STRING || type.numeric() || type.collection() || type.kind() == MAP || !name.equals("len") && type.name().equals("time.Time");
            expected = "a string, number, collection" + (name.equals("len") ? "" : " or time.Time");
        } else if (name.equals("eq") || name.equals("ne")) {
            valid = type.kind() == STRING || type.kind() == BOOLEAN || type.numeric() || type.collection() || type.kind() == MAP;
            expected = "a string, boolean, number or collection";
        } else if (Set.of("numeric", "number", "latitude", "longitude").contains(name)) {
            valid = type.kind() == STRING || type.numeric(); expected = "a string or numeric field";
        } else if (Set.of("oneof", "iso3166_1_alpha_numeric", "country_code", "eu_country_code", "luhn_checksum").contains(name)) {
            valid = type.kind() == STRING || type.kind() == INTEGER || type.kind() == UNSIGNED;
            expected = "a string or integer field";
        } else if (name.equals("iso4217_numeric")) {
            valid = type.kind() == INTEGER || type.kind() == UNSIGNED; expected = "an integer field";
        } else if (name.equals("boolean")) {
            valid = type.kind() == STRING || type.kind() == BOOLEAN; expected = "a string or boolean field";
        } else if (name.equals("json")) {
            valid = type.kind() == STRING || type.kind() == SLICE && type.element() != null && type.element().name().equals("uint8");
            expected = "a string or byte slice";
        } else {
            valid = type.kind() == STRING; expected = "a string field";
        }
        return valid ? null : "Rule '" + name + "' requires " + expected + " at this position";
    }
    public static final class State {
        private FieldType current;
        private FieldType pendingMap;
        private final Deque<FieldType> mapValues = new ArrayDeque<>();
        public State(FieldType field) { current = field; }
        public FieldType current() { return current; }
        public boolean timeComparison(String rule) { return current.dereference().name().equals("time.Time") && Set.of("gt", "gte", "lt", "lte", "min", "max").contains(rule.split("=", 2)[0]); }
        public String accept(String rule, Set<String> custom) {
            String name = rule.split("=", 2)[0];
            if (custom.contains(name)) { pendingMap = null; return null; }
            FieldType type = current.dereference();
            if (name.equals("keys")) {
                if (pendingMap == null) return "Rule 'keys' requires a map immediately after dive";
                mapValues.push(current);
                current = pendingMap.key() == null ? FieldType.UNKNOWN : pendingMap.key(); pendingMap = null;
                return null;
            }
            pendingMap = null;
            if (name.equals("endkeys")) {
                if (mapValues.isEmpty()) return "Rule 'endkeys' requires a preceding keys block";
                current = mapValues.pop(); return null;
            }
            String problem = problem(rule, current);
            if (name.equals("dive")) {
                pendingMap = type.kind() == MAP ? type : type.unknown() ? FieldType.container(MAP, FieldType.UNKNOWN, FieldType.UNKNOWN) : null;
                current = type.collection() || type.kind() == MAP ? type.element() == null ? FieldType.UNKNOWN : type.element() : FieldType.UNKNOWN;
            }
            return problem;
        }
        public boolean allows(String candidate, Set<String> custom) {
            String name = candidate.replace("=", "");
            if (custom.contains(name)) return true;
            if (name.equals("keys")) return pendingMap != null;
            if (name.equals("endkeys")) return !mapValues.isEmpty();
            if (candidate.equals("unique=")) {
                FieldType type = current.dereference();
                return type.unknown() || type.collection() && (type.element() == null || type.element().dereference().unknown() || type.element().dereference().kind() == STRUCT);
            }
            if (candidate.equals("validateFn=")) return current.dereference().unknown() || !current.dereference().methods().isEmpty();
            return problem(candidate, current) == null;
        }
    }
    public static List<String> validate(String value, FieldType type, Set<String> custom) {
        if (value.isEmpty()) return List.of();
        List<String> messages = new ArrayList<>();
        State state = new State(type);
        for (String and : TagValidator.split(value, ',')) {
            for (String or : TagValidator.split(and, '|')) {
                String problem = state.accept(or, custom);
                if (problem != null) messages.add(problem);
            }
        }
        return messages;
    }
    public static boolean supportsCandidate(TagCompletion.Context context, String candidate, FieldType type, Set<String> custom) {
        return stateBefore(context, type, custom).allows(candidate, custom);
    }
    public static State stateBefore(TagCompletion.Context context, FieldType type, Set<String> custom) {
        State state = new State(type);
        String before = context.valueBefore().substring(0, Math.max(0, context.valueBefore().length() - context.prefix().length()));
        for (String and : TagValidator.split(before, ',')) for (String or : TagValidator.split(and, '|')) if (!or.isEmpty()) state.accept(or, custom);
        return state;
    }
}
