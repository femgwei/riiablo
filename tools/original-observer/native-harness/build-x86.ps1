$ErrorActionPreference = 'Stop'
$vcvars = 'C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvars32.bat'
if (!(Test-Path -LiteralPath $vcvars)) { throw "vcvars32.bat not found: $vcvars" }
$out = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path $out | Out-Null
foreach ($name in @('d2common_smoke', 'd2common_seed', 'd2common_rng', 'd2game_controller', 'd2game_cast', 'd2game_frozen_orb', 'd2game_bone_prison', 'd2game_bone_spear', 'd2game_poison_javelin', 'd2game_molten_boulder', 'd2game_inferno', 'd2game_enchant', 'd2game_druid_firestorm', 'd2game_druid_molten', 'd2game_druid_fissure', 'd2game_druid_volcano', 'd2game_druid_volcano_hits', 'd2game_druid_twister', 'd2game_druid_tornado', 'd2game_druid_firestorm_callbacks', 'd2game_barbarian', 'd2game_barbarian_cast', 'd2game_barbarian_target', 'd2game_whirlwind', 'd2game_runtime_trace', 'd2game_fallen_death', 'd2game_raven_target', 'd2game_ai_batch', 'd2game_server_population', 'd2net_trace', 'd2socket_trace', 'd2client_animation_trace', 'd2gfx_export_trace', 'recover-runtime-trace')) {
  $src = Join-Path $PSScriptRoot "$name.cpp"
  $obj = Join-Path $out "$name.obj"
  $exe = Join-Path $out "$name.exe"
  cmd /c "call `"$vcvars`" && cl /nologo /utf-8 /std:c++17 /EHsc /W4 /DUNICODE /D_UNICODE /c `"$src`" /Fo`"$obj`" && link /nologo /subsystem:console /out:`"$exe`" `"$obj`""
  if ($LASTEXITCODE -ne 0) { throw "x86 build failed for $name ($LASTEXITCODE)" }
  Write-Output $exe
}
