# Iron Maiden fractional death threshold

Status: PARTIAL. Only the existing Iron Maiden reflection resolver's death threshold is covered.

D2MOO `source/D2Game/src/SKILLS/SkillNec.cpp:1923` sends reflected damage through SUNITDMG_ExecuteMissileDamage. Its shared `UNIT/SUnitDmg.cpp:1300–1308` path subtracts positive damage and sets remaining life below 256 internal units (1 HP) to zero. Exactly 1 HP survives; zero damage preserves positive fractional life.

The Java resolver already returns for nonpositive applied damage. It now sets life below 1 HP to zero after subtraction. DamageEvent describes the reflected packet; DeathEvent reports the death with the existing attribution.

Five new tests reuse the established applyCurse/event harness. Level-one Iron Maiden reacts to a 20-physical-damage melee event with a 40 damage packet. Cases cover 255/256 HP remaining, exactly 1 HP, 257/256 HP, cancelled reflection and physical immunity. They check packet count, physical damage, life and death attribution. Baseline: 11 tests, one expected failure.

This fixture follows the current resolver's defender-state lookup. Native curse state ownership/lookup, reactive event direction, percentage selection and complete cast-to-reflection parity are explicitly NOT established here and require separate work. The tests validate only subtraction/death semantics in the existing resolver.

`prepare-source.ps1` archives source-base-sha.txt and overlays test sources; `-Repaired` also overlays StateUpdater. `run-tests.ps1 -Pass final` and `-Pass repeat` retain XML/logs. `verify.ps1` checks 351 passing tests per run, matching 64 Nova samples and source hashes against the tested snapshot. The reused snapshot script overlays and hashes the unchanged explosion data test as well.

Other reflective damage paths, native precision/mitigation, full death callbacks and full combat parity remain outside acceptance.
