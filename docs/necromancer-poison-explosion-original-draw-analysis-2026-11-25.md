# Poison Explosion: original draw comparison and fixes (2026-11-25)

This pass obtained a usable minimized original D2Gfx trace and compares its
animation layers with Riiablo. It supersedes the previous `skill_trigger_unproven`
status for this individual skill. It does not establish complete GPU parity.

## Capture integrity repair

The earlier empty traces were consistent with a concrete debugger-handle bug.
Controller::handles closed CREATE_THREAD/CREATE_PROCESS debug-event handles
early. Windows closes these handles when their EXIT events are continued.
If a trace file reused a closed handle value, continuing the thread exit
closed the trace instead. The event count alone could therefore report
success while the stream silently stopped writing.

The owned, console-free `debug_event_handle_probe.cpp` reproduces this without
launching Diablo II. In the early-close case the file reused handle 288 and
WriteFile failed with ERROR_INVALID_HANDLE (6) after continuing thread exit.
In the OS-managed case the thread/file handles were distinct and the write
succeeded. Both bounded probe assertions exited zero; retained outputs:

- `tools/original-observer/debug-event-early-close-2026-11-25.jsonl`
- `tools/original-observer/debug-event-os-managed-2026-11-25.jsonl`

The controller now leaves debug-event process/thread handles to that lifecycle.
Independently opened handles remain owned by the controller. D2Gfx collection
also checks stream health after flushing, so event counts cannot silently
stand in for persisted trace evidence. The D2Gfx and corpse-preparation helpers
were rebuilt before the original run.

## Original observations

Run: `20261125-111613-09129d9a`, under
`tools/original-observer/broker/20261121-165133-4490d598/runs/`.
The outer process session failed on ReadProcessMemory after the original
exited (WinError 299). The native collector confirmed detach/cleanup and
left a 4,995,433-byte trace. This is **partial runtime evidence with an exit
warning**, not a successful complete runtime session. All 143 retained window
state samples are minimized. There was no restored-window render setup or
global input. No original loader process remained after the job.

Normalized evidence:
`tools/original-observer/necromancer-poison-explosion-original-draw-2026-11-25.json`.
It retains source hashes and raw-line references. The trace contains 5,003
cell draws, including NE/SC and these missile class/resource families:

- 116, poisoncorpseexplosion / CorpseExplodeGutsPoison: 35 draws at the corpse
  (416,264); frames advance through 1,3,5,7,9,10,11, then hold 12 through the
  end of texture sampling at 4000 ms.
- 217, poisonexplosioncloud / PoisonSparks: 272 draws; eight raw path-facing
  values (0,7,15,23,32,40,47,56) spread out from the corpse. Frames enter the
  10..18 loop, then leave it; the last sampled frame is 23.
- 141, poisonpuff / PoisonSmokePuff: 108 draws, sampled frames 1..24, starting
  later than the cloud family and drifting independently.
- 152, greenlightmissile: 18 draws at the corpse; CelFile=null, so this is a
  light/control layer, not a separate visible poison sprite.

Draw counts and GfxData class indices are not unique entity identities.
The 5000 texture-call cap ends cell sampling around 4000 ms while frame-start
observations continue to 15000 ms. Later absence is not removal evidence.
Debugger elapsed wall time must not be used as exact game speed.

## Confirmed differences and implementation

1. **Cloud drift speed.** ServerSkillSystem used a normalized vector of length
   Param1=2 directly as world units per second. D2MOO's SrvDo063 calls
   MISSMODE_CreatePoisonCloudHitSubmissiles with the eight even ring offsets;
   that function passes Param1<<7 as precise velocity, and native creation
   applies 75/100 before fp16 path conversion. Riiablo now uses this same
   conversion and marks the clouds as traveling paths. MissileCollisionSystem
   retains fp16 steps for this family. This also changes where the existing
   authoritative clouds apply poison; the fixed poison rate and damage
   ownership remain as before.
2. **Cloud intro/loop/exit.** The ordinary animation stepper could remain in
   SubLoop or restart it after a late DCC load. Poison Explosion now uses the
   existing native poison-cloud clock, with Range=60, AnimLen=31, SubStart=10
   and SubStop=19. The final segment leaves the middle loop. PoisonSparks
   direction reduction uses the existing cloud sprite-family mapping.
3. **Central burst landing.** CltDo33 creates a single centered poison corpse
   burst, unlike CltDo32's scattered gore family. It now uses the transient
   corpse-fragment terminal-frame clock. Like the preceding corpse pass,
   residue reclamation at 128 ticks is a bounded local reconstruction policy;
   the original's final residue removal is still unobserved.

The poisonpuff emission callback already existed and is retained. No duplicate
eight-cloud visual fan is added by the client skill callback. Green light,
palette/occlusion and exact random smoke births remain unverified.

## Reproducible comparison

The fixture
`core/src/test/resources/original/necromancer-poison-explosion-draw-1.10f.json`
contains 34 original cloud samples for raw path direction 47, with frame,
screen position, source trace hash and raw-line references. Game age is
**inferred** by unwrapping the sampled nine-frame middle loop, not directly
read from a server tick. Inferred ages span 2..59; the recorded frame sequence
ends at frame 23, outside the 10..18 middle loop.

NecromancerPoisonExplosionOriginalTest checks each retained frame against
MissileLoader/AnimationStepper playback and each relative screen displacement
against the fp16 movement adapter (one-pixel projection tolerance). It reads
the real MPQ DCC direction/frame header and uses mocked rendering data; this
is a production logic replay, not GPU image comparison. Existing server
integration covers eight authoritative clouds and their poison application.

Validation result and final hashes are recorded after the focused run in
`tools/original-observer/necromancer-poison-explosion-draw-validation-2026-11-25/validation.json`.
The log is `tools/original-observer/necromancer-poison-explosion-draw-validation-2026-11-25.log`.
