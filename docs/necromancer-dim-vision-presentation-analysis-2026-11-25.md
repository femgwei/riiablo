# Necromancer Dim Vision presentation analysis (2026-11-25)

Dim Vision (skill 71) was audited as the next active Necromancer state skill.
The shared curse path already consumes the Necromancer `Skills.txt` row rather
than using the older UI helper estimate:

- `srvdofunc=30` selects the target-centred curse search;
- `aurarangecalc` determines the selected-point radius;
- `auratargetstate` resolves to `StateId.DIMVISION`;
- the target state carries the caster entity, skill id, duration and
  `needsSync=true` for the client snapshot;
- `StateOverlaySystem` starts the native `cursecast` one-shot and defers the
  persistent `cursedimvision` layer until the one-shot is no longer active.

The new Necromancer-specific regression casts skill 71 at a selected monster,
checks a second hostile monster inside the same target-centred radius, and
checks that an outside monster is untouched. It also verifies the authoritative
target state is marked for synchronization. The existing AI and overlay tests
remain part of the same validation set.

Validation log:

```text
tools/original-observer/necromancer-dim-vision-validation-2026-11-25.log
```

Passed tests:

```text
NecromancerCurseIntegrationTest: 19
NecromancerCurseAiIntegrationTest: 7
NativeNecromancerCurseDataTest: 4
StateOverlaySystemTest: 13
AssassinNecromancerPassiveIntegrationTest: 8
```

No production change was needed for Dim Vision in this pass. The original
client has not been run for this skill, so `runtime_status` remains
`not_run_in_class_batch`; this record establishes the Riiablo state, AI and
overlay behavior without claiming original pixel or timing parity.
