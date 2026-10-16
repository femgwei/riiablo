# Iron Maiden attacker-state lookup

Status: PARTIAL. This change covers curse-state direction and missing victim state components.

D2MOO `source/D2Game/src/SKILLS/SkillNec.cpp`, D2GAME_EventFunc04_6FD0E840, checks and reads the Iron Maiden stat list from pAttacker, then sends reflected damage from pUnit back to pAttacker. StateUpdater previously looked for the curse on the victim and returned before any reactive processing if that victim had no UnitStates component.

StateUpdater now reads Iron Maiden from the original attacker. Defensive states and Life Tap continue to read the victim. Missing victim states no longer prevent attacker-curse reactions, and a snapshot-only attacker cannot repeat reflection.

Three new tests cast Iron Maiden through the real SkillDoEvent path, then dispatch an explicit physical melee event. They verify cursed attacker reflection with an unconfigured victim, victim-only curse rejection, attacker snapshot rejection and missile rejection. The prior five death-boundary cases now put the curse on the attacker, and the combined Life Tap/Iron Maiden case is corrected similarly. These tests establish cast/state/event reaction, not the melee hit roll or range selection.

The isolated baseline has 14 tests and seven expected failures: three life boundaries, cancelled reactive packet count, attacker-cast reflection, victim-only rejection and the corrected combined curse case.

`prepare-source.ps1` archives source-base-sha.txt and overlays tests; `-Repaired` also overlays StateUpdater. Two complete runs are retained as final/repeat XML and logs. `verify.ps1` checks 354 passing tests per run, matching 64 Nova samples, and source hashes against the tested snapshot. The reused script also overlays/hashes the unchanged explosion data test.

Native percentage branch selection, reflection precision/mitigation order, available-life limiting, complete death callbacks and full combat parity remain outside acceptance. Prior death-threshold evidence used the former lookup and is superseded by these corrected fixtures.
