# DiabCold fractional death threshold

Status: PARTIAL. This change covers the direct DiabCold damage death threshold.

D2MOO `source/D2Game/src/UNIT/SUnitDmg.cpp:1300–1308` subtracts positive damage and sets remaining life below 256 internal units (1 HP) to zero. Exactly 1 HP survives; zero damage does not snap positive fractional life to zero.

The Java resolver now applies this threshold after event-adjusted positive damage. DamageEvent remains the damage packet; DeathEvent reports death, including any fractional life removed by the threshold.

Five new tests use the real S2 animation entry. For three exact remaining-life boundaries and event cancellation, a controlled damage-event fixture sets defender life relative to the actual native random roll; it does not replace the roll or fabricate a SkillDoEvent. Tests cover 255/256, exactly 1, 257/256 HP, cancellation, and naturally zero damage from MDR. The animation is finished to check no duplicate hit/death within the cast. This fixture validates application semantics, not natural RNG distribution. Baseline: 19 tests, one expected failure.

`prepare-source.ps1` archives source-base-sha.txt and overlays the test; `-Repaired` also overlays the resolver. `run-tests.ps1 -Pass final` and `-Pass repeat` retain logs and XML. `verify.ps1` checks 310 passing tests per run, matching 64 Nova samples, and production/test source hashes against the tested snapshot.

Complete native RNG, death animation/callback parity, and other direct damage resolvers remain outside acceptance. Existing DiabCold absorb, cold-length, lethal damage and invalid-target cases are included.
