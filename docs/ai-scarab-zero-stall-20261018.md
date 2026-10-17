# Scarab zero stall duration — 2026-10-18

D2MOO AiThink.cpp AITHINK_Fn020_Scarab passes its stall parameter to IdleInNeutralMode after a declined attack. AiTactics.cpp AITACTICS_Idle substitutes one frame when nFrames is zero before scheduling the AI event. Java previously retained a zero countdown.

Changed the Scarab declined-attack wait to convert exactly zero to one frame. Other values retain their existing conversion; no broader clamp was introduced.

Added a zero-duration test across all difficulties: the countdown is one frame, movement receives a stop request, no second decision occurs during half a frame, and another decision runs after the wait. Added a one-frame control case; the existing 13-frame check remains. Before the production change, the zero-duration method failed with expected 0.04 versus actual 0.0, while the other four branch methods passed.

Scope: local countdown and decision gating only; this does not establish native frame-event scheduling or neutral animation equivalence. No shared executor files changed and no gameplay reproduction was run. The original immobility report remains unconfirmed as resolved. Native command/minion ownership, control flags and complete path/action execution still need validation.

After correction, the focused Scarab/Baboon regression run passed 33 tests. No full suite claim.
