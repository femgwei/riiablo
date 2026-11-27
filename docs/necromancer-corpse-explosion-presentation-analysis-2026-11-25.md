# Necromancer corpse explosion presentation analysis (2026-11-25)

Corpse Explosion (74) and Poison Explosion (83) were the next client-facing
Necromancer entries after the summon and Dim Vision passes. Existing code uses
the authoritative corpse position for both burst presentations; it does not
trust the stale target vector sent with a skill event.

The focused client regression dispatches both skills with an intentionally
invalid target vector and verifies the created missile and position:

```text
Corpse Explosion -> corpseexplosion at the corpse's Position
Poison Explosion -> poisoncorpseexplosion at the corpse's Position
```

The authoritative server regression also covers the presentation-relevant
steps:

- the corpse reservation is consumed once;
- `CORPSE_NOSELECT` and `CORPSE_NODRAW` are installed at the correct point;
- inner targets receive the physical packet and outer targets receive the
  elemental half;
- Poison Explosion creates eight authoritative drifting clouds with the
  fixed poison rate, duration and pierce snapshot;
- repeated keyframes do not create a second explosion.

The lower-level native missile and curse-area mapping is recorded in
`docs/necromancer-client-area-trail-2026-11-01.md`. The initial position-only
regression did not establish animation parity. Its initial conclusion that
no production step was missing was superseded by the original draw analysis
below.

Validation log:

```text
tools/original-observer/necromancer-corpse-explosion-validation-2026-11-25.log
```

Passed tests:

```text
NecromancerExplosionPresentationTest: 1
NecromancerExplosionIntegrationTest: 13
NativeNecromancerExplosionDataTest: 2
```

## Original Corpse Explosion draw evidence and correction

Source run: `20261125-090136-9b8aee9a`, under
`tools/original-observer/broker/20261121-165133-4490d598/runs/`.
The process session failed after the original exited (`WinError 299`), but
the saved D2Gfx trace has 5,003 cell events. Native debugger cleanup was
confirmed. Its observed draw window state is minimized. The old setup code
first called SW_SHOWNOACTIVATE before minimizing: there was no recorded focus
takeover, but this transient restored-window step did not satisfy the user's
strict hidden/minimized policy. Subsequent minimized jobs now use
SW_SHOWMINNOACTIVE directly, without that restored-window step.

Reproducible normalized evidence:
`tools/original-observer/necromancer-corpse-explosion-original-normalized-2026-11-25.json`.
The converter joins **unit_type=3 class indices** to the effective Missiles.txt
rows. Empty token fields do not mean the effect was absent. Class indices and
reused GfxData scratch pointers are not unique unit identities, and the
normalizer deliberately does not count them as entities.

Observed sequence and layers (elapsed times are debugger wall time):

- NE/SC is drawn at 1016..1609 ms, sampled frames 2,4,6,8,10,12,14.
- The SK death/corpse candidate is at screen (256,264); its DD draws stop at
  1250 ms. This is last observed corpse drawing, not proof of exact deletion.
- Missile class 42, `explodingarrowexp` / `ExpArrowExplode.dcc`, draws at the
  same (256,264) at 1422..2016 ms, sampled frames 1,3,5,7,9,11,13,14.
  Offline DCC header and row both report 16 frames, one direction.
- Class 115, `corpseexplosion` / `CorpseExplodeGuts.dcc`, draws at (224,264)
  and (256,280). It advances through sampled frames 1,3,5,7,9,11, then holds
  frame 12 through the end of texture sampling (3844 ms).
- Class 21, `bigblood2` / `BloodLarge02.dcc`, moves from (244,255) to
  (273,255), then holds frame 8 through 3906 ms.
- Class 151, `redlightmissile`, is also submitted at the corpse position.
  Its CelFile is `null`; this is a light/control layer, not evidence of a
  visible explosion sprite.
- TO and X1 are scene objects already drawing before the cast. They are
  excluded from explosion-layer classification.

The shipped D2Client.dll SHA256 is
`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`.
The skill callback table at 6FB775A8 maps CltDo32 to 6FAC88F0, which calls
6FAC85F0. The latter reads cltmissileb at Skills+EC and creates it at
6FAC884E..78, and reads cltmissilec at Skills+EE at 6FAC887A..A5.
Archived disassembly:
`tools/original-observer/necromancer-explosion-client-callback-disassembly-2026-11-25.txt`.
This is static corroboration, separate from the observed D2Gfx draws.

**Confirmed defect and fix:** SkillCastHandler created only cltmissilea.
It now also creates Corpse Explosion's cltmissileb flame layer at the corpse
position, as a local presentation-only missile. Its additive blend and frame
clock use the existing missile loader. Poison Explosion's cltmissileb is the
cloud family already created by the server, so this branch does not create a
duplicate poison-cloud set.

Final headless validation: 16 tests, zero failures/errors/skips:
NecromancerExplosionPresentationTest (1), NecromancerExplosionIntegrationTest
(13), NativeNecromancerExplosionDataTest (2). The presentation test verifies
both layers' corpse position, the flame's presentation-only ownership and
16-tick removal. Log:
`tools/original-observer/necromancer-explosion-original-layer-validation-2026-11-25.log`.
This verifies Riiablo behavior; it does not establish GPU or pixel parity.

## Remaining animation gaps

Corpse Explosion is **partially compared, not animation-complete**. The first
flame-layer fix still left a single centered gore sprite and no moving blood.
The subsequent fragment pass below replaces that path. The red/green
light-only layer is still not covered. Do not create an extra visible
`null.dcc` sprite to simulate it.

The probe stops repeated texture sampling at its 5000-call cap (3906 ms in
the Corpse Explosion run), although frame-start observations continue to
about 15 seconds. Absence of later texture events is not cleanup evidence.
Debugger wall time is not an exact game-speed measurement. Palette, light,
occlusion and final raster appearance remain unverified.

Poison Explosion attempt `20261125-090620-226e15f7` failed after process exit;
its final saved draw file is empty despite its native summary reporting
events, so it is unusable as draw evidence. Attempt
`20261125-101531-cdd7f66b` completed with debugger cleanup and all recorded
draws minimized, but observed only NE/NU and no missile cells. Its skill
trigger is unproven, so it is not an animation pass. A further bounded attempt
updates the target HWND's WM_MOUSEMOVE before the right-button messages,
because corpse selection may still refer to the previous hover position;
this does not move the system cursor or send global input. That attempt,
`20261125-101803-c6a7ef43`, completed with confirmed debugger cleanup but
again left an empty final draw file despite reporting native event counts.
It is unusable for draw comparison; the cause of this inconsistent trace
persistence remains unknown. No further unchanged retries were performed.

The best retained Poison Explosion normalization is
`tools/original-observer/necromancer-poison-explosion-original-normalized-2026-11-25.json`,
whose status is `skill_trigger_unproven`, not a visual match.
This earlier status is superseded by the later usable run 111613-09129d9a:
see `docs/necromancer-poison-explosion-original-draw-analysis-2026-11-25.md`
for the handle-lifetime repair, actual poison draw evidence and resulting fixes.
Final test XML copies, source hashes and aggregate status are retained in
`tools/original-observer/necromancer-explosion-original-layer-validation-2026-11-25/validation.json`.
The broker was verified ready after the jobs, with no original loader process
left running. No commit or push was performed.

## Fragment scatter, movement and landing pass

This pass reuses the retained original draw trace; it does not run the original
again. The additional static evidence is from the same hash-pinned D2Client:

- CltDo32's ordinary blood branch at 6FAC87E4 calls the scatter helper with
  radius=2, count=2, for cltmissilea. At 6FAC881B..39 it selects the missile
  row's SubMissile2 and calls the helper with radius=1, count=3. Retail also
  has other blood-policy branches; these are not covered by this reconstruction.
- Helper 6FABD8A0 picks a starting quadrant, jitters each birth independently
  in `[-radius,radius-1]`, rotates diagonals and sets a direction reference
  16 cells away. Counts here come from disassembly, **not from counting the
  sampled GfxData indices as unique entities**. The retained runtime sample
  identifies two gore positions and one visible moving blood trajectory.
  The helper leaves the creation parameters at its last birth position;
  CltDo32 restores x/y only after the blood-family call (6FAC883E..4A).
  Blood scatter therefore uses the last gore birth as its centre.
- Client missile creation at 6FB3E31C..42B converts velocity to 8.8, then
  applies 75 percent. Native fp16 path steps are reused in the reconstruction.
- The missile CltDo table maps 11 to 6FB31540. Its D2Common import at
  6FB6B400 is ordinal 10378, UNITS_IsAtEndOfFrameCycle. At the terminal
  frame, CltDo11 marks UNITFLAG_ISDEAD and releases the light through
  6FB296E0 instead of simply deleting the rendered residue. CltDo2 at
  6FB30260 similarly marks landed blood dead. This corroborates the
  observed frame-12 gore and frame-8 blood residence.

Archived selected disassembly files are
`necromancer-corpse-fragment-scatter-disassembly-2026-11-25.txt`,
`necromancer-corpse-fragment-terminal-disassembly-2026-11-25.txt`, and
`necromancer-corpse-fragment-velocity-disassembly-2026-11-25.txt`, all under
`tools/original-observer/`.

Implemented behavior:

- Corpse Explosion now creates two jittered gore fragments, three outward
  SubMissile2 blood fragments and the already-fixed centered flame layer.
- A transient CorpseFragment component owns these local visuals. It never
  changes corpse consumption, damage, networking or poison clouds.
- Blood uses fixed-point native movement until the terminal animation frame,
  then stops. Gore and blood clamp to the terminal DCC frame instead of
  disappearing with the former short animation lifetime.
- The simulation clock runs while assets are cold. MissileLoader and
  AnimationStepper seek to the fragment age, preventing a late-loaded blood
  sprite from restarting in midair or continuing to slide after landing.
- Randomness uses a separate client stream. Matching the original caster
  seed and exact sprite-facing variants remains unverified.

**Reconstruction policy, not observed retail cleanup:** local residue is
reclaimed at age 128 game ticks (5.12 seconds) to bound allocations. The
original's eventual residue removal was outside texture sampling, so this
must not be described as a matched native lifetime or an observed cleanup
pass. Blood-setting variants, all sprite directions, light and GPU blending
remain open.

The retained class-21 blood moves from x=244 at animation frame 1 to x=273
at frame 8, with constant sampled screen y=255. The directional production
replay moves 29.6953125 projected pixels over that interval, versus the
sample's 29 integer pixels (within one pixel of projection quantization).
This is a relative trajectory comparison; it does not establish identical
random birth coordinates or debugger wall-clock speed.

Final fragment-pass validation: **19 tests passed**, zero failures/errors/skips.
NativeCorpseFragmentPresentationTest contributes three tests: retained relative
blood displacement and landing, cold-DCC terminal-frame loading/reclamation,
and 25/60/120 Hz with zero-delta stability. The existing presentation test
now checks two gore/three blood births and the independent flame; the 13
server integration tests and two native data tests also passed. The cold-DCC
test reads real MPQ direction/frame headers and uses mock DCC rendering data;
it executes MissileLoader, AnimationStepper and the real presentation clock,
but submits no GPU draws.

Log: `tools/original-observer/necromancer-corpse-fragment-validation-2026-11-25.log`.
Test XML copies, final source hashes and the explicit observed/reconstructed
comparison are in
`tools/original-observer/necromancer-corpse-fragment-validation-2026-11-25/validation.json`.
No server:d2gs code changed in this pass; this validation invokes only the
targeted core task. All checks are headless; no original game was launched.
