package cutehusky.gotagwise.core;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import static cutehusky.gotagwise.core.FieldType.Kind.*;

/** Generates committed support inventories from the vocabulary actually shipped in the plugin. */
public final class SupportReferenceGenerator {
    private static final Set<String> HUMA = new HashSet<>(List.of(("query path header cookie form default enum doc format const pattern patternDescription example dependentRequired contentType required readOnly writeOnly deprecated hidden uniqueItems nullable minimum maximum exclusiveMinimum exclusiveMaximum multipleOf minLength maxLength minItems maxItems minProperties maxProperties").split(" ")));
    private static String cell(String value) { return value.replace("|", "\\|").replace("\n", " "); }
    private static String code(String value) { return "`" + cell(value) + "`"; }
    private static String words(List<String> values) { return values.isEmpty() ? "—" : values.stream().map(SupportReferenceGenerator::code).collect(Collectors.joining(", ")); }
    private static List<TagCatalog.Spec> tags() { return TagCatalog.all().stream().sorted(Comparator.comparing(TagCatalog.Spec::key)).toList(); }
    private static String mode(TagCatalog.Spec spec) {
        return switch (spec.kind()) {
            case TEXT -> "Text; arbitrary values unless noted";
            case NAME_OPTIONS -> "Name followed by comma options";
            case RULES -> "Comma-separated rules";
            case DIRECTIVES -> "Semicolon-separated directives";
            case BOOLEAN -> "Go boolean literal";
            case NUMBER -> "Numeric literal";
            case NONNEGATIVE -> "Non-negative integer";
            case POSITIVE -> "Positive number";
        };
    }
    private static String checks(String key) {
        return switch (key) {
            case "json" -> "Known options; scalar/pointer-to-scalar string option; supported map keys; unsupported function/channel/complex fields; skip marker.";
            case "bson" -> "Known options; inline struct/map shape and string map keys; effective minsize/truncate datatypes; unsupported field kinds.";
            case "dynamodbav" -> "Known options; omit/null conflicts; mutually exclusive set encodings; set/container/element datatypes; numeric string encoding; time-compatible unixtime; unsupported field kinds.";
            case "env" -> "Known options; init requires a pointer; unsupported function/channel/complex types unless custom conversion is detected.";
            case "envDefault" -> "Go boolean, decimal integer/range, float and duration literals; slice elements and map keys/values using sibling separators. Empty defaults, unknown/custom types, file input and expanded variables remain conservative. See env default guide.";
            case "envPrefix" -> "Nested struct or pointer-to-struct datatype.";
            case "envSeparator" -> "Slice, array or map datatype; separator contents remain free-form.";
            case "envKeyValSeparator" -> "Map datatype; separator contents remain free-form.";
            case "xml" -> "Known options; mutually exclusive encoding modes; innerxml/comment string or byte collection; maps and unsupported field kinds rejected.";
            case "yaml" -> "Known options; inline struct/map shape and string keys; flow collection/struct shape; unsupported field kinds.";
            case "msgpack" -> "Known options; inline struct shape; asArray struct marker datatype; unsupported field kinds. Marker field name is not checked.";
            case "toml" -> "Known omission options; unsupported function/channel/complex fields. Shared vocabulary for both TOML libraries.";
            case "comment" -> "Tag syntax only; free-form comment text.";
            case "commented" -> "Boolean literal syntax.";
            case "multiline", "literal" -> "Boolean literal syntax and string/string-container datatype policy.";
            case "redis" -> "Known options and unsupported field kinds; shared vocabulary. omitempty is a Redigo option.";
            case "hcl" -> "Known union of HCL options; label/key string datatype; block struct or struct collection. Full HCL decoder/type schema is not evaluated.";
            case "help" -> "HCL help text; no content constraint.";
            case "default", "enum" -> "Shared Huma/HCL/TOML-style metadata; no typed default/enum content validation.";
            case "query", "path", "cookie" -> "Known options; Huma request context; nonempty parameter tags reject pointer fields and selected unsupported function/channel/complex types.";
            case "form", "header" -> "Known options; Huma request pointer checks only in detected/explicit request context; Gin pointer fields remain allowed. Selected unsupported kinds checked.";
            case "uri" -> "Known options and selected unsupported kinds for Gin binding.";
            case "collection_format" -> "One of csv/ssv/tsv/pipes/multi and a slice/array field.";
            case "time_format", "time_location" -> "time.Time or a collection thereof. Layout/zone text is not evaluated.";
            case "time_utc" -> "Boolean literal and time.Time/collection datatype.";
            case "validate", "binding" -> "Known rules/parameters, datatype and traversal checks, custom overrides, field references and condition literals; see validator rule inventory.";
            case "conform" -> "Known sanitizer names or custom allowlist; string/string-container policy; nested structs remain conservative.";
            case "gorm" -> "Known case-insensitive directives; numeric size/precision/scale/increment arguments; time units; embedded/increment/time datatypes; custom Scanner/Valuer handling.";
            case "bun" -> "Known options; array/embedded/model/relationship shapes; custom Scanner/Valuer handling. SQL expressions and relation target fields are not validated.";
            case "minimum", "maximum" -> "Numeric tag value and numeric field datatype; minimum must not exceed maximum.";
            case "exclusiveMinimum", "exclusiveMaximum" -> "Numeric tag value and numeric field datatype; no exclusive-bound relationship check.";
            case "multipleOf" -> "Tag value greater than zero and numeric field datatype.";
            case "minLength", "maxLength" -> "Non-negative integer tag value; string/base64 byte-collection schema; paired bounds ordered.";
            case "minItems", "maxItems" -> "Non-negative integer tag value; array-schema datatype (byte collections excluded); paired bounds ordered.";
            case "minProperties", "maxProperties" -> "Non-negative integer tag value; struct/map datatype; paired bounds ordered.";
            case "uniqueItems" -> "Boolean literal and slice/array schema datatype (byte collections excluded).";
            case "required", "readOnly", "writeOnly", "deprecated", "hidden", "nullable" -> "Boolean literal syntax; metadata applies across field datatypes.";
            case "pattern" -> "String-compatible schema datatype; regular expression syntax is not compiled/checked.";
            case "example" -> "Huma schema datatype: raw strings, JSON boolean/integral number/number/array/object; string-array CSV, collection elements and map values. Unknown/custom schemas remain conservative. See example guide.";
            case "doc", "format", "const", "patternDescription", "dependentRequired", "contentType" -> "Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated.";
            default -> throw new IllegalArgumentException("Missing support description: " + key);
        };
    }
    private static String tagsReference() {
        StringBuilder out = new StringBuilder("# Complete struct tag support inventory\n\n");
        out.append("Generated from the plugin's `TagCatalog` and current inspection implementation. This lists the ").append(TagCatalog.all().size()).append(" handled keys; it is a plugin coverage snapshot, not the complete vocabulary of every upstream library.\n\n")
            .append("See [all validator rules](validator-rules.md), [all Huma keys](huma-reference.md), and [all implemented checks](checks-reference.md).\n\n")
            .append("Every listed key has key completion, a bundled `st<key>` live template and hover/Quick Documentation. Mapping-name templates derive their default from the Go field. Datatype-incompatible entries are filtered. Unknown/custom types stay permissive. The combined `sthuma` template is additional.\n\n")
            .append("`name` is an arbitrary mapping/parameter name. A trailing `=` or `:` in a vocabulary entry introduces a parameter. GORM displays `not null` rather than the catalog's internal `not_null`. Recognized vocabulary does not imply full runtime semantic validation.\n\n")
            .append("| Key | Library/consumer | Value form | Exact options, directives or suggested values | Implemented checks |\n| --- | --- | --- | --- | --- |\n");
        for (var spec : tags()) {
            String vocabulary = spec.key().equals("validate") || spec.key().equals("binding") ? "[Full rule list](validator-rules.md)" : words(spec.words().stream().map(w -> w.equals("not_null") ? "not null" : w).toList());
            String library = spec.key().equals("help") ? "alecthomas/hcl" : spec.library();
            out.append("| ").append(code(spec.key())).append(" | ").append(cell(library)).append(" | ").append(mode(spec)).append(" | ").append(vocabulary).append(" | ").append(cell(checks(spec.key()))).append(" |\n");
        }
        return out.append("\n## Shared keys and limits\n\nThe HCL, TOML and Redis vocabularies are unions of the supported consumers. `form` and `header` need Huma request context before Huma pointer rules apply. `help` belongs to HCL; `default` and `enum` are shared metadata. JSON naming/omission tags also participate in Huma schemas.\n\nFor all keys, the parser checks Go tag structure, escapes, spacing and duplicate keys. Unknown keys are allowed. Free-form values are not validated against arbitrary runtime types or registries. See [the check inventory](checks-reference.md) for the precise boundaries.\n").toString();
    }
    private static String ruleTypes(String rule) {
        String name = rule.replace("=", "");
        if (name.equals("keys")) return "Immediately after a map dive";
        if (name.equals("endkeys")) return "Inside an active map-key block";
        if (name.equals("omitnil")) return "Nil-capable pointers, slices, maps, interfaces, channels or functions";
        if (name.equals("validateFn")) return "A value exposing the selected validation method";
        if (name.equals("unique")) return "Slice/array/map with comparable elements/values; =Field needs struct elements";
        List<FieldType> types = List.of(FieldType.of(STRING,"string"), FieldType.of(INTEGER,"int"), FieldType.of(UNSIGNED,"uint"), FieldType.of(FLOAT,"float64"), FieldType.of(BOOLEAN,"bool"), FieldType.of(STRUCT,"time.Time"), FieldType.of(STRUCT,"struct"), FieldType.container(SLICE,FieldType.of(STRING,"string"),null), FieldType.container(ARRAY,FieldType.of(STRING,"string"),null), FieldType.container(MAP,FieldType.of(STRING,"string"),FieldType.of(STRING,"string")), FieldType.of(COMPLEX,"complex128"), FieldType.of(FUNCTION,"func"), FieldType.of(CHANNEL,"chan"));
        List<String> supported = types.stream().filter(t -> ValidatorTypeRules.problem(name,t) == null).map(t -> t.name()).toList();
        if (supported.size() == types.size()) return "Any known kind; reference rules additionally check the target";
        if (name.equals("json")) return "String or byte slice";
        return String.join(", ", supported).replace("int, uint, float64", "signed/unsigned integer or float");
    }
    private static String referenceChecks(String name) {
        if (name.endsWith("field") || Set.of("fieldcontains","fieldexcludes","postcode_iso3166_alpha2_field").contains(name)) return "Go field path exists; comparison/reference kinds compatible";
        if (name.endsWith("_if") || name.endsWith("_unless")) return "Field Value pairs, Go paths, literal type/range; required_if also rejects duplicate paths";
        if (name.contains("_with") || name.contains("_without")) return "Go field paths exist; no same-type requirement";
        if (name.equals("unique")) return "Optional member path exists and its known type is comparable";
        if (Set.of("dive","keys","endkeys").contains(name)) return "Traversal shape/order and current element/key/value datatype";
        if (name.equals("validateFn")) return "Selected method name is present; full method signature is not verified";
        return "Rule presence, parameter shape and current datatype; actual input is not executed";
    }
    private static String validatorReference() {
        var rules = TagCatalog.get("validate").words().stream().sorted().toList();
        StringBuilder out = new StringBuilder("# Complete validator and Gin binding rule inventory\n\n");
        out.append("The same ").append(rules.size()).append(" catalog rules are handled in `validate` and Gin `binding`. Both have rule-level hover/Quick Documentation, completion, parameter checks and datatype filtering. Built-in names can be overridden with the custom-rule allowlist.\n\n")
            .append("A displayed `=parameter` normally requires a nonempty parameter. `unique` and `validateFn` also work without a parameter. Supported `time.Time` ordering rules (`gt`, `gte`, `lt`, `lte`, `min`, `max`) can omit it. Most pointer fields are dereferenced before rule checks. Unknown/interfaces/custom runtime conversions stay conservative.\n\n")
            .append("Datatypes below describe the plugin's static compatibility policy. String/numeric format rules are recognized; the plugin does not validate actual application input, run filesystem/network checks or execute custom validation functions.\n\n")
            .append("| Rule syntax | Datatype/context allowed by the plugin | Meaning | Checks beyond vocabulary |\n| --- | --- | --- | --- |\n");
        for (String word : rules) {
            String name = word.replace("=", "");
            String syntax = word.endsWith("=") ? name + (Set.of("unique","validateFn").contains(name) ? "[=parameter]" : "=parameter") : word;
            out.append("| ").append(code(syntax)).append(" | ").append(cell(ruleTypes(word))).append(" | ").append(cell(ValidatorRuleDocumentation.description(name))).append(" | ").append(cell(referenceChecks(name))).append(" |\n");
        }
        return out.append("\n## Syntax and reference behavior\n\nCommas combine rules; `|` supplies alternatives. `dive` moves to collection elements/map values, `keys` switches to map keys and `endkeys` restores values. Nested collection traversal is tracked. Single quotes preserve spaces in parameter values.\n\nReference paths use Go field names, including nested/indexed/promoted fields, rather than JSON/form aliases. Scalar comparison kinds must match (for example, int and int64 differ). Same-kind collection comparisons use lengths. Presence rules allow unrelated field types. Conditional literals are checked against the referenced field, not the field carrying the condition. See [reference examples and limitations](validator-references.md).\n\nThe reference parser also recognizes the Field Value shape of `skip_unless`, but that name is **not** in this completion/catalog vocabulary and is reported unknown by default. Runtime validator rules not listed above must be configured as custom or used with unknown-rule diagnostics disabled. Custom overrides bypass built-in datatype/reference assumptions.\n\n[Upstream validator reference](https://pkg.go.dev/github.com/go-playground/validator/v10) · [Implementation check inventory](checks-reference.md)\n").toString();
    }
    private static String humaReference() {
        StringBuilder out = new StringBuilder("# Complete handled Huma v2 tag and check inventory\n\n");
        out.append("This page lists ").append(HUMA.size()).append(" Huma-related keys recognized by this plugin, including shared parameter/default/enum keys. Huma uses separate Go tags; there is no `huma:\"rule\"` namespace. JSON naming/omission tags are listed separately in the [struct tag inventory](support-reference.md).\n\n")
            .append("| Tag | Purpose | Recognized value/options | Checks currently implemented |\n| --- | --- | --- | --- |\n");
        for (var spec : tags()) if (HUMA.contains(spec.key())) {
            String vocabulary = spec.words().isEmpty() ? mode(spec) : mode(spec) + "; " + words(spec.words());
            String purpose = spec.key().equals("form") ? "Binds Huma form-data input. Shared with Gin; default= and collection_format= in the vocabulary are Gin-style options." : TagDocumentation.description(spec.key());
            out.append("| ").append(code(spec.key())).append(" | ").append(cell(purpose)).append(" | ").append(vocabulary).append(" | ").append(cell(checks(spec.key()))).append(" |\n");
        }
        return out.append("\n## Request parameter checks\n\nNonempty request parameter tags (`query`, `path`, `header`, `form`, `cookie`) reject pointer fields, including aliases/defined pointer types. Value-based ParamWrapper implementations remain allowed; a pointer to the wrapper is still rejected. Untagged pointers, embedded fields, Body/RawBody fields and the handler's pointer-to-input argument are excluded from this field rule. Selected function/channel/complex parameter types are also rejected unless a relevant custom conversion makes the type uncertain.\n\nFor `form`/`header`, Huma context comes from sibling query/path/cookie tags, a Huma import with form fields or function-input usage, or the explicit inspection setting. Standalone Gin form/header pointer fields remain valid. This is static context detection, not route-registration analysis. See [Huma usage](usage.md#huma-parameters).\n\n## Value and datatype checks\n\n- Boolean tags accept Go forms: `1`, `0`, `t`, `T`, `true`, `TRUE`, `True`, `f`, `F`, `false`, `FALSE`, `False`.\n- Numeric tags must parse numerically; `multipleOf` must be greater than zero. Length/count tags require a non-negative integer.\n- Numeric constraints need numeric fields; string constraints need string schemas; item constraints need array schemas; property counts need structs/maps. Byte slices/arrays follow Huma's base64 string-schema special case. Known custom schema/text/JSON encoders remain conservative.\n- Ordered pairs checked: minimum/maximum, minLength/maxLength, minItems/maxItems, minProperties/maxProperties. Exclusive bounds are individually checked but not compared with other bounds.\n- Example values are checked against known schema datatypes, including JSON arrays/objects, string-array CSV, collection elements and map values. See [example value checks](example-values.md).\n- Metadata such as enum/default/const/format/doc remains free-form. The plugin does not compile regex patterns, check enum/default against arbitrary schemas, resolve dependentRequired field names or evaluate media types.\n\n## Example\n\n```go\ntype SearchInput struct {\n    Name string   `query:\"name\" minLength:\"1\" maxLength:\"80\" doc:\"Name to search\"`\n    Age  int      `query:\"age\" minimum:\"0\" maximum:\"130\"`\n    Tags []string `query:\"tags,explode\" minItems:\"1\" uniqueItems:\"true\"`\n}\n```\n\n[Huma request inputs](https://huma.rocks/features/request-inputs/) · [Huma validation](https://huma.rocks/features/request-validation/) · [All implemented checks](checks-reference.md)\n").toString();
    }
    public static void main(String[] args) throws Exception {
        Map<String,String> files = new LinkedHashMap<>();
        files.put("support-reference.md", tagsReference());
        files.put("validator-rules.md", validatorReference());
        files.put("huma-reference.md", humaReference());
        boolean verify = args.length > 0 && args[0].equals("--check");
        Files.createDirectories(Path.of("docs"));
        for (var file : files.entrySet()) {
            Path path = Path.of("docs",file.getKey());
            if (verify) { if (!Files.readString(path).equals(file.getValue())) throw new IllegalStateException("Stale reference: " + path); }
            else Files.writeString(path,file.getValue());
        }
        System.out.println((verify ? "Verified" : "Generated") + " support references: " + TagCatalog.all().size() + " tag keys, " + TagCatalog.get("validate").words().size() + " validator rules, " + HUMA.size() + " Huma-related keys.");
    }
}
