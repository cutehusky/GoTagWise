package dev.structtags.core;

import java.util.*;

/** Completion state from text before the caret. Works while the tag is incomplete. */
public final class TagCompletion {
    private TagCompletion() {}
    public record Context(String key, String prefix, String valueBefore, boolean keyPosition) {}
    public static Context context(String before) {
        int i = 0;
        while (i < before.length()) {
            while (i < before.length() && before.charAt(i) == ' ') i++;
            int start = i;
            while (i < before.length() && TagParser.validKey(before.charAt(i))) i++;
            if (i == before.length()) return new Context("", before.substring(start), "", true);
            if (i == start || before.charAt(i++) != ':' || i >= before.length() || before.charAt(i++) != '"') return null;
            String key = before.substring(start, i - 2);
            int valueStart = i; boolean escaped = false;
            while (i < before.length()) {
                char c = before.charAt(i);
                if (c == '"' && !escaped) break;
                if (c == '\\' && !escaped) escaped = true; else escaped = false;
                i++;
            }
            if (i == before.length()) {
                String value = before.substring(valueStart);
                char delimiter = key.equals("gorm") ? ';' : ',';
                List<String> pieces = TagValidator.split(value, delimiter);
                String prefix = pieces.get(pieces.size() - 1);
                if (key.equals("validate") || key.equals("binding")) {
                    List<String> ors = TagValidator.split(prefix, '|'); prefix = ors.get(ors.size() - 1);
                }
                return new Context(key, prefix, value, false);
            }
            i++;
            if (i < before.length() && before.charAt(i) != ' ') return null;
        }
        return new Context("", "", "", true);
    }
    public static List<String> values(Context context, String field) {
        TagCatalog.Spec spec = TagCatalog.get(context.key());
        if (spec == null) return List.of();
        List<String> result = new ArrayList<>();
        boolean named = spec.kind() == TagCatalog.Kind.NAME_OPTIONS;
        if (named && !context.valueBefore().contains(",")) {
            if (!field.isBlank()) result.add(TagCatalog.name(field, context.key().equals("env") ? "upper" : context.key().equals("json") ? "camel" : "snake"));
            result.add("-");
            if (!context.key().equals("bun")) return result;
        }
        for (String word : spec.words()) {
            String candidate = word;
            if (spec.key().equals("gorm")) candidate = word.equals("not_null") ? "not null" : word;
            List<String> used = TagValidator.split(context.valueBefore(), spec.key().equals("gorm") ? ';' : ',');
            boolean validation = spec.key().equals("validate") || spec.key().equals("binding");
            if (validation) {
                int levelStart = 0;
                for (int i = 0; i < used.size(); i++) if (List.of("dive", "keys", "endkeys").contains(used.get(i))) levelStart = i + 1;
                used = used.subList(levelStart, used.size());
            }
            if (validation && List.of("dive", "keys", "endkeys").contains(candidate) || !used.contains(candidate)) result.add(candidate);
        }
        return result;
    }
    public static List<String> values(Context context, String field, FieldType type, boolean humaInput, Set<String> custom) {
        List<String> candidates = new ArrayList<>(values(context, field));
        if (context.key().equals("validate") || context.key().equals("binding")) {
            candidates.add("unique"); candidates.add("validateFn");
            var state = ValidatorTypeRules.stateBefore(context, type, custom);
            candidates.replaceAll(word -> state.timeComparison(word) && word.endsWith("=") ? word.substring(0, word.length() - 1) : word);
        }
        if (context.key().equals("validate") || context.key().equals("binding") || context.key().equals("conform"))
            for (String rule : custom) if (!candidates.contains(rule)) candidates.add(rule);
        return candidates.stream().filter(value -> TagTypeSupport.supportsValue(context, value, type, humaInput, custom)).toList();
    }
}
