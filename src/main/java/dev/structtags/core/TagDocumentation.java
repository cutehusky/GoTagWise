package dev.structtags.core;

import java.util.*;

/** Bundled help is available offline; source links open the upstream documentation. */
public final class TagDocumentation {
    private TagDocumentation() {}
    public static String html(String key, String field) {
        return html(key, field, FieldType.UNKNOWN, false, -1, null, Set.of());
    }
    /** valueIndex is a decoded-value offset; -1 requests documentation on the key. */
    public static String html(String key, String field, FieldType type, boolean humaInput, int valueIndex, String value, Set<String> custom) {
        TagCatalog.Spec spec = TagCatalog.get(key);
        if (spec == null || !TagTypeSupport.supportsKey(key, type, humaInput)) return null;
        Selection selection = selection(spec, value == null ? "" : value, valueIndex);
        if (selection.token() != null && !TagTypeSupport.supportsValue(selection.context(), selection.token(), type, humaInput, custom)) return null;
        if (selection.token() != null && (key.equals("validate") || key.equals("binding")))
            return ValidatorRuleDocumentation.html(key, selection.token(), ValidatorTypeRules.stateBefore(selection.context(), type, custom).current(), custom);
        var words = TagCompletion.values(selection.context(), "", type, humaInput, custom);
        StringBuilder out = new StringBuilder("<html><body><div class='definition'><pre>")
            .append(escape(key)).append(":&quot;value&quot;</pre></div><div class='content'><p><b>")
            .append(escape(spec.library())).append("</b></p><p>").append(escape(filteredDescription(key, type))).append("</p>");
        if (key.equals("query")) {
            if (!type.dereference().unknown()) {
                String name = value != null && !value.isBlank() ? TagValidator.split(value, ',').get(0) : TagCatalog.name(field == null || field.isBlank() ? "Field" : field, "snake");
                out.append("<p>Query parameters are optional by default. Use <code>required:&quot;true&quot;</code> to require this value.</p><pre>")
                    .append(escape("`query:\"" + name + "\" doc:\"Parameter description\"`" )).append("</pre>");
                if (type.dereference().collection()) out.append("<p>Slices accept comma-separated values. Use <code>query:&quot;").append(escape(name))
                    .append(",explode&quot;</code> for repeated parameters, such as <code>?").append(escape(name)).append("=go&amp;").append(escape(name)).append("=api</code>.</p>");
                else out.append("<p>Reads <code>?").append(escape(name)).append("=").append(type.dereference().numeric() ? "1" : type.dereference().kind() == FieldType.Kind.BOOLEAN ? "true" : "hello").append("</code>.</p>");
            } else {
            out.append("<p>Query parameters are optional unless you add <code>required:&quot;true&quot;</code>. ")
                .append("Use separate schema tags such as <code>default</code>, <code>minimum</code>, or <code>doc</code>.</p>")
                .append("<pre>").append(escape("type SearchInput struct {\n    Search string `query:\"q\" doc:\"Search text\"`\n    Page int `query:\"page\" default:\"1\" minimum:\"1\"`\n    Tags []string `query:\"tags,explode\"`\n}"))
                .append("</pre><p><code>query:&quot;q&quot;</code> reads <code>?q=hello</code>. ")
                .append("Slices normally accept comma-separated values. The <code>explode</code> option accepts ")
                .append("repeated parameters, for example <code>?tags=go&amp;tags=api</code>.</p>");
            }
        } else {
            String exampleValue = spec.kind() == TagCatalog.Kind.NAME_OPTIONS
                ? TagCatalog.name(field == null || field.isBlank() ? "Field" : field, key.equals("env") ? "upper" : key.equals("json") ? "camel" : "snake") : spec.example();
            out.append("<p><b>Example</b></p><pre>").append(escape("`" + key + ":\"" + exampleValue + "\"`" )).append("</pre>");
        }
        if (!words.isEmpty()) {
            out.append("<p><b>").append(switch (spec.kind()) {
                case NAME_OPTIONS -> "Options after the name";
                case DIRECTIVES -> "Directives (separate with ;)";
                case RULES -> "Built-in rules (separate with ,)";
                default -> "Suggested values";
            }).append("</b><br>");
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) out.append(", ");
                String word = words.get(i);
                out.append("<code>").append(escape(word.equals("not_null") && key.equals("gorm") ? "not null" : word)).append("</code>");
            }
            out.append("</p>");
        }
        if (java.util.Set.of("query", "path", "cookie", "header", "form").contains(key))
            out.append("<p><b>Datatype compatibility (Huma request inputs)</b><br>Pointer fields are unsupported. Use a value field or a ParamWrapper value to represent an optional parameter. Pointer body fields are allowed. For shared form/header tags, the inspection uses request context or its explicit Huma setting.</p>");
        if (key.equals("hcl") || key.equals("toml") || key.equals("redis") || key.equals("header"))
            out.append("<p>This key is shared by multiple libraries; available options depend on the library consuming the struct.</p>");
        out.append("<p><a href='").append(source(key)).append("'>Upstream documentation</a>");
        if (key.equals("hcl")) out.append(" · <a href='https://pkg.go.dev/github.com/alecthomas/hcl'>alecthomas/hcl</a>");
        if (key.equals("toml")) out.append(" · <a href='https://pkg.go.dev/github.com/pelletier/go-toml'>pelletier/go-toml</a>");
        if (key.equals("redis")) out.append(" · <a href='https://pkg.go.dev/github.com/gomodule/redigo/redis'>Redigo</a>");
        if (key.equals("header")) out.append(" · <a href='https://pkg.go.dev/github.com/gin-gonic/gin'>Gin</a>");
        return out.append("</p></div></body></html>").toString();
    }
    private record Selection(TagCompletion.Context context, String token) {}
    private static Selection selection(TagCatalog.Spec spec, String value, int index) {
        String base = spec.kind() == TagCatalog.Kind.NAME_OPTIONS ? "field," : "";
        if (index < 0 || value.isEmpty()) return new Selection(new TagCompletion.Context(spec.key(), "", base, false), null);
        char delimiter = spec.kind() == TagCatalog.Kind.DIRECTIVES ? ';' : ',';
        int start = 0;
        for (String part : TagValidator.split(value, delimiter)) {
            int end = start + part.length();
            if (index <= end) {
                if (spec.kind() == TagCatalog.Kind.NAME_OPTIONS && start == 0 && !(spec.key().equals("bun") && part.contains(":")))
                    return new Selection(new TagCompletion.Context(spec.key(), "", base, false), null);
                if (spec.key().equals("validate") || spec.key().equals("binding")) {
                    int orStart = start;
                    for (String or : TagValidator.split(part, '|')) {
                        if (index <= orStart + or.length()) return new Selection(new TagCompletion.Context(spec.key(), "", value.substring(0, orStart), false), or.isEmpty() ? null : or);
                        orStart += or.length() + 1;
                    }
                }
                return new Selection(new TagCompletion.Context(spec.key(), "", value.substring(0, start), false), part.isEmpty() ? null : part);
            }
            start = end + 1;
        }
        return new Selection(new TagCompletion.Context(spec.key(), "", base, false), null);
    }
    public static int valueIndex(String literal, TagParser.Entry entry, int sourceOffset) {
        if (sourceOffset < entry.valueStart() || sourceOffset >= entry.valueEnd()) return -1;
        GoStrings.Decoded body = GoStrings.literal(literal);
        int start = decodedIndex(body, entry.valueStart());
        int end = decodedIndex(body, entry.valueEnd());
        int at = decodedIndex(body, sourceOffset) - start;
        GoStrings.Decoded value = GoStrings.decode(body.text().substring(start, end), 0, false);
        return decodedIndex(value, at);
    }
    private static int decodedIndex(GoStrings.Decoded value, int source) {
        int index = 0;
        while (index < value.text().length() && value.offset(index + 1) <= source) index++;
        return index;
    }
    private static String filteredDescription(String key, FieldType type) {
        if (type.dereference().unknown()) return description(key);
        return switch (key) {
            case "bson" -> "Sets the BSON element name and compatible encoding options.";
            case "yaml" -> "Sets the YAML mapping key and compatible formatting options.";
            case "env" -> "Sets the environment variable name and compatible parsing options.";
            case "hcl" -> "Maps a configuration name to this field. Options depend on the HCL library consuming the struct.";
            default -> description(key);
        };
    }
    public static String description(String key) {
        return switch (key) {
            case "query" -> "Huma v2: maps an input struct field to a URL query parameter. The value is the parameter name.";
            case "path" -> "Huma v2: binds a named route parameter. Path parameters are always required.";
            case "cookie" -> "Huma v2: reads a named request cookie into the input field.";
            case "header" -> "Binds an HTTP header. Huma v2 uses this on request inputs or response outputs; Gin also supports header binding.";
            case "form" -> "Gin: binds form or query data; default= supplies a fallback. Huma also uses form on form-data fields, with Huma's own parameter datatype rules.";
            case "uri" -> "Gin: binds a named route parameter with ShouldBindUri or BindUri.";
            case "binding" -> "Gin binding validation rules, using go-playground/validator. Rules can require parameters; custom rules are configured in inspection settings.";
            case "validate" -> "go-playground/validator validation rules. Commas combine rules; | specifies alternatives; = supplies a rule parameter. Custom aliases are allowed through inspection settings.";
            case "conform" -> "Applies comma-separated string sanitizers in order. Configure custom sanitizer names in inspection settings.";
            case "json" -> "Sets the JSON property name and encoding options. An empty name keeps the default field name; - skips the field.";
            case "bson" -> "Sets the BSON element name and encoding options. Use inline to flatten an embedded struct or map, or - to skip a field.";
            case "dynamodbav" -> "Sets the DynamoDB attribute name and encoding options. Set encodings and Unix time require compatible Go field types.";
            case "env" -> "Sets the environment variable name to load. Options can require a value, expand it, read from a file, or unset the variable after parsing.";
            case "envDefault" -> "Fallback value used by env when the environment variable is absent.";
            case "envPrefix" -> "Prefix added to environment variable names for a nested configuration struct.";
            case "envSeparator" -> "Separator used when parsing environment values into slices.";
            case "envKeyValSeparator" -> "Separator between map keys and values when parsing environment values.";
            case "xml" -> "Sets an XML element or attribute name and encoding mode. Modes such as attr, chardata and innerxml are mutually exclusive.";
            case "yaml" -> "Sets the YAML mapping key. Supports omitting empty values, flow formatting and inline fields.";
            case "msgpack" -> "Sets the MessagePack field name and options. asArray is used on the special _msgpack field.";
            case "toml" -> "Sets the TOML key and omission options. Shared by BurntSushi/toml and pelletier/go-toml.";
            case "comment" -> "pelletier/go-toml: comment text emitted with the field.";
            case "commented" -> "pelletier/go-toml: emits the field as commented-out TOML when true.";
            case "multiline" -> "pelletier/go-toml: emits a string using multiline formatting when true.";
            case "literal" -> "pelletier/go-toml: requests a literal string representation when true.";
            case "redis" -> "Maps the field to a Redis hash name. - skips a field. Redigo supports omitempty; go-redis ignores the option suffix when scanning.";
            case "hcl" -> "Maps a configuration name to a field. HashiCorp gohcl uses kind options such as attr, block and label; alecthomas/hcl has its own schema options.";
            case "help" -> "alecthomas/hcl: description associated with the configuration field.";
            case "gorm" -> "GORM schema and relationship directives. Separate directives with semicolons and parameters with a colon. Directive names are case-insensitive.";
            case "bun" -> "Bun column, table or relationship configuration. Options follow the column name; table: and alias: can configure bun.BaseModel.";
            case "collection_format" -> "Gin: controls how a collection is split, such as csv for commas or multi for repeated values.";
            case "time_format" -> "Gin: Go time layout or supported Unix timestamp format for binding time values.";
            case "time_utc" -> "Gin: interprets bound time values in UTC when true.";
            case "time_location" -> "Gin: time zone used when parsing a bound time value, for example UTC or an IANA location.";
            case "default" -> "Fallback value or schema default. Used by Huma v2 and HCL/TOML libraries; interpretation depends on the field type and consuming library.";
            case "enum" -> "Lists allowed schema values. Huma v2 uses comma-separated values; HCL libraries also use this key.";
            case "doc" -> "Huma v2: description included in the generated API schema.";
            case "format" -> "Huma v2: schema format hint, such as email, uuid or date-time.";
            case "const" -> "Huma v2: requires a field to equal this constant value.";
            case "pattern" -> "Huma v2: regular expression constraint on a string.";
            case "patternDescription" -> "Huma v2: human-readable explanation of the pattern constraint.";
            case "example" -> "Huma v2: example value shown in API documentation.";
            case "dependentRequired" -> "Huma v2: names of fields required when this field is present.";
            case "contentType" -> "Huma v2: overrides the request body's media type.";
            case "required" -> "Huma v2: explicitly marks a query, header or cookie parameter as required. These parameters are optional by default.";
            case "readOnly" -> "Huma v2: marks a schema field for responses only.";
            case "writeOnly" -> "Huma v2: marks a schema field for requests only.";
            case "deprecated" -> "Huma v2: marks the field as deprecated in the API schema.";
            case "hidden" -> "Huma v2: hides the field or parameter from generated API documentation.";
            case "uniqueItems" -> "Huma v2: requires array items to be unique when true.";
            case "nullable" -> "Huma v2: controls whether the schema accepts null.";
            case "minimum" -> "Huma v2: inclusive numeric lower bound.";
            case "maximum" -> "Huma v2: inclusive numeric upper bound.";
            case "exclusiveMinimum" -> "Huma v2: exclusive numeric lower bound.";
            case "exclusiveMaximum" -> "Huma v2: exclusive numeric upper bound.";
            case "multipleOf" -> "Huma v2: numeric values must be multiples of this positive number.";
            case "minLength" -> "Huma v2: minimum string length, as a non-negative integer.";
            case "maxLength" -> "Huma v2: maximum string length, as a non-negative integer.";
            case "minItems" -> "Huma v2: minimum array size, as a non-negative integer.";
            case "maxItems" -> "Huma v2: maximum array size, as a non-negative integer.";
            case "minProperties" -> "Huma v2: minimum object property count, as a non-negative integer.";
            case "maxProperties" -> "Huma v2: maximum object property count, as a non-negative integer.";
            default -> throw new IllegalArgumentException("No documentation for " + key);
        };
    }
    public static String source(String key) {
        return switch (key) {
            case "json", "xml" -> "https://pkg.go.dev/encoding/" + key;
            case "bson" -> "https://pkg.go.dev/go.mongodb.org/mongo-driver/bson";
            case "dynamodbav" -> "https://pkg.go.dev/github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue";
            case "env", "envDefault", "envPrefix", "envSeparator", "envKeyValSeparator" -> "https://pkg.go.dev/github.com/caarlos0/env/v11";
            case "yaml" -> "https://pkg.go.dev/gopkg.in/yaml.v3";
            case "msgpack" -> "https://pkg.go.dev/github.com/vmihailenco/msgpack";
            case "toml" -> "https://pkg.go.dev/github.com/BurntSushi/toml";
            case "comment", "commented", "multiline", "literal" -> "https://pkg.go.dev/github.com/pelletier/go-toml";
            case "redis" -> "https://pkg.go.dev/github.com/redis/go-redis/v9";
            case "hcl" -> "https://pkg.go.dev/github.com/hashicorp/hcl2/gohcl";
            case "help" -> "https://pkg.go.dev/github.com/alecthomas/hcl";
            case "gorm" -> "https://gorm.io/docs/models.html";
            case "bun" -> "https://bun.uptrace.dev/guide/models.html";
            case "conform" -> "https://pkg.go.dev/github.com/leebenson/conform";
            case "validate" -> "https://pkg.go.dev/github.com/go-playground/validator/v10";
            case "binding", "form", "uri", "collection_format", "time_format", "time_utc", "time_location" -> "https://gin-gonic.com/en/docs/examples/binding-and-validation/";
            case "query", "path", "header", "cookie", "required", "contentType" -> "https://huma.rocks/features/request-inputs/";
            default -> "https://huma.rocks/features/request-validation/";
        };
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
