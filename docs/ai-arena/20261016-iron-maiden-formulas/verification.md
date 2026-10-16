# Iron Maiden formula selection

Status: PARTIAL. This change covers native calc1/calc2/calc3 selection.

D2MOO `source/D2Game/src/SKILLS/SkillNec.cpp`, D2GAME_EventFunc04_6FD0E840, chooses calc1 for player attackers. For monster/hireling attackers it chooses calc2 if the original victim is a monster, otherwise calc3. It evaluates the selected formula using the curse's skill level. The Java helper previously used calc1 and a universal one-eighth reduction for player/hireling attackers; that rule belongs to Thorns, not this native Iron Maiden callback.

StateUpdater now passes both combatant types to NecromancerSkills, which evaluates the selected skill-row formula. Mercenaries are represented as native monster/hireling units for branch selection. Existing reflected physical mitigation and PvP scaling remain in place.

One integration test exercises all six player/monster/mercenary attacker versus player/monster victim combinations, logs the selected real formula/percentage, and checks reflected packet and life. An independent test uses distinct literal formula values to make all branches observable even when game data formulas coincide. Existing data contracts and the player reflection integration expectation are updated. The baseline overlays the integration test only, retains the old helper/data test, and has 15 tests with two expected formula-selection failures.

The real 1.10f row evaluates calc1 (`ln56`) to 200 and calc2/calc3 (`ln56/4`) to 50 at level one. Initial repaired preflight XML/logs retain six failures from older fixtures expecting 200% for monster reflection. The six-combatant matrix passed in that run and printed all formulas. Final fixtures use a 10 HP reflection for a 20 HP monster melee packet, while player reflection is 40 HP before existing PvP scaling. The previous exact-life and cast-direction assertions are updated accordingly. Baseline XML predates these final fixture corrections; preflight provides the transition evidence.

`prepare-source.ps1` archives source-base-sha.txt; `-Repaired` also overlays StateUpdater, NecromancerSkills and the revised data test. Two runs retain XML/logs. `verify.ps1` checks 356 passing tests per run, matching 64 Nova samples and all modified source hashes against the tested snapshot. The unchanged explosion data test is also overlaid/hashed by the reused script.

Precision, native mitigation order, caster-dependent formula context, available-life limiting and complete death callbacks remain outside acceptance. These explicit melee-event tests validate the reactive callback, not melee hit rolls or range.
