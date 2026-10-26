package dev.structtags.core;

import java.math.BigInteger;
import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

/** Field names are Go names, not JSON/form aliases. Runtime nil values are not predicted. */
public final class ValidatorReferences {
    private ValidatorReferences() {}
    public enum Status { FOUND, MISSING, UNKNOWN, AMBIGUOUS }
    public enum Step { VALUE, KEY }
    public record Resolved(Status status, FieldType type) {
        public static Resolved found(FieldType type) { return new Resolved(Status.FOUND, type); }
        public static Resolved unknown() { return new Resolved(Status.UNKNOWN, FieldType.UNKNOWN); }
    }
    public interface Lookup {
        /** null traversal starts at the parent struct; a list starts at this field's type. */
        Resolved resolve(String path, List<Step> traversal);
    }
    private static final Set<String> COMPARE = Set.of("eqfield", "nefield", "gtfield", "gtefield", "ltfield", "ltefield", "eqcsfield", "necsfield", "gtcsfield", "gtecsfield", "ltcsfield", "ltecsfield");
    private static final Set<String> PAIRS = Set.of("required_if", "required_unless", "excluded_if", "excluded_unless", "skip_unless");
    private static final Set<String> PRESENCE = Set.of("required_with", "required_with_all", "required_without", "required_without_all", "excluded_with", "excluded_with_all", "excluded_without", "excluded_without_all");
    public record Completion(String rule, String prefix, FieldType current, List<Step> traversal,
                             Set<String> used, boolean fieldPosition) {}
    /** A non-null context owns this parameter, even while entering a literal rather than a path. */
    public static Completion completion(TagCompletion.Context context, FieldType declared, Set<String> custom) {
        if (context.keyPosition() || !Set.of("validate", "binding").contains(context.key())) return null;
        int equal = context.prefix().indexOf('=');
        if (equal < 0) return null;
        String name = context.prefix().substring(0, equal);
        if (custom.contains(name) || !(COMPARE.contains(name) || PAIRS.contains(name) || PRESENCE.contains(name)
                || Set.of("unique", "fieldcontains", "fieldexcludes", "postcode_iso3166_alpha2_field").contains(name))) return null;
        String param = context.prefix().substring(equal + 1);
        List<String> completed = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quote = false, started = false, quoted = false;
        for (int i = 0; i < param.length(); i++) {
            char c = param.charAt(i);
            if (c == '\'') { quote = !quote; quoted = true; started = true; }
            else if (Character.isWhitespace(c) && !quote) {
                if (started) { completed.add(word.toString()); word.setLength(0); started = false; quoted = false; }
            } else { word.append(c); started = true; }
        }
        boolean fieldPosition = !quote && !quoted && (PAIRS.contains(name) ? completed.size() % 2 == 0
            : PRESENCE.contains(name) || completed.isEmpty());
        Set<String> used = new HashSet<>();
        if (name.equals("required_if")) for (int i = 0; i < completed.size(); i += 2) used.add(completed.get(i));
        FieldType current = ValidatorTypeRules.stateBefore(context, declared, custom).current();
        List<Step> traversal = null;
        if (name.equals("unique")) {
            FieldType collection = current.dereference();
            fieldPosition &= collection.collection() && collection.element() != null && collection.element().dereference().kind() == STRUCT;
            traversal = new ArrayList<>();
            Deque<List<Step>> mapValues = new ArrayDeque<>();
            String before = context.valueBefore().substring(0, context.valueBefore().length() - context.prefix().length());
            for (String and : TagValidator.split(before, ',')) for (String token : TagValidator.split(and, '|')) {
                if (custom.contains(token)) continue;
                if (token.equals("dive")) traversal.add(Step.VALUE);
                if (token.equals("keys") && !traversal.isEmpty()) { mapValues.push(List.copyOf(traversal)); traversal.set(traversal.size() - 1, Step.KEY); }
                if (token.equals("endkeys") && !mapValues.isEmpty()) traversal = new ArrayList<>(mapValues.pop());
            }
            traversal.add(Step.VALUE);
            traversal = List.copyOf(traversal);
        }
        fieldPosition &= ValidatorTypeRules.problem(name, current) == null;
        return new Completion(name, word.toString(), current, traversal, Set.copyOf(used), fieldPosition);
    }
    public static boolean supportsCompletion(Completion context, String path, FieldType target) {
        if (!context.fieldPosition() || context.used().contains(path)) return false;
        String name = context.rule();
        if (COMPARE.contains(name) || Set.of("fieldcontains", "fieldexcludes", "postcode_iso3166_alpha2_field").contains(name))
            return comparisonProblem(name, context.current(), target) == null;
        if (name.equals("unique")) return !Set.of(MAP, SLICE, FUNCTION).contains(target.dereference().kind());
        if (PAIRS.contains(name)) return literalProblem(target, "0") == null || literalProblem(target, "false") == null || literalProblem(target, "nil") == null;
        return true;
    }
    public static List<TagValidator.Finding> validate(String literal, FieldType declared, Lookup lookup, Set<String> custom) {
        List<TagValidator.Finding> out = new ArrayList<>();
        for (var entry : TagParser.parse(literal).entries()) {
            if (!entry.key().equals("validate") && !entry.key().equals("binding")) continue;
            var state = new ValidatorTypeRules.State(declared);
            List<Step> traversal = new ArrayList<>();
            Deque<List<Step>> mapValues = new ArrayDeque<>();
            for (String and : TagValidator.split(entry.value(), ',')) for (String token : TagValidator.split(and, '|')) {
                String[] rule = token.split("=", 2);
                String name = rule[0];
                String param = rule.length == 2 ? rule[1] : "";
                if (custom.contains(name)) { state.accept(token, custom); continue; }
                if (COMPARE.contains(name) || Set.of("fieldcontains", "fieldexcludes", "postcode_iso3166_alpha2_field").contains(name)) {
                    if (!param.isEmpty()) {
                        Resolved target = reference(out, entry, name, param, lookup, null);
                        if (target.status() == Status.FOUND) {
                            String problem = comparisonProblem(name, state.current(), target.type());
                            if (problem != null) add(out, entry, name + " reference '" + param + "': " + problem);
                        }
                    }
                } else if (PAIRS.contains(name) || PRESENCE.contains(name)) {
                    if (!param.isEmpty()) {
                        List<String> params;
                        try { params = parameters(param); }
                        catch (IllegalArgumentException error) { add(out, entry, name + ": " + error.getMessage()); continue; }
                        if (PAIRS.contains(name) && params.size() % 2 != 0) {
                            String path = params.get(params.size() - 1);
                            Resolved target = reference(out, entry, name, path, lookup, null);
                            String hint = target.status() == Status.FOUND && target.type().dereference().kind() == STRING
                                ? "; use " + name + "=" + param.stripTrailing() + " '' to compare with an empty string (trailing whitespace is not a value)"
                                : "; missing comparison value for '" + path + "'";
                            add(out, entry, name + " requires Field Value pairs" + hint); continue;
                        }
                        Set<String> seen = new HashSet<>();
                        for (int i = 0; i < params.size(); i += PAIRS.contains(name) ? 2 : 1) {
                            String path = params.get(i);
                            if (name.equals("required_if") && !seen.add(path)) add(out, entry, "required_if repeats reference '" + path + "'");
                            Resolved target = reference(out, entry, name, path, lookup, null);
                            if (PAIRS.contains(name) && target.status() == Status.FOUND) {
                                String problem = literalProblem(target.type(), params.get(i + 1));
                                if (problem != null) add(out, entry, name + " comparison for '" + path + "': " + problem);
                            }
                        }
                    }
                } else if (name.equals("unique") && !param.isEmpty() && state.current().dereference().collection() && state.current().dereference().element() != null && state.current().dereference().element().dereference().kind() == STRUCT) {
                    List<Step> elements = new ArrayList<>(traversal); elements.add(Step.VALUE);
                    Resolved target = reference(out, entry, name, param, lookup, elements);
                    if (target.status() == Status.FOUND && Set.of(MAP, SLICE, FUNCTION).contains(target.type().dereference().kind()))
                        add(out, entry, "unique reference '" + param + "' must be comparable");
                }
                if (name.equals("dive")) traversal.add(Step.VALUE);
                if (name.equals("keys") && !traversal.isEmpty()) { mapValues.push(List.copyOf(traversal)); traversal.set(traversal.size() - 1, Step.KEY); }
                if (name.equals("endkeys") && !mapValues.isEmpty()) traversal = new ArrayList<>(mapValues.pop());
                state.accept(token, custom);
            }
        }
        return out;
    }
    private static Resolved reference(List<TagValidator.Finding> out, TagParser.Entry entry, String rule, String path, Lookup lookup, List<Step> traversal) {
        Resolved result = lookup.resolve(path, traversal);
        if (result.status() == Status.MISSING) {
            String hint = "; references use case-sensitive Go field names, not JSON/form tag names";
            if (!path.isEmpty() && Character.isLowerCase(path.charAt(0))) {
                String candidate = Character.toUpperCase(path.charAt(0)) + path.substring(1);
                if (lookup.resolve(candidate, traversal).status() == Status.FOUND)
                    hint = "; use Go field path '" + candidate + "'" + hint;
            }
            add(out, entry, rule + " references missing or invalid Go field path '" + path + "'" + hint);
        }
        if (result.status() == Status.AMBIGUOUS) add(out, entry, rule + " references ambiguous promoted field '" + path + "'");
        return result;
    }
    public static String comparisonProblem(String rule, FieldType left, FieldType right) {
        FieldType a = left.dereference(), b = right.dereference();
        if (a.unknown() || b.unknown() || a.has("ValidatorValue") || b.has("ValidatorValue")) return null;
        if (Set.of("fieldcontains", "fieldexcludes", "postcode_iso3166_alpha2_field").contains(rule))
            return b.kind() == STRING ? null : "referenced field must be a string";
        if (a.kind() != b.kind()) return "incompatible datatypes (" + a.name() + " and " + b.name() + ")";
        if (a.numeric() && !a.name().equals(b.name())) return "comparison requires the same Go numeric kind (" + a.name() + " and " + b.name() + ")";
        if (a.kind() == STRUCT && a.name().equals("time.Time") != b.name().equals("time.Time")) return "time.Time must be compared with a time-compatible field";
        if (a.kind() == STRUCT && !a.name().equals("struct") && !b.name().equals("struct") && !a.name().equals(b.name())) return "comparison requires matching struct types (" + a.name() + " and " + b.name() + ")";
        return null;
    }
    public static String literalProblem(FieldType declared, String value) {
        FieldType type = declared.dereference();
        if (type.unknown() || type.has("ValidatorValue")) return null;
        if (value.equals("nil") && (declared.kind() == POINTER || type.kind() == SLICE || type.kind() == MAP)) return null;
        if (type.kind() == STRING) return null;
        if (type.kind() == BOOLEAN) return Set.of("true", "false").contains(value) ? null : "expected true or false for a boolean field";
        if (type.kind() == INTEGER || type.kind() == UNSIGNED) {
            try {
                if (!value.matches("[+-]?[0-9]+")) throw new NumberFormatException();
                BigInteger number = new BigInteger(value);
                int bits = type.name().replaceAll("[^0-9]", "").isEmpty() ? 64 : Integer.parseInt(type.name().replaceAll("[^0-9]", ""));
                BigInteger min = type.kind() == UNSIGNED ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits - 1).negate();
                BigInteger max = BigInteger.ONE.shiftLeft(type.kind() == UNSIGNED ? bits : bits - 1).subtract(BigInteger.ONE);
                if (number.compareTo(min) < 0 || number.compareTo(max) > 0) return "integer literal is outside the range of " + type.name();
                return null;
            } catch (NumberFormatException error) { return "expected an integer literal for " + type.name(); }
        }
        if (type.kind() == FLOAT) {
            try {
                if (Set.of("NaN", "Inf", "+Inf", "-Inf").contains(value)) return null;
                double number = Double.parseDouble(value);
                if (!Double.isFinite(number) || type.name().equals("float32") && Math.abs(number) > Float.MAX_VALUE) return "floating-point literal is outside the field's range";
                return null;
            } catch (NumberFormatException error) { return "expected a numeric literal for " + type.name(); }
        }
        if (type.collection() || type.kind() == MAP) {
            try { if (new BigInteger(value).signum() >= 0) return null; } catch (NumberFormatException ignored) {}
            return "expected a non-negative collection length" + (type.kind() == ARRAY ? "" : " or nil");
        }
        return "this datatype does not support a scalar condition literal";
    }
    public static List<String> parameters(String value) {
        List<String> out = new ArrayList<>();
        StringBuilder word = new StringBuilder(); boolean quote = false, started = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'') { quote = !quote; started = true; continue; }
            if (Character.isWhitespace(c) && !quote) {
                if (started) { out.add(word.toString()); word.setLength(0); started = false; }
            } else { word.append(c); started = true; }
        }
        if (quote) throw new IllegalArgumentException("unclosed quoted parameter");
        if (started) out.add(word.toString());
        return out;
    }
    private static void add(List<TagValidator.Finding> out, TagParser.Entry entry, String message) {
        out.add(new TagValidator.Finding(entry.valueStart(), entry.valueEnd(), message, null));
    }
}
