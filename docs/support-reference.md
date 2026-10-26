# Complete struct tag support inventory

Generated from the plugin's `TagCatalog` and current inspection implementation. This lists the 62 handled keys; it is a plugin coverage snapshot, not the complete vocabulary of every upstream library.

See [all validator rules](validator-rules.md), [all Huma keys](huma-reference.md), and [all implemented checks](checks-reference.md).

Every listed key has key completion, a bundled `st<key>` live template and hover/Quick Documentation. Mapping-name templates derive their default from the Go field. Datatype-incompatible entries are filtered. Unknown/custom types stay permissive. The combined `sthuma` template is additional.

`name` is an arbitrary mapping/parameter name. A trailing `=` or `:` in a vocabulary entry introduces a parameter. GORM displays `not null` rather than the catalog's internal `not_null`. Recognized vocabulary does not imply full runtime semantic validation.

| Key | Library/consumer | Value form | Exact options, directives or suggested values | Implemented checks |
| --- | --- | --- | --- | --- |
| `binding` | Gin binding / validator/v10 | Comma-separated rules | [Full rule list](validator-rules.md) | Known rules/parameters, datatype and traversal checks, custom overrides, field references and condition literals; see validator rule inventory. |
| `bson` | MongoDB BSON | Name followed by comma options | `omitempty`, `minsize`, `truncate`, `inline` | Known options; inline struct/map shape and string map keys; effective minsize/truncate datatypes; unsupported field kinds. |
| `bun` | Bun | Name followed by comma options | `pk`, `autoincrement`, `notnull`, `nullzero`, `unique`, `unique:`, `type:`, `default:`, `scanonly`, `array`, `json_use_number`, `msgpack`, `soft_delete`, `rel:`, `join:`, `m2m:`, `polymorphic:`, `on_delete:`, `on_update:`, `embed:`, `extend`, `table:`, `alias:`, `select:` | Known options; array/embedded/model/relationship shapes; custom Scanner/Valuer handling. SQL expressions and relation target fields are not validated. |
| `collection_format` | Gin binding | Text; arbitrary values unless noted | `csv`, `ssv`, `tsv`, `pipes`, `multi` | One of csv/ssv/tsv/pipes/multi and a slice/array field. |
| `comment` | pelletier/go-toml | Text; arbitrary values unless noted | — | Tag syntax only; free-form comment text. |
| `commented` | pelletier/go-toml | Go boolean literal | `true`, `false` | Boolean literal syntax. |
| `conform` | leebenson/conform | Comma-separated rules | `trim`, `ltrim`, `rtrim`, `lower`, `upper`, `title`, `camel`, `snake`, `slug`, `ucfirst`, `name`, `email`, `num`, `!num`, `alpha`, `!alpha`, `!html`, `!js` | Known sanitizer names or custom allowlist; string/string-container policy; nested structs remain conservative. |
| `const` | Huma v2 | Text; arbitrary values unless noted | — | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `contentType` | Huma v2 | Text; arbitrary values unless noted | — | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `cookie` | Huma v2 | Name followed by comma options | — | Known options; Huma request context; nonempty parameter tags reject pointer fields and selected unsupported function/channel/complex types. |
| `default` | HCL · Huma v2 | Text; arbitrary values unless noted | — | Shared Huma/HCL/TOML-style metadata; no typed default/enum content validation. |
| `dependentRequired` | Huma v2 | Text; arbitrary values unless noted | — | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `deprecated` | Huma v2 | Go boolean literal | `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `doc` | Huma v2 | Text; arbitrary values unless noted | — | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `dynamodbav` | AWS attributevalue | Name followed by comma options | `omitempty`, `omitemptyelem`, `nullempty`, `nullemptyelem`, `string`, `binaryset`, `numberset`, `stringset`, `unixtime` | Known options; omit/null conflicts; mutually exclusive set encodings; set/container/element datatypes; numeric string encoding; time-compatible unixtime; unsupported field kinds. |
| `enum` | HCL · Huma v2 | Text; arbitrary values unless noted | — | Shared Huma/HCL/TOML-style metadata; no typed default/enum content validation. |
| `env` | caarlos0/env/v11 | Name followed by comma options | `required`, `notEmpty`, `file`, `unset`, `expand`, `init` | Known options; init requires a pointer; unsupported function/channel/complex types unless custom conversion is detected. |
| `envDefault` | caarlos0/env/v11 | Text; arbitrary values unless noted | — | Tag syntax only; fallback text is not parsed as the field's runtime value. |
| `envKeyValSeparator` | caarlos0/env/v11 | Text; arbitrary values unless noted | — | Map datatype; separator contents remain free-form. |
| `envPrefix` | caarlos0/env/v11 | Text; arbitrary values unless noted | — | Nested struct or pointer-to-struct datatype. |
| `envSeparator` | caarlos0/env/v11 | Text; arbitrary values unless noted | — | Slice, array or map datatype; separator contents remain free-form. |
| `example` | Huma v2 | Text; arbitrary values unless noted | — | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `exclusiveMaximum` | Huma v2 | Numeric literal | — | Numeric tag value and numeric field datatype; no exclusive-bound relationship check. |
| `exclusiveMinimum` | Huma v2 | Numeric literal | — | Numeric tag value and numeric field datatype; no exclusive-bound relationship check. |
| `form` | Gin | Name followed by comma options | `default=`, `collection_format=` | Known options; Huma request pointer checks only in detected/explicit request context; Gin pointer fields remain allowed. Selected unsupported kinds checked. |
| `format` | Huma v2 | Text; arbitrary values unless noted | — | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `gorm` | GORM | Semicolon-separated directives | `column:`, `type:`, `serializer:`, `size:`, `primaryKey`, `unique`, `default:`, `precision:`, `scale:`, `not null`, `autoIncrement`, `autoIncrementIncrement:`, `embedded`, `embeddedPrefix:`, `autoCreateTime`, `autoUpdateTime`, `index`, `uniqueIndex`, `check:`, `<-`, `->`, `-`, `foreignKey:`, `references:`, `polymorphic:`, `polymorphicValue:`, `many2many:`, `joinForeignKey:`, `joinReferences:`, `constraint:` | Known case-insensitive directives; numeric size/precision/scale/increment arguments; time units; embedded/increment/time datatypes; custom Scanner/Valuer handling. |
| `hcl` | HashiCorp gohcl · alecthomas/hcl | Name followed by comma options | `attr`, `block`, `label`, `remain`, `body`, `optional`, `key` | Known union of HCL options; label/key string datatype; block struct or struct collection. Full HCL decoder/type schema is not evaluated. |
| `header` | Gin · Huma v2 | Name followed by comma options | — | Known options; Huma request pointer checks only in detected/explicit request context; Gin pointer fields remain allowed. Selected unsupported kinds checked. |
| `help` | alecthomas/hcl | Text; arbitrary values unless noted | — | HCL help text; no content constraint. |
| `hidden` | Huma v2 | Go boolean literal | `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `json` | encoding/json | Name followed by comma options | `omitempty`, `omitzero`, `string` | Known options; scalar/pointer-to-scalar string option; supported map keys; unsupported function/channel/complex fields; skip marker. |
| `literal` | pelletier/go-toml | Go boolean literal | `true`, `false` | Boolean literal syntax and string/string-container datatype policy. |
| `maxItems` | Huma v2 | Non-negative integer | — | Non-negative integer tag value; array-schema datatype (byte collections excluded); paired bounds ordered. |
| `maxLength` | Huma v2 | Non-negative integer | — | Non-negative integer tag value; string/base64 byte-collection schema; paired bounds ordered. |
| `maxProperties` | Huma v2 | Non-negative integer | — | Non-negative integer tag value; struct/map datatype; paired bounds ordered. |
| `maximum` | Huma v2 | Numeric literal | — | Numeric tag value and numeric field datatype; minimum must not exceed maximum. |
| `minItems` | Huma v2 | Non-negative integer | — | Non-negative integer tag value; array-schema datatype (byte collections excluded); paired bounds ordered. |
| `minLength` | Huma v2 | Non-negative integer | — | Non-negative integer tag value; string/base64 byte-collection schema; paired bounds ordered. |
| `minProperties` | Huma v2 | Non-negative integer | — | Non-negative integer tag value; struct/map datatype; paired bounds ordered. |
| `minimum` | Huma v2 | Numeric literal | — | Numeric tag value and numeric field datatype; minimum must not exceed maximum. |
| `msgpack` | vmihailenco/msgpack | Name followed by comma options | `omitempty`, `asArray`, `inline`, `noinline` | Known options; inline struct shape; asArray struct marker datatype; unsupported field kinds. Marker field name is not checked. |
| `multiline` | pelletier/go-toml | Go boolean literal | `true`, `false` | Boolean literal syntax and string/string-container datatype policy. |
| `multipleOf` | Huma v2 | Positive number | — | Tag value greater than zero and numeric field datatype. |
| `nullable` | Huma v2 | Go boolean literal | `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `path` | Huma v2 | Name followed by comma options | — | Known options; Huma request context; nonempty parameter tags reject pointer fields and selected unsupported function/channel/complex types. |
| `pattern` | Huma v2 | Text; arbitrary values unless noted | — | String-compatible schema datatype; regular expression syntax is not compiled/checked. |
| `patternDescription` | Huma v2 | Text; arbitrary values unless noted | — | Tag syntax and documentation/completion; metadata content is free-form. Runtime schema semantics are not evaluated. |
| `query` | Huma v2 | Name followed by comma options | `explode` | Known options; Huma request context; nonempty parameter tags reject pointer fields and selected unsupported function/channel/complex types. |
| `readOnly` | Huma v2 | Go boolean literal | `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `redis` | go-redis/v9 · redigo/redis | Name followed by comma options | `omitempty` | Known options and unsupported field kinds; shared vocabulary. omitempty is a Redigo option. |
| `required` | Huma v2 | Go boolean literal | `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `time_format` | Gin binding | Text; arbitrary values unless noted | `unix`, `unixmilli`, `unixmicro`, `unixnano` | time.Time or a collection thereof. Layout/zone text is not evaluated. |
| `time_location` | Gin binding | Text; arbitrary values unless noted | `UTC`, `Local` | time.Time or a collection thereof. Layout/zone text is not evaluated. |
| `time_utc` | Gin binding | Go boolean literal | `true`, `false` | Boolean literal and time.Time/collection datatype. |
| `toml` | BurntSushi/toml · pelletier/go-toml | Name followed by comma options | `omitempty`, `omitzero` | Known omission options; unsupported function/channel/complex fields. Shared vocabulary for both TOML libraries. |
| `uniqueItems` | Huma v2 | Go boolean literal | `true`, `false` | Boolean literal and slice/array schema datatype (byte collections excluded). |
| `uri` | Gin | Name followed by comma options | — | Known options and selected unsupported kinds for Gin binding. |
| `validate` | go-playground/validator/v10 | Comma-separated rules | [Full rule list](validator-rules.md) | Known rules/parameters, datatype and traversal checks, custom overrides, field references and condition literals; see validator rule inventory. |
| `writeOnly` | Huma v2 | Go boolean literal | `true`, `false` | Boolean literal syntax; metadata applies across field datatypes. |
| `xml` | encoding/xml | Name followed by comma options | `attr`, `chardata`, `cdata`, `innerxml`, `comment`, `any`, `omitempty` | Known options; mutually exclusive encoding modes; innerxml/comment string or byte collection; maps and unsupported field kinds rejected. |
| `yaml` | yaml.v3 | Name followed by comma options | `omitempty`, `flow`, `inline` | Known options; inline struct/map shape and string keys; flow collection/struct shape; unsupported field kinds. |

## Shared keys and limits

The HCL, TOML and Redis vocabularies are unions of the supported consumers. `form` and `header` need Huma request context before Huma pointer rules apply. `help` belongs to HCL; `default` and `enum` are shared metadata. JSON naming/omission tags also participate in Huma schemas.

For all keys, the parser checks Go tag structure, escapes, spacing and duplicate keys. Unknown keys are allowed. Free-form values are not validated against arbitrary runtime types or registries. See [the check inventory](checks-reference.md) for the precise boundaries.
