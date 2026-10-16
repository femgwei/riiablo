# Corpse Explosion fractional death threshold

Status: PARTIAL. This change covers the direct Corpse Explosion death threshold.

D2MOO `source/D2Game/src/UNIT/SUnitDmg.cpp:1300–1308` subtracts positive damage and sets remaining life below 256 internal units (1 HP) to zero. Exactly 1 HP survives, and zero damage does not snap positive fractional life to zero.

The Java resolver now checks this threshold after event-adjusted positive damage. DamageEvent remains the damage packet; DeathEvent reports death including the fractional remainder removed by the threshold.

Six new real SkillDoEvent tests cover fractional death in the inner mixed-damage ring and outer fire-only ring, exactly 1 HP, 257/256 HP, event cancellation, and full absorb on a delayed target with 0.5 HP. Exact positive-damage boundaries use a controlled damage-event fixture to set defender life relative to the real random packet; this validates application semantics rather than natural RNG distribution. All cases check that recasting on the consumed corpse cannot repeat damage or death events. The baseline has 13 tests with two expected failures.

`prepare-source.ps1` archives source-base-sha.txt and overlays test sources; `-Repaired` also overlays the resolver. `run-tests.ps1 -Pass final` and `-Pass repeat` retain logs and XML. `verify.ps1` checks 325 passing tests per run, matching 64 Nova samples and matching production/test source hashes against the tested snapshot.

Native random roll/mitigation order, full death-mode/callback parity and other direct damage paths remain outside acceptance. Existing explosion radius, absorb, reservation, poison-cloud, data and town rejection tests remain included.
