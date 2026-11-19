param(
  [Parameter(Mandatory = $true)][string]$D2Home
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$selectedHome = (Resolve-Path -LiteralPath $D2Home).Path
if (!(Test-Path -LiteralPath (Join-Path $selectedHome 'Patch_D2.mpq') -PathType Leaf)) {
  throw "Missing Patch_D2.mpq in selected installation: $selectedHome"
}
$savedJavaOptions = $env:JAVA_TOOL_OPTIONS
$savedNativeHome = $env:D2_110F_HOME
Push-Location -LiteralPath $repo
try {
  python (Join-Path $PSScriptRoot 'seed-differential.py') --home $selectedHome
  if ($LASTEXITCODE -ne 0) { throw 'Original DLL differential gate failed' }
  python (Join-Path $PSScriptRoot 'rng-differential.py') --home $selectedHome
  if ($LASTEXITCODE -ne 0) { throw 'Original RNG sequence differential gate failed' }
  # This repository's Byte Buddy predates Java 25. Scope its compatibility
  # option to this process and descendants, then restore the caller's value.
  $env:JAVA_TOOL_OPTIONS = "$savedJavaOptions -Dnet.bytebuddy.experimental=true".Trim()
  $env:D2_110F_HOME = $selectedHome
  & (Join-Path $repo 'gradlew.bat') :core:monsterAiTest :core:playerCombatRegression :core:m4CoreDataSourceRegression "-Pd2Home=$selectedHome" --continue --no-daemon --console=plain
  $regressionExitCode = $LASTEXITCODE
  & (Join-Path $repo 'gradlew.bat') :core:test --tests '*ReferenceElementalPresentationTest' --tests '*RenderSystemOrderingTest' --tests '*ServerSystemIsolationTest' --tests '*DependencyDirectionTest' "-Pd2Home=$selectedHome" --no-daemon --console=plain
  $isolationExitCode = $LASTEXITCODE
  # This opt-in JUnit fixture otherwise reports a skipped installation as a
  # successful Gradle task. An unattended original-resource gate must run it.
  $resourceReport = Join-Path $repo 'core\build\test-results\m4CoreDataSourceRegression\TEST-com.riiablo.codec.excel.Native110FTxtTablesIntegrationTest.xml'
  if (!(Test-Path -LiteralPath $resourceReport -PathType Leaf)) {
    throw 'Original MPQ verification produced no JUnit report'
  }
  [xml]$resourceResult = Get-Content -LiteralPath $resourceReport -Raw
  $resourceSuite = $resourceResult.testsuite
  if ([int]$resourceSuite.tests -lt 1 -or [int]$resourceSuite.skipped -ne 0 -or
      [int]$resourceSuite.failures -ne 0 -or [int]$resourceSuite.errors -ne 0 -or
      @($resourceSuite.testcase | Where-Object { $_.name -eq 'manifestsAndRequiredHeadersMatchNative110f()' }).Count -ne 1) {
    throw "Original MPQ verification did not pass without skips: $resourceReport"
  }
  if ($regressionExitCode -ne 0) { throw 'Headless regression gate failed; see core/build/reports/tests/' }
  if ($isolationExitCode -ne 0) { throw 'World isolation / presentation gate failed; see core/build/reports/tests/test/' }
} finally {
  $env:JAVA_TOOL_OPTIONS = $savedJavaOptions
  $env:D2_110F_HOME = $savedNativeHome
  Pop-Location
}
