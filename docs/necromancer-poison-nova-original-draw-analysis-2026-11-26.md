# Necromancer Poison Nova client draw analysis (1.10f)

This pass uses the hidden/minimized original run `20261125-132906-dbd6b713`.
The native D2Gfx collector completed with `status=passed`, `detached=true`,
and `cleanup_confirmed=true`; every sampled Poison Nova draw reported
`window_state=minimized`. The outer orchestrator still returned a non-zero
batch status, so this is partial runtime evidence rather than a clean end to
the full job.

The raw trace is:

`tools/original-observer/broker/20261121-165133-4490d598/runs/20261125-132906-dbd6b713/orchestrator/process-session/001-poison-nova-d2gfx-minimized/d2gfx-call-trace.jsonl`

The normalized evidence is
`tools/original-observer/necromancer-poison-nova-original-draw-2026-11-26.json`.
It contains 719 unit type 3 / missile class 118 draws. The observed cell has
`max_frames=30`, frame values
`0,3,5,7,9,11,13,15,17,19,21,22,24,26,28,29`, and 52 sampled path-direction
values. The 52 values cover every distinct direction produced by the native
64-offset ring; duplicate directions are expected because the original
`PATH_GetDirectionVector` quantizes nearby offsets to the same 64-way facing.
The projectile starts at screen `(320,232)` and the farthest sampled eastward
path reaches `(689,231)` at frame 29. The draw window spans 1422–2734 ms.

The static D2MOO implementation in `SKILLS_CreateMissileRing` confirms the
exact `NATIVE_NOVA_X/Y` table already used by Riiablo: 64 offsets, missile row
118, `Vel=12`, `Range=30`, and a 32-direction / 30-frame `poisonNova.dcc`.
`MISSILES_CreateMissileFromParams` then applies the native 75% velocity rule
before the fp16 path conversion. The resulting Riiablo velocity is 14.0625
world units per second, and 30 native frames cover 16.875 map units.

The actual gap was in the entity factory: Poison Nova fell through the generic
missile branch, which used the table velocity directly and did not allocate at
the integer cell centre. Poison Nova now has a dedicated native motion adapter
that applies the 75% rule, uses the native cell origin, preserves the quantized
fp16 path, and keeps its presentation-facing angle synchronized with that
velocity. `MissileCollisionSystem` uses the same fixed-point path for movement.

The focused core validation passed:

* `NativePoisonNovaMotionTest`
* `NecromancerPoisonNovaIntegrationTest` (3 tests)

This trace does not establish the caster COF sequence, target-hit timing, final
native removal, or GPU blend output. Those remain open and are recorded as
limitations rather than inferred from the draw sample.
