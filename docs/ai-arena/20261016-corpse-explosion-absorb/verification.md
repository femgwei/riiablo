# Corpse Explosion delayed-death absorb

Status: PARTIAL. This change covers absorb healing during DEATH_DELAY in the direct Corpse Explosion resolver.

D2MOO `source/D2Game/src/UNIT/SUnitDmg.cpp:1292` permits absorb healing only for a living defender without STATE_DEATH_DELAY. The resolver already rejects corpses and nonpositive life; it now also guards absorb healing with this state check.

Three new real SkillDoEvent integration tests use a target in the outer fire-only ring. Delayed full absorb preserves 500 HP, delayed partial absorb subtracts the recorded damage without healing, and ordinary full absorb still heals. Tests check zero physical damage, no death event and consumption of the source corpse. The isolated baseline has seven tests with two expected failures.

The expanded suite also found an existing data expectation mismatch: `preflight/` and `preflight.log` retain the failing NativeNecromancerExplosionDataTest (70 expected, 60 actual). The 1.10f skill row uses 60/100, not the later 70/120 values. Data assertions and integration bounds now reflect 60..99 rolled percent and 30..49 outer-ring fire damage for the fixture's 100 HP corpse. D2MOO `SkillNec.cpp` SrvDo055 evaluates calc1/calc2 and rolls an exclusive upper bound. No production damage formula was changed for this test correction. The initial baseline XML predates these corrected range assertions.

`prepare-source.ps1` archives source-base-sha.txt and overlays the test; `-Repaired` also overlays the resolver. `run-tests.ps1 -Pass final` and `-Pass repeat` retain logs and XML. `verify.ps1` requires 319 passing tests per run, matching 64 Nova samples and matching production/test source hashes against the tested snapshot.

Corpse Explosion fractional death thresholds, native damage roll/mitigation order and complete death callbacks remain outside acceptance. Existing explosion reservation, radius, poison-cloud and town rejection coverage is included.
