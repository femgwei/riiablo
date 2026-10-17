# Baboon escape coordinates — 2026-10-18

Evidence: local D2MOO AiTactics.cpp D2GAME_AICORE_Escape_6FCD0560 (0x6FCD0560) reads unit/target integer coordinates, computes independent D2signum differences per axis and requests (x + signX * distance, y + signY * distance). Fn011 uses distance 15 for both regeneration escape and hit-triggered regeneration start.

Java used a floating-point normalized direction vector, making diagonal offsets approximately 10.6 per axis and retaining fractional coordinates. Replaced both call sites with a local escapeFrom helper that uses integer coordinates and independent axis signs, preserving the existing velocity bonus, INVALID_ENTITY coordinate target and walkTo result handling.

BaboonEscapeCoordinatesTest covers both entry points across all difficulties and four position pairs: diagonal negative direction, one equal integer axis despite differing fractional coordinates, positive direction and equal integer cells. It verifies the exact requested point, untouched source/target positions, ESCAPE state, no cast and unchanged base delay. Both new methods failed before the production fix.

Limits: decision coordinates only. Native distance override/path type, AI-event deletion on failed regeneration escape, collision, exact movement distance and frame scheduling are not established by this coordinate fix. Same integer cells deliberately request the integer source rather than inventing a random direction. Existing failed-escape handling remains. Shared path/animation/damage executors were not changed. No new real-game or post-fix arena run was performed; the earlier arena batch did not exercise a deliberately injured regenerating monster, so it is not evidence for this change. The original real-map/client immobility remains unconfirmed as resolved.

After correction, both new methods passed; the focused Scarab/Baboon regression run passed 48 tests. No full suite claim.
