package cutehusky.gotagwise.core;

import java.util.*;
import static cutehusky.gotagwise.core.FieldType.Kind.*;

final class CompletionTypeTest {
    private static int checks;
    private static final FieldType TEXT = FieldType.of(STRING, "string");
    private static final FieldType INT = FieldType.of(INTEGER, "int");
    private static final FieldType BOOL = FieldType.of(BOOLEAN, "bool");
    private static FieldType slice(FieldType t) { return FieldType.container(SLICE, t, null); }
    private static FieldType pointer(FieldType t) { return FieldType.container(POINTER, t, null); }
    private static FieldType map(FieldType key, FieldType value) { return FieldType.container(MAP, value, key); }
    private static void key(String key, FieldType type, boolean expected) {
        checks++;
        if (TagTypeSupport.supportsKey(key, type, false) != expected) throw new AssertionError("Key " + key + " on " + type);
    }
    private static void value(String before, String word, FieldType type, boolean expected) {
        checks++;
        var context = Objects.requireNonNull(TagCompletion.context(before));
        boolean found = TagCompletion.values(context, "Field", type, false, Set.of()).contains(word);
        if (found != expected) throw new AssertionError("Completion " + before + " -> " + word + " on " + type + "; got " + TagCompletion.values(context, "Field", type, false, Set.of()));
    }
    private static void invalid(String rules, FieldType type, String expected) {
        checks++;
        var messages = ValidatorTypeRules.validate(rules, type, Set.of());
        if (expected == null ? !messages.isEmpty() : messages.stream().noneMatch(m -> m.contains(expected))) throw new AssertionError("Rules " + rules + " on " + type + ": " + messages);
    }
    static int run() {
        key("minimum", TEXT, false); key("minimum", INT, true);
        key("minLength", TEXT, true); key("minLength", INT, false);
        key("minItems", slice(TEXT), true); key("minItems", TEXT, false);
        key("envPrefix", TEXT, false); key("envPrefix", FieldType.of(STRUCT, "struct"), true);
        key("conform", INT, false); key("conform", slice(TEXT), true); key("conform", slice(INT), false);
        key("json", FieldType.of(FUNCTION, "func"), false); key("json", INT, true);
        key("xml", map(TEXT, TEXT), false); key("json", map(BOOL, TEXT), false);
        key("json", map(INT, TEXT), true);
        key("query", pointer(TEXT), false); key("query", TEXT, true);
        key("time_format", TEXT, false); key("time_format", FieldType.of(STRUCT, "time.Time"), true);
        key("collection_format", INT, false); key("collection_format", slice(INT), true);
        key("literal", INT, false); key("literal", TEXT, true);
        for (var spec : TagCatalog.all()) key(spec.key(), FieldType.UNKNOWN, true);
        value("validate:\"", "email", INT, false);
        value("validate:\"", "numeric", INT, true);
        value("validate:\"", "min=", INT, true);
        value("validate:\"", "oneof=", INT, true);
        value("validate:\"", "iso4217_numeric", INT, true);
        value("validate:\"", "email", TEXT, true);
        value("validate:\"", "dive", TEXT, false);
        value("validate:\"", "omitnil", TEXT, false);
        value("validate:\"", "omitnil", pointer(TEXT), true);
        value("validate:\"", "boolean", BOOL, true);
        value("validate:\"", "min=", BOOL, false);
        value("validate:\"", "len=", BOOL, false);
        value("validate:\"", "oneof=", BOOL, false);
        value("validate:\"", "eq=", BOOL, true);
        value("validate:\"", "json", slice(FieldType.of(UNSIGNED, "uint8")), true);
        value("validate:\"", "json", slice(INT), false);
        value("validate:\"", "dive", slice(TEXT), true);
        value("validate:\"", "email", slice(TEXT), false);
        value("validate:\"", "unique", slice(TEXT), true);
        value("validate:\"", "unique=", slice(TEXT), false);
        value("validate:\"dive,", "email", slice(TEXT), true);
        value("validate:\"dive,", "unique=", slice(TEXT), false);
        value("validate:\"", "unique=", slice(FieldType.of(STRUCT, "struct")), true);
        value("validate:\"", "validateFn", INT, false);
        value("validate:\"", "validateFn", INT.withMethods(Set.of("Validate")), true);
        value("validate:\"", "gte", FieldType.of(STRUCT, "time.Time"), true);
        value("validate:\"dive,", "dive", slice(slice(TEXT)), true);
        value("validate:\"dive,dive,", "email", slice(slice(TEXT)), true);
        FieldType mapping = map(INT, TEXT);
        value("validate:\"dive,", "keys", mapping, true);
        value("validate:\"", "keys", mapping, false);
        value("validate:\"dive,keys,", "email", mapping, false);
        value("validate:\"dive,keys,", "numeric", mapping, true);
        value("validate:\"dive,keys,", "endkeys", mapping, true);
        value("validate:\"dive,keys,numeric,endkeys,", "email", mapping, true);
        value("binding:\"dive,", "email", slice(TEXT), true);
        value("validate:\"dive,email|", "uuid", slice(TEXT), true);
        value("json:\"field,", "string", slice(INT), false);
        value("json:\"field,", "string", pointer(INT), true);
        value("bson:\"field,", "minsize", INT, false);
        value("bson:\"field,", "minsize", FieldType.of(INTEGER, "int64"), true);
        value("bson:\"field,", "inline", FieldType.of(STRUCT, "struct"), true);
        value("yaml:\"field,", "flow", INT, false);
        value("yaml:\"field,", "flow", slice(INT), true);
        value("dynamodbav:\"field,", "numberset", slice(INT), true);
        value("dynamodbav:\"field,", "stringset", slice(INT), false);
        value("dynamodbav:\"field,", "binaryset", slice(slice(FieldType.of(UNSIGNED, "uint8"))), true);
        value("xml:\"field,", "innerxml", INT, false);
        value("xml:\"field,", "innerxml", TEXT, true);
        value("env:\"FIELD,", "init", INT, false);
        value("env:\"FIELD,", "init", pointer(INT), true);
        value("hcl:\"field,", "label", INT, false);
        value("hcl:\"field,", "label", TEXT, true);
        value("gorm:\"", "embedded", TEXT, false);
        value("gorm:\"", "embedded", FieldType.of(STRUCT, "struct"), true);
        value("bun:\"field,", "array", TEXT, false);
        value("bun:\"field,", "array", slice(INT), true);
        value("msgpack:\"field,", "inline", INT, false);
        invalid("email", INT, "string field");
        invalid("required,min=1,max=10", INT, null);
        invalid("dive,email", slice(TEXT), null);
        invalid("dive,email", slice(INT), "string field");
        invalid("dive,keys,numeric,endkeys,email", mapping, null);
        invalid("dive,keys,email,endkeys,email", mapping, "string field");
        invalid("dive", TEXT, "slice, array or map");
        invalid("keys,email", TEXT, "immediately after dive");
        invalid("endkeys", TEXT, "preceding keys");
        invalid("structonly", TEXT, "struct field");
        invalid("datetime=2006-01-02", FieldType.of(STRUCT, "time.Time"), "string field");
        invalid("gte", FieldType.of(STRUCT, "time.Time"), null);
        invalid("unique=Name", slice(TEXT), "struct elements");
        invalid("unique", slice(map(TEXT, TEXT)), "comparable");
        checks++;
        if (!TagValidator.validate("`validate:\"gte\"`", TagValidator.Options.defaults(), FieldType.of(STRUCT, "time.Time")).isEmpty()) throw new AssertionError("Time comparison without parameter");
        checks++;
        if (!ValidatorTypeRules.validate("email", INT, Set.of("email")).isEmpty()) throw new AssertionError("Custom override");
        checks++;
        if (!TagCompletion.values(TagCompletion.context("validate:\""), "Field", INT, false, Set.of("my_rule")).contains("my_rule")) throw new AssertionError("Custom completion");
        checks++;
        if (TagTypeSupport.supportsLookup(TagCompletion.context(""), "stminLength", INT, false, Set.of())) throw new AssertionError("Incompatible template leaked");
        checks++;
        if (TagTypeSupport.supportsLookup(TagCompletion.context(""), "json:\",string\"", slice(INT), false, Set.of())) throw new AssertionError("Incompatible full-tag lookup leaked");
        return checks;
    }
}
