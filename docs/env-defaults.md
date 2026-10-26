# Env default datatype checks

GoTagWise checks `envDefault` against the resolved field datatype using caarlos0/env/v11's built-in parsing conventions. Aliases, pointers and supported generic wrapper payloads share these checks.

```go
type Config struct {
    Port    int            `env:"PORT" envDefault:"8080"`
    Enabled bool           `env:"ENABLED" envDefault:"true"`
    Timeout time.Duration  `env:"TIMEOUT" envDefault:"5s"`
    Ports   []uint16       `env:"PORTS" envSeparator:"|" envDefault:"80|443"`
    Limits  map[string]int `env:"LIMITS" envKeyValSeparator:"=" envDefault:"read=10,write=20"`
}
```

| Datatype | Checks |
| --- | --- |
| String | Any text is valid |
| Boolean | Go forms: `true`, `false`, `True`, `False`, `TRUE`, `FALSE`, `t`, `f`, `T`, `F`, `1`, `0` |
| Integer | Decimal syntax and signed/unsigned bounds; no hexadecimal or underscores. Env's built-in `int` and `uint` parsers use **32 bits**, including on 64-bit systems |
| Float | Go decimal/hexadecimal floating-point syntax and overflow; `NaN` and signed infinity forms are allowed |
| time.Duration | Go duration syntax (`300ms`, `1.5h`, `2h45m`, or unitless `0`) and signed 64-bit nanosecond range |
| Slice | Each element, using `envSeparator` (default `,`). Values are not trimmed |
| Map | Entry format, key datatype and value datatype. Uses `envSeparator` (default `,`) and `envKeyValSeparator` (default `:`); split on the first key/value separator |

For example, `Port int` with `envDefault:"abc"`, `Enabled bool` with `envDefault:"yes"`, and `[]int` with `envDefault:"1,bad"` produce warnings. Arrays, complex numbers, functions, channels and uintptr have no built-in env default parser, so incompatible tag completion and documentation are hidden for those field kinds.

`type Delay = time.Duration` retains duration parsing. A new defined type (`type Delay time.Duration`) has the underlying integer parser unless a runtime custom parser is registered.

## Runtime boundaries

An empty default skips conversion in env/v11, so `envDefault:""` is not reported as a malformed numeric value. An `env:"-"` field is ignored. With `env:",file"`, the default is a file path, so its contents cannot be checked statically. Defaults containing `$` with the `expand` option are left to runtime environment expansion.

Detected `UnmarshalText` methods and unresolved/interface types remain conservative. Arbitrary struct defaults, URL parsing, location database lookups, and application-specific `Options.FuncMap` parsers are not executed. Custom parsers registered at runtime can change scalar parsing rules; this inspection describes the built-in parsers. Generic wrapper projection is a static convention and does not register a runtime env parser.

The checks follow the [env/v11 implementation](https://github.com/caarlos0/env/blob/v11.3.1/env.go). The datatype inspection setting controls these warnings. See the [check inventory](checks-reference.md).
