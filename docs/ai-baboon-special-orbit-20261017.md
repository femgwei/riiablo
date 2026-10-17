# Baboon special orbit decisions — 2026-10-17

Compared against local D2MOO AiThink.cpp, AITHINK_Fn011_Baboon (0x6FCD2A00), and AiTactics.cpp sub_6FCD0E80.

- Distant regeneration (distance >=24, outside state 3/19) consumes a native percentage draw, requests orbit with budget 4 below 33, then always stops movement and records a 20-frame neutral wait.
- Hit-memory melee, after a previous attack, consumes a native percentage draw and requests orbit with budget 3 below 20. It clears attack memory and returns directly without the ordinary 15-frame tail or an extra random 1–2 second delay.
- Orbit direction consumes the next native random value, testing its low byte against 128. Failed orbit requests are not retried. APPROACH/IDLE reflects request acceptance for diagnostics.

Validation: both new test methods failed before the production fix and passed afterward. The focused run passed 19 tests (special orbit 2, ordinary melee 4, escape fallback 2, regeneration recovery 3, regeneration decisions 5, pursuit 3). New cases cover selected/declined distant orbit, accepted/rejected requests, hit-memory modes 3 and 19, and exact RNG continuation.

Scope: decision commands and wait values only. Existing float scheduling and shared movement/neutral animation execution are not claimed equivalent to native frame scheduling. No new gameplay reproduction was performed; the original actual-game immobility report remains unproven as resolved. Scarab Jab execution remains a separate known gap.
