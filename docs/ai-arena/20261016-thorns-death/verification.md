# Thorns fractional death threshold

Status: PARTIAL. This change covers the StateUpdater Thorns damage death threshold.

D2MOO `source/D2Game/src/SKILLS/SkillPal.cpp`, SKILLS_ApplyThornsDamage, sends reflected damage through SUNITDMG_ExecuteMissileDamage. The shared `UNIT/SUnitDmg.cpp:1300–1308` path subtracts positive damage and sets remaining life below 256 internal units (1 HP) to zero. Exactly 1 HP survives; zero damage preserves positive fractional life.

StateUpdater now applies this threshold after event-adjusted positive Thorns damage. DamageEvent describes the reflected packet; DeathEvent identifies the defender as killer and the original attacker as victim.

Five new integration tests select the actual level-one Thorns aura and dispatch a 10-physical-damage melee event. The resulting 25 damage packet leaves 255/256, exactly 1, or 257/256 HP. Separate cases cancel the reactive event or naturally reduce reflected damage to zero through monster physical immunity. Tests check life, reactive packet count and death attribution. These validate the reaction to an explicit melee event, not the melee hit roll. Baseline: 10 tests, one expected failure.

`prepare-source.ps1` archives source-base-sha.txt and overlays test sources; `-Repaired` also overlays StateUpdater. `run-tests.ps1 -Pass final` and `-Pass repeat` retain XML/logs. `verify.ps1` requires 346 passing tests per run, matching 64 Nova samples and matching production/test source hashes against the tested snapshot. The reused snapshot script also overlays and hashes the unchanged explosion data test.

Other reflective damage paths, native precision/mitigation parity, full death callbacks and complete combat parity remain outside acceptance.

The initial fixture attempted monster flat physical reduction, which this existing resolver does not apply. fixture-initial.xml/log retain that extra fixture failure. The final zero-damage control uses physical immunity; changing the reduction resolver is outside this patch.
