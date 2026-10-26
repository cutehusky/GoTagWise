# Complete implemented check reference

This describes the current plugin implementation, rather than every behavior of the upstream libraries. Exact vocabularies are in [struct tags](support-reference.md), [validator rules](validator-rules.md), and [Huma keys](huma-reference.md).

## Go tag syntax

These apply even to keys outside the catalog:

| Check | Behavior |
| --- | --- |
| Literal delimiters | Reports unclosed raw/interpreted tag literals |
| Entry syntax | Requires `key:"value"` with a nonempty Go tag key |
| Quoting | Reports unclosed quoted values and invalid/incomplete Go escapes |
| Escapes | Handles ordinary, hexadecimal, octal and Unicode escapes with source-offset mapping |
| Spacing | Requires spaces between tag entries |
| Duplicate keys | Reports repeated keys; Go reads the first value |
| Unknown keys | Allowed; the plugin does not invent semantics for custom keys |

Inspection and documentation handle raw and interpreted literals. Completion and live templates target raw/backtick tags.

## Vocabulary and value syntax

| Check | Scope |
| --- | --- |
| Unknown options | Name/option tags such as JSON, BSON, DynamoDB, env, XML, YAML, MessagePack, TOML, Redis, HCL, Gin/Huma parameters and Bun |
| Unknown rules | `validate`, Gin `binding` and conform; custom names/aliases can be configured |
| Unknown directives | GORM; directive names are case-insensitive |
| Rule parameters | Required parameters must be present; parameterless rules reject unexpected `=...` |
| Optional parameters | `unique` and `validateFn` permit omission; supported time comparisons can omit parameters |
| Empty rules | Reports empty comma/OR rule positions; an entirely empty validation tag is allowed |
| Quoted/special arguments | Separators respect quotes, escaped delimiters and SQL parentheses |
| Boolean values | Go boolean forms accepted for Huma/TOML/Gin boolean tags |
| Numeric values | Numeric Huma constraints parse as numbers; counts/lengths are non-negative integers; `multipleOf` is positive |
| Bounds | Checks minimum ≤ maximum, minLength ≤ maxLength, minItems ≤ maxItems, minProperties ≤ maxProperties |
| XML modes | Rejects combinations of multiple primary modes among attr/chardata/cdata/innerxml/comment |
| DynamoDB conflicts | Rejects omitempty + nullempty, omitemptyelem + nullemptyelem, and multiple set encodings |
| Gin collection format | Restricts values to csv, ssv, tsv, pipes or multi |
| GORM integer arguments | Checks digits for size, precision, scale and autoIncrementIncrement |
| GORM time units | Accepts nano, milli, true or false when autoCreateTime/autoUpdateTime has a parameter |
| Typo fixes | Offers close spelling replacements for selected unknown options/collection formats in raw tags |

Parameter recognition is not a general validator for every argument's runtime contents. For example, arbitrary SQL, Go time layouts, regular expressions, enum/default payloads and custom format names are not evaluated.

## Datatype compatibility

Pointers, named underlying types, aliases, collection elements and map keys are resolved through Go PSI. The following are the implemented static restrictions:

| Tag/option | Check |
| --- | --- |
| Serialization tags | Selected unsupported function/channel/complex fields; detected custom converters can preserve compatibility |
| JSON map keys | String, signed/unsigned integer or detected TextMarshaler |
| JSON `string` | String, boolean or numeric scalar, including pointer-to-scalar |
| BSON/YAML `inline` | Struct, pointer-to-struct, or non-pointer map with string keys |
| BSON `minsize` | Effective for int64, uint, uint32 and uint64; warns on other known types |
| BSON `truncate` | Integer field; warns when ineffective on another known type |
| YAML `flow` | Struct, map, slice or array |
| MessagePack `inline` | Struct datatype |
| MessagePack `asArray` | Struct marker datatype; does not enforce the special field name |
| XML | Maps unsupported; innerxml/comment require strings or byte collections |
| DynamoDB `string` | Numeric or recognized number-like representation/custom text conversion |
| DynamoDB `unixtime` | time.Time-compatible representation |
| DynamoDB element options | Slice/array/map container |
| DynamoDB sets | Slice/array container and compatible string/numeric/binary-encoded elements |
| env `init` | Pointer field |
| envPrefix | Nested struct, including pointer-to-struct |
| envSeparator | Slice, array or map |
| envKeyValSeparator | Map |
| Gin time tags | time.Time or a collection thereof |
| Gin collection_format | Slice or array |
| HCL label/key | String |
| HCL block | Struct or collection of structs |
| GORM embedded | Struct |
| GORM increment directives | Integer |
| GORM automatic time fields | time.Time or integer |
| Bun model directives | table/alias/select/extend require a struct model field |
| Bun array/embed/relations | Collection, struct, or struct/collection shape respectively |
| TOML multiline/literal | String/string-container policy |
| conform | String/string-container policy; nested structs remain conservative |
| Huma request parameters | Pointer rejection and selected unsupported kinds, with framework/request-context detection |
| Huma schema constraints | Numeric/string/array/object compatibility; byte collections follow the base64 string-schema case |

Known schema/text/JSON or library encoder methods cause uncertain checks to be skipped. Scanner/Valuer methods are recognized for ORM fields. Method-name recognition is conservative and does not prove the full runtime implementation/signature.

## Validator rule datatypes and traversal

- Generic presence/conditional rules are broadly available; string format rules are restricted to strings unless a specific wider policy exists.
- Length and numeric comparison rules permit their implemented string/numeric/container/time categories. `eq`/`ne` additionally support booleans.
- `oneof` accepts strings/integers; `oneofci` accepts strings. `numeric`/`number` and coordinates accept strings/numbers. `boolean` accepts string/bool. `json` accepts string/byte slice. Numeric currency codes require integers.
- `dive` requires a slice/array/map and changes the current type to the element/value. `keys` immediately after a map dive changes it to the key. `endkeys` restores map values. Nested traversal is tracked.
- `unique` requires comparable elements/values; its field form requires struct elements. `validateFn` requires the selected method name.
- `omitnil` requires a nil-capable field; structonly/nostructlevel require a struct.

The [full per-rule table](validator-rules.md) lists every catalog rule with the compatibility policy used by the plugin. The plugin does not validate actual user input against email, URL, filesystem, numeric-format, or other runtime validators.

## Referenced fields and conditional comparisons

| Rule family | Checks |
| --- | --- |
| eq/ne/gt/gte/lt/lte field and csfield forms | Reference exists; compatible Go comparison kinds; numeric kinds such as int/int64 remain distinct; known named struct identity/time compatibility |
| fieldcontains/fieldexcludes | Referenced field exists and is a string |
| postcode_iso3166_alpha2_field | Referenced country field exists and is a string |
| required_if/required_unless/excluded_if/excluded_unless | Field Value pairs; paths exist; literals match referenced types/ranges |
| Presence conditions | Referenced paths exist; unlike value comparisons, different current/reference types are allowed |
| required_if | Repeated field references rejected |
| unique=Member | Member exists on struct elements and its known type is comparable |
| Dotted/indexed/promoted paths | Go names, aliases, pointers and cross-file types resolved; ambiguous promotion, invalid map key literals and known fixed-array bounds reported |

Conditional literals include quoted strings, integer/range checks, numeric floating-point values, true/false, non-negative collection lengths and supported nil comparisons. Field references use Go names, not JSON/form aliases. Lookups start at the current parent struct. Slice/map entry existence and pointer nilness are left to runtime. See [examples](validator-references.md).

## Completion, templates and documentation

The same compatibility policy filters catalog keys, options, rules, bundled templates and known incompatible lookups from subsequent completion contributors. Unknown custom providers/keys remain under their own semantics. Mapping templates derive field names; other templates offer editable example values.

Hover and Quick Documentation are suppressed for known incompatible tag/rule/option targets. Tag-key help lists compatible vocabulary. Individual validator/binding values show that rule's own meaning, parameters and example, respecting traversal position and custom overrides. Raw/interpreted offset mapping and documentation target invalidation after edits are supported.

## Settings and boundaries

- **Report unknown validation rules, sanitizers and GORM directives** controls extensible vocabulary warnings.
- **Custom validator/binding rules and aliases** bypass built-in rule/type/reference assumptions for configured names and add completion entries.
- **Custom conform sanitizers** extend the sanitizer vocabulary.
- **Check tag compatibility with resolved Go field datatypes** controls datatype/reference diagnostics.
- **Treat ambiguous form/header tags as Huma request parameters** enables Huma assumptions for separate request model files.

Unknown external/generic/interface types are conservative. Shared HCL/TOML/Redis keys use union vocabularies. The plugin does not evaluate application data, custom registries or custom type functions, compile patterns/SQL/time layouts, validate all enum/default payloads, check complete ORM relationship targets, or model full Huma route registration and every decoder conversion. Recognized vocabulary, static compatibility, and runtime acceptance are distinct.

## Keeping the inventories current

The vocabulary inventories are generated from the shipped catalog and static rule policy. Generation and verification commands are in the [development guide](development.md#update-templates-and-support-references). This check inventory is maintained manually alongside the implementation.
