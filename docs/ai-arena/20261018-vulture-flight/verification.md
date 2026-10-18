# Vulture flight verification — 2026-10-18

The previous Vulture AI had no takeoff branch, required Class on player targets,
and had an unconditional false landing placeholder. Its airborne branch only idled.

Implemented ground/takeoff/airborne/landing decisions, seeded chance draws, S1
movement mode mapping and animation speed, S2 landing, transient target flags,
no-presence airborne collision, and straight coordinate flight through Pathfinder
and VelocityAdder. Ground and dynamic unit occupancy are checked before landing.
Reference: D2MOO AITHINK_Fn023_Vulture, AiThink.cpp:1926–2163.

Validation: 67 selected tests passed, including 7 Vulture tests and 60 existing
Scarab/Baboon tests. Vulture coverage includes target acquisition without Class,
takeoff flags/modes, air countdown, actual Pathfinder air waypoint creation,
landing, obstructed landing retry, owner restriction and death mode restoration.

Final server arena: vulture1–4, difficulty 0, seed 1, 1000 ticks each, near/far:
8 executed scenes. All four far scenes show changing positions during FLYING,
then landing and IDLE after losing the stationary player's acquisition range.
Near scenes attack. Far scenes are MOVEMENT_ONLY, not a combat correctness pass.
See results.jsonl and logs.zip. Warnings/errors are recorded in results.

Limits: synthetic open terrain, stationary high-HP player, no renderer/network or
real-map room-seam validation. S1 mode mapping has test coverage; on-screen flight
visuals have not been manually verified. This is a flight integration repair,
not a claim of full native behavioral equivalence. Native room-specific branches,
coordinate collision fallback and exact path-type 7/1 distinctions need further
comparison. The arena special field NOT_IMPLEMENTED means there is no dedicated
Vulture correctness oracle; it does not mean no flight occurred.
