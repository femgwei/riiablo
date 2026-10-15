# Nova fractional damage precision — PARTIAL

Base source: `a5d6f23a92adb8b9acfe4595fd207b2cc9a7f180`.
The isolated checkout was made with `git archive`; only the five implementation/test
paths in `prepare-source.ps1` were overlaid. Project cache, build directories,
JavaCompile destinations and source-set output directories are isolated.

## Native evidence and scope

Local reference `F:/3rd_src/D2MOO/source/D2Common/src/Units/Missile.cpp`,
`MISSILE_CalculateDamageData` / `MISSILE_SetDamageStats`, constructs and stores
fixed-point elemental missile stats. D2Game `MISSILES/MissMode.cpp:281`,
`MISSMODE_RollDamageValue`, samples those stats using an exclusive maximum.
`UNIT/SUnitDmg.cpp:766` selects the lightning resistance row; lines 800–820 shift
flat magical reduction by eight bits, and `SUNITDMG_ApplyResistancesAndAbsorb`
retains fixed-point units through percentages and flat absorb.

This patch opts **Nova only** into 256 elemental units per HP. It rolls before
mitigation in those units, retains integer truncation at 1/256 HP, and converts
once when publishing HP-valued event channels, collision/combat logs, absorb
healing and final HP damage. Physical damage remains in whole HP. `totalDamage`
is a legacy whole-HP projection; precision-aware consumers use `totalDamageHp()`.
Existing callers default to scale 1. Reset restores scale 1.

This is not a full native damage replay. In particular the existing attack path
still applies MDR after resistance, PvP after absorb, player-style resistance
caps to monsters, and its existing absorb cap. Native PvP precedes mitigation,
MDR precedes resistance and elemental absorb percent caps at 40. The precision
tests explicitly characterize existing ordering rather than certify it as native.
Upstream skill-bound/mastery precision, native per-entity RNG, other spells,
mercenary/boss modifiers, natural monster AI, rendering and networking are outside
this change. The existing zero-damage/full-absorb healing gate is also unchanged.
No monster probabilities or collision gates were changed.

## Reproduction

Run `prepare-source.ps1` without `-Repaired` for old production plus the new ECS
test, then run only `:core:test --tests '*SorceressNovaIntegrationTest'` with the
absolute `isolated.init.gradle` path. This is the failing baseline.
Use a fresh source root with `-Repaired` for the repaired runs, then run
`run-tests.ps1 -SourceRoot <root> -Pass <unused-name>` twice. Evidence directories
are write-once. The test runner executes the prior combined skill/arena suite
plus the complete CombatSystemTest and five precision tests in one invocation.

`setup.log` records an initial invocation that used a relative init path which
Gradle resolved inside the archived project. It failed before tests; the absolute
path corrected that setup error. `baseline.log` and `baseline-nova.xml` are the
actual behavioral baseline: 8 tests, one failure, seed 0 raw 3.0, expected 1.5,
actual 1.0.

`first.log` / `first/` contain the first repaired pass (233 tests). A final small
review changed the combat audit to report HP and preserved negative-bound legacy
inputs; `final/` and `repeat/` are the final-source verification passes.

The ECS test runs 64 paired no-resistance/50%-resistance worlds with identical
test seeds, checks exact fixed-point truncation, requires fractional rolls and
at least 0.5 HP after resistance, verifies lightning event = total event = HP
loss, checks 100% immunity and two shared target claims, and confirms that six
additional ticks do not replay hits. The caller's RNG object and both states
are restored. These are controlled headless skill/collision fixtures, not natural
AI acceptance evidence. The existing arena unit tests are included as regression
coverage; no new natural arena run is claimed.

Final counts and deterministic comparisons are recorded in `verification.log`.
Both final-source runs passed 233 tests with zero failures, errors or skips.
All 64 paired samples match between runs, and SHA256 checks confirm all five
implementation/test files match the tested isolated snapshot.
