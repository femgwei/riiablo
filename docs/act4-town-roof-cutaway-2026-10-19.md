# Act IV town roof cutaway

Reference: user screenshots p3/p4 (Diablo II) and p5 (riiablo).
Entering the Pandemonium Fortress temple hides the roof while foreground
columns remain translucent.

`RenderSystem.drawRoofs` previously reused the wall opacity path: outdoor
logical regions were ignored and even a selected Pop only faded to 55%.
Roofs now use their matching positive native logical region or an explicit
native Pop rectangle, independently of the outdoor wall policy, and fade to
zero over the existing 500 ms transition. Leaving restores the roof.
Unassigned groups (zero/negative) cannot hide roofs by themselves.

Native references: D2MOO `DRLGLOGIC_SetCoordListForTiles` assigns the room's
logical indices; `DRLGPRESET_UpdatePops` selects preset regions and
`DRLGPRESET_TogglePopsVisibility` updates tile visibility transitions.
The renderer's logical-region fallback is an adaptation, not a verified
reconstruction of D2Client. Exact native Pop style/flag selection remains
outside this change.

Regression: `OutdoorWallOpacityTest` covers matching/distinct/unassigned roof
groups, explicit Pop bounds, and preservation of outdoor wall opacity.
`./gradlew.bat :core:test --tests com.riiablo.map.OutdoorWallOpacityTest
--no-daemon` passed on 2026-10-19 (including core/test compilation).
Visual acceptance remains required on the user's other computer: walk from
the stairs to Tyrael, check roof disappearance and foreground columns, then
walk back outside and check roof restoration. Other buildings should retain
their roofs when their logical region/Pop is not selected.
