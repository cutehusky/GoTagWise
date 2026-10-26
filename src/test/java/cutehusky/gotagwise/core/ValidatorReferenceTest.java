package cutehusky.gotagwise.core;

import java.util.*;
import static cutehusky.gotagwise.core.FieldType.Kind.*;

final class ValidatorReferenceTest {
    private static int checks;
    private static final FieldType TEXT = FieldType.of(STRING, "string");
    private static final FieldType INT = FieldType.of(INTEGER, "int");
    private static FieldType slice(FieldType type) { return FieldType.container(SLICE, type, null); }
    private static ValidatorReferences.Completion completion(String rules, FieldType type) {
        return ValidatorReferences.completion(TagCompletion.context("validate:\"" + rules), type, Set.of());
    }
    private static void assertCompletion(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
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
        assertCompletion(completion("excluded_if=Des", TEXT).prefix().equals("Des"), "Field prefix excludes rule name");
        assertCompletion(completion("excluded_if=", TEXT).fieldPosition(), "First condition field");
        assertCompletion(!completion("excluded_if=Name ", TEXT).fieldPosition(), "No field names in condition value slot");
        assertCompletion(!completion("excluded_if=Name 'New ", TEXT).fieldPosition(), "No field names in quoted literal");
        assertCompletion(completion("excluded_if=Name '' ", TEXT).fieldPosition(), "Field after empty string value");
        assertCompletion(completion("excluded_if=Name 'New York' ", TEXT).fieldPosition(), "Field after quoted space value");
        assertCompletion(!completion("eqfield=Name ", TEXT).fieldPosition(), "Single-reference rule accepts one path");
        assertCompletion(completion("required_with=Name Other", TEXT).prefix().equals("Other"), "Presence rule next path");
        assertCompletion(completion("required_if=Name '' ", TEXT).used().contains("Name"), "Duplicate required_if references tracked");
        assertCompletion(!ValidatorReferences.supportsCompletion(completion("eqfield=", TEXT), "Count", INT), "Comparison datatype filter");
        assertCompletion(ValidatorReferences.supportsCompletion(completion("required_with=", TEXT), "Count", INT), "Presence accepts different datatypes");
        assertCompletion(!ValidatorReferences.supportsCompletion(completion("excluded_if=", TEXT), "Object", FieldType.of(STRUCT, "Object")), "Conditions exclude unsupported struct literals");
        assertCompletion(!ValidatorReferences.supportsCompletion(completion("excluded_if=", TEXT), "Callback", FieldType.of(FUNCTION, "func()")), "Conditions exclude unsupported function literals");
        assertCompletion(ValidatorReferences.supportsCompletion(completion("excluded_if=", TEXT), "Object", FieldType.container(POINTER, FieldType.of(STRUCT, "Object"), null)), "Conditions allow pointer nil comparisons");
        assertCompletion(completion("dive,eqfield=", slice(INT)).current().kind() == INTEGER, "Comparison follows dive");
        assertCompletion(completion("dive,unique=", slice(slice(FieldType.of(STRUCT, "Item")))).traversal().equals(List.of(ValidatorReferences.Step.VALUE, ValidatorReferences.Step.VALUE)), "Unique element traversal");
        assertCompletion(completion("email", TEXT) == null && completion("min=1", TEXT) == null, "Ordinary rule completion stays unchanged");
        assertCompletion(ValidatorReferences.completion(TagCompletion.context("validate:\"eqfield="), TEXT, Set.of("eqfield")) == null, "Custom overrides do not assume references");
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
        for (String name : List.of("required_if", "required_unless", "excluded_if", "excluded_unless")) {
            check(name + "=Name ", FieldType.container(POINTER, TEXT, null), Map.of("Name", TEXT), "trailing whitespace is not a value");
            check(name + "=Name", TEXT, Map.of("Name", TEXT), "use " + name + "=Name ''");
            check(name + "=Name ''", FieldType.container(POINTER, TEXT, null), Map.of("Name", TEXT), null);
            check(name + "=Name '' Age 18", TEXT, Map.of("Name", TEXT, "Age", INT), null);
            check(name + "=Age 18 Name ", TEXT, Map.of("Name", FieldType.container(POINTER, TEXT, null), "Age", INT), "use " + name + "=Age 18 Name ''");
            check(name + "=Age ''", TEXT, Map.of("Age", INT), "integer literal");
        }
        check("excluded_if=Missing ", TEXT, Map.of(), "missing or invalid");
        check("excluded_if=Age ", TEXT, Map.of("Age", INT), "missing comparison value");
        check("excluded_if=Name ' '", TEXT, Map.of("Name", TEXT), null);
        check("excluded_if=description ''", TEXT, Map.of("Description", FieldType.container(POINTER, TEXT, null)), "use Go field path 'Description'");
        check("excluded_if=Description ''", TEXT, Map.of("Description", FieldType.container(POINTER, TEXT, null)), null);
        check("eqfield=description", TEXT, Map.of("Description", TEXT), "use Go field path 'Description'");
        check("required_with=description", TEXT, Map.of("Description", TEXT), "use Go field path 'Description'");
        check("excluded_if=description ''", TEXT, Map.of(), "case-sensitive Go field names");
        check("excluded_if=description ''", TEXT, Map.of("description", TEXT, "Description", INT), null);
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
        checks++;
        if (!condition.contains("empty string") || !condition.contains("trailing whitespace")) throw new AssertionError("Empty string condition docs");
        for (String word : TagCatalog.get("validate").words()) {
            checks++;
            if (ValidatorRuleDocumentation.description(word.replace("=", "")).isBlank()) throw new AssertionError("Missing rule docs " + word);
        }
        return checks;
    }
}
