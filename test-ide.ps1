param(
    [Parameter(Mandatory = $true)][string]$IdePath,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $JavaHome) { $JavaHome = Split-Path (Split-Path (Get-Command javac).Source) }
& (Join-Path $PSScriptRoot 'build-local.ps1') -IdePath $IdePath -JavaHome $JavaHome
$root = $PSScriptRoot
$sandbox = Join-Path $root ('build/ide-test-' + [Guid]::NewGuid().ToString('N'))
$testClasses = Join-Path $sandbox 'test-classes'
New-Item -ItemType Directory -Force -Path "$sandbox/plugins/smoke/lib","$sandbox/config","$sandbox/system","$sandbox/results",$testClasses | Out-Null
Expand-Archive -LiteralPath "$root/build/distributions/GoTagWise-0.1.0.zip" -DestinationPath "$sandbox/plugins"
$cp = "$IdePath/lib/*;$IdePath/plugins/go-plugin/lib/*;$sandbox/plugins/GoTagWise/lib/*"
$tests = @(Get-ChildItem -LiteralPath "$root/src/ideTest/java" -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome/bin/javac.exe" --release 21 -encoding UTF-8 -classpath $cp -d $testClasses @tests
if ($LASTEXITCODE -ne 0) { throw 'IDE smoke test compilation failed' }
& "$JavaHome/bin/jar.exe" --create --file "$sandbox/plugins/smoke/lib/smoke.jar" -C $testClasses . -C "$root/src/ideTest/resources" .
if ($LASTEXITCODE -ne 0) { throw 'IDE smoke test packaging failed' }
$normalized = $sandbox.Replace('\','/')
Set-Content -LiteralPath "$sandbox/idea.properties" -Value @(
    "idea.config.path=$normalized/config",
    "idea.system.path=$normalized/system",
    "idea.plugins.path=$normalized/plugins",
    "idea.log.path=$normalized/log",
    'idea.trust.all.projects=true',
    'ide.show.tips.on.startup.default.value=false'
)
$info = Get-Content -LiteralPath "$IdePath/product-info.json" -Raw | ConvertFrom-Json
$launch = $info.launch | Where-Object { $_.os -eq 'Windows' -and $_.arch -eq 'amd64' } | Select-Object -First 1
$bootCp = ($launch.bootClassPathJarNames | ForEach-Object { "$IdePath/lib/$_" }) -join ';'
$launchArgs = @('-Xmx1536m','-Djava.awt.headless=true',"-Didea.properties.file=$sandbox/idea.properties",
    '-Didea.initially.ask.config=never',"-Dcutehusky.gotagwise.smoke.report=$sandbox/editor-result.txt")
$launchArgs += @($launch.additionalJvmArguments | ForEach-Object { $_.Replace('%IDE_HOME%', $IdePath) })
$launchArgs += @('-cp',$bootCp,'com.intellij.idea.Main','inspect',"$root/testData/inspection","$root/testData/inspection/profile.xml","$sandbox/results",'-v2')
$quotedArgs = $launchArgs | ForEach-Object { '"' + $_.Replace('\','/').Replace('"','\"') + '"' }
Set-Content -LiteralPath "$sandbox/launch.args" -Value $quotedArgs -Encoding utf8NoBOM
& "$JavaHome/bin/java.exe" "@$sandbox/launch.args"
if ($LASTEXITCODE -ne 0) { throw 'Headless GoLand inspection failed' }
$editorReport = Get-Content -LiteralPath "$sandbox/editor-result.txt" -Raw
if (-not $editorReport.StartsWith('Passed ')) { throw $editorReport }
[xml]$inspection = Get-Content -LiteralPath "$sandbox/results/GoTagWise.xml" -Raw
$problems = @($inspection.problems.problem)
$syntaxProblems = @($problems | Where-Object { $_.file -like '*/tags.go' })
$typeProblems = @($problems | Where-Object { $_.file -like '*/types.go' })
$ruleProblems = @($problems | Where-Object { $_.file -like '*/rules.go' })
$referenceProblems = @($problems | Where-Object { $_.file -like '*/references.go' })
$wrapperProblems = @($problems | Where-Object { $_.file -like '*/wrappers.go' })
if ($syntaxProblems.Count -ne 6 -or $typeProblems.Count -ne 14 -or $ruleProblems.Count -ne 12 -or $referenceProblems.Count -ne 12 -or $wrapperProblems.Count -ne 7 -or $problems.Count -ne 51) { throw "Expected six value, fourteen datatype, twelve rule, twelve reference and seven wrapper diagnostics; found $($syntaxProblems.Count), $($typeProblems.Count), $($ruleProblems.Count), $($referenceProblems.Count), $($wrapperProblems.Count)" }
$lines = @($syntaxProblems | ForEach-Object { [int]$_.line })
if (($lines -join ',') -ne '4,5,6,7,8,9') { throw "Unexpected inspection locations: $lines" }
Write-Output $editorReport.Trim()
if (@($typeProblems | Where-Object { [int]$_.line -lt 8 -or [int]$_.line -gt 21 }).Count -ne 0) { throw 'Unexpected datatype diagnostics on valid fields' }
if (@($ruleProblems | Where-Object { [int]$_.line -lt 4 -or [int]$_.line -gt 15 }).Count -ne 0) { throw 'Unexpected rule diagnostics on valid fields' }
if (@($referenceProblems | Where-Object { [int]$_.line -lt 5 -or [int]$_.line -gt 16 }).Count -ne 0) { throw 'Unexpected reference diagnostics on valid fields' }
if (@($wrapperProblems | Where-Object { [int]$_.line -lt 5 -or [int]$_.line -gt 11 }).Count -ne 0) { throw 'Unexpected wrapper diagnostics on valid fields' }
Write-Output 'Passed six value, fourteen datatype, twelve rule, twelve reference and seven wrapper inspection diagnostics; valid fields have no plugin diagnostics.'
Write-Output "Reports: $sandbox"
