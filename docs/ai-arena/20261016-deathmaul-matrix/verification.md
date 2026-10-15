# DeathMaul difficulty/seed matrix — 2026-10-16

This is a validation batch, with no production behavior changes. DeathMaul's
server implementation remains the accepted `f93f0738f` version; its AI,
AnimStepper, NativeDeathMaul, ServerSkillSystem and MissileCollisionSystem
sources were checked unchanged during this run.

## Scope and result

Real 1.10f resources from D2_HOME, all six deathmauler1–6 rows, difficulties
0/1/2 (Normal/Nightmare/Hell), seeds1/7/42,500 ticks per scene. Each configuration
runs near, far and deathmaul-cycle for each row:18 scenes, including six strict
natural skill assertions. All nine configurations are independently repeated.

**162 scenes per pass,324 executions total;54 natural assertions per pass,108
total.** All selected scenes have zero warnings/errors. All54 combinations of
monster/difficulty/seed pass the existing natural DeathMaul lifecycle oracle.
The nine first/repeat JSONL pairs are byte-identical; individual SHA256 values
are in `summary.json`. Normal/seed1 matches all18 accepted scene records from
the previous complete Do136 arena, field for field.

The unchanged oracle requires the real A2 sequence action at point13, an actual
owner/skill308/row502 missile with correct target position, level and lifetime,
an inactive interval without HP loss, stationary behavior, repeated physical-only
MISSILE events at least10 frames apart, matching HP reduction, and expiry/removal.
Calling a skill or causing ordinary melee damage cannot satisfy it. No forced
SkillDo, AI probability changes, teleportation or disabled collision are used.
The near scenes independently retain ordinary melee damage observations.

## Evidence and reproduction

`d<difficulty>-s<seed>-first/repeat.jsonl` contain the18 selected scene records.
The corresponding `*-scenes.zip` archives retain all scene logs and the original
unfiltered `results.jsonl`. The arena also journals1162 unselected catalog cases
as NOT_RUN in each original1180-record file. These are deliberately excluded
from the selected-scene count and are **not** claimed as executed or passing.
`verify.ps1` checks both archived raw journals against the selected records,
including the exact unselected count and status, rather than trusting only
the extracted files. Extraction preserves the original JSON lines.

`*.log` preserve the18 actual Gradle runs; `execution.log` lists their order.
All Gradle runs succeeded. Separate module outputs and a separate Gradle project
cache prevent concurrent builds from deleting these class outputs. `run.ps1`
performs all runs and archives/collects the evidence. It refuses to overwrite
existing evidence or run outputs. Use an unused RunName and another evidence directory.

```powershell
$env:D2_HOME='G:\BaiduNetdiskDownload\Diablo II 1.10F'
./docs/ai-arena/20261016-deathmaul-matrix/run.ps1 -RunName 20261016-deathmaul-matrix-new -EvidenceDirectory build/deathmaul-matrix-evidence-new
./docs/ai-arena/20261016-deathmaul-matrix/verify.ps1 -Evidence build/deathmaul-matrix-evidence-new
```

This batch closes the three-difficulty/multi-seed **synthetic server arena**
coverage gap for DeathMaul. It is not a new full-directory acceptance run,
native RNG equivalence proof, real terrain test, TCP session or GL visual check.
No new unit tests were added for this validation-only change.

CltDo76/trail generation remains pending. A search of the local D2MOO sources
finds the server Do136 but no corresponding client callback implementation;
the available Skills/Missiles rows alone do not establish its generation
algorithm. No speculative client effect was introduced. Overall native
alignment remains PARTIAL, including client visuals, GL/TCP, terrain,
native unit RNG and global scheduling. Concurrent path edits are excluded.
