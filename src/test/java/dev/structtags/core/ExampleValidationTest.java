package dev.structtags.core;

import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

final class ExampleValidationTest {
    private static int checks;
    private static final FieldType TEXT = FieldType.of(STRING, "string"), INT = FieldType.of(INTEGER, "int"), BOOL = FieldType.of(BOOLEAN, "bool");
    private static FieldType slice(FieldType type) { return FieldType.container(SLICE, type, null); }
    private static void check(FieldType type, String value, String expected) {
        checks++;
        String literal = "`example:\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"`";
        var findings = TagTypeValidator.validate(literal, type, false);
        if (expected == null ? !findings.isEmpty() : findings.stream().noneMatch(f -> f.message().contains(expected)))
            throw new AssertionError(type + " example=" + value + ": " + findings + "; expected " + expected);
    }
    static int run() {
        for (String value : List.of("hello", "", "true", "null", "123", "\"quoted\"", "not JSON {")) check(TEXT, value, null);
        for (String value : List.of("true", "false", " true ")) check(BOOL, value, null);
        for (String value : List.of("1", "\"true\"", "null", "{}", "[]")) check(BOOL, value, "JSON boolean");
        for (String value : List.of("True", "TRUE", "yes", "", "true false")) check(BOOL, value, "valid JSON");
        for (String value : List.of("0", "-1", "1.0", "1e3", " 12 ")) check(INT, value, null);
        for (String value : List.of("1.5", "\"12\"", "null", "true", "[]")) check(INT, value, "integral JSON number");
        for (String value : List.of("+1", "01", "0x10", "1_000", "NaN", "Inf", "1.", ".5", "1e", "1e400")) check(INT, value, "valid JSON");
        check(FieldType.of(FLOAT, "float64"), "1.25", null);
        check(FieldType.of(FLOAT, "float32"), "1e-3", null);
        check(FieldType.of(FLOAT, "float64"), "false", "JSON number");
        check(slice(INT), "[1,2,3]", null);
        check(slice(INT), "[1,2.0,3e1]", null);
        check(slice(INT), "[1,\"two\"]", "[1]: expected an integral JSON number");
        check(slice(INT), "[1,null]", "[1]: expected an integral JSON number");
        check(slice(INT), "{}", "JSON array");
        check(slice(INT), "1,2", "valid JSON");
        check(slice(INT), "[1,]", "valid JSON");
        check(slice(INT), "[1] trailing", "valid JSON");
        check(slice(TEXT), "one,two", null);
        check(slice(TEXT), "[\"one\",\"two\"]", null);
        check(slice(TEXT), "[1]", "JSON string");
        check(slice(TEXT), "", "valid JSON");
        check(slice(slice(INT)), "[[1],[2]]", null);
        check(slice(slice(INT)), "[[1],[\"bad\"]]", "[1]: [0]");
        check(FieldType.container(ARRAY, INT, null), "[1,2]", null);
        check(slice(FieldType.of(UNSIGNED, "uint8")), "SGVsbG8=", null);
        check(FieldType.container(MAP, INT, TEXT), "{\"a\":1}", null);
        check(FieldType.container(MAP, INT, TEXT), "{\"a\":\"bad\"}", "[a]: expected an integral JSON number");
        check(FieldType.container(MAP, TEXT, TEXT), "{\"a\":null}", "JSON string");
        check(FieldType.of(STRUCT, "Object"), "{}", null);
        check(FieldType.of(STRUCT, "Object"), "{\"a\":[true,null,2.5,{}]}", null);
        check(FieldType.of(STRUCT, "Object"), "[]", "JSON object");
        for (String value : List.of("{a:1}", "{\"a\":1,}", "{\"a\":1}{}", "{\"a\":\"\\q\"}", "{\"a\":\"\\u123\"}", "{\"a\":\"line\nbreak\"}")) check(FieldType.of(STRUCT, "Object"), value, "valid JSON");
        check(FieldType.of(STRUCT, "Object"), "{\"a\":\"\\u0041\\n\\t\\\\\\\"\\/\\b\\r\\f\"}", null);
        check(FieldType.container(POINTER, INT, null), "12", null);
        check(FieldType.container(POINTER, INT, null), "null", "integral JSON number");
        check(INT.asGenericValueWrapper(), "wrong", "valid JSON");
        check(FieldType.of(STRUCT, "time.Time"), "2026-10-26T12:00:00Z", null);
        check(FieldType.of(STRUCT, "net/url.URL"), "https://example.com", null);
        check(FieldType.of(INTEGER, "int64").withEnvParser("time.Duration"), "5s", "valid JSON");
        check(FieldType.of(STRUCT, "Custom").withMethods(Set.of("Schema")), "anything", null);
        check(INT.withMethods(Set.of("UnmarshalText")), "anything", null);
        check(slice(INT.withMethods(Set.of("UnmarshalText"))), "[1]", "JSON string");
        check(INT.withMethods(Set.of("UnmarshalJSON")), "anything", null);
        check(FieldType.UNKNOWN, "anything", null);
        check(FieldType.of(FUNCTION, "func"), "anything", "no schema");
        checks++;
        if (!TagTypeSupport.supportsKey("example", INT, false)) throw new AssertionError("Numeric example key availability");
        checks++;
        if (TagTypeSupport.supportsKey("example", FieldType.of(CHANNEL, "chan"), false)) throw new AssertionError("Unsupported example key hidden");
        checks++;
        if (!TagDocumentation.html("example", "Values", slice(TEXT), false, -1, null, Set.of()).contains("[\\&quot;value\\&quot;]")) throw new AssertionError("Array example docs escape embedded JSON quotes");
        return checks;
    }
}
