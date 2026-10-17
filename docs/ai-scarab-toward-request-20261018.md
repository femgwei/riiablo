# Scarab phase-one Toward request — 2026-10-18

Evidence: D2MOO AiThink.cpp AITHINK_Fn020_Scarab sets velocity parameters (2,0,4) before WalkToTargetUnitWithFlags(...,7). D2Common Path.h identifies path type 2 as PATHTYPE_TOWARD. MonsterMode.cpp passes the nonzero distance override through sub_6FC65680 to PATH_SetNewDistance.

Replaced the AI's generic walkTo request with existing Pathfinder.findNativePath(actor,target,4,0), selecting the Toward projection with a four-step parameter and zero direction offset. The successful request no longer invokes ordinary A* pursuit. Existing failure fallback and subsequent phase-clear RNG ordering remain in place. No shared path executor files changed.

Validation: updated two phase tests and three fallback tests to require the native request and reject the former generic pursuit request. All five failed before the production fix and passed after it. The focused Scarab/Baboon regression run passed 31 tests, covering successful phase transitions, accepted/rejected fallback movement and ten-frame idle. No full suite claim.

Boundaries: this verifies the request to a mocked Pathfinder. The existing NativeTowardPathProbe is a projection, not a complete dynamic-path wrapper; its unobstructed ray branch is not proof of a four-tile movement cap. Native path failure wall-follow behavior, mode/event scheduling, flags-4 event deletion and flags-1 control bit 0x40 are not established by this change. This supersedes earlier notes that the AI request lacks the path type/four-step argument, without claiming the execution layer is equivalent. No new real-game run was performed; the original immobility report remains unresolved by gameplay evidence.
