# Iron Maiden overkill reflection base

Status: PARTIAL. This change removes the victim-life cap from Iron Maiden's damage base.

D2MOO `source/D2Game/src/SKILLS/SkillNec.cpp`, D2GAME_EventFunc04_6FD0E840, calculates reflected physical damage from pDamage->dwPhysDamage and the selected skill formula. It does not limit that base to the original victim's remaining life. StateUpdater previously shared a life-limited base between Life Tap, Blood Golem life link and Iron Maiden.

The resolver now retains the physical packet for Iron Maiden and computes a separate life-limited value for the existing healing/link paths. The input damage event is unchanged.

Three explicit-melee-event integration cases apply Iron Maiden to a monster attacker and Life Tap to its victim. A 20 HP physical packet reflects 10 HP with the level-one monster formula even when the victim has 1 or 0.5 HP; Life Tap restores only 0.5 or 0.25 HP respectively. A 100 HP victim supplies the full-life control. Assertions cover reflected packet count, physical amount, resulting life, no death and preservation of the original packet. This validates callback behavior, not the caller's subsequent subtraction or melee hit roll. Baseline: 18 tests with two expected failures.

`prepare-source.ps1` archives source-base-sha.txt; `-Repaired` overlays production sources and related test sources. Two runs retain XML/logs. `verify.ps1` checks 359 passing tests per run, matching 64 Nova samples and source hashes against the tested snapshot. Unchanged helper/data files are included in the reused overlay/hash workflow.

Native precision/mitigation order, callback ordering, caster formula context, full death callbacks and complete combat parity remain outside acceptance. The physical base still honors the existing event total-damage bound; this patch changes only the remaining-life cap.

The modified StateUpdater and integration test must match the tested snapshot byte for byte. Two unchanged files (NecromancerSkills and NativeNecromancerCurseDataTest) differ between archive and workspace only in CRLF/LF endings; the verifier compares their normalized contents and logs both byte hashes. It does not relax checks for the modified files.
