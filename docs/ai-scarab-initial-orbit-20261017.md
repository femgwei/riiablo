# Scarab initial pursuit orbit — 2026-10-17

Evidence in local D2MOO:
- AiThink.cpp AITHINK_Fn020_Scarab, non-melee phase zero: sub_6FCD0E80(...,0,0), set phase to one, return regardless of request result.
- AiTactics.cpp sub_6FCD0E80: one native random draw, low byte <128 selects path type 5, otherwise 6; requests WALK with no failure fallback flags.
- MonsterMode.cpp D2GAME_MONSTERMODE_SetVelocityParams_6FC62FC0: zero distance does not override the stored parameter. MONSTERMODE_GetModeChangeInfo clears the mode-change structure. D2GAME_ModeChange_6FC65220 resolves an unset distance to five before sub_6FC65680 sets PATH_SetNewDistance. Thus zero is not a zero-step orbit.

Replaced the Java direct pursuit substitution with findNativeOrbit using the resolved five-step budget and native direction draw. Phase advances even on rejection, with no retry. Diagnostic state reflects acceptance. Removed the branch's extra random 1–2 second delay; the existing base AI delay remains, without claiming full native event scheduling.

Validation: two new test methods failed before the production change. Accepted/rejected cases now cover all three difficulties and four seeds each, exact RNG continuation, budget/direction, phase advancement, absence of extra path calls/casting and base delay. Updated the existing target-discovery test to expect Scarab orbit instead of the old direct pursuit. The focused regression run passed 26 tests. No full suite or real-game execution validation was performed.

Limits: this verifies AI requests using a mock Pathfinder. Existing shared orbit projection, collision, mode execution and scheduling are not established as fully equivalent. The original immobility report remains unconfirmed as resolved. Phase-one path flags/four-step parameters and minion commands remain follow-ups. No shared execution files changed.
