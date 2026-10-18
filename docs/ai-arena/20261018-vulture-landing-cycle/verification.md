# Vulture landing and combat cycle repair — 2026-10-18

Follow-up to user observation: takes off, remains airborne, does not attack.
The prior 19af2b444 validation established flight movement in an open arena;
it did not establish the complete takeoff/landing/attack cycle or obstacle recovery.
That earlier result is insufficient to declare the gameplay issue resolved.

## Failure reproduced and repair

A regression starting airborne on blocked ground, with free neighboring cells,
failed before the repair: the bird remained airborne instead of starting S2.
The old landing helper checked only the exact current footprint. Native
AiThink.cpp sub_6FCD55D0 calls sub_6FCBDFE0, which invokes
COLLISION_GetFreeCoordinates, searching nearby coordinates with a 50-step limit.
The repair searches nearby terrain and unit-free positions in the current RoomEx,
stops the air path, relocates/reserves the ground footprint, then starts S2.

Also restored Fn023's different-room/distance>12 branch, corrected coordinate
metric and axis stepping to AIUTIL_GetDistanceToCoordinates, and added air
waypoint free-coordinate correction plus fallback to landing when the waypoint
cannot be resolved. This avoids repeatedly requesting unusable destinations.
Landing failure reason, countdown, and waypoint now appear in AI diagnostics.
Parameter constant names were restored for the existing parity test API.

## Validation

70 focused tests passed (10 Vulture plus 60 Scarab/Baboon). New tests cover
blocked-cell/free-neighbor landing, a real map collision cell and native RoomEx
boundary, and distant different-room ground approach. Test XML is archived.
The baseline failure log is included.

Final production and arena observer: seeds 1 and 2, Normal, vulture1–4,
1600-tick maximum. 24 executed scenes total; eight dedicated flight-cycle scenes
all passed with zero warnings/errors across all scenes. The dedicated observer
requires natural takeoff, an actually started S2 COF, then subject-origin damage
and a decrease in the player's HP after landing. Player movement follows the bird
through ordinary Pathfinder requests until landing, then stops. No forced monster
state, target override, HP weakening, skill injection or probability changes.
Example seed1/vulture1: takeoff tick1, S2 tick194, 10HP damage to player tick222.

## Limits

The dedicated scene keeps the player in range via movement; it does not promise
that a stationary distant player will always be attacked. Seed1 far baselines
still show MOVEMENT_ONLY after losing acquisition range. Native room and obstacle
behavior has focused test coverage, but no manual rendered-game confirmation or
reproduction of the user's exact map is available. This evidence supports the
repaired failure paths and complete stimulated cycle, not full native equivalence
or a claim that the user's on-screen symptom is definitively resolved.
