package dev.structtags.core;

import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

/** Static compatibility checks; unknown and custom-encoded types are left to their library. */
public final class TagTypeValidator {
    private TagTypeValidator() {}
    public static List<TagValidator.Finding> validate(String literal, FieldType declared, boolean humaInput) {
        return validate(literal, declared, humaInput, Set.of());
    }
    public static List<TagValidator.Finding> validate(String literal, FieldType declared, boolean humaInput, Set<String> customRules) {
        List<TagValidator.Finding> out = new ArrayList<>();
        FieldType type = declared.dereference();
        boolean bytes = type.collection() && type.element() != null && type.element().name().equals("uint8");
        FieldType schemaType = bytes ? FieldType.of(STRING, "base64 string") : type;
        for (var e : TagParser.parse(literal).entries()) {
            if (e.value().equals("-") && Set.of("json", "xml", "bson", "yaml", "toml", "msgpack", "dynamodbav", "redis").contains(e.key())) continue;
            String keyProblem = TagTypeSupport.keyProblem(e.key(), declared);
            if (keyProblem != null) { add(out, e, keyProblem); continue; }
            if (e.key().equals("validate") || e.key().equals("binding")) {
                for (String message : ValidatorTypeRules.validate(e.value(), declared, customRules)) add(out, e, message);
            }
            if (humaInput && Set.of("form", "header", "path", "query", "cookie").contains(e.key()) && !e.value().isEmpty()) {
                if (declared.kind() == POINTER && !declared.genericValueWrapper()) {
                    add(out, e, "Huma request parameter '" + e.key() + "' does not support pointer fields; use a value or a ParamWrapper value for optionality");
                } else if (Set.of(FUNCTION, CHANNEL, COMPLEX).contains(type.kind()) && !type.has("UnmarshalText") && !type.has("Receiver")) {
                    add(out, e, "Huma request parameter '" + e.key() + "' does not support this datatype (" + type.name() + ")");
                }
            }
            if (type.unknown()) continue;
            if (!declared.customSchema() && !type.customSchema() && !Set.of("time.Time", "net/url.URL", "net.IP").contains(type.name())) {
                if (Set.of("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf").contains(e.key()) && !schemaType.numeric())
                    add(out, e, "Huma '" + e.key() + "' requires a numeric field");
                if (Set.of("minLength", "maxLength", "pattern").contains(e.key()) && schemaType.kind() != STRING)
                    add(out, e, "Huma '" + e.key() + "' requires a string field");
                if (Set.of("minItems", "maxItems", "uniqueItems").contains(e.key()) && !schemaType.collection())
                    add(out, e, "Huma '" + e.key() + "' requires a slice or array field");
                if (Set.of("minProperties", "maxProperties").contains(e.key()) && schemaType.kind() != STRUCT && schemaType.kind() != MAP)
                    add(out, e, "Huma '" + e.key() + "' requires a struct or map field");
            }
            List<String> parts = TagValidator.split(e.value(), ',');
            if (parts.get(0).equals("-") && (!e.key().equals("json") || parts.size() == 1)) continue;
            Set<String> options = new HashSet<>(parts.subList(1, parts.size()));
            var extraOptions = new ArrayList<>(e.key().equals("gorm") ? TagValidator.split(e.value(), ';') : options);
            if (e.key().equals("bun") && parts.get(0).contains(":")) extraOptions.add(parts.get(0));
            for (String option : extraOptions) {
                String problem = TagTypeSupport.extraOptionProblem(e.key(), option, declared);
                if (problem != null) add(out, e, problem);
            }
            switch (e.key()) {
                case "json" -> {
                    if (options.contains("string") && !declared.customSchema() && !type.customSchema() && type.kind() != STRING && type.kind() != BOOLEAN && !type.numeric())
                        add(out, e, "JSON ',string' is only supported on string, boolean or numeric fields (or pointers to them)");
                }
                case "bson" -> {
                    if (declared.has("MarshalBSON") || declared.has("MarshalBSONValue") || declared.has("UnmarshalBSON") || declared.has("UnmarshalBSONValue")) break;
                    if (options.contains("inline") && !(type.kind() == STRUCT || declared.kind() != POINTER && type.kind() == MAP))
                        add(out, e, "BSON ',inline' requires a struct, pointer to struct, or map field");
                    if (options.contains("inline") && type.kind() == MAP && type.key() != null && !type.key().unknown() && type.key().kind() != STRING)
                        add(out, e, "BSON ',inline' requires string map keys");
                    if (options.contains("minsize") && !Set.of("int64", "uint", "uint32", "uint64").contains(type.name()))
                        add(out, e, "BSON ',minsize' has no effect on this datatype; supported types are int64, uint, uint32 and uint64");
                    if (options.contains("truncate") && type.kind() != INTEGER && type.kind() != UNSIGNED)
                        add(out, e, "BSON ',truncate' has no effect on this datatype; use it on an integer field");
                }
                case "yaml" -> {
                    if (declared.has("MarshalYAML") || declared.has("UnmarshalYAML")) break;
                    if (options.contains("inline") && !(type.kind() == STRUCT || declared.kind() != POINTER && type.kind() == MAP))
                        add(out, e, "YAML ',inline' requires a struct, pointer to struct, or map field");
                    if (options.contains("inline") && type.kind() == MAP && type.key() != null && !type.key().unknown() && type.key().kind() != STRING)
                        add(out, e, "YAML ',inline' requires string map keys");
                }
                case "dynamodbav" -> checkDynamo(out, e, declared, type, options);
            }
        }
        return List.copyOf(out);
    }
    private static void checkDynamo(List<TagValidator.Finding> out, TagParser.Entry e, FieldType declared, FieldType type, Set<String> options) {
        if (declared.has("MarshalDynamoDBAttributeValue") || declared.has("UnmarshalDynamoDBAttributeValue")) return;
        if (options.contains("string") && !dynamoNumber(type) && !type.has("MarshalText"))
            add(out, e, "DynamoDB ',string' requires a numeric field");
        if (options.contains("unixtime") && !type.name().equals("time.Time"))
            add(out, e, "DynamoDB ',unixtime' requires time.Time or a type with the same underlying representation");
        for (String option : List.of("omitemptyelem", "nullemptyelem")) {
            if (options.contains(option) && !type.collection() && type.kind() != MAP)
                add(out, e, "DynamoDB '," + option + "' requires a slice, array or map field");
        }
        for (String option : List.of("stringset", "numberset", "binaryset")) {
            if (!options.contains(option)) continue;
            if (!type.collection()) { add(out, e, "DynamoDB '," + option + "' requires a slice or array field"); continue; }
            FieldType element = type.element() == null ? FieldType.UNKNOWN : type.element().dereference();
            if (element.unknown() || element.has("MarshalDynamoDBAttributeValue") || element.has("MarshalText")) continue;
            boolean valid = switch (option) {
                case "stringset" -> element.kind() == STRING && !dynamoNumber(element) || element.name().equals("time.Time");
                case "numberset" -> dynamoNumber(element);
                default -> element.collection() && element.element() != null && element.element().name().equals("uint8");
            };
            if (!valid) add(out, e, "DynamoDB '," + option + "' has an incompatible element datatype; expected " + switch (option) {
                case "stringset" -> "string values"; case "numberset" -> "numeric values"; default -> "byte slices or byte arrays";
            });
        }
    }
    private static boolean dynamoNumber(FieldType type) {
        return type.numeric() || type.kind() == STRING && type.has("Float64") && type.has("Int64") && type.has("String");
    }
    private static void add(List<TagValidator.Finding> out, TagParser.Entry e, String message) {
        out.add(new TagValidator.Finding(e.start(), e.end(), message, null));
    }
}
