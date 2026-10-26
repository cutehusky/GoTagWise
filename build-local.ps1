param(
    [Parameter(Mandatory = $true)][string]$IdePath,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not (Test-Path -LiteralPath (Join-Path $IdePath 'plugins/go-plugin/lib/go-plugin.jar'))) {
    throw 'IdePath must point to a GoLand installation with its bundled Go plugin.'
}
if (-not $JavaHome) { $JavaHome = Split-Path (Split-Path (Get-Command javac).Source) }
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
$jar = Join-Path $JavaHome 'bin/jar.exe'
$root = $PSScriptRoot
$build = Join-Path $root 'build'
$classes = Join-Path $build ('local-' + [Guid]::NewGuid().ToString('N'))
$testClasses = Join-Path $classes 'tests'
$mainClasses = Join-Path $classes 'main'
$lib = Join-Path $classes 'GoTagWise/lib'
New-Item -ItemType Directory -Force -Path $mainClasses,$testClasses,$lib | Out-Null
$cp = (Join-Path $IdePath 'lib/*') + ';' + (Join-Path $IdePath 'plugins/go-plugin/lib/*')
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& $javac --release 21 -encoding UTF-8 -classpath $cp -d $mainClasses @sources
if ($LASTEXITCODE -ne 0) { throw 'Plugin compilation failed' }
$tests = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/test/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& $javac --release 21 -encoding UTF-8 -classpath "$mainClasses;$cp" -d $testClasses @tests
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
Push-Location $root
try {
    & $java -cp "$mainClasses;$testClasses" dev.structtags.core.CoreTest
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
} finally { Pop-Location }
& $jar --create --file (Join-Path $lib 'GoTagWise.jar') -C $mainClasses . -C (Join-Path $root 'src/main/resources') .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed' }
$dist = Join-Path $build 'distributions'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$zip = Join-Path $dist 'GoTagWise-0.1.0.zip'
Compress-Archive -Path (Join-Path $classes 'GoTagWise') -DestinationPath $zip -Force
Write-Output "Built $zip"
