# Scarab leader command generation — 2026-10-18

D2MOO AiThink.cpp Fn020 generates command 1 only when no current command exists, target distance is below 20, GetMinionOwner(unit)==unit and Param5's percentage roll succeeds. AiGeneral.cpp AllocCommandsForMinions copies to the owner's explicit minion list; Fn020 then copies to the leader and immediately executes its command branch.

Scarab now projects leader identity from Monster.minionOwnerId==self or the explicit nativePackLeader spawn marker when no parent owner is set. The second case is needed because RoomActivationSystem.resolvePackOwners currently assigns owners only to nonleader pack members. Distribution scans Monster components for explicit minionOwnerId==leader, excludes self, copies command 1 without discarding queued commands, then copies once to self. It does not elect leaders or infer membership by proximity, class or zone.

ScarabLeaderCommandTest covers self-owner and spawn-marker leaders; owned, unowned and other-owner recipients; preserving a recipient's pending command; executing the leader's own Jab in the same decision; distance exactly 20; a nonleader; and an existing nonattack command blocking generation. RNG assertions distinguish the single command-chance draw from subsequent ordinary draws. Before the production fix the positive leader test failed (expected JAB, actual IDLE), while three gating controls passed.

Limits: this uses the existing active ECS owner projection, not D2MOO's persisted linked minion list. It cannot distribute to inactive/nonresident monsters and does not add generation-safe owner identities to that projection. Deletion/reuse safety and full spawn-owner lifecycle need dedicated integration work. No shared spawn/movement/animation/damage files were changed. Scarab Jab animation remains a known execution gap; no post-change arena/client run was performed. The original real-map immobility remains unconfirmed as resolved. This supersedes previous notes that all leader command generation/distribution is absent, without claiming complete AiGeneral parity.

After correction, the focused Scarab/Baboon regression run passed 57 tests. No full suite claim.
