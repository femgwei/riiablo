# DiabRun fractional death threshold

Status: PARTIAL. This change covers the direct DiabRun death threshold.

D2MOO `source/D2Game/src/UNIT/SUnitDmg.cpp:1300–1308` subtracts positive damage and sets remaining life below 256 internal units (1 HP) to zero. Exactly 1 HP survives, and zero damage preserves positive fractional life.

The resolver now applies this threshold after event-adjusted positive damage. DamageEvent describes the damage packet; DeathEvent reports death including the fractional remainder removed by the threshold.

Five new tests execute the real DiabRun animation sequence through Actioneer. They cover 255/256 HP remaining, exactly 1 HP, 257/256 HP, event cancellation and naturally zero physical damage from flat reduction. A controlled damage-event fixture sets defender life relative to the actual random packet for exact boundaries, without replacing the random roll or sequence. These cases verify application semantics rather than natural RNG distribution. Each finishes the sequence and checks that further ticks produce no duplicate hit/death. Baseline: 14 tests, one expected failure.

`prepare-source.ps1` archives source-base-sha.txt and overlays test sources; `-Repaired` also overlays the resolver. `run-tests.ps1 -Pass final` and `-Pass repeat` retain XML/logs. `verify.ps1` checks 341 passing tests per run, matching 64 Nova samples and production/test source hashes against the tested snapshot. The unchanged explosion data test is also hash-checked because the reused snapshot script overlays it.

Native RNG, full death-mode/callback parity, hit-reaction semantics for cancelled damage and other direct damage paths remain outside acceptance. Existing DiabRun movement, sequence, resistance, invalid-target and blocked-path coverage is included.
