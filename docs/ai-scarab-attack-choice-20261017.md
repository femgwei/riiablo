# Scarab attack choice — 2026-10-17

Evidence: local D2MOO source/D2Game/src/AI/AiThink.cpp, AITHINK_Fn020_Scarab calls AITACTICS_ChangeModeAndTargetUnitToAttack1Or2 after the attack roll succeeds and Jab is not chosen. The helper selects MONMODE_ATTACK1 when the configured chance succeeds and MONMODE_ATTACK2 otherwise.

The Java Scarab branch had these modes reversed. Corrected its A1/A2 mapping while retaining attack/Jab roll ordering.

Validation: both ScarabAttackChoiceTest methods failed on the old mapping and passed after the fix. Endpoint cases (0 and 100) cover all three difficulties; intermediate probability 37 covers seeds 0, 1, 2 and 123456. Assertions cover mode, neutral return mode, basic attack skill, target entity and position, and exact native RNG continuation. Focused regression run passed 24 tests across Scarab attack choice, branch decisions, pursuit and Baboon decisions. No full AI suite claim.

Scope: ordinary attack decision only. Native AiCommand/minion ownership and distribution remain incomplete. Scarab Jab animation/dispatch is a separate execution gap. This batch does not establish that the reported actual-game immobility is resolved and does not change shared movement, collision, animation or damage execution.
