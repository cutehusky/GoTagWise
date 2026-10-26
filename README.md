# GoTagWise

<img src="src/main/resources/META-INF/pluginIcon.svg" width="96" height="96" alt="GoTagWise icon: a cyan code tag with a green check">

**Smart struct tags for Go.**

Author: **cutehusky**.

A GoLand plugin for writing and checking Go struct tags. Get datatype-aware completion, editable live templates, per-rule hover documentation, and inspections for tag syntax, options, field references, and conditional values.

## Features

- Complete tag keys, options, and validation rules supported by the current field's datatype.
- Complete Go field references in validator and Gin binding rules, with nested paths and datatype filtering.
- Expand bundled `stjson`, `stbson`, `stenv`, `stvalidate`, `stgorm`, `stbun`, and other templates with **Tab**.
- Hover over a tag or individual validator rule for documentation and examples.
- Find malformed tags, duplicate keys, incompatible datatypes, missing referenced fields, and invalid conditional literals.
- Track validator `dive`, `keys`, and `endkeys` through collection elements and map keys/values.
- Infer the payload datatype of generic wrappers with a `value T` or `Value T` field, such as `Wrap[string]` → `string`.
- Configure custom validator aliases, conform sanitizers, and Huma request-context assumptions.

Supports JSON, XML, YAML, BSON, DynamoDB, env, Gin, HCL, MessagePack, GORM, Redis, TOML, validator/v10, Bun, conform, and Huma v2. See the [complete support inventory](docs/support-reference.md) for exact keys and options.

## Compatibility

Targets **GoLand 2026.2+ (build 262+)** and IntelliJ IDEA with the Go plugin installed. Verified against GoLand 2026.2.1.1. Building against this SDK requires **JDK 25**; plugin sources target Java 21.

## Build and install

Build the installable ZIP using the included Gradle wrapper:

```powershell
.\gradlew.bat check buildPlugin
```

The default build downloads GoLand and build dependencies. To use an installed GoLand SDK:

```powershell
.\gradlew.bat check buildPlugin "-PlocalIdePath=C:\path\to\GoLand"
```

Install `build/distributions/GoTagWise-0.1.0.zip` through **Settings → Plugins → gear → Install Plugin from Disk**. Restart the IDE if prompted.

For an offline SDK build, isolated IDE tests, or development setup, see the [development guide](docs/development.md).

## Quick start

Inside a struct field's backticks, press **Ctrl+Space** for completion or type a template abbreviation such as `stjson` and press **Tab**.

```go
type Input struct {
    Email   string   `json:"email" validate:"required,email"`
    Emails  []string `validate:"dive,email"`
    Confirm string   `validate:"eqfield=Email"`
    Mode    string   `query:"mode" doc:"Search mode"`
    Limit   int      `query:"limit" minimum:"1" maximum:"100"`
    Port    int      `env:"PORT,required" envDefault:"8080"`
}
```

Hover over `query`, `email`, or `eqfield=Email`, or use **Ctrl+Q**, to read the relevant documentation. Enable automatic hover popups under **Settings → Editor → Code Editing → Show quick documentation on hover**. Use **Alt+Enter** for available spelling fixes.

See the [usage guide](docs/usage.md) for completion examples, templates, Huma pointers, and inspection settings. More Go examples are in [examples/tags.go](examples/tags.go).

## Documentation

| Guide | Contents |
| --- | --- |
| [Usage](docs/usage.md) | Editor workflow, datatype filtering, templates, hover help, and settings |
| [Struct-tag reference](docs/support-reference.md) | All 62 catalog keys, options, directives, and checks |
| [Validator rule reference](docs/validator-rules.md) | All 166 validator/Gin binding rules and datatype policies |
| [Huma reference](docs/huma-reference.md) | All 33 handled Huma-related keys and checks |
| [Implemented checks](docs/checks-reference.md) | Inspection coverage and runtime-only limits |
| [Validator field references](docs/validator-references.md) | Comparison/conditional rules, nested paths, and examples |
| [Generic wrappers](docs/generic-wrappers.md) | Automatic payload-type inference and qualification rules |
| [Development](docs/development.md) | Build, tests, project layout, and documentation generation |

The plugin performs static checks. Unknown/custom types remain conservative, and shared HCL/TOML/Redis keys use combined vocabularies. Runtime input, custom registries, pointer nilness, and collection entry existence still need application tests.
