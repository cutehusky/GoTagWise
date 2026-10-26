# Complete handled Huma v2 tag and check inventory

This page lists 33 Huma-related keys recognized by this plugin, including shared parameter/default/enum keys. Huma uses separate Go tags; there is no `huma:"rule"` namespace. JSON naming/omission tags are listed separately in the [struct tag inventory](support-reference.md).

| Tag | Purpose | Recognized value/options | Checks currently implemented |
| --- | --- | --- | --- |
| `const` | Huma v2: requires a field to equal this constant value. | Text; arbitrary values unless noted | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `contentType` | Huma v2: overrides the request body's media type. | Text; arbitrary values unless noted | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `cookie` | Huma v2: reads a named request cookie into the input field. | Name followed by comma options | Known options; Huma request context; nonempty parameter tags reject pointer fields and selected unsupported function/channel/complex types. |
| `default` | Fallback value or schema default. Used by Huma v2 and HCL/TOML libraries; interpretation depends on the field type and consuming library. | Text; arbitrary values unless noted | Shared Huma/HCL/TOML-style metadata; no typed default/enum content validation. |
| `dependentRequired` | Huma v2: names of fields required when this field is present. | Text; arbitrary values unless noted | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `deprecated` | Huma v2: marks the field as deprecated in the API schema. | Go boolean literal; `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `doc` | Huma v2: description included in the generated API schema. | Text; arbitrary values unless noted | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `enum` | Lists allowed schema values. Huma v2 uses comma-separated values; HCL libraries also use this key. | Text; arbitrary values unless noted | Shared Huma/HCL/TOML-style metadata; no typed default/enum content validation. |
| `example` | Huma v2: example value shown in API documentation. | Text; arbitrary values unless noted | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `exclusiveMaximum` | Huma v2: exclusive numeric upper bound. | Numeric literal | Numeric tag value and numeric field datatype; no exclusive-bound relationship check. |
| `exclusiveMinimum` | Huma v2: exclusive numeric lower bound. | Numeric literal | Numeric tag value and numeric field datatype; no exclusive-bound relationship check. |
| `form` | Binds Huma form-data input. Shared with Gin; default= and collection_format= in the vocabulary are Gin-style options. | Name followed by comma options; `default=`, `collection_format=` | Known options; Huma request pointer checks only in detected/explicit request context; Gin pointer fields remain allowed. Selected unsupported kinds checked. |
| `format` | Huma v2: schema format hint, such as email, uuid or date-time. | Text; arbitrary values unless noted | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `header` | Binds an HTTP header. Huma v2 uses this on request inputs or response outputs; Gin also supports header binding. | Name followed by comma options | Known options; Huma request pointer checks only in detected/explicit request context; Gin pointer fields remain allowed. Selected unsupported kinds checked. |
| `hidden` | Huma v2: hides the field or parameter from generated API documentation. | Go boolean literal; `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `maxItems` | Huma v2: maximum array size, as a non-negative integer. | Non-negative integer | Non-negative integer tag value; array-schema datatype (byte collections excluded); paired bounds ordered. |
| `maxLength` | Huma v2: maximum string length, as a non-negative integer. | Non-negative integer | Non-negative integer tag value; string/base64 byte-collection schema; paired bounds ordered. |
| `maxProperties` | Huma v2: maximum object property count, as a non-negative integer. | Non-negative integer | Non-negative integer tag value; struct/map datatype; paired bounds ordered. |
| `maximum` | Huma v2: inclusive numeric upper bound. | Numeric literal | Numeric tag value and numeric field datatype; minimum must not exceed maximum. |
| `minItems` | Huma v2: minimum array size, as a non-negative integer. | Non-negative integer | Non-negative integer tag value; array-schema datatype (byte collections excluded); paired bounds ordered. |
| `minLength` | Huma v2: minimum string length, as a non-negative integer. | Non-negative integer | Non-negative integer tag value; string/base64 byte-collection schema; paired bounds ordered. |
| `minProperties` | Huma v2: minimum object property count, as a non-negative integer. | Non-negative integer | Non-negative integer tag value; struct/map datatype; paired bounds ordered. |
| `minimum` | Huma v2: inclusive numeric lower bound. | Numeric literal | Numeric tag value and numeric field datatype; minimum must not exceed maximum. |
| `multipleOf` | Huma v2: numeric values must be multiples of this positive number. | Positive number | Tag value greater than zero and numeric field datatype. |
| `nullable` | Huma v2: controls whether the schema accepts null. | Go boolean literal; `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `path` | Huma v2: binds a named route parameter. Path parameters are always required. | Name followed by comma options | Known options; Huma request context; nonempty parameter tags reject pointer fields and selected unsupported function/channel/complex types. |
| `pattern` | Huma v2: regular expression constraint on a string. | Text; arbitrary values unless noted | String-compatible schema datatype; regular expression syntax is not compiled/checked. |
| `patternDescription` | Huma v2: human-readable explanation of the pattern constraint. | Text; arbitrary values unless noted | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `query` | Huma v2: maps an input struct field to a URL query parameter. The value is the parameter name. | Name followed by comma options; `explode` | Known options; Huma request context; nonempty parameter tags reject pointer fields and selected unsupported function/channel/complex types. |
| `readOnly` | Huma v2: marks a schema field for responses only. | Go boolean literal; `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `required` | Huma v2: explicitly marks a query, header or cookie parameter as required. These parameters are optional by default. | Go boolean literal; `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `uniqueItems` | Huma v2: requires array items to be unique when true. | Go boolean literal; `true`, `false` | Boolean literal and slice/array schema datatype (byte collections excluded). |
| `writeOnly` | Huma v2: marks a schema field for requests only. | Go boolean literal; `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |

## Request parameter checks

Nonempty request parameter tags (`query`, `path`, `header`, `form`, `cookie`) reject pointer fields, including aliases/defined pointer types. Value-based ParamWrapper implementations remain allowed; a pointer to the wrapper is still rejected. Untagged pointers, embedded fields, Body/RawBody fields and the handler's pointer-to-input argument are excluded from this field rule. Selected function/channel/complex parameter types are also rejected unless a relevant custom conversion makes the type uncertain.

For `form`/`header`, Huma context comes from sibling query/path/cookie tags, a Huma import with form fields or function-input usage, or the explicit inspection setting. Standalone Gin form/header pointer fields remain valid. This is static context detection, not route-registration analysis. See [Huma usage](usage.md#huma-parameters).

## Value and datatype checks

- Boolean tags accept Go forms: `1`, `0`, `t`, `T`, `true`, `TRUE`, `True`, `f`, `F`, `false`, `FALSE`, `False`.
- Numeric tags must parse numerically; `multipleOf` must be greater than zero. Length/count tags require a non-negative integer.
- Numeric constraints need numeric fields; string constraints need string schemas; item constraints need array schemas; property counts need structs/maps. Byte slices/arrays follow Huma's base64 string-schema special case. Known custom schema/text/JSON encoders remain conservative.
- Ordered pairs checked: minimum/maximum, minLength/maxLength, minItems/maxItems, minProperties/maxProperties. Exclusive bounds are individually checked but not compared with other bounds.
- Metadata such as enum/default/const/format/doc/example remains free-form. The plugin does not compile regex patterns, check enum/default against arbitrary schemas, resolve dependentRequired field names or evaluate media types.

## Example

```go
type SearchInput struct {
    Name string   `query:"name" minLength:"1" maxLength:"80" doc:"Name to search"`
    Age  int      `query:"age" minimum:"0" maximum:"130"`
    Tags []string `query:"tags,explode" minItems:"1" uniqueItems:"true"`
}
```

[Huma request inputs](https://huma.rocks/features/request-inputs/) · [Huma validation](https://huma.rocks/features/request-validation/) · [All implemented checks](checks-reference.md)
