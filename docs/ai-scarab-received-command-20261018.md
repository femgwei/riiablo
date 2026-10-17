# Scarab received command decisions — 2026-10-18

Evidence: local D2MOO AiThink.cpp AITHINK_Fn020_Scarab, command nCmdParam[0]==1. In melee with Skill1 it frees the current command then uses Skill1 without attack/Jab chance rolls. Otherwise it sets path type 2, speed bonus 100 and requests target movement with flags 0, retaining the command on success and freeing only the current command on failure. The unset distance resolves to five in MonsterMode.cpp.

Added a NativeAiCommand mapper to Scarab and handles its ATTACK=1 marker before ordinary pursuit/attack decisions once a current AI target is selected. Melee pops only the current command and issues configured Skill1. Non-melee requests findNativePath(actor,target,5,0), applies +100 percent speed on acceptance, retains the command, and pops on rejection. A pending command is preserved via pop(); the component is removed only when no command remains. No failure fallback, ordinary phase mutation, probability draw or extra wait is added.

Four ScarabCommandTest methods failed before the production change: melee bypass with zero ordinary attack/Jab chances and pending queue restoration; accepted pursuit retention/bonus; failed pursuit restoring a pending command; failed sole command removing the component. Assertions include requests, state, speed, base delay, skill/target where applicable and untouched RNG continuation. Melee covers all three difficulties.

Limits: receives an existing marker only. Native minion-owner detection, leader command generation and command distribution remain unimplemented. Fn020's marker acts on the newly selected AI target, not a saved command target; this code does not dereference stored command owner/target IDs. No-target handling is still the old Java idle path. Complete native path fallback/mode execution, action locking, AI-event/control flags and Scarab Jab animation remain separate gaps. No shared executor files were changed, and no post-change arena/client run was performed. This does not establish resolution of the real-map immobility report.

After correction, the focused Scarab/Baboon regression run passed 53 tests. No full suite claim.
