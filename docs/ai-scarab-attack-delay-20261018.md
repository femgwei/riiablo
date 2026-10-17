# Scarab ordinary attack delay — 2026-10-18

D2MOO AiThink.cpp AITHINK_Fn020_Scarab returns immediately after AITACTICS_ChangeModeAndTargetUnitToAttack1Or2 in the ordinary attack branch. Removed Java's additional random 1–2 second countdown after issuing that attack. The base AI sleep remains; this does not implement native animation-driven wake scheduling.

Extended ScarabAttackChoiceTest to require the base delay in its endpoint cases across all difficulties and intermediate chance cases across four seeds. Both methods failed against the old random wait and passed after correction. Their existing assertions continue to verify attack mode, neutral return mode, target, basic skill and RNG continuation. Focused Scarab/Baboon regression: 31 tests passed. No full suite claim.

RNG investigation: AiThink.cpp AI_RollPercentage delegates to D2Common D2Seed.h SEED_RollPercentage, which uses the full uint64 seed result modulo 100. Java's Seed.rollPercentage already matches that width. ITEMS_RollRandomNumber-dependent branches separately use the truncated uint32 result. No RNG implementation change was needed.

Scope and risk: AI countdown only. This reduces the time before another Java decision; interaction with animation completion/action locking still requires execution-level validation. No shared movement/animation/damage files changed, and no new real-game reproduction was run. The reported immobility remains unconfirmed as fixed. Native event deletion/control flags, minion command ownership and exact path execution remain follow-ups.
