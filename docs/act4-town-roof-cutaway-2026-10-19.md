# Act IV town roof cutaway (corrected)

Reference: user screenshots p3/p4 (Diablo II) and p5 (riiablo).
Entering the Pandemonium Fortress temple hides the roof while foreground
columns remain translucent in the original client.

`RenderSystem.drawRoofs` previously reused the wall opacity path: outdoor
logical regions were ignored and even a selected Pop only faded to 55%.
The initial fix (`1af7c52a9`) used positive floor logical regions for outdoor
roofs. User screenshots showed fragmented roofs outside and surviving opaque
strips inside. This was an incorrect substitution for native preset Pops.

Corrected behavior for presets with Pops:

- Do not use floor logical-group equality to hide roof fragments.
- Select a Pop logical index (default 0 outside the rectangles), and switch
  every rectangle with that index together.
- Match the DT1 main/style index against the Pop's subIndex, and restrict the
  affected tiles to the rectangle expanded by one tile.
- Exclude object walls (0x100) and the separate reveal-hidden branch (0x200)
  from the ordinary style fade. Do not fade unrelated scenery in the rectangle.
- Fade matching artwork, including non-ROOF orientations belonging to the roof
  style, to zero. Previously non-ROOF trim could remain at 55% or fully opaque.
- Keep the existing 55% foreground-wall treatment separately, limited to
  layer-one walls in the selected Pop bounds. The installed fortress's
  columns use Exterior.dt1 style 4, flags 0x4001/0x4005; roof trim uses style 2,
  layer two. This foreground blend policy remains a client approximation.

Presets without Pops retain the existing logical cutaway fallback.

Native references: D2MOO `DRLGLOGIC_SetCoordListForTiles` assigns the room's
logical indices; `DRLGPRESET_UpdatePops` selects preset regions and
`DRLGPRESET_TogglePopsVisibility` updates tile visibility transitions.
These selection rules follow D2Common; exact D2Client blend treatment and
reveal-hidden tiles remain separate verification work.

Regression: `OutdoorWallOpacityTest` covers Pop group selection, style/flags,
bounds, and prevention of the logical fallback when native Pops exist.
`Act4FortressResourceTest` builds native Act IV using the installed 1.10f MPQs:
the fortress exports Pop index 1, style 2, bounds (3,1,5,14), controlling 53
roof tiles (all 53 ROOF entries, flags 0xc001). Tests assert no roof tile hides
outside and every roof tile is selected inside. Both test classes passed with
no resource skips.
Final targeted verification on 2026-10-19: 6 opacity tests and 2 real-resource
tests passed, no skips (JUnit XML timestamps 14:00 UTC). A local Gradle init
script limited test compilation to these classes and RiiabloTest because
concurrent changes in AssassinSkillSpecializationTest lacked assertion imports.
The regular Act IV offscreen startup passed. A temporary inside-Pop placement
also confirmed roof/trim removal in a real screenshot. That temporary placement
was removed. The last outside/foreground screenshot rerun failed while packaging
core.jar during concurrent workspace builds; the targeted tests themselves
completed successfully. Final foreground opacity (including flags 0x4001)
is covered by the passing tests, but the final visual rerun is still pending.
Visual acceptance remains required on the user's other computer: walk from
the stairs to Tyrael, check roof disappearance and foreground columns, then
walk back outside and check roof restoration. Other buildings should retain
their roofs when their logical region/Pop is not selected.
