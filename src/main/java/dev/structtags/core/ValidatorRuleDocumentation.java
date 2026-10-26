package dev.structtags.core;

import java.util.*;

/** Documentation for a single rule, rather than the whole validate/binding tag. */
public final class ValidatorRuleDocumentation {
    private ValidatorRuleDocumentation() {}
    public static String html(String tag, String token, FieldType type, Set<String> custom) {
        String name = token.split("=", 2)[0];
        String example = token.contains("=") ? token : example(name);
        boolean projectRule = custom.contains(name);
        String description = projectRule ? "Project-defined validation rule or alias. Its behavior and parameters are defined by your application." : description(name);
        StringBuilder out = new StringBuilder("<html><body><div class='definition'><pre>")
            .append(escape(tag + ":\"" + token + "\"")).append("</pre></div><div class='content'><p><b>")
            .append(projectRule ? "Custom validator rule" : "go-playground/validator/v10").append("</b> · <code>").append(escape(name)).append("</code></p><p>")
            .append(escape(description)).append("</p>");
        if (name.endsWith("field") || Set.of("fieldcontains", "fieldexcludes", "postcode_iso3166_alpha2_field").contains(name))
            out.append("<p><b>Parameter:</b> a Go field name or path relative to the parent struct, for example <code>Other</code>, <code>Address.Code</code>, or <code>Items[0].Code</code>. Use Go names, not JSON/form aliases. Pointers are dereferenced; runtime nil values and missing collection entries still affect validation.</p>");
        if (name.endsWith("_if") || name.endsWith("_unless"))
            out.append("<p><b>Parameters:</b> space-separated <code>Field Value</code> pairs. Quote values containing spaces with single quotes. Each literal is checked against its referenced field's datatype. Collections compare lengths (or <code>nil</code> for slices/maps); booleans use <code>true</code> or <code>false</code>.</p>");
        if (name.contains("_with") || name.contains("_without"))
            out.append("<p><b>Parameters:</b> space-separated Go field paths. Presence conditions do not require those fields to have the same datatype as the field being validated.</p>");
        if (Set.of("len", "min", "max", "gt", "gte", "lt", "lte", "eq", "ne").contains(name))
            out.append("<p>Numeric fields compare values. Collection rules compare lengths. String length constraints count characters; equality compares text. Supported time ordering rules compare with the current time and need no parameter.</p>");
        if (name.equals("required_if")) out.append("<p>Each referenced field must occur only once in this rule.</p>");
        out.append("<p><b>Example</b></p><pre>").append(escape("`" + tag + ":\"" + example + "\"`" )).append("</pre>");
        if (!type.dereference().unknown()) out.append("<p><b>Datatype at this rule:</b> <code>").append(escape(type.dereference().name())).append("</code></p>");
        return out.append("<p><a href='https://pkg.go.dev/github.com/go-playground/validator/v10'>Validator rule reference</a></p></div></body></html>").toString();
    }
    public static String description(String name) {
        return switch (name) {
            case "required" -> "Requires a present, non-empty scalar value. Pointers, interfaces, slices and maps must be non-nil. Struct handling follows validator's required-struct configuration.";
            case "required_if" -> "Requires this field when all supplied Field Value comparisons match.";
            case "required_unless" -> "Requires this field unless the supplied Field Value comparisons exempt it.";
            case "excluded_if" -> "Requires this field to be absent or empty when all supplied Field Value comparisons match.";
            case "excluded_unless" -> "Requires this field to be absent or empty unless the supplied comparisons exempt it.";
            case "required_with" -> "Requires this field when any referenced field is present.";
            case "required_with_all" -> "Requires this field when all referenced fields are present.";
            case "required_without" -> "Requires this field when any referenced field is absent or empty.";
            case "required_without_all" -> "Requires this field when all referenced fields are absent or empty.";
            case "excluded_with" -> "Requires this field to be absent or empty when any referenced field is present.";
            case "excluded_with_all" -> "Requires this field to be absent or empty when all referenced fields are present.";
            case "excluded_without" -> "Requires this field to be absent or empty when any referenced field is absent or empty.";
            case "excluded_without_all" -> "Requires this field to be absent or empty when all referenced fields are absent or empty.";
            case "omitempty" -> "Skips subsequent validation when this value is empty according to validator's empty-value rules.";
            case "omitnil" -> "Skips subsequent validation when this pointer, slice, map or interface value is nil.";
            case "isdefault" -> "Checks that the field has its default/zero value; the opposite of required.";
            case "dive" -> "Applies the following rules to each slice/array element or map value. Repeat dive for nested collections.";
            case "keys" -> "Immediately after a map dive, applies following rules to map keys until endkeys.";
            case "endkeys" -> "Ends a map-key rule block and resumes validation of map values.";
            case "structonly" -> "Runs struct-level validation on the nested struct without validating its individual fields.";
            case "nostructlevel" -> "Skips nested struct field validation and struct-level validation.";
            case "-" -> "Skips validation of this field.";
            case "email" -> "Checks that the string is a valid email address.";
            case "len" -> "Requires an exact string/collection length, or an exact numeric value.";
            case "min", "gte" -> "Requires a value or length greater than or equal to the parameter.";
            case "max", "lte" -> "Requires a value or length less than or equal to the parameter.";
            case "gt" -> "Requires a value or length greater than the parameter.";
            case "lt" -> "Requires a value or length less than the parameter.";
            case "eq" -> "Requires equality with the parameter; collections compare lengths.";
            case "ne" -> "Requires inequality with the parameter; collections compare lengths.";
            case "eqfield", "eqcsfield" -> "Requires equality with the referenced field. Scalar kinds must match; collections of the same kind compare lengths. Time fields compare times.";
            case "nefield", "necsfield" -> "Checks inequality against the referenced field. The inspection flags incompatible kinds even where runtime inequality would simply pass.";
            case "gtfield", "gtcsfield" -> "Requires a value greater than the referenced field. Numeric kinds must match; string ordering uses lengths and time fields compare times.";
            case "gtefield", "gtecsfield" -> "Requires a value greater than or equal to the referenced field, using matching numeric kinds, string lengths or time values.";
            case "ltfield", "ltcsfield" -> "Requires a value less than the referenced field, using matching numeric kinds, string lengths or time values.";
            case "ltefield", "ltecsfield" -> "Requires a value less than or equal to the referenced field, using matching numeric kinds, string lengths or time values.";
            case "fieldcontains" -> "Checks that this string contains the value of the referenced string field.";
            case "fieldexcludes" -> "Checks that this string does not contain the value of the referenced string field.";
            case "oneof" -> "Requires one of the space-separated string or integer values. Use single quotes around an allowed value containing spaces.";
            case "oneofci" -> "Requires one of the supplied string values, ignoring case.";
            case "unique" -> "Requires unique comparable elements or map values. Use unique=Field for a comparable member of each struct element.";
            case "validateFn" -> "Calls a validation method on the field. The default method name is Validate; validateFn=Method selects another method.";
            case "datetime" -> "Parses a string using the Go time layout supplied as the parameter.";
            case "timezone" -> "Checks that the string identifies a loadable time zone.";
            case "numeric", "number" -> "Accepts numeric Go fields or strings matching the validator's numeric representation rules.";
            case "boolean" -> "Accepts a boolean Go value or a string that can be parsed as a boolean.";
            case "json" -> "Checks that a string or byte slice contains valid JSON.";
            case "lowercase" -> "Checks that letters in the string are lowercase.";
            case "uppercase" -> "Checks that letters in the string are uppercase.";
            case "alpha" -> "Checks that the string contains only ASCII letters.";
            case "alphanum" -> "Checks that the string contains only ASCII letters and digits.";
            case "alphaunicode" -> "Checks that the string contains only Unicode letters.";
            case "alphanumunicode" -> "Checks that the string contains only Unicode letters and digits.";
            case "contains" -> "Checks that the string contains the supplied substring.";
            case "containsany" -> "Checks that the string contains at least one character from the parameter.";
            case "containsrune" -> "Checks that the string contains the Unicode character supplied as the parameter.";
            case "excludes" -> "Checks that the string does not contain the supplied substring.";
            case "excludesall" -> "Checks that the string contains none of the parameter's characters.";
            case "excludesrune" -> "Checks that the string excludes the supplied Unicode character.";
            case "startswith" -> "Checks that the string begins with the supplied prefix.";
            case "endswith" -> "Checks that the string ends with the supplied suffix.";
            case "startsnotwith" -> "Checks that the string does not begin with the supplied prefix.";
            case "endsnotwith" -> "Checks that the string does not end with the supplied suffix.";
            case "file", "image" -> "Checks a file path against the runtime filesystem; image additionally checks the file's media type.";
            case "dir" -> "Checks that the path names a directory in the runtime filesystem.";
            case "filepath", "dirpath" -> "Checks path syntax/validity for a file or directory path according to the runtime platform.";
            case "postcode_iso3166_alpha2_field" -> "Validates a postal code using the country code stored in the referenced string field.";
            case "postcode_iso3166_alpha2" -> "Validates a postal code using the ISO 3166-1 alpha-2 country code supplied as the parameter.";
            case "latitude", "longitude" -> "Checks a string or number for a valid geographic " + name + ".";
            case "uuid", "uuid3", "uuid4", "uuid5", "uuid_rfc4122", "uuid3_rfc4122", "uuid4_rfc4122", "uuid5_rfc4122" -> "Checks UUID syntax, including the version/RFC requirements encoded in this rule's name.";
            case "iscolor" -> "Accepts a string matching one of validator's supported color formats.";
            default -> "Checks the string using validator's " + name.replace('_', ' ') + " format or syntax rules. See the linked upstream reference for its exact accepted format.";
        };
    }
    private static String example(String name) {
        if (name.endsWith("_if") || name.endsWith("_unless")) return name + "=Mode advanced";
        if (name.contains("_with") || name.contains("_without")) return name + "=Email Phone";
        if (name.endsWith("field") || name.equals("fieldcontains") || name.equals("fieldexcludes")) return name + "=Other";
        return switch (name) {
            case "len", "min", "gt", "gte", "eq", "ne" -> name + "=1";
            case "max", "lt", "lte" -> name + "=100";
            case "oneof", "oneofci" -> name + "=red green blue";
            case "datetime" -> "datetime=2006-01-02";
            case "contains", "containsany", "containsrune", "excludes", "excludesall", "excludesrune", "startswith", "startsnotwith", "endswith", "endsnotwith" -> name + "=text";
            default -> name;
        };
    }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
