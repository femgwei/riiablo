# Necromancer Bone Spirit client draw analysis (1.10f)

This pass used the hidden/minimized original run `20261126-084711-c125e2a8`.
The orchestrator and native collector both completed successfully:
`status=passed`, `detached=true`, and `cleanup_confirmed=true`. The trace's
Bone Spirit samples all reported `window_state=minimized`; no original window
was restored to the foreground.

Raw trace:

`tools/original-observer/broker/20261121-165133-4490d598/runs/20261126-084711-c125e2a8/orchestrator/process-session/001-bone-spirit-d2gfx-minimized/d2gfx-call-trace.jsonl`

Normalized evidence:

`tools/original-observer/necromancer-bone-spirit-original-draw-2026-11-26.json`

The trace contains 80 draws of unit type 3 / missile class 193 (`bonespirit`).
The sampled path direction is 56, and the DCC cycles through all eight frames
`0..7`. The first and last samples are at screen `(347,215)` and `(1364,724)`;
the run therefore captures a long in-flight segment. No class 327
`bonespiritexplode` draw was sampled, so this run does not prove target-hit or
impact cleanup.

Static 1.10f data identifies Bone Spirit as skill 93 with `SrvDoFunc=10`,
`bonespirit` missile 193, `pSrvDoFunc=7` guided motion, `pCltDoFunc=7`,
`pSrvHitFunc=10`, `pCltHitFunc=13`, `Vel=12`, `Range=128`, and an 8-direction,
8-frame `BoneSpirit.dcc`. The native missile creation path applies the common
75% velocity rule before fp16 stepping, producing the same 14.0625 world-units
per-second conversion used by the existing native projectile adapters.

The implementation gap was that Bone Spirit used the generic missile factory:
it kept the table speed directly and did not allocate at the integer cell
centre. Riiablo now has a dedicated `NativeBoneSpiritMotion` adapter, uses the
native cell origin and fixed-point movement path, and keeps the presentation
angle synchronized with the quantized native velocity. Existing guided-target
retargeting remains authoritative and is unchanged.

Focused validation passed:

* `NativeBoneSpiritMotionTest`
* `NativePoisonNovaMotionTest`
* `NecromancerPoisonNovaIntegrationTest` (3 tests)
* `NecromancerDamageOwnershipTest`
* d2gs `D2GSSimulationAssemblyTest`
* d2gs `ServerEntityLifecycleTest`

The caster COF sequence, a controlled BoneSpiritExplode hit, final removal,
and GPU blend/palette equality remain open because they were not present in this
sample.
