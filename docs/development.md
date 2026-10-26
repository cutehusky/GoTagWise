# Development

## Requirements

- JDK 25 for the configured GoLand 2026.2 SDK. Sources compile with Java 21 compatibility.
- The included Gradle 9.3.0 wrapper; no separate Gradle installation is needed.
- PowerShell 7 for the Windows helper scripts.
- Network access for Gradle dependencies and the default SDK download, or an installed GoLand SDK for the offline build route.

## Build and run

From the repository root:

```powershell
.\gradlew.bat check buildPlugin
```

The default SDK is GoLand 2026.2.1.1. To use a local installation and launch an isolated development IDE:

```powershell
.\gradlew.bat check buildPlugin "-PlocalIdePath=C:\path\to\GoLand"
.\gradlew.bat runIde "-PlocalIdePath=C:\path\to\GoLand"
```

On macOS/Linux, invoke the wrapper with `sh ./gradlew` and use the appropriate IDE path. The PowerShell packaging/smoke scripts below target Windows.

The distributable is written to `build/distributions/GoTagWise-0.1.0.zip`. Install it through the IDE's **Install Plugin from Disk** command. `build/`, Gradle caches and IDE project metadata are ignored by Git.

### Offline SDK build on Windows

```powershell
./build-local.ps1 -IdePath 'C:\path\to\GoLand' -JavaHome 'C:\path\to\jdk-25'
```

This compiles against the installed SDK, runs the dependency-free core/resource suite, and packages the ZIP without downloading Gradle or dependencies. Intermediate class directories are unique to avoid stale classes.

## Validation

`check` runs the dependency-free `coreTest` Java entry point. The ordinary JUnit task is disabled because this suite does not use JUnit. Core checks cover parsing, value/type policies, validator traversal/references, completion filtering, documentation and resource consistency.

Run the IDE integration suite on Windows:

```powershell
./test-ide.ps1 -IdePath 'C:\path\to\GoLand' -JavaHome 'C:\path\to\jdk-25'
```

The script builds the plugin and starts GoLand's headless inspection command with separate config/system/plugin directories under `build`. It tests editor integration and checks expected warnings against valid/invalid fixtures. Its test-only plugin is excluded from the distributable. Report locations are printed on completion. The IDE needs a local socket for its startup lock; environments that block local sockets cannot run this suite.

Before proposing an implementation change, run checks relevant to it. Changes to PSI/editor behavior need the IDE suite; documentation-only changes need inventory and Markdown-link checks.

## Project layout

| Path | Purpose |
| --- | --- |
| `src/main/java/dev/structtags/core` | Catalog, parser, value/type rules, references, completion policy and documentation rendering |
| `src/main/java/dev/structtags/ide` | Go PSI, inspections, completion, templates and documentation targets |
| `src/main/resources` | Plugin descriptor, bundled templates and inspection description |
| `src/test/java` | Dependency-free regression suite and reference/template generators |
| `src/ideTest` | Test-only IDE integration plugin |
| `testData/inspection` | Valid/invalid Go inspection fixtures |
| `examples` | Go examples for manual exploration |
| `docs` | User guides and complete support/check references |

## Update templates and support references

`TagCatalog` is the vocabulary source. Keep compatibility policy, documentation descriptions, examples, templates and tests aligned when adding or changing a key/rule.

Compile the core and generator utilities, then regenerate committed artifacts:

```powershell
New-Item -ItemType Directory -Force -Path build/doc-generator | Out-Null
javac --release 21 -encoding UTF-8 -d build/doc-generator src/main/java/dev/structtags/core/*.java src/test/java/dev/structtags/core/*.java
java -cp build/doc-generator dev.structtags.core.TemplateGenerator
java -cp build/doc-generator dev.structtags.core.SupportReferenceGenerator
java -cp build/doc-generator dev.structtags.core.SupportReferenceGenerator --check
```

The template generator updates `src/main/resources/liveTemplates/GoTagWise.xml`; the regression suite detects stale templates. The support generator updates [struct tags](support-reference.md), [validator rules](validator-rules.md), and [Huma keys](huma-reference.md). Update the manually maintained [check inventory](checks-reference.md) when inspection behavior changes.

To verify support references without rewriting them, use the final `--check` command after compiling the utilities. Check relative Markdown links after moving or removing documentation.
