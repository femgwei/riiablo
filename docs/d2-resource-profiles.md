# Diablo II resource profiles

## Alignment baseline

The D2MOO behavior-alignment branch uses **Diablo II 1.10f** resources. D2MOO's implemented
code paths and unresolved retail DLL fallbacks both target that version, so gameplay formulas,
Excel rows and MPQ assets must come from one 1.10f installation.

`libd2` and `dark-magic` target **1.14d**. They are useful independent references for tests,
architecture, DRLG, save and item code, but their IDs, data rows and expected numeric results are
not 1.10f parity fixtures.

## Runtime rules

- Never combine `Patch_D2.mpq` or extracted Excel files from different profiles.
- Keep 1.10f and 1.14 output, extracted data and visual baselines in separate directories.
- Pass a declared profile to real-resource verification with `-Pd2Version=1.10f` or
  `-Pd2Version=1.14`. Startup logs record it as `[D2_RESOURCE_PROFILE]`.
- A declared profile records test intent; it does not make mixed MPQs valid. The installation
  directory remains the source of all runtime resources.

Primary verification command:

```powershell
.\gradlew.bat :desktop:offscreenCamp `
  '-Pd2Home=<Diablo II 1.10f 目录>' `
  '-PsavesDir=<独立测试存档目录>' `
  '-PvisualOutput=<独立的 1.10f 输出目录>' `
  '-Pd2Version=1.10f'
```

The 1.14 profile is compatibility-only and must be written to a different output directory.
Retail resources remain external and must never be committed to this repository.
