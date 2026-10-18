# Cursed monster melee-hit callback

Source: local D2MOO `D2Game/src/UNIT/SUnitDmg.cpp:1287` calls
`sub_6FC6E890` for a monster attacker carrying SUCCESSFULHIT. This is distinct
from positive final damage. `MonsterUnique.cpp:981` rejects non-unique units
and random low bits equal to zero: 3 of 4 outcomes cast, not 1 of 4.

The callback uses Amplify Damage, level `max(monsterLevel / 5 + 1, 1)`,
range clamped to 1..40, centered on the attacking monster. Curse values and
duration come from the skill row. The unique-mod callback does not use the
player curse difficulty duration reduction.

Implementation subscribes to the existing authoritative MeleeAttackEvent.
Misses and blocked hits are excluded; successful zero-damage melee hits
are included. The existing curse layer implementation provides replacement
and state/stat synchronization. Tests cover the 75% low-bit predicate and
the hit/miss/block event gate.

Limits: ranged successful-hit routing, pet/hireling eligibility and exact
native RNG parity remain unaudited. The shared NativeRng is not D2's seed
algorithm. No claim of complete unique-mod equivalence or in-game acceptance.
Fire Enchanted's delayed death explosion remains unimplemented in this batch.

Corrections to earlier chat statements: Scarab CHARGEDBOLTS is MonUMod **20**,
not 16 (16 is champion modifiers). Existing runtime mapping still needs repair.
ColdEnchantedModeChange (`MonsterUnique.cpp:1666`) checks DEATH and schedules
four frames later; it is not an ordinary nonlethal struck effect. Existing Scarab/LE
two-frame cooldowns do not implement native deferred callbacks or LE's separate
10-frame constraint. Earlier bolt snapshot checks cover integer values only,
not fractional fixed-point damage or full collision-to-life-loss behavior.
