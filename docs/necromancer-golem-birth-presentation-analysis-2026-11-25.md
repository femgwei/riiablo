# Necromancer Golem birth presentation analysis (2026-11-25)

The four Necromancer Golem rows (Clay 75, Blood 85, Iron 90 and Fire 94)
all declare `Summode=S1`. Their creation path previously created the pet and
immediately installed passive/granted skills, but did not hold the new unit in
an `S1 -> NU` sequence. That allowed `NecroPet` to receive ordinary AI control
before the emergence animation had completed, the same early-walk condition
found for Skeleton and Skeletal Mage.

`ServerSkillSystem.spawnNecromancerGolem` now installs the sequence immediately
after successful pet creation and before skill inheritance, resistance setup or
Iron Golem source-item handling. This keeps all four Golem variants on the
same native birth boundary while preserving their distinct passive effects.

Validation:

```text
tools/original-observer/necromancer-golem-birth-sequence-validation-2026-11-25.log
```

Passed tests:

```text
NecromancerGolemReviveIntegrationTest: 3
NecromancerSummonIntegrationTest: 6
ServerEntityFactorySummonQuotaTest: 3
```

The targeted test asserts Clay Golem's `Sequence(S1 -> NU)` and continues to
verify Fire Golem Holy Fire, Iron Golem source-item ownership/stat aggregation,
and the shared Golem PetMax quota. No original-client Golem capture was run in
this pass; runtime pixel/timing parity remains pending.
