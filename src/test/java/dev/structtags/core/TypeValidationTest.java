package dev.structtags.core;

import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

final class TypeValidationTest {
    private static int checks;
    private static final FieldType STRING = FieldType.of(FieldType.Kind.STRING, "string");
    private static final FieldType INT = FieldType.of(INTEGER, "int");
    private static final FieldType FLOATING = FieldType.of(FLOAT, "float64");
    private static final FieldType OBJECT = FieldType.of(STRUCT, "struct");
    private static FieldType ptr(FieldType t) { return FieldType.container(POINTER, t, null); }
    private static FieldType slice(FieldType t) { return FieldType.container(SLICE, t, null); }
    private static FieldType map(FieldType key) { return FieldType.container(MAP, STRING, key); }
    private static void check(String keyValue, FieldType type, boolean huma, String expected) {
        checks++;
        var findings = TagTypeValidator.validate("`" + keyValue + "`", type, huma);
        if (expected == null ? !findings.isEmpty() : findings.stream().noneMatch(f -> f.message().contains(expected)))
            throw new AssertionError("Datatype check " + keyValue + " (" + type + "): " + findings + "; expected " + expected);
    }
    static int run() {
        for (String key : List.of("query", "path", "header", "form", "cookie")) {
            check(key + ":\"name\"", ptr(STRING), true, "does not support pointer");
            check(key + ":\"name\"", STRING, true, null);
            check(key + ":\"name\"", ptr(INT), false, null);
            check(key + ":\"\"", ptr(INT), true, null);
        }
        check("query:\"name\"", ptr(OBJECT.withMethods(Set.of("Receiver"))), true, "pointer");
        check("query:\"name\"", OBJECT.withMethods(Set.of("Receiver")), true, null);
        check("query:\"name\"", FieldType.of(CHANNEL, "chan"), true, "datatype");
        check("query:\"name\"", FieldType.of(FUNCTION, "func"), true, "datatype");
        check("json:\"name\"", ptr(STRING), true, null);
        check("minLength:\"1\"", INT, false, "string field");
        check("minLength:\"1\"", ptr(STRING), false, null);
        check("minimum:\"1\"", STRING, false, "numeric field");
        check("minimum:\"1\"", FLOATING, false, null);
        check("minItems:\"1\"", STRING, false, "slice or array");
        check("minItems:\"1\"", slice(INT), false, null);
        check("minItems:\"1\"", slice(FieldType.of(UNSIGNED, "uint8")), false, "slice or array");
        check("minLength:\"1\"", slice(FieldType.of(UNSIGNED, "uint8")), false, null);
        check("dependentRequired:\"other\"", STRING, false, null);
        check("minProperties:\"1\"", INT, false, "struct or map");
        check("minProperties:\"1\"", OBJECT, false, null);
        check("minProperties:\"1\"", map(STRING), false, null);
        check("minimum:\"1\"", FieldType.UNKNOWN, false, null);
        check("minimum:\"1\"", FieldType.of(INTERFACE, "any"), false, null);
        check("minimum:\"1\"", OBJECT.withMethods(Set.of("Schema")), false, null);
        check("minLength:\"1\"", OBJECT.withMethods(Set.of("MarshalJSON")), false, null);
        check("json:\",string\"", slice(INT), false, "only supported");
        check("json:\",string\"", ptr(INT), false, null);
        check("json:\",string\"", OBJECT.withMethods(Set.of("MarshalJSON")), false, null);
        check("bson:\",inline\"", INT, false, "requires a struct");
        check("bson:\",inline\"", ptr(OBJECT), false, null);
        check("bson:\",inline\"", map(INT), false, "string map keys");
        check("bson:\",inline\"", map(STRING), false, null);
        check("bson:\",minsize\"", STRING, false, "has no effect");
        check("bson:\",minsize\"", FieldType.of(INTEGER, "int64"), false, null);
        check("bson:\",truncate\"", FLOATING, false, "has no effect");
        check("bson:\",truncate\"", INT, false, null);
        check("yaml:\",inline\"", slice(INT), false, "requires a struct");
        check("yaml:\",inline\"", ptr(OBJECT), false, null);
        check("yaml:\",inline\"", map(INT), false, "string map keys");
        check("dynamodbav:\",string\"", OBJECT, false, "numeric field");
        check("dynamodbav:\",string\"", INT, false, null);
        check("dynamodbav:\",unixtime\"", INT, false, "requires time.Time");
        check("dynamodbav:\",unixtime\"", ptr(FieldType.of(STRUCT, "time.Time")), false, null);
        check("dynamodbav:\",omitemptyelem\"", STRING, false, "slice, array or map");
        check("dynamodbav:\",omitemptyelem\"", slice(STRING), false, null);
        check("dynamodbav:\",stringset\"", STRING, false, "slice or array");
        check("dynamodbav:\",stringset\"", slice(INT), false, "incompatible element");
        check("dynamodbav:\",stringset\"", slice(STRING), false, null);
        check("dynamodbav:\",numberset\"", slice(STRING), false, "incompatible element");
        check("dynamodbav:\",numberset\"", slice(FLOATING), false, null);
        check("dynamodbav:\",binaryset\"", slice(INT), false, "incompatible element");
        check("dynamodbav:\",binaryset\"", slice(slice(FieldType.of(UNSIGNED, "uint8"))), false, null);
        check("dynamodbav:\",binaryset\"", OBJECT.withMethods(Set.of("MarshalDynamoDBAttributeValue")), false, null);
        check("json:\"-,string\"", OBJECT, false, "only supported");
        check("json:\"-\"", OBJECT, false, null);
        FieldType number = STRING.withMethods(Set.of("Float64", "Int64", "String"));
        check("dynamodbav:\",string\"", number, false, null);
        check("dynamodbav:\",numberset\"", slice(number), false, null);
        check("dynamodbav:\",stringset\"", slice(number), false, "incompatible element");
        check("query:\"q\"", ptr(STRING).asGenericValueWrapper(), true, null);
        check("query:\"q\"", ptr(STRING.asGenericValueWrapper()), true, "pointer fields");
        check("json:\",string\"", ptr(INT).asGenericValueWrapper(), false, null);
        return checks;
    }
}
