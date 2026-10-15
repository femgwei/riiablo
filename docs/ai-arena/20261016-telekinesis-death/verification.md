# Telekinesis fractional death boundary

Status: PARTIAL. Only the Telekinesis direct damage death threshold is covered.

D2MOO `source/D2Game/src/UNIT/SUnitDmg.cpp:1292–1308` applies absorb healing before damage and, for positive damage, sets remaining life below 256 internal units (1 HP) to zero. Exactly 1 HP survives.

The Java resolver now checks that threshold after event-adjusted positive damage. Cancelled damage preserves fractional life. DamageEvent continues to describe the damage packet; DeathEvent reports the death, including the fractional remainder removed by the threshold.

Five new real SkillDoEvent integration cases cover 255/256 HP remaining, exactly 1 HP, 257/256 HP, partial absorb before the threshold, and controlled event cancellation. Fatal cases also check a second cast cannot duplicate damage or death events. The isolated baseline has 11 tests and two expected failures.

Run `prepare-source.ps1` for the failing baseline, or with `-Repaired` for the repaired source. `run-tests.ps1 -Pass final` and `-Pass repeat` retain independent XML and logs. `verify.ps1` checks both 289-test runs, the 64 paired Nova samples, and production/test source SHA256 equality with the tested snapshot.

Full native Telekinesis RNG, mitigation order/precision, death animation/callback parity, and other direct damage resolvers remain outside this acceptance.
