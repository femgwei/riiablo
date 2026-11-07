# Immolation Arrow client follow-up (1.10f)

## Evidence and scope

Target: `G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Client.dll`, SHA-256
`94466B84C0593AB682F1D3D8DC65EF4F8170F33E430865D802BC752458B2B282`.
Addresses below are image VAs, observed with local GNU objdump (static analysis,
not an observation of a running original client). REA/Ghidra's Java probe was
unavailable in the preceding investigation; no REA Evidence ID is claimed.

The user's p1/p2 comparison shows that ed2881b33 did not establish visual parity.
Its tests checked resource fields and isolated callback branches, not a rendered
fire field. Window scale, effect age and the number of simultaneous casts are
not controlled in those screenshots.

## Confirmed follow-up defects

- `0x6FB5AFD2..0x6FB5B07A`: the missile rendering branch reads Trans at row+0x18D;
  Trans=1 selects draw mode 3 at `0x6FB5B020`. There is no groundFireBig/name
  exception in this branch. Riiablo's groundFireBig exception selected ordinary
  alpha-over/LUMINOSITY, hiding neighbouring flames instead of accumulating
  their colour. Retain the existing poison-specific policy; remove only the
  ground-fire exception. GPU pixel parity remains unverified.
- Impact child setup copied the parent's birth seed into every visual fire.
  This overrode distinct factory allocation seeds and synchronized CltDo5's
  random phase choices. Inherit skill metadata only; retain each child's seed.
- `0x6FB3DB1E..0x6FB3DB58`: while remaining lifetime is greater than
  `AnimLen-SubStop+1`, the generic callback calls D2Common#10375 with SubStart and
  SubStop. D2MOO `Units.cpp`, `D2COMMON_10375_UNITS_SetFrameNonRate`, advances the
  precise frame and wraps at SubStop on the first pass. Riiablo's ordinary
  Animation sub-loop previously waited for the end of the full DCC. The new
  callback-specific update preserves fractional playback and wraps immediately.
- Non-authoritative fire replicas do not own the server's remainingFrames
  countdown. Use their nativeLifetimeFrames/nativeFrame snapshot clock, rather
  than interpreting a default remainingFrames=0 as the ending animation phase.

## Screenshot-driven APK composition (2026-11-07)

The supplied comparison shows the native fire patch at roughly 1.4x the width
of the current riiablo patch, with a filled core instead of visible sparse
holes.  The recovered APK's `ode` handler confirms that a successful unit hit
plays `explodingarrowexp` and creates one continuous `immolationfire` zone.
Riiablo now keeps the native server field and damage ownership, but its client
unit-hit branch adds that explosion presentation, fills the visual child
cells, and adds one outer visual ring.  Wall/range termination remains on the
native sparse `CltHit12` path, so this is a presentation change only and does
not alter area damage, duration, or network authority.

## All CltDo5 fire uses APK growth and fade (2026-11-07)

User requested the reference APK's playback/extinguishing for every CltDo5
missile, including small/medium flames and other skills. This supersedes the
native frame rewinding/sub-loop playback described in the earlier evidence.

Verified static sources in `D2Survivors_extracted`:

- `analysis/readable-www/assets/itemArt-BOATIR-s.js:5828` (`Ts`): play age-based
  frames from zero until loopStart, then apply the random phase within the
  inclusive loopStart..loopEnd range.
- Same file `:10202` (`Ws`): age starts at zero; phase is random in 0..24.
  This phase does **not** select the first displayed frame when a sheet has
  a loop. The previous explanation of immediate random-frame startup was
  incorrect for the looped ground-fire sheets.
- Same file `:10279` (`vne`): continue normal animation, multiply alpha by
  `min(1, remainingSeconds / 0.4)` and delete the zone at expiry. No reversed
  frames, shrinking sprites or shrinking radius are used.
- `assets/www/d2/missiles.json`, `immolationfire`: 25 FPS, 37 frames, loopStart
  12, loopEnd 36. Thus ages 0..11 show growth; subsequent frames loop in 12..36.

Riiablo applies the same growth/phase/fade behavior to all pCltDoFunc=5 rows.
The immutable missile birth seed supplies a stable 0..24 phase independent
of damage RNG. A presentation clock catches up to snapshots and advances
between them, preserving age across DCC reloads. Fade lasts ten 25 Hz ticks
before the existing missile expiry; server lifetimes and damage stay intact.
Independent missile cells retain their own phases rather than sharing the
APK zone's phase plus child-index offsets; field geometry/composition remain
the existing riiablo implementation. This is static-source-guided playback,
not proof of whole-scene pixel equivalence with a running APK or original.

## Regression boundary

The impact integration test invokes the actual handler with a recording factory
and an independent unsigned RNG oracle: circular integer cells, child selection,
63..87-frame lifetime, percentage gate, skill inheritance and distinct child birth
seeds. Ground-fire tests now cover initial growth, inclusive phased looping,
the ten-tick alpha fade, RNG isolation and reload/sparse-snapshot continuity.
Adjacent missile animation and existing Immolation Arrow server tests remain
part of the targeted regression.

Validation: JDK 17 with the installed 1.10F MPQs; 23 targeted tests passed,
zero failures/skips. The selection includes `NativeGroundFirePresentationTest`,
`GroundFireReplicaClockTest`, `ImmolationArrowPresentationTest`,
`MissileAnimationCatchupTest`, `MissileLoaderTest`, and the four Immolation
Arrow methods in `AmazonSkillSpecializationTest`. Remote window verification
of the growth and fade remains for the user.

The client hit field was enlarged in the screenshot-driven change above;
this follow-up only changes playback and fading. The actual framebuffer
appearance, wall/room filtering, remote impact source lookup and
sound-instance parity still require separate verification; passing these tests
does not claim complete original-client parity.
