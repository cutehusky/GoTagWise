# Validator rule documentation and field references

Hover on an individual rule or its parameter inside `validate` or Gin `binding`. Each rule gets its own definition, meaning, parameter syntax, example and upstream reference. For `validate:"required,email"`, hovering on `required` explains presence requirements; hovering on `email` explains email syntax. `|` alternatives and rules after `dive`/`keys` are resolved individually. Hovering on the tag key still shows its datatype-filtered rule list.

```go
type Input struct {
    Age int
    Enabled bool
    Mode string
    Password string
    Confirm string `validate:"eqfield=Password"`
    Email string `validate:"required_if=Age 18 Enabled true"`
    Comment string `validate:"required_if=Mode 'New York'"`
}
```

## Field-name completion

Inside `validate` and Gin `binding`, completion offers case-sensitive Go field names after reference rules such as `eqfield=`, `excluded_if=`, and `required_with=`. For example, type `excluded_if=Des` and accept `Description`, then enter the condition value (`''` for an empty string). Field names are offered again after each complete `Field Value` pair, without appearing inside the value itself. Repeated paths are omitted for `required_if`.

Comparison rules filter fields by compatible datatypes; presence rules allow different types. Generic wrapper payload types and `dive`/`keys`/`endkeys` are respected. Type a nested path such as `Address.` or `Items[0].` to complete its members. Promoted fields are included when unambiguous. `unique=Field` completes comparable members of struct collection elements. Custom rule overrides retain their own parameter behavior. JSON/form aliases are not suggested.

## Inspections

- `eqfield`, `nefield`, `gtfield`, `gtefield`, `ltfield`, `ltefield` and their `csfield` forms resolve referenced Go fields and check comparison kinds. Named types are resolved to their underlying numeric kind; `int` and `int64` are distinct. Pointers are dereferenced. Collection comparisons of the same kind compare lengths, so different element types do not automatically make those comparisons invalid.
- `fieldcontains`, `fieldexcludes` and `postcode_iso3166_alpha2_field` require a referenced string field.
- `required_if`, `required_unless`, `excluded_if` and `excluded_unless` check `Field Value` pairs, field existence and literal compatibility with each referenced field. This is the referenced field's type, not the type of the field carrying the condition.
- Presence conditions (`required_with`, `required_without`, their `_all` variants, and corresponding `excluded` rules) check referenced paths without requiring matching datatypes.
- `required_if` reports repeated references. Missing values, unclosed quotes and malformed pairs are reported.
- `unique=Member` checks that struct elements have that member and that its known datatype is comparable.

Examples of warnings:

```go
type Invalid struct {
    Count int
    Enabled bool
    Confirm int `validate:"eqfield=Missing"`             // missing Go field
    Email string `validate:"required_if=Count many"`    // integer compared with text
    Note string `validate:"excluded_if=Enabled yes"`   // use true or false
    Different string `validate:"eqfield=Count"`         // incompatible comparison kinds
}
```

Conditions accept quoted strings, typed numeric/boolean literals and collection lengths. Slices/maps can compare with `nil`; pointer conditions may also compare with `nil`. Known integer ranges are checked. Collection lengths must be non-negative.

For an empty string condition, use **two single quotes**. Trailing whitespace is not a comparison value: `excluded_if=Name ` is invalid and validator can panic with a bad parameter count. GoTagWise reports the missing value and suggests the empty string syntax when the referenced field is a string (including a pointer to a string):

```go
type Request struct {
    Name  string  `validate:""`
    Email *string `validate:"excluded_if=Name ''"`
}
```

This requires `Email` to be absent or empty when `Name` is the empty string. The same syntax works with `required_if`, `required_unless`, `excluded_unless`, and Gin `binding`. A quoted space (`' '`) compares with one space instead. Empty string literals are still rejected for incompatible referenced datatypes such as integers and booleans.

## Paths and scope

Use **Go field names**, not JSON/form tag aliases. Lookups start at the current field's parent struct, following validator's field lookup behavior. Supported paths include `Address.Code`, `Items[0].Code`, and `Lookup[key].Code`. The `csfield` convention supports nested paths; it does not create a global name lookup.

Names are case-sensitive. For `Description *string` tagged `json:"description,omitempty"`, write `validate:"excluded_if=Description ''"`. Using `description` does not resolve at runtime. A generic wrapper on the field carrying the rule does not change the reference scope. Missing-reference diagnostics explain the naming convention and suggest an existing Go path when capitalizing the initial letter resolves it.

Embedded/promoted fields are resolved, with ambiguous promoted names reported. Named types, aliases, pointers and fields declared in other files are resolved through Go PSI. Fixed array bounds and map key literal datatypes are checked. Slice/map entry existence and pointer nilness are runtime concerns, so a statically valid path is not rejected just because an entry or pointee could be absent.

Unknown external/generic/interface types remain conservative. Custom rule overrides in inspection settings disable built-in reference assumptions for those names. Runtime custom type functions, `VarWithValue`, custom registries and application-specific alias expansions may need runtime tests.

The checks use [validator/v10](https://pkg.go.dev/github.com/go-playground/validator/v10), its [built-in rules](https://github.com/go-playground/validator/blob/master/baked_in.go), and its [field lookup implementation](https://github.com/go-playground/validator/blob/master/field_level.go) as references.
