# Necromancer skeleton original D2Gfx capture (2026-11-25)

The hidden/minimized original-client path was exercised with a newly created
`CXNecro` 1.10f fixture. The fixture was a Necromancer with Raise Skeleton
(skill 70) at level 20, and the F1 hotkey was populated with that skill. The
bounded `d2gfx_export_trace.exe` probe completed with:

- 5,396 D2Gfx export events;
- `detached=true` and `cleanup_confirmed=true`;
- all recorded calls reported `window_state=minimized`;
- client sampling completed without visual capture claims.

The attempt did not produce a skeleton. The canonical D2Gfx cell filter saw
5,003 decoded cell calls with token/unit pairs `NE/0`, `R3/2`, `R2/2`, and
`TO/2`, but zero `SK` calls. The client animation sample stayed at `NENUHTH`
and `client_animation_skill_mode_observed=false`. Raise Skeleton requires a
real corpse target; the Blood Moor entry scene had no prepared corpse, and the
background right-click therefore did not enter the skill animation.

This is a successful toolchain/negative-control result, not a skill parity
result. It proves the hidden D2Gfx probe works for the Necromancer scene and
that zero `SK` calls are an observed absence under a no-corpse precondition.
The next capture must add a bounded corpse-preparation step in the same live
scene, then reuse this exact D2Gfx and normalization path.

## Corpse-preparation retry

The follow-up harness now prepares a same-room corpse before the client cast,
and the client sampler observed the caster's `NESCHTH` (`SC`) action: 16 frames,
speed 256, with the native action flag at frame 8. Exact corpse projection was
also retried. The bounded D2Gfx helper still completed its attach/detach gate,
but no decoded `SK` cell was emitted and the client never published a summon
unit animation. This keeps the result negative: the remaining problem is the
retail corpse-target/skill acceptance boundary (or the helper's short-lived
render session), not an observed Riiablo draw mismatch.

## Preserved-corpse capture

The next bounded run kept the generated corpse in the live monster hash while
removing only the temporary attacker. The refreshed client snapshot confirmed
the exact target unit before the click:

```text
type=1 class_id=0 unit_id=23 mode=0 position=(4527,5641)
```

The run directory was:

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261125-044454-bd492aa2/
```

The D2Gfx probe recorded 5,370 decoded cell events while the window remained
minimized and produced 765 normalized `SK` draws. Unlike the earlier negative
samples, the normalized trace contains a second entity (`unit_index` 363) with
the complete summon transition:

```text
SK/S1 frames 3..11   elapsed 1313..2141 ms   entity 363
SK/NU frame 0         elapsed 2203..2219 ms   entity 363
SK/WL frames 0..3     elapsed 2328..2688 ms   entity 363
SK/NU frames 0..3      elapsed 2750..2985 ms   entity 363
```

The first entity (`unit_index` 0) is the corpse and remains `SK/DT` followed
by `SK/DD`; it is excluded from movement conclusions. The summon entity's
`WL` screen path moved from approximately `(414,264)` to `(353,309)` during
the observed walk segment. This is evidence of a real original summon and its
draw-mode order, rather than a pixel or complete-frame capture. The helper's
final debugger detach reported a Win32 5 cleanup warning and the enclosing
process session later reported the original process exited, so the run is
`runtime_status=observed_with_cleanup_warning`, not an end-to-end accepted
batch.

The normalized output is available at:

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261125-044454-bd492aa2/orchestrator/process-session/001-raise-skeleton-d2gfx-minimized/skeleton-draw-trace.json
```

## Riiablo correction

The original `Skills.txt` row for both Raise Skeleton (70) and Raise Skeletal
Mage (80) specifies `Summode=S1`. Riiablo created the pet with the S1 visual
mode but did not install a `Sequence(S1 -> NU)`, allowing `NecroPet` to begin
following before the emergence animation had completed. The server skill
handler now installs that sequence immediately after successful pet creation.
The existing 25 Hz movement/animation coupling remains at the retail values:
the skeleton's MonStats `Velocity=13`, native 75% movement state and
`SKWLHTH` base animation rate 128 resolve to the tested 96 movement animation
rate.

Validation:

```text
tools/original-observer/necromancer-skeleton-birth-sequence-validation-2026-11-25.log
```

`NecromancerSummonIntegrationTest` and `NecromancerSkeletonMovementTest`
passed under JDK 17. This fixes the observed early-walk/sliding presentation
path; it does not claim pixel identity, GPU visual equivalence, or complete
network interpolation parity.
