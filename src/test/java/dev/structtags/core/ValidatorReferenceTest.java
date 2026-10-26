package dev.structtags.core;

import java.util.*;
import static dev.structtags.core.FieldType.Kind.*;

final class ValidatorReferenceTest {
    private static int checks;
    private static final FieldType TEXT = FieldType.of(STRING, "string");
    private static final FieldType INT = FieldType.of(INTEGER, "int");
    private static FieldType slice(FieldType type) { return FieldType.container(SLICE, type, null); }
    private static void check(String rules, FieldType current, Map<String, FieldType> fields, String expected) {
        checks++;
        ValidatorReferences.Lookup lookup = (path, traversal) -> {
            String key = traversal == null ? path : "[]:" + path;
            if (!fields.containsKey(key)) return new ValidatorReferences.Resolved(ValidatorReferences.Status.MISSING, FieldType.UNKNOWN);
            return fields.get(key) == FieldType.UNKNOWN ? ValidatorReferences.Resolved.unknown() : ValidatorReferences.Resolved.found(fields.get(key));
        };
        var findings = ValidatorReferences.validate("`validate:\"" + rules + "\"`", current, lookup, Set.of());
        if (expected == null ? !findings.isEmpty() : findings.stream().noneMatch(f -> f.message().contains(expected))) throw new AssertionError(rules + ": " + findings);
    }
    static int run() {
        check("eqfield=Other", INT, Map.of("Other", INT), null);
        check("eqfield=Other", INT, Map.of("Other", TEXT), "incompatible datatypes");
        check("gtfield=Other", INT, Map.of("Other", FieldType.of(INTEGER, "int64")), "same Go numeric kind");
        check("eqcsfield=Address.Code", TEXT, Map.of("Address.Code", TEXT), null);
        check("eqfield=Missing", INT, Map.of(), "missing or invalid");
        check("required_with=Other", TEXT, Map.of("Other", INT), null);
        check("required_without_all=A B", INT, Map.of("A", TEXT, "B", TEXT), null);
        check("excluded_with=Missing", TEXT, Map.of(), "missing or invalid");
        check("required_if=Age twenty", TEXT, Map.of("Age", INT), "integer literal");
        check("required_if=Age 18", TEXT, Map.of("Age", INT), null);
        check("required_if=Age 128", TEXT, Map.of("Age", FieldType.of(INTEGER, "int8")), "outside the range");
        check("required_if=Age -1", TEXT, Map.of("Age", FieldType.of(UNSIGNED, "uint")), "outside the range");
        check("required_if=Enabled yes", TEXT, Map.of("Enabled", FieldType.of(BOOLEAN, "bool")), "true or false");
        check("excluded_if=Enabled false", TEXT, Map.of("Enabled", FieldType.of(BOOLEAN, "bool")), null);
        check("required_if=Mode 'New York'", INT, Map.of("Mode", TEXT), null);
        check("required_if=Mode ''", INT, Map.of("Mode", TEXT), null);
        check("required_if=Mode 'unterminated", INT, Map.of("Mode", TEXT), "unclosed quoted");
        check("required_if=Mode", INT, Map.of("Mode", TEXT), "Field Value pairs");
        check("required_if=Mode x Mode y", INT, Map.of("Mode", TEXT), "repeats reference");
        check("required_unless=Missing yes", INT, Map.of(), "missing or invalid");
        check("excluded_unless=Count text", TEXT, Map.of("Count", INT), "integer literal");
        check("required_if=Values 2", TEXT, Map.of("Values", slice(TEXT)), null);
        check("required_if=Values nil", TEXT, Map.of("Values", slice(TEXT)), null);
        check("required_if=Values -1", TEXT, Map.of("Values", slice(TEXT)), "non-negative collection length");
        check("required_if=Count nil", TEXT, Map.of("Count", FieldType.container(POINTER, INT, null)), null);
        check("required_if=Price wrong", TEXT, Map.of("Price", FieldType.of(FLOAT, "float64")), "numeric literal");
        check("required_if=Price 1.5", TEXT, Map.of("Price", FieldType.of(FLOAT, "float64")), null);
        check("required_if=Object x", TEXT, Map.of("Object", FieldType.of(STRUCT, "struct")), "scalar condition");
        check("required_if=Unknown x", TEXT, Map.of("Unknown", FieldType.UNKNOWN), null);
        check("fieldcontains=Other", TEXT, Map.of("Other", INT), "must be a string");
        check("postcode_iso3166_alpha2_field=Country", TEXT, Map.of("Country", TEXT), null);
        check("eqfield=Other", slice(INT), Map.of("Other", slice(TEXT)), null);
        check("unique=ID", slice(FieldType.of(STRUCT, "struct")), Map.of("[]:ID", INT), null);
        check("unique=Missing", slice(FieldType.of(STRUCT, "struct")), Map.of(), "missing or invalid");
        check("unique=Data", slice(FieldType.of(STRUCT, "struct")), Map.of("[]:Data", slice(INT)), "must be comparable");
        check("dive,keys,eqfield=Other,endkeys", FieldType.container(MAP, TEXT, INT), Map.of("Other", INT), null);
        check("dive,keys,numeric,endkeys,eqfield=Other", FieldType.container(MAP, TEXT, INT), Map.of("Other", INT), "incompatible datatypes");
        checks++;
        if (!ValidatorReferences.validate("`validate:\"eqfield=Missing\"`", INT, (path, traversal) -> new ValidatorReferences.Resolved(ValidatorReferences.Status.MISSING, FieldType.UNKNOWN), Set.of("eqfield")).isEmpty()) throw new AssertionError("Custom reference rule override");
        String email = TagDocumentation.html("validate", "Email", TEXT, false, 10, "required,email", Set.of());
        checks++;
        if (!email.contains("valid email address") || email.contains("Built-in rules") || !email.contains("<pre>validate:&quot;email&quot;")) throw new AssertionError("Rule-specific email docs");
        String required = TagDocumentation.html("validate", "Email", TEXT, false, 1, "required,email", Set.of());
        checks++;
        if (!required.contains("present, non-empty") || required.equals(email)) throw new AssertionError("Separate rule docs");
        String condition = TagDocumentation.html("validate", "Name", TEXT, false, 18, "required_if=Mode advanced", Set.of());
        checks++;
        if (!condition.contains("Field Value") || !condition.contains("only once")) throw new AssertionError("Conditional rule docs");
        for (String word : TagCatalog.get("validate").words()) {
            checks++;
            if (ValidatorRuleDocumentation.description(word.replace("=", "")).isBlank()) throw new AssertionError("Missing rule docs " + word);
        }
        return checks;
    }
}
