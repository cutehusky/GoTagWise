# Usage

Build and install the plugin using the [repository README](../README.md#build-and-install), then open a Go struct field with a raw/backtick tag. Completion and template expansion work inside existing backticks. Inspections and documentation also handle interpreted Go tag literals.

## Completion and templates

Press **Ctrl+Space** on a key prefix, such as `js`, to complete `json:""`. Press it inside a value to see supported field names, options, booleans, or rules. Options use commas; GORM directives use semicolons; validator also supports `|` alternatives.

Suggestions use the current datatype:

| Field/context | Offered | Hidden |
| --- | --- | --- |
| `int` at a tag key | `json`, `minimum`, `validate` | `minLength`, `minItems`, `envPrefix`, `conform` |
| `int` inside `validate` | `required`, `numeric`, `min=`, `oneof=` | `email`, `uuid`, `contains=` |
| `bool` inside `binding` | `required`, `boolean`, `eq=` | `min=`, `len=`, `oneof=` |
| `[]string` inside `validate` | `dive`, `unique`, `min=` | `email` until after `dive` |
| `[]int` inside `json:"name,..."` | `omitempty`, `omitzero` | `string` |
| `*int` inside `json:"name,..."` | `string`, omission options | Huma `query` at a tag-key position |

Unknown or unresolved types retain suggestions. Detected custom encoders and configured custom rules preserve the applicable choices. Known incompatible tag/template suggestions from subsequent completion contributors are also filtered.

Generic structs with a direct `value T` or `Value T` payload are checked as their supplied payload type: `Wrap[string]` offers string rules, while `Wrap[int]` hides `email`. See [generic wrapper inference](generic-wrappers.md) for exact qualification and alias behavior.

Type an abbreviation such as `stjson`, `stbson`, `stenv`, `stbinding`, `stvalidate`, `stgorm`, `stbun`, or `sthuma`, then **Tab**. Each catalog key has an `st<key>` template; `sthuma` adds a combined JSON/schema example. Mapping templates derive names from the Go field: `HTTPServerID` becomes `httpServerID` for JSON, `http_server_id` for other mappings, and `HTTP_SERVER_ID` for env. Tab moves through editable values.

Manage templates under **Settings → Editor → Live Templates → GoTagWise**. They expand at tag-key positions and are filtered by datatype.

## Validator collections and references

```go
type Input struct {
    Emails []string       `validate:"required,dive,required,email"`
    Values map[int]string `validate:"dive,keys,numeric,endkeys,email"`
    Matrix [][]string     `validate:"dive,dive,email"`
    Mode   string
    Note   string         `validate:"required_if=Mode advanced"`
}
```

`dive` changes the current type to an element or map value. `keys` immediately after a map dive switches to keys; `endkeys` returns to values. Nested traversal, per-element rules, completion and hover documentation follow these transitions.

Comparison and conditional rules check referenced Go field paths and compatible values. Presence rules can reference fields of different types. Read [validator field references](validator-references.md) for examples, path scope, and runtime limits, or the [full rule table](validator-rules.md) for supported vocabulary.

## Hover documentation and inspections

Hover over a supported tag key, option, or validator rule, or press **Ctrl+Q** with the standard Windows keymap. Enable automatic popups under **Settings → Editor → Code Editing → Show quick documentation on hover**.

Tag-key documentation lists compatible options/rules. Individual validator values show that rule's meaning, parameter syntax, example and upstream reference. For `validate:"required,email"`, the two rules have separate documentation. Huma `query` documentation explains parameter naming and repeated values using `explode`.

Unsupported targets have no plugin documentation popup. Unknown datatypes remain permissive. Custom overrides, map/collection traversal and interpreted-literal offsets are respected.

Inspection warnings cover malformed tags, duplicate keys, option/rule errors, datatype mismatches, referenced fields and conditional values. Press **Alt+Enter** for available spelling fixes. The [check inventory](checks-reference.md) records what is and is not checked.

## Huma parameters

```go
type SearchInput struct {
    Query *string `query:"q"`        // warning: pointer request parameter
    Token *string `header:"X-Token"` // warning: request context established by query
}

type CorrectInput struct {
    Query string `query:"q" doc:"Search text"`
    Body *struct {
        Name string `json:"name"`
    } // pointer bodies are allowed
}
```

Huma request parameter fields use values rather than pointers, including aliases of pointer types. For presence tracking, use a non-pointer value implementing Huma's `ParamWrapper`; its `Receiver` method can have a pointer receiver. The handler's `*Input`, embedded fields, untagged pointers, `Body`, and `RawBody` are excluded from this parameter-field rule.

Gin allows pointer `form`/`header` fields. Huma checks for those shared keys require sibling `query`/`path`/`cookie` tags, a Huma import plus form/input usage, or an explicit inspection setting. Header-only response models do not become request inputs simply by importing Huma.

See the [Huma reference](huma-reference.md) for exact handled keys and static checks. Huma uses individual schema tags such as `readOnly:"true"`; there is no `huma:"readOnly"` namespace.

## Settings

Open **Settings → Editor → Inspections → Struct tags → GoTagWise: Go struct tags**.

| Setting | Use |
| --- | --- |
| Report unknown validation rules, sanitizers and GORM directives | Disable for runtime extensions whose vocabulary should remain unrestricted |
| Custom validator/binding rules and aliases | Comma-separated names; adds completion and bypasses built-in type/reference assumptions for those names |
| Custom conform sanitizers | Comma-separated additional sanitizer names |
| Check tag compatibility with resolved Go field datatypes | Controls datatype and reference diagnostics |
| Treat ambiguous form/header tags as Huma request parameters | Enable for separate Huma request-model files without sufficient local context |

Standard IDE inspection suppression also applies. In mixed-framework projects, leave the ambiguous Huma assumption off unless those shared fields are request inputs. These checks use static information; runtime codecs, custom registries and application input still require application tests.
