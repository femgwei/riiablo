# Scarab declined-attack neutral wait — 2026-10-17

D2MOO AiThink.cpp AITHINK_Fn020_Scarab ends a failed attack roll with AITACTICS_IdleInNeutralMode using SCARAB_AI_PARAM_STALL_DURATION. AiTactics.cpp implements a neutral mode change before scheduling the wait. Java previously set diagnostic IDLE and a countdown without requesting movement cancellation.

Added stopMovement() before the failed-attack wait. The existing branch test now begins with Running and a 100-percent mode speed bonus, verifies the stop request, cleared Running and speed bonus, and retains its assertions for no Jab/cast and the configured 13-frame wait across all three difficulties. It failed before the production change (missing stop request). Afterward the focused Scarab/Baboon run passed 24 tests. No full suite or actual-game reproduction is claimed.

This is an AI movement-stop request, not proof of complete native neutral animation or frame-event equivalence. Shared execution code was not modified. It does not establish resolution of the original immobility report.

Follow-up investigation: non-melee phase zero still substitutes direct pursuit for sub_6FCD0E80(...,0,0). MonsterMode.cpp D2GAME_MONSTERMODE_SetVelocityParams_6FC62FC0 only overrides its step field for a nonzero argument: passing zero directly as findNativeOrbit's budget would generate no orbit. Resolve the mode-change default distance before changing that branch. Phase-one movement flags and its four-step velocity parameter also remain to be aligned.
