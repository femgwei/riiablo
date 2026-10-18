# Fire Enchanted death callback

Native evidence: D2MOO `D2Game/src/MONSTER/MonsterUnique.cpp:1050-1086`.
Unique + DEATH schedules MONUMOD four frames later. CastCorpseExplode creates
`monstercorpseexplode` (level 1), obtains class/level/difficulty base max HP,
and computes:

- maximum = baseMaxHP * MonsterCEDamagePercent / 100
- minimum = maximum * 60 / 100
- roll = minimum + random(maximum - minimum), upper bound excluded
- each physical/fire channel = roll << 6 in 8.8 (roll / 4 life points)
- radius = difficulty + 4

Runtime uses ServerSkillSystem's simulation-frame clock and retained
NativePathIdentity.Token. Duplicate deaths are claimed on Monster, and stale
identities cancel the delayed event. No ordinary AI polling or skill cast is
used. Visual missiles are presentationOnly to avoid a second damage packet.
Targets are copied before damage dispatch so recursive deaths cannot invalidate
the scan or delayed queue iterator. Resistance/reduction/absorb use the existing
native direct combat helpers; each victim receives a single physical+fire
DamageEvent and normal lethal DeathEvent.

Limits: direct helpers currently project each channel to integer life points;
fractional 8.8 parity is not established. Native seed algorithm, exact 0x581
target flags/range geometry, classic game-type selection, deleted-source
attribution and full in-game behavior remain unaudited. Current base calculation
uses expansion game type, consistent with the existing monster explosion path.
This is an initial production callback, not complete native equivalence.

Regression scope: percent/base-HP arithmetic; four-frame delay; duplicate death
guard; actual explosion damage dispatch and life loss at 50% fire resistance.
Scheduling tests spy the resolver; the separate data-backed damage test calls
the production resolver and checks both channels and resulting HP.
Removed/recycled source identities are rejected before resolution.
The delay test exposed the existing processing gate, which only checked skill
timers; the gate now also wakes while a death callback is queued.
