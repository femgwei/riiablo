# Baboon failed chase fallback — 2026-10-18

Evidence: Fn011's ordinary noncombat approach and healthy recovery call sub_6FCD0410(...,7). AiTactics.cpp maps this to path type 13 then MoveToTarget flags 7. Failed movement draws a uint32 value modulo 100: >=70 idles in neutral for ten frames; otherwise WalkCloseToUnit(self,4) makes one coordinate request after four native draws.

Added chaseWithFallback for both Baboon call sites. Successful chase keeps the requested speed bonus. Failed chase now chooses the ten-frame stop/wait or the existing self-relative local walk with radius four, INVALID_ENTITY target and no extra retry. Local fallback uses zero additional speed bonus because MonsterMode consumes/resets the initial velocity parameters before the failure returns. Diagnostics now reflect idle or accepted local movement instead of always reporting APPROACH.

BaboonChaseFallbackTest covers idle, accepted local movement and rejected local movement across all three difficulties and both ordinary/recovery entry points. Assertions include native chase request, no generic target pursuit, exact local coordinates, timer, state, zero fallback speed bonus, no casting and exact RNG continuation. All three methods failed before production changes.

Limits: this covers flags-2 fallback decisions, not flags-4 AI-event deletion or flags-1 control bit 0x40. Shared path projection, native neutral animation and exact event scheduling remain separate execution concerns. No shared executor files changed and no gameplay reproduction was run. The original immobility report remains unconfirmed as resolved. This supersedes the earlier note that Baboon has no chase fallback; native control/event flag gaps remain.

After correction, the focused Scarab/Baboon regression run passed 46 tests. No full suite claim.
