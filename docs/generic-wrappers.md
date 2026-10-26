# Generic value wrappers

GoTagWise automatically treats a qualifying generic value wrapper as its payload type for static datatype checks, completion and hover documentation.

```go
type MyCustomWrap[T any] struct {
    value T
}

type Input struct {
    Email MyCustomWrap[string] `validate:"email"`     // checked as string
    Count MyCustomWrap[int]    `minimum:"1"`          // checked as int
    Wrong MyCustomWrap[int]    `validate:"email"`     // warning: email requires string
    Names MyCustomWrap[[]string] `validate:"dive,email"` // checked as []string
}
```

The exported spelling `Value T` is supported too. Extra state fields such as `Set bool` do not prevent inference.

## Qualification

Unwrapping requires all of the following:

- A generic type declaration with supplied type arguments.
- A struct payload with exactly one field named `value` or `Value`.
- That field's declared type is directly one of the declaration's type parameters, such as `T` or `V`.

For multiple type parameters, GoTagWise selects the argument corresponding to the payload field, not necessarily the first:

```go
type Wrapped[K, V any] struct {
    Value V
    Set   bool
}

// Wrapped[int, string] is checked as string.
```

The following do **not** qualify:

```go
type Plain struct { value string }          // not generic
type Fixed[T any] struct { value string }   // payload is not a type parameter
type Other[T any] struct { Data T }         // no value/Value field
type Slice[T any] struct { value []T }      // payload is not directly T
type Pointer[T any] struct { value *T }     // payload is not directly T
type Ambiguous[T any] struct { value T; Value T } // ambiguous payload
```

These remain structs for datatype checking. An uninstantiated/unresolved argument remains unknown rather than being guessed from its constraint.

## Composition and aliases

Nested wrappers and arguments inside slices, arrays and maps are resolved recursively:

| Declared type | Effective datatype |
| --- | --- |
| `MyCustomWrap[string]` | `string` |
| `MyCustomWrap[MyCustomWrap[int]]` | `int` |
| `MyCustomWrap[[]string]` | `[]string` |
| `[]MyCustomWrap[string]` | `[]string` for element validation |
| `map[int]MyCustomWrap[string]` | Integer keys and string values for validation |
| `*MyCustomWrap[string]` | Pointer to a string payload; the outer pointer is preserved |

Type aliases remain transparent, including generic aliases that forward type arguments. A new **non-generic defined type** based on a generic struct remains a struct:

```go
type Alias = MyCustomWrap[string]        // checked as string
type List[T any] = MyCustomWrap[[]T]     // List[string] checked as []string
type Defined MyCustomWrap[string]       // remains a non-generic defined struct
```

Type parameters are bound within their declaration's scope. Named types, aliases and wrappers declared in other Go files resolve through Go PSI. Cycle/depth guards keep recursive or unresolved definitions conservative. Wrapper methods are retained alongside the inferred payload type so existing custom-conversion policies continue to apply.

## Checks using the inferred type

The inferred type feeds the shared datatype policy for tag keys, serialization options, Huma schema constraints, validator rules, collection traversal, and documentation. Direct referenced-field comparisons and condition literals use the inferred referenced datatype too.

Huma's physical pointer restriction remains distinct: `*MyCustomWrap[string]` is still a pointer request field, while the value wrapper `MyCustomWrap[*string]` is not itself a pointer. This preserves the existing value-based wrapper behavior.

This feature is a static checking convention. It does not rewrite Go code, automatically implement codecs/ParamWrapper interfaces, or guarantee that a runtime library will marshal or bind the wrapper. Referenced field paths continue to use actual Go field names; the plugin does not invent promoted paths through the payload.
