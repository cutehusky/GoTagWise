package dev.structtags.core;

import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

final class EnvDefaultTest {
    private static int checks;
    private static final FieldType INT = FieldType.of(INTEGER, "int"), TEXT = FieldType.of(STRING, "string");
    private static final FieldType DURATION = FieldType.of(INTEGER, "int64").withEnvParser("time.Duration");
    private static void check(FieldType type, String value, String extra, String expected) {
        checks++;
        var findings = TagTypeValidator.validate("`envDefault:\"" + value + "\" " + extra + "`", type, false);
        if (expected == null ? !findings.isEmpty() : findings.stream().noneMatch(f -> f.message().contains(expected)))
            throw new AssertionError(type + " default=" + value + ": " + findings + "; expected " + expected);
    }
    static int run() {
        for (String valid : List.of("0", "+12", "-12", "0012", "2147483647", "-2147483648", "")) check(INT, valid, "", null);
        for (String invalid : List.of("1.5", "0xff", "1_000", " 1", "1 ", "nil", "abc")) check(INT, invalid, "", "decimal integer");
        check(INT, "2147483648", "", "32 bits");
        check(FieldType.of(INTEGER, "int8"), "128", "", "outside");
        check(FieldType.of(INTEGER, "int64"), "9223372036854775807", "", null);
        check(FieldType.of(INTEGER, "int64"), "9223372036854775808", "", "outside");
        check(FieldType.of(UNSIGNED, "uint8"), "255", "", null);
        check(FieldType.of(UNSIGNED, "uint8"), "256", "", "outside");
        check(FieldType.of(UNSIGNED, "uint64"), "18446744073709551615", "", null);
        for (String invalid : List.of("-1", "+1", "0x10")) check(FieldType.of(UNSIGNED, "uint"), invalid, "", "decimal integer");
        for (String valid : List.of("true", "false", "True", "False", "TRUE", "FALSE", "1", "0", "t", "f", "T", "F")) check(FieldType.of(BOOLEAN, "bool"), valid, "", null);
        for (String invalid : List.of("yes", "TRUE ", "tRuE")) check(FieldType.of(BOOLEAN, "bool"), invalid, "", "Go boolean");
        for (String valid : List.of("1.5", "-1e3", ".5", "1.", "0x1.fp2", "1_000.5", "NaN", "Inf", "-Infinity", "nan")) check(FieldType.of(FLOAT, "float64"), valid, "", null);
        for (String invalid : List.of("1f", "0x10", " 1.5", "1__0", "abc")) check(FieldType.of(FLOAT, "float64"), invalid, "", "floating-point literal");
        check(FieldType.of(FLOAT, "float32"), "3.4028235e38", "", null);
        check(FieldType.of(FLOAT, "float32"), "3.5e38", "", "outside");
        check(FieldType.of(FLOAT, "float64"), "1e400", "", "outside");
        for (String valid : List.of("0", "-0", "300ms", "1.5h", "2h45m", "-.5s", "1µs", "1μs", "1.s", "9223372036854775807ns", "-9223372036854775808ns")) check(DURATION, valid, "", null);
        for (String invalid : List.of("30", "1d", "one second", "1s ", "1e3s", ".s")) check(DURATION, invalid, "", "Go duration");
        check(DURATION, "9223372036854775808ns", "", "outside");
        check(DURATION, "-9223372036854775809ns", "", "outside");
        check(FieldType.container(POINTER, INT, null), "wrong", "", "decimal integer");
        check(INT.asGenericValueWrapper(), "wrong", "", "decimal integer");
        check(FieldType.container(SLICE, INT, null), "1,2,3", "", null);
        check(FieldType.container(SLICE, INT, null), "1,wrong", "", "slice element 2");
        check(FieldType.container(SLICE, INT, null), "1, 2", "", "slice element 2");
        check(FieldType.container(SLICE, INT, null), "1|2", "envSeparator:\"|\"", null);
        check(FieldType.container(SLICE, INT, null), "1,2,", "envSeparator:\"\"", "slice element 3");
        check(FieldType.container(SLICE, DURATION, null), "1s,2m", "", null);
        check(FieldType.container(MAP, INT, TEXT), "a:1,b:2", "", null);
        check(FieldType.container(MAP, INT, TEXT), "a:wrong", "", "map value 1");
        check(FieldType.container(MAP, TEXT, INT), "wrong:value", "", "map key 1");
        check(FieldType.container(MAP, TEXT, TEXT), "missing", "", "key:value format");
        check(FieldType.container(MAP, TEXT, TEXT), "key:a:b", "", null);
        check(FieldType.container(MAP, INT, TEXT), "a=>1||b=>2", "envSeparator:\"||\" envKeyValSeparator:\"=>\"", null);
        check(FieldType.container(MAP, INT, TEXT), "a:1,b:2", "envSeparator:\"\" envKeyValSeparator:\"\"", null);
        check(INT, "/path/config", "env:\"PORT,file\"", null);
        check(INT, "${PORT}", "env:\"PORT,expand\"", null);
        check(INT, "wrong", "env:\"PORT,expand\"", "decimal integer");
        check(INT, "wrong", "env:\"-\"", null);
        check(INT, "$PORT", "env:\"PORT\"", "decimal integer");
        check(INT.withMethods(Set.of("UnmarshalText")), "anything", "", null);
        check(FieldType.UNKNOWN, "anything", "", null);
        check(TEXT, "anything", "", null);
        check(FieldType.of(STRUCT, "Custom"), "anything", "", null);
        check(FieldType.of(COMPLEX, "complex64"), "1+2i", "", "no built-in parser");
        check(FieldType.container(ARRAY, INT, null), "1,2", "", "no built-in parser");
        for (FieldType type : List.of(INT, FieldType.of(BOOLEAN, "bool"), DURATION, FieldType.container(SLICE, INT, null), FieldType.container(MAP, INT, TEXT))) {
            checks++;
            if (!TagTypeSupport.supportsKey("envDefault", type, false)) throw new AssertionError("envDefault must remain available for " + type);
        }
        checks++;
        if (TagTypeSupport.supportsKey("envDefault", FieldType.container(ARRAY, INT, null), false)) throw new AssertionError("envDefault array tag availability");
        checks++;
        if (!TagDocumentation.html("envDefault", "Port", INT, false, -1, null, Set.of()).contains("envDefault:&quot;1&quot;")) throw new AssertionError("Numeric env default documentation example");
        checks++;
        if (!TagDocumentation.html("envDefault", "Timeout", DURATION, false, -1, null, Set.of()).contains("envDefault:&quot;5s&quot;")) throw new AssertionError("Duration env default documentation example");
        return checks;
    }
}
