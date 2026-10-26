# Example value datatype checks

GoTagWise checks Huma's `example` tag against the resolved field's schema datatype, including pointers, aliases and supported generic wrapper payloads.

```go
type Response struct {
    Name    string         `example:"cutehusky"`
    Count   int            `example:"12"`
    Enabled bool           `example:"true"`
    Scores  []int          `example:"[1,2,3]"`
    Tags    []string       `example:"go,tags"`
    Limits  map[string]int `example:"{\"read\":10,\"write\":20}"`
}
```

| Schema datatype | Accepted example values |
| --- | --- |
| String | Raw text, including the empty string; JSON quotation marks are not needed |
| Boolean | JSON `true` or `false`; `TRUE`, `1` and quoted booleans are rejected |
| Integer | An integral JSON number, including `1.0` or `1e2`; fractional numbers and quoted numbers are rejected |
| Number | A finite JSON number; no Go hex numbers, `NaN` or infinity |
| Array | A JSON array, with each known element datatype checked |
| String array | Also accepts comma-separated plain text, following Huma's shorthand |
| Map | A JSON object, with each known value datatype checked |
| Struct | A JSON object; property datatypes are not currently inspected |

For example, `Count int` with `example:"wrong"`, `Enabled bool` with `example:"TRUE"`, and `[]int` with `example:"[1,true]"` produce warnings. Malformed JSON, trailing values and invalid JSON escapes are reported for non-string schemas. JSON syntax and JSON number overflow are checked. Go integer widths, unsigned bounds, float32 range and schema constraints such as minimum/maximum or enum membership are not checked for examples; examples describe schema values rather than converting them to Go fields.

Byte slices/arrays use Huma's string schema. Known time, URL, IP and text-unmarshaling types also use string schemas. The checker does not validate those strings' formats or base64 encoding. `time.Duration` uses its integer schema for examples (`example:"1000000000"`), while `envDefault` uses duration text (`envDefault:"1s"`).

Pointers use their underlying schema datatype. A pointer does not automatically make `example:"null"` valid: Huma's example type checker expects a value of the schema type. String examples treat `null` as literal text. Unknown interfaces, custom schemas, schema transformers, and custom JSON encoders remain conservative. JSON nested deeper than 128 levels is left unchecked.

Completion keeps `example` available on supported field kinds and hides it on known function/channel/complex kinds without custom schemas. Hover documentation shows a datatype-compatible sample, even when the current value is invalid. The datatype inspection setting controls warnings.

The conventions follow Huma's [schema and example parsing implementation](https://github.com/danielgtaylor/huma/blob/main/schema.go). See the [check inventory](checks-reference.md).
