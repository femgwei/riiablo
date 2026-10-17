# Baboon native chase requests — 2026-10-18

Evidence: local D2MOO AiThink.cpp AITHINK_Fn011_Baboon uses sub_6FCD0410(...,7) for ordinary non-melee approach and healthy noncombat regeneration recovery. AiTactics.cpp sub_6FCD0410 sets path type 13 before WalkToTargetUnitWithFlags. Healthy recovery first supplies the run/velocity-derived speed bonus.

Replaced both generic path requests with the existing findNativeChase interface. Recovery reapplies its calculated speed bonus after a successful request, since the path installer clears temporary bonuses. Target-discovery regression expectations now require native chase for Baboon, including a replacement target in a later world.

Added BaboonChaseTest for ordinary and healthy recovery requests across all three difficulties. Both methods failed before the change. Assertions cover native chase, absence of generic pursuit calls, diagnostic state, base delay, no casting, recovered timer and speed bonus.

Limits: AI request layer only. The existing type-13 path projection and target retention are not proof of full native dynamic-path equivalence. Flags-7 failure behavior remains missing in these two Baboon branches; the old APPROACH diagnostic on failed requests remains unchanged and is not evidence of accepted movement. Native event/control flags, action locking and actual animation scheduling require separate work. No shared executor files changed and no real-game reproduction was performed. The reported immobility remains unconfirmed as fixed.

After correction, the focused Scarab/Baboon regression run passed 43 tests. No full suite claim.
