# Baboon decision delays — 2026-10-18

Compared against local D2MOO AiThink.cpp AITHINK_Fn011_Baboon (0x6FCD2A00). Its ordinary attack/approach, hit-memory first attack, regeneration start, regeneration melee attack, healthy noncombat recovery and regeneration escape/failure-attack branches return after their action without a separate random 1–2 second wait.

Removed seven Java assignments imposing that extra delay. The base AI sleep remains. Explicit ordinary neutral wait (15 frames), distant regeneration neutral wait (20 frames), and unrelated no-target wandering remain as before. RNG decision calls and action commands were not changed.

BaboonDecisionTimingTest adds eight methods, covering the branches above across all three difficulties. Accepted escape and failed melee escape attack exercise both outcomes of the shared final delay. Assertions verify state, base delay, attack target/sequence or absence of casting, and appropriate regeneration timer outcomes. All eight methods failed against the old extra waits.

Limits: base float countdown is not native frame-event/action completion scheduling. Reducing the artificial delay makes action-lock/animation completion integration more important; those remain execution-layer validation work. No shared movement, animation or damage code changed. No new actual-game run was performed, so the user's immobility report is not proven resolved.

After correction, all eight new methods passed; the focused Scarab/Baboon regression run passed 41 tests. No full suite claim.
