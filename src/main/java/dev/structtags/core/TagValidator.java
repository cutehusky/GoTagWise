package dev.structtags.core;

import java.math.BigDecimal;
import java.util.*;

public final class TagValidator {
    private TagValidator() {}
    public record Finding(int start, int end, String message, String replacement) {}
    public record Options(boolean unknownRules, Set<String> customValidators, Set<String> customSanitizers) {
        public static Options defaults() { return new Options(true, Set.of(), Set.of()); }
    }
    private static final Set<String> BOOLEANS = Set.of("1", "0", "t", "T", "true", "TRUE", "True", "f", "F", "false", "FALSE", "False");
    public static List<Finding> validate(String literal, Options options) {
        return validate(literal, options, FieldType.UNKNOWN);
    }
    public static List<Finding> validate(String literal, Options options, FieldType fieldType) {
        TagParser.Parsed parsed = TagParser.parse(literal);
        List<Finding> out = new ArrayList<>();
        for (var issue : parsed.issues()) out.add(new Finding(issue.start(), issue.end(), issue.message(), null));
        Map<String, TagParser.Entry> seen = new HashMap<>();
        for (var entry : parsed.entries()) {
            if (seen.putIfAbsent(entry.key(), entry) != null)
                out.add(new Finding(entry.start(), entry.end(), "Duplicate tag '" + entry.key() + "'; Go reads the first value", null));
            TagCatalog.Spec spec = TagCatalog.get(entry.key());
            if (spec == null) continue;
            String value = entry.value();
            switch (spec.kind()) {
                case BOOLEAN -> { if (!BOOLEANS.contains(value)) add(out, entry, "Expected a boolean value (true or false)", null); }
                case NUMBER, NONNEGATIVE, POSITIVE -> {
                    try {
                        BigDecimal number = new BigDecimal(value);
                        if (!value.equals(value.trim())) throw new NumberFormatException();
                        if (spec.kind() == TagCatalog.Kind.NONNEGATIVE && (number.signum() < 0 || !value.matches("[+]?[0-9]+")))
                            add(out, entry, "Expected a non-negative integer", null);
                        if (spec.kind() == TagCatalog.Kind.POSITIVE && number.signum() <= 0)
                            add(out, entry, "Expected a number greater than zero", null);
                    } catch (NumberFormatException ex) { add(out, entry, "Expected a finite numeric value", null); }
                }
                case NAME_OPTIONS -> checkNameOptions(out, entry, spec);
                case RULES -> checkRules(out, entry, spec, options, fieldType);
                case DIRECTIVES -> checkDirectives(out, entry, spec, options);
                case TEXT -> {
                    if (entry.key().equals("collection_format") && !spec.words().contains(value))
                        add(out, entry, "Unknown collection format '" + value + "'", nearest(value, spec.words()));
                }
            }
        }
        for (String[] pair : List.of(new String[]{"minimum", "maximum"}, new String[]{"minLength", "maxLength"},
                new String[]{"minItems", "maxItems"}, new String[]{"minProperties", "maxProperties"})) {
            if (seen.containsKey(pair[0]) && seen.containsKey(pair[1])) {
                try {
                    if (new BigDecimal(seen.get(pair[0]).value()).compareTo(new BigDecimal(seen.get(pair[1]).value())) > 0)
                        add(out, seen.get(pair[1]), pair[1] + " must be at least " + pair[0], null);
                } catch (NumberFormatException ignored) { /* Individual values already diagnosed. */ }
            }
        }
        return List.copyOf(out);
    }
    private static void checkNameOptions(List<Finding> out, TagParser.Entry e, TagCatalog.Spec spec) {
        List<String> parts = split(e.value(), ',');
        // Bun permits directives in the first position instead of a column name.
        int first = e.key().equals("bun") && parts.get(0).contains(":") ? 0 : 1;
        for (int i = first; i < parts.size(); i++) {
            String token = parts.get(i);
            if (token.isEmpty()) continue;
            boolean known = spec.words().stream().anyMatch(word -> matchesOption(word, token));
            if (known) continue;
            String closest = nearest(token, spec.words());
            List<String> fixed = new ArrayList<>(parts);
            if (closest != null) fixed.set(i, closest);
            add(out, e, "Unknown " + e.key() + " option '" + token + "'", closest == null ? null : String.join(",", fixed));
        }
        if (e.key().equals("xml")) {
            long modes = parts.stream().skip(1).filter(Set.of("attr", "chardata", "cdata", "innerxml", "comment")::contains).count();
            if (modes > 1) add(out, e, "XML encoding modes cannot be combined", null);
        }
        if (e.key().equals("dynamodbav")) {
            conflict(out, e, parts, "omitempty", "nullempty");
            conflict(out, e, parts, "omitemptyelem", "nullemptyelem");
            if (parts.stream().filter(Set.of("binaryset", "numberset", "stringset")::contains).count() > 1)
                add(out, e, "Choose only one DynamoDB set encoding", null);
        }
    }
    private static void conflict(List<Finding> out, TagParser.Entry e, List<String> parts, String a, String b) {
        if (parts.contains(a) && parts.contains(b)) add(out, e, "Conflicting options: " + a + " and " + b, null);
    }
    private static boolean matchesOption(String word, String token) {
        if (word.endsWith(":") || word.endsWith("=")) return token.startsWith(word);
        return word.equals(token);
    }
    private static void checkRules(List<Finding> out, TagParser.Entry e, TagCatalog.Spec spec, Options options, FieldType fieldType) {
        if (e.value().isEmpty()) return;
        Set<String> custom = e.key().equals("conform") ? options.customSanitizers() : options.customValidators();
        var traversal = new ValidatorTypeRules.State(fieldType);
        for (String and : split(e.value(), ',')) {
            for (String rule : e.key().equals("conform") ? List.of(and) : split(and, '|')) {
                if (rule.isEmpty()) { add(out, e, "Empty validation rule", null); continue; }
                String key = rule.split("=", 2)[0];
                boolean timeComparison = !e.key().equals("conform") && traversal.timeComparison(rule);
                if (!e.key().equals("conform")) traversal.accept(rule, custom);
                if (custom.contains(key)) continue;
                String word = spec.words().stream().filter(w -> w.replace("=", "").equals(key)).findFirst().orElse(null);
                if (word == null) {
                    if (options.unknownRules()) add(out, e, "Unknown " + e.key() + " rule '" + key + "' (add custom rules in inspection settings)", null);
                } else if (word.endsWith("=") && (!rule.contains("=") || rule.endsWith("=")) && !Set.of("unique", "validateFn").contains(key) && !timeComparison) {
                    add(out, e, "Rule '" + key + "' requires a parameter", null);
                } else if (!word.endsWith("=") && rule.contains("=")) {
                    add(out, e, "Rule '" + key + "' does not accept a parameter", null);
                }
            }
        }
    }
    private static void checkDirectives(List<Finding> out, TagParser.Entry e, TagCatalog.Spec spec, Options options) {
        Set<String> known = new HashSet<>();
        for (String word : spec.words()) known.add(word.replace(":", "").replace('_', ' ').toLowerCase(Locale.ROOT));
        for (String token : split(e.value(), ';')) {
            if (token.isEmpty()) continue;
            String key = token.split(":", 2)[0].toLowerCase(Locale.ROOT);
            if (!known.contains(key)) {
                if (options.unknownRules()) add(out, e, "Unknown GORM directive '" + key + "'", null);
                continue;
            }
            if (Set.of("size", "precision", "scale", "autoincrementincrement").contains(key)) {
                String[] pair = token.split(":", 2);
                if (pair.length < 2 || !pair[1].matches("[0-9]+")) add(out, e, key + " requires a non-negative integer", null);
            }
            if (Set.of("autocreatetime", "autoupdatetime").contains(key) && token.contains(":")) {
                String value = token.substring(token.indexOf(':') + 1);
                if (!Set.of("nano", "milli", "true", "false").contains(value.toLowerCase(Locale.ROOT)))
                    add(out, e, "Expected nano, milli, true or false for " + key, null);
            }
        }
    }
    private static void add(List<Finding> out, TagParser.Entry e, String message, String replacement) {
        out.add(new Finding(e.valueStart(), Math.max(e.valueStart(), e.valueEnd()), message, replacement));
    }
    /** Do not split SQL functions, quoted validator arguments or escaped delimiters. */
    public static List<String> split(String value, char delimiter) {
        List<String> out = new ArrayList<>();
        int start = 0, depth = 0; char quote = 0; boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (quote != 0) { if (c == quote) quote = 0; continue; }
            if (c == '\'' || c == '"') { quote = c; continue; }
            if (c == '(') depth++;
            if (c == ')') depth = Math.max(0, depth - 1);
            if (c == delimiter && depth == 0) { out.add(value.substring(start, i)); start = i + 1; }
        }
        out.add(value.substring(start));
        return out;
    }
    private static String nearest(String token, List<String> words) {
        String best = null; int distance = 3;
        for (String word : words) {
            int score = distance(token, word);
            if (score < distance) { distance = score; best = word; }
        }
        return best;
    }
    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] next = new int[b.length() + 1]; next[0] = i;
            for (int j = 1; j <= b.length(); j++) next[j] = Math.min(Math.min(next[j-1] + 1, prev[j] + 1), prev[j-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1));
            prev = next;
        }
        return prev[b.length()];
    }
}
