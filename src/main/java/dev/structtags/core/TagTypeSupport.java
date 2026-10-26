package dev.structtags.core;

import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

/** All catalog keys go through the same compatibility policy for inspection and completion. */
public final class TagTypeSupport {
    private TagTypeSupport() {}
    private static final Set<String> SERIALIZATION = Set.of("json", "xml", "bson", "yaml", "toml", "msgpack", "dynamodbav", "redis");
    public static String keyProblem(String key, FieldType declared) {
        FieldType type = declared.dereference();
        if (type.unknown()) return null;
        if (key.equals("example")) return ExampleValidator.keyProblem(declared);
        if (key.equals("envDefault") && !declared.has("UnmarshalText") && !type.has("UnmarshalText")
                && (Set.of(ARRAY, FUNCTION, CHANNEL, COMPLEX).contains(type.kind()) || type.name().equals("uintptr")))
            return "envDefault has no built-in parser for this field datatype (" + type.name() + ")";
        if (SERIALIZATION.contains(key) && !custom(key, declared)) {
            if (Set.of(FUNCTION, CHANNEL, COMPLEX).contains(type.kind())) return key + " does not support this field datatype (" + type.name() + ")";
            if (key.equals("xml") && type.kind() == MAP) return "XML does not support map fields";
            if (key.equals("json") && type.kind() == MAP && type.key() != null) {
                FieldType mapKey = type.key().dereference();
                if (!mapKey.unknown() && !Set.of(STRING, INTEGER, UNSIGNED).contains(mapKey.kind()) && !mapKey.has("MarshalText"))
                    return "JSON map keys require string, integer or TextMarshaler types";
            }
        }
        if (key.equals("envPrefix") && type.kind() != STRUCT) return "envPrefix requires a nested struct field";
        if (key.equals("envSeparator") && !type.collection() && type.kind() != MAP) return "envSeparator requires a slice, array or map field";
        if (key.equals("envKeyValSeparator") && type.kind() != MAP) return "envKeyValSeparator requires a map field";
        if (Set.of("time_format", "time_utc", "time_location").contains(key) && !timeLike(type)) return key + " requires time.Time or a collection of time.Time values";
        if (key.equals("conform") && !stringLike(type)) return "conform sanitizers require string fields or containers of strings";
        if (key.equals("collection_format") && !type.collection()) return "Gin collection_format requires a slice or array field";
        if ((key.equals("multiline") || key.equals("literal")) && !stringLike(type)) return "TOML " + key + " requires a string field or string container";
        if (Set.of("env", "form", "header", "uri", "gorm", "bun").contains(key) && Set.of(FUNCTION, CHANNEL, COMPLEX).contains(type.kind()) && !custom(key, declared) && !type.has("UnmarshalText") && !type.has("UnmarshalParam"))
            return key + " has no built-in support for this datatype (" + type.name() + ")";
        return null;
    }
    public static String extraOptionProblem(String key, String option, FieldType declared) {
        FieldType type = declared.dereference();
        if (type.unknown() || custom(key, declared)) return null;
        if (key.equals("env") && option.equals("init") && declared.kind() != POINTER) return "env ',init' requires a pointer field";
        if (key.equals("yaml") && option.equals("flow") && !type.collection() && type.kind() != MAP && type.kind() != STRUCT) return "YAML ',flow' requires a struct, map, slice or array";
        if (key.equals("msgpack") && option.equals("inline") && type.kind() != STRUCT) return "MessagePack ',inline' requires a struct field";
        if (key.equals("msgpack") && option.equals("asArray") && type.kind() != STRUCT) return "MessagePack ',asArray' requires the struct marker field";
        if (key.equals("xml") && (option.equals("innerxml") || option.equals("comment")) && type.kind() != STRING && !bytes(type)) return "XML '," + option + "' requires a string or byte collection";
        if (key.equals("hcl")) {
            if ((option.equals("label") || option.equals("key")) && type.kind() != STRING) return "HCL '," + option + "' requires a string field";
            if (option.equals("block") && type.kind() != STRUCT && !(type.collection() && (type.element() == null || type.element().dereference().unknown() || type.element().dereference().kind() == STRUCT))) return "HCL ',block' requires a struct or collection of structs";
        }
        String directive = option.split(":", 2)[0].toLowerCase(Locale.ROOT);
        if (key.equals("gorm")) {
            if (directive.equals("embedded") && type.kind() != STRUCT) return "GORM embedded requires a struct field";
            if (Set.of("autoincrement", "autoincrementincrement").contains(directive) && type.kind() != INTEGER && type.kind() != UNSIGNED) return "GORM " + directive + " requires an integer field";
            if (Set.of("autocreatetime", "autoupdatetime").contains(directive) && !type.name().equals("time.Time") && type.kind() != INTEGER && type.kind() != UNSIGNED) return "GORM " + directive + " requires time.Time or an integer field";
        }
        if (key.equals("bun")) {
            if (Set.of("table", "alias", "select", "extend").contains(directive) && type.kind() != STRUCT) return "Bun " + directive + " requires a struct model field";
            if (directive.equals("array") && !type.collection()) return "Bun array requires a slice or array field";
            if (directive.equals("embed") && type.kind() != STRUCT) return "Bun embed requires a struct field";
            if (Set.of("rel", "m2m").contains(directive) && type.kind() != STRUCT && !type.collection()) return "Bun relationships require a struct or collection field";
        }
        return null;
    }
    public static boolean supportsKey(String key, FieldType type, boolean humaInput) {
        TagCatalog.Spec spec = TagCatalog.get(key);
        if (spec == null) return true;
        if (key.equals("envDefault") || key.equals("example")) return keyProblem(key, type) == null;
        // These keys establish Huma input context themselves while being completed.
        boolean huma = humaInput || Set.of("query", "path", "cookie").contains(key);
        return TagTypeValidator.validate(literal(key, spec.example()), type, huma).isEmpty();
    }
    public static boolean supportsValue(TagCompletion.Context context, String candidate, FieldType type, boolean humaInput, Set<String> custom) {
        if (!supportsKey(context.key(), type, humaInput)) return false;
        if (context.key().equals("validate") || context.key().equals("binding")) return ValidatorTypeRules.supportsCandidate(context, candidate, type, custom);
        TagCatalog.Spec spec = TagCatalog.get(context.key());
        if (spec == null) return true;
        if (spec.kind() == TagCatalog.Kind.NAME_OPTIONS) {
            if (!context.valueBefore().contains(",") && !spec.key().equals("bun")) return true;
            return TagTypeValidator.validate(literal(spec.key(), "field," + candidate), type, humaInput).isEmpty();
        }
        if (spec.kind() == TagCatalog.Kind.DIRECTIVES) return TagTypeValidator.validate(literal(spec.key(), candidate), type, humaInput).isEmpty();
        return true;
    }
    /** Filter known tag/rule/template lookups contributed by GoLand or other providers too. */
    public static boolean supportsLookup(TagCompletion.Context context, String lookup, FieldType type, boolean humaInput, Set<String> custom) {
        String text = lookup.replace("`", "").trim();
        if (text.contains(":\"")) {
            var parsed = TagParser.parse("`" + text + "`");
            if (!parsed.entries().isEmpty() && parsed.issues().isEmpty()) {
                for (var entry : parsed.entries()) if (!supportsKey(entry.key(), type, humaInput)) return false;
                return TagTypeValidator.validate("`" + text + "`", type, humaInput, custom).isEmpty();
            }
        }
        if (context.keyPosition()) {
            String key = text.split(":", 2)[0];
            if (key.startsWith("st") && TagCatalog.get(key.substring(2)) != null) key = key.substring(2);
            if (key.equals("sthuma")) key = "minLength";
            return TagCatalog.get(key) == null || supportsKey(key, type, humaInput);
        }
        if (!supportsKey(context.key(), type, humaInput)) return false;
        TagCatalog.Spec spec = TagCatalog.get(context.key());
        if (spec == null) return true;
        String stem = text.split("[=:]", 2)[0];
        if (spec.words().stream().anyMatch(w -> w.split("[=:]", 2)[0].equals(stem)) || custom.contains(stem))
            return supportsValue(context, text, type, humaInput, custom);
        return true;
    }
    public static boolean custom(String key, FieldType type) {
        return switch (key) {
            case "json" -> type.customSchema();
            case "xml" -> type.has("MarshalXML") || type.has("UnmarshalXML") || type.has("MarshalText") || type.has("UnmarshalText");
            case "bson" -> type.has("MarshalBSON") || type.has("MarshalBSONValue") || type.has("UnmarshalBSON") || type.has("UnmarshalBSONValue");
            case "yaml" -> type.has("MarshalYAML") || type.has("UnmarshalYAML");
            case "msgpack" -> type.has("EncodeMsgpack") || type.has("DecodeMsgpack") || type.has("MarshalMsgpack") || type.has("UnmarshalMsgpack");
            case "dynamodbav" -> type.has("MarshalDynamoDBAttributeValue") || type.has("UnmarshalDynamoDBAttributeValue");
            case "toml" -> type.has("MarshalTOML") || type.has("UnmarshalTOML") || type.has("MarshalText") || type.has("UnmarshalText");
            case "redis" -> type.has("RedisScan") || type.has("Scan") || type.has("MarshalBinary") || type.has("UnmarshalBinary");
            case "gorm", "bun" -> type.has("Scan") || type.has("Value");
            default -> false;
        };
    }
    private static boolean timeLike(FieldType type) { return type.unknown() || type.name().equals("time.Time") || type.collection() && type.element() != null && timeLike(type.element().dereference()); }
    private static boolean stringLike(FieldType type) { return type.unknown() || type.kind() == STRING || type.kind() == STRUCT || (type.collection() || type.kind() == MAP) && type.element() != null && stringLike(type.element().dereference()); }
    private static boolean bytes(FieldType type) { return type.collection() && type.element() != null && type.element().name().equals("uint8"); }
    private static String literal(String key, String value) { return "`" + key + ":\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"`"; }
}
