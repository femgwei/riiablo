# Fallen death-reaction observation, 2026-11-17

The resident broker (PID 24896, session `20261116-014209-3a0c898c`)
executed all runs without another elevation request. Jobs requested hidden entry.
No AI parity conclusion is established by these runs.

## Runtime evidence

- `20261117-165525-2a95351d`: 203 asynchronous samples. Reused CXSorc
  was already in player DD (17). Posted attack input could not execute.
- `20261117-165744-bd5a91f7`: 231 samples after restart. Player modes
  included NU, WL, DT and DD; no monster DT transition observed.
- `20261117-170024-9a3eff52`: prepared a backed-up repository-owned
  CXSorc with 8000 life/mana and level 20 Fire Bolt. Entry failed its
  hidden/non-iconic window invariant; no collector baseline accepted.
- `20261117-170154-94992de9`: reused the prepared process. 221 samples,
  player modes NU/WL, no observed monster death. Input failed because
  eight approach attempts did not bring a target into view. Loaded target
  class was 5, so this was not an isolated Fallen fixture either.

All three launched native collectors report `detached=true` and
`cleanup_confirmed=true`. Each has `death-observations.json` alongside
`server-ai-samples.jsonl` in its run's orchestrator/process-session directory.
These reports include the source SHA256 and explicitly return
`incomplete_no_death_observed`. Sampling may miss short transitions.

## Changes

- Reject player DT/DD before attaching a runtime-input collector and before
  posting an attack.
- Wait for bounded native collector cleanup even if approach/input fails.
- Analyze encounter samples separately from collector execution success.
  AI parameter 0 becoming 1 is ambiguous (attack commands also set it).
- Fresh survivor job backs up CXSorc before replacing only that test fixture.
  It does not modify the user's aaa save. The actual registered save directory
  may be the sibling Diablo II/Save directory; executable/DLL identity remains
  pinned to Diablo II 1.10F.

## Controlled experiment completed later the same day

`native-harness/d2game_fallen_death.cpp` uses the existing bounded game-thread
call mechanism. It creates class 19 Fallen and class 0 skeleton in the same
native room, calls native GetModeChangeInfo/ModeChange for DT, checks the
corpse GUID in the actual room ring, samples 121 consecutive update entries,
then removes its own units. No mouse input is needed after scene entry.
The original DLL hashes and entry signatures are checked before attachment.
Failure after spawning requires restarting this disposable test world.

- `20261117-171522-1e4f81cf`: death-call fault with the temporary mode structure
  too close to the callee stack; debugger cleanup/detach confirmed, fixture
  cleanup not completed. Discarded and original process restarted.
- `20261117-171614-0a74db94`: failed hidden entry before native collection.
- `20261117-171714-c4188d99`: first completed controlled death, at step 20.
  Observer was in S2; no param0/escape reaction was observed during corpse DT.
  This is not evidence that Fallen ignores the death: no think-call trace was
  recorded, and the next visible behavior change followed the DT window.
- `20261117-171832-95c37dcc` and `20261117-171955-5dc5fec8`: death moved to
  step 1. Both observed WL and param0=1 at step 2, first integer displacement
  at step 4, corpse DD at step 25. Walking continued after DD.
  Both have confirmed native death, ring membership, fixture removal and
  debugger cleanup/detach. Source CSV hashes and reports are stored alongside
  each run's `frames.csv`. These are repeated scenarios in one game, not
  identical initial RNG/position replays.
- `20261117-172225-c49239f5`: no free placement corridor remained after repeated
  runs. Native Fallen allocation also creates followers; removing only the
  two primary units had left those followers in the disposable world.
  The helper now records the complete synchronous spawn delta and removes
  those units, verifying absence from the monster hash. Prior cleanups only
  proved removal of the two primary targets, not the complete spawned group.
- `20261117-172355-887409d7`: after a fresh process, the final helper completed
  121 frames, confirmed native death/ring membership and verified complete
  owned-group removal plus debugger detach. This is the final-source smoke run.

Static disassembly of shipped `D2Game.dll` RVA `0xA0560` independently confirms
the Escape destination uses integer per-axis sign times distance, rather than
a normalized Euclidean direction. Instructions `0x6FCD0633..0x6FCD066C`
perform sign selection and per-axis multiplication/addition; evidence with
binary SHA256 is in `runs/fallen-escape-native-entry.json`.

Riiablo's Fallen destination was corrected accordingly. Tests cover unequal
diagonal deltas, negative directions and fractional positions sharing one
native integer axis. 79 Java regression tests and 10 Python observation tests
passed. This change does not align the full pathfinder, movement duration,
think schedule, sound RNG consumption or all Fallen behaviors.

## Remaining experiment

Extend the working controlled fixture to distance/adjacent-room/death-ring
capacity cases and pair them with identical port state. Capture exact AI
think/target/path evidence before claiming event or path parity. Ordinary
window-input encounter runs above remain excluded from the baseline.

The controlled experiment forces DT through original functions; it does not
exercise ordinary damage settlement, kill attribution or client animation.

## Distance, ring capacity and adjacent-room batch

Final-source run `20261117-173118-ccf29be3` completed five hidden profiles
with map seed `127897359`, 121 contiguous frames each (605 total). Every
collector confirmed native death, room-ring recording, owned-group removal,
debugger cleanup and detach. No new UAC was needed. Earlier batch
`20261117-172824-fc7b559e` covered the first four profiles, and
`20261117-173012-ffcbc043` first exercised a neighboring room; the final batch
adds persisted room-list evidence and supersedes those for reanalysis.

- `distance-14`: exact native-distance footprint verified after spawn.
  Param0=1 and WL observed at steps 2..24; first integer displacement step 4.
- `distance-15`: no param0=1/WL combination during corpse DT.
- `ring-4`: first nearby corpse remained DT and in the ring after three
  additional native deaths. Reaction observations matched distance-14.
- `ring-5`: fifth native death overwrote the first corpse GUID. The first
  corpse remained DT, but no param0=1/WL combination was observed during DT.
- `adjacent-room`: observer and corpse had different native room pointers;
  the corpse room was present in the observer room's actual adjacency list.
  Reaction observations matched distance-14.

Each additional ring-capacity corpse is removed immediately after its native
DT transition, so it occupies a historical GUID slot without independently
triggering another nearby-DT reaction. Per-death `deaths.jsonl` records verify
cursor advance and exact single-slot overwrite. No direct ring writes are used.

The helper validates the shipped D2Common #10035 getter instructions
at RVA `0x4BC36` before reading room-list/count offsets `0x24/0x28`.
`identity.json` retains the actual adjacency pointers. The analyzer rejects
mismatched requested distances, false room adjacency, missing death evidence,
noncontiguous frames or incomplete cleanup.

Riiablo's corresponding distance, room scope and four-slot history cases
passed. One new test uses the observed unequal-diagonal distance-14 case;
15 death-reaction, 8 leader-command and 8 map-checkpoint tests passed
(`fallen-boundaries-regression.log`). The Python analyzer/contract suite has
15 passing tests. No additional gameplay fix was required by these cases.
These observations establish selected branch behavior, not identical initial
RNG, collision paths, think scheduling, damage or client animation. A negative
observation alone does not establish absence of an internal escape attempt.

## Escape completion and reengagement

The bounded `recovery` profile extends collection to 600 updates (601 samples).
It records FP16 coordinates, native path velocity and point count, player
coordinates, path target and final path point. Shipped D2Common getter
instructions establish these fields: #10147 RVA `0x69AB0`, #10154 `0x69B80`,
#10175 `0x69DE0`, #10176 `0x69DF0`, #10157 `0x69BD0`. The helper checks their
signatures; archived sources/executable hashes live in the corresponding runs.

- `20261117-173533-ba58bd82`: first extended observation, before path-target
  fields were added. It cannot distinguish all WL segments by target.
- `20261117-173757-4dedb43c`: 601 completed samples with path evidence. Initial
  WL at step 2 aimed from `(4525,5644)` to `(4537,5656)`, velocity 1600.
  Corpse DD began at step 25. At step 46 the observer reached its initial
  target, changed target to `(4535,5659)` and velocity to 960 while remaining
  in WL. Later WL is therefore not necessarily continued fleeing. The player
  stayed at `(4523,5642)`; no attack mode occurred during these 600 updates.
- `20261117-174127-be4dd494`: explicit `recovery-reengage` profile. Same initial
  target was reached at step 46. At step 100 a native mode-change call issued
  an ordinary walk-to-player action; `intervention.json` records frame, IDs
  and action. At step 178 native AI entered A2 (mode 5), path count became
  zero, and AI param0 changed from 1 to 0. This proves attack-mode reentry
  following a deliberate approach, not spontaneous pursuit or damage parity.

Both final extended runs confirmed owned-group removal, cleanup and detach.
One detach attempt in the reengagement run logged Win32 5; the controller's
fallback completed and the final summary confirms `detached=true` and
`cleanup_confirmed=true`. No new UAC was requested.

Riiablo changes are deliberately limited: while ESCAPE owns an active
Pathfind, ordinary think updates do not replace it after an arbitrary timer;
corpse DD does not terminate it. On completion the temporary velocity bonus
is released. Successful Escape no longer consumes an extra RNG draw for a
random 1..2 second cooldown. Sound-choice RNG and other action timers remain
separate work. The ordinary neutral-mode handling still applies when needed.

90 Java tests passed (17 death reaction, 8 leader command, 57 behavior scenarios,
8 map checkpoint), including active-path preservation beyond the former two
second delay, retained speed bonus during movement and release on completion,
and conditional attack resumption once in melee/NU. 18 Python observation
tests passed. An initial test run exposed a misplaced generic SLEEP assignment
before the completion guard; it failed and is retained as
`fallen-recovery-regression.log`. The corrected final run is
`fallen-recovery-regression-final.log`.

Remaining limits: no complete natural path/tick replay against riiablo, no
same initial RNG replay of the two original runs, no native attack damage or
client animation equivalence. In particular, 178 is a fixture observation,
not a universal recovery timer or a port timing assertion.

## Recorded movement replay (2026-11-17)

Two hashed CSV fixtures under core/src/test/resources/native retain original
FP16 positions and source provenance. NativeFallenEscapeMovementTest drives
production VelocityAdder with the observed endpoint, native table velocity,
25 Hz updates and an otherwise empty dynamic collision world.

- Diagonal flee, recovery run 20261117-173757-4dedb43c: steps 2..46,
  44 updates, velocity 1600. Every raw X/Y coordinate and the arrival tick match.
- Oblique approach, reengagement run 20261117-174127-be4dd494: steps 100..177,
  77 updates, velocity 960. Every raw X/Y coordinate matches. This approach
  followed the explicit native walk intervention, not spontaneous pursuit.
- Step 178 is retained in the second fixture but excluded from WL integration:
  native enters A2, clears the path and centers position at (4524.5,5644.5).
  Treating this as another WL tick fails. This is an action-transition boundary,
  not evidence of an incorrect walking speed. Port parity at that boundary
  remains unverified. Pathfinder.findPath(src,null) currently removes the path
  and zeros velocity without an explicit center reset; full native path
  completion separately uses D2PathTick.reset. A transition-level reproduction
  is needed before changing either behavior.

19 tests passed: 2 recorded replays, 8 native monster movement, 2 VelocityAdder,
7 native path tick (fallen-movement-replay-final.log). The replay exercises the
fallback production integrator; it does not recreate the native topology path
runtime, path search, occupied terrain, AI scheduling or attack entry. No new
original process, foreground window or UAC was needed for this replay.

## Fallen attack-entry boundary correction (2026-11-17)

FallenAttackTransitionTest now feeds native step 177 directly into the real
Fallen think branch with real Pathfinder and VelocityAdder. Before the fix,
A2/Casting were selected and movement was stopped, but raw X stayed 296542873
instead of the observed 296517632 (fallen-attack-transition-before.log).
Thus the missing center fit can be reproduced independently of path search.

Fallen.stopForAttack now stops movement and fits known native coordinates to
the current subtile center before selecting its normal or command A1/A2
sequence. It invalidates the path cache after fitting. The occupied cell is
unchanged. Unknown/legacy floating-point precision is preserved. Generic stop,
death and other monster AI behavior are not changed by this correction.

Tests cover the ordinary A2 branch, pack-command A2 branch, and the legacy
precision guard. After AI selection, one real world update verifies no extra
movement. This compares the position/path/velocity/sequence-intent boundary;
it does not replay native think scheduling or establish the client COF start
frame, A1 runtime parity, damage, natural pursuit, or animation equivalence.

Final focused regression: 47 tests passed (3 attack transition, 17 death
reaction, 8 leader command, 2 recorded movement, 8 native movement, 2 velocity,
7 path tick). Log: fallen-attack-transition-final.log. No original process
launch, foreground interaction or UAC was needed in this step.

## Fallen attack duration and port keyframe lifecycle (2026-11-18)

Extracted the twelve complete A1/A2 spans from the existing 601-frame
recovery-reengage run into native/fallen-attack-durations.csv. Its JSON sidecar
pins both source and fixture SHA256. Eight A2 spans last 15 ticks each; four
A1 spans last 10 ticks each. The final A1 starts at step597 and is truncated
by the sample boundary, so it is excluded. These are mode-state spans, not
native animation-cursor or hit-event observations.

MonsterMeleeAnimationRegressionTest replays each span through real eanimdata,
AnimDataResolver, CofManager, SequenceHandler and AnimStepper at 25 Hz. Both
modes retain their mode until the observed duration, emit exactly one ATK
marker at their asset marker index, emit one finished event and return to NU
with Sequence removed. Seven focused tests passed, including the three
Fallen attack-entry tests (fallen-attack-animation-final.log). The initial
run failed because its expected span count was mistakenly 13 rather than 12;
that log remains fallen-attack-animation.log. No gameplay change was needed.

Scope: this confirms the sampled durations and port marker/sequence lifecycle
at base animation rate. No Actioneer damage execution, original hit-event
sampling, full AI think/recovery scheduling or client rendering was included.
The original collector does not yet capture animation cursor/action events;
matching native hit timing requires those fields or a bounded native event
trace, rather than inferring a hit from a mode change. No new original game
process, foreground window or UAC was used.

## Native planned attack events (2026-11-18)

Resident broker PID24896 was reused without UAC. Two hidden controlled
recovery-reengage captures completed, each with 601 contiguous samples and
confirmed fixture removal, cleanup and debugger detach:
- 20261118-014056-360c0fe4: first animation-state experiment.
- 20261118-014314-2b4a9367: adds pending unit timer observations.

Shell pefile/Capstone disassembly of the hash-pinned shipped DLLs (not REA
Evidence) establishes the field reads. D2Common #10380 RVA0x7FA25 reads
unit+0x44/0x48; #10373 RVA0x7EF02 reads +0x44 and signed speed+0x4C;
#10381 RVA0x7FA49/0x7FA64 reads animdata+0x50 and writes action byte+0x4E.
The server's +0x44 state was constant across each attack and outside the
relative animation range. It must not be presented as a live client cursor.
Action bytes stayed zero at these boundaries; this is not absence of attacks.

D2Game RVA0x8CE40 returns unit+0xDC; RVA0x5185 traverses timer+0x24,
reads byte event type and expire frame+4. New signatures guard these layouts;
timer walks are bounded with cycle rejection. MODECHANGE custom id is read
at +0x14 according to the reference event layout (no independent accessor
signature for that custom-id offset). It consistently reads 1 for these
attack events. All reads happen at the same stopped update boundary.

In the final run, fifteen complete attacks were observed (five A1, ten A2).
Their first pending MODECHANGE expiry is +7 ticks for A1 and +8 for A2;
ENDANIM is +10/+15 respectively, relative to the first attack-mode snapshot.
The pending action disappears later in the span. This is scheduled-event
state, not a callback execution trace or damage confirmation. A final A2
span is truncated and excluded from the replay fixture.

native/fallen-attack-event-schedule.csv and its hash manifest preserve the
fifteen comparisons. fallen-attack-animation-native.json records full span
state and planned events. The analyzer rejects sample gaps, identifies
truncated spans and does not treat out-of-range frame state as a relative
cursor. Its 21 tests pass. Production marker/sequence replay compares these
planned offsets using real eanimdata, AnimDataResolver, CofManager,
SequenceHandler and AnimStepper. No gameplay implementation was changed.

Final Java validation: eight tests passed (five melee animation regressions,
including 27 recorded spans, and three Fallen attack-entry tests), log
fallen-attack-event-schedule.log. The port ATK marker dispatch offsets agree
with all fifteen new native planned event offsets. Remaining work is bounded
native callback execution/damage observation and client presentation; matching
these planned offsets does not establish either.

## Actual per-mode callback execution (2026-11-18)

Added a bounded native callback probe to the controlled Fallen collector.
Only when the observer's next update has a scheduled attack/end event does
it arm the corresponding event dispatcher (D2Game RVA0x35080/0x35150).
It validates game/unit identity, stops after callback-table lookup, stops at
the actual per-mode function, and observes return through the dispatcher.
Unrelated units are allowed to return before rearming; all hooks are removed
before cleanup. A maximum of 128 dispatcher visits per update bounds the loop.
No wall-clock speed, game RNG, attack rating or damage stats were changed.

Results using the same elevated broker, hidden game and native walk-to-player
intervention (not spontaneous pursuit):
- 20261118-015331-e88d8a82: 14 attack and 14 end callbacks observed, all at their
  scheduled game frame. Actual callees were RVA0x34790 (attack) and 0x33680
  (end). Every end returned to NU. One update lost 345 HP fixed-point units,
  but other monster dispatchers also ran, so attribution is unresolved.
- 20261118-015503-e5338c02: tightened the HP bracket to actual per-mode entry
  through event-dispatcher return using the already validated native stat
  getter D2Common RVA0x77C30 (stat6). Two attacks and two ends were observed;
  neither attack changed player HP inside that bracket.
- 20261118-015620-a6e54c55: no attack occurred within the 600-update window.
  Callback evidence is incomplete, not proof of nonexecution or an AI defect.

All three runs completed 601 contiguous samples, removed their owned spawn
sets, restored breakpoint bytes and confirmed cleanup/detach. Other monster
AI/RNG/world history is not rolled back between these observations. These
are successive controlled observations, not equivalent initial-state runs.

Analyzer checks unit identity, duplicate observations, exact entry/return
frames and coverage of every due attack/end timer in frames.csv. HP bracket
coverage is reported separately from zero observed decreases. 25 Python tests
pass. callback-analysis.json is saved in each run; the tightened observation
is also summarized by fallen-attack-callback-native.json with source hashes.

No riiablo gameplay code changed. Previous production marker/NU tests cover
the corresponding action lifecycle, but full damage/hit/block/absorb parity
remains unverified. Next useful probe is the actual melee hit/result branch
and damage settlement with attacker/defender identity; more unlabelled HP
snapshots or assuming a dispatched attack must hit would not establish it.
No foreground interaction, game relaunch or new UAC was needed.

## Actual melee settlement result (2026-11-18)

Added one-shot probes inside the observer's actual attack callback at shipped
D2Game RVA0x91AC0 (melee processing / DrainItemDurability) and RVA0x91260
(shared ExecuteMissileDamage settlement, also called by the melee path).
Entry signatures and the existing DLL hash guard the ABI. Reads include
attacker/victim types and GUIDs, result flags and fixed-point damage at
settlement entry/return. This is not a projectile-only probe: the observed
melee call reaches the shared settlement function. One-shot hooks do not
promise a complete trace of nested/reflected damage.

20261118-020034-953b7949: normal recovery-reengage observation completed,
but no attack occurred, so damage evidence is incomplete.
20261118-020243-c21b61ff: first explicit attack-direct profile was accepted,
but the step1 corpse death triggered flee at step2 and cancelled the attack
before its event. This run is retained as an interrupted action, not a miss.

The final attack-direct profile deletes the spawned observer's pending AI
think after issuing native A1, and delays the corpse death until step20.
Attack/end timers, original hit RNG and combat attributes remain untouched.
This is a controlled action trace, not evidence of autonomous AI decisions.
Its intervention.json declares the changes. The broker discovers the finite
new job ai-hidden-fallen-attack-direct without restarting or elevating again.

Final run 20261118-020359-690dbcae (121 contiguous samples):
- Explicit observer GUID128 attacks player GUID1; melee_entry confirms both.
- Frame768318 enters actual attack callback RVA0x34790 and then the melee
  and shared settlement functions. Entry/return result_flags=0, hit_flags=0,
  physical_fixed=0 and total_fixed=0. No successful-hit, block or avoidance
  result flag was present. Callback-scoped player HP stays2025233 fixed.
- Frame768321 executes the actual end callback RVA0x33680 and returns NU.

This explains this particular zero-HP-loss attack as an unsuccessful combat
result at settlement, rather than a missing callback. The reference melee
path copies a previously allocated combat descriptor; native hit-roll inputs,
chance computation and RNG branch were not intercepted here. Do not infer
why the roll failed or extrapolate to the two earlier unbracketed attacks.

All three new runs confirmed owned fixture removal, restored code patches,
cleanup and detach. User saves were not edited. No foreground window, game
relaunch or new UAC was required. Final report: fallen-attack-damage-native.json,
with frames/damage trace SHA256 and explicit intervention. 29 Python tests
pass, covering identities, paired settlement returns, callback coverage,
success/block/unsuccessful flag separation and finite profile validation.
No riiablo gameplay change or same-seed hit/damage parity claim is made.
Next comparison should capture the original hit roll inputs/result at combat
allocation and replay them through riiablo's melee resolver.

## Native hit-roll replay (2026-11-18)

The first hit probe was placed at mode entry. Run 20261118-020835-9b504755
completed safely but captured zero stages; its zero-filled fields are NOT
hit inputs. The probe now spans the actual mode callback instead. Temporary
one-shot handler state is cleared on scope exit, including exceptions.

Run 20261118-021209-ab4afca5 captured all five hit stages inside the real
attack callback: observer137 -> player1, effective AR8, defense6,
levels1/99, integer AR factor57, final chance5, draw53, hit=false.
Seed before=(1048432921,1086237184), after=(813998653,437293371).
This time the failed random hit comparison is directly observed, rather
than inferred from settlement flags. All121 samples, fixture removal,
patch cleanup and debugger detach passed. The existing elevated broker
was reused without foreground interaction or another UAC prompt.

Raw source is attack-hit-roll.jsonl in that run. The derived report is
fallen-attack-hit-roll-native.json; the CSV and SHA256 provenance manifest
are core/src/test/resources/native/fallen-attack-hit-roll.*.
NativeMeleeHitReplayTest replays the recorded effective inputs through
production NativeCombatMath, NativeUnitRandom and NativeAttackResolver,
checking final chance, draw, both post-draw seed words and hit decision.
Python analysis rejects partial stages, wrong identities, invalid bounds
and contradictory hit results; an empty trace is explicitly zero samples.

Scope: a single controlled A1 miss at the minimum hit chance. This does
not establish upstream stat derivation, unclamped chance arithmetic across
all inputs, successful damage, block/avoidance, full combat ECS wiring,
natural AI decisions or client rendering. Next useful coverage is a native
successful hit with an unclamped chance and the subsequent damage draws.

## Successful physical damage checkpoints (2026-11-18)

Added finite attack-repeat profile:64 direct native A1 actions, one every16
ticks,1040 total updates. Pending AI think is deleted after each explicit
action. Corpse death is delayed to1030. No hit RNG, combat stats or save
files are changed. This is repeated controlled action evidence, not natural
AI decisions. intervention.json declares the final entry and64 repetitions.

Run20261118-071502-dc75a092 collected64 hit checks (4 successes),1041
samples and successful cleanup/detach, but the broker marked it failed:
the Python validator only allowed121/601 rows. Added explicit1041 support
and the repeat intervention boundary; retained the original failure result.
Run20261118-071643-1c0ce16f passed and captured physical draw checkpoints.
It contains a doubled462->924 damage example, but no critical probe yet;
do not call that a directly captured critical roll.
Run20261118-071755-29011e43 added base attacker stat snapshots and callback
physical defense stats; passed. It did not yet capture critical draws.

Final run20261118-071918-e41b0f57 passed,1041 samples, fixtures removed,
patch cleanup and detach confirmed.64 complete hit checks:3 hits/61 misses.
Each success has matching physical, monster-critical, settlement and HP
records. Physical endpoints are[256,512), source scale128; draws44,161,135
produce300,417,391 fixed damage. Monster critical chance5, draws88,29,56:
none critical. Defender flat physical reduction/resistance are both0.
Settlement and callback-scoped HP loss match all three fixed values.

New guarded probes:physical range before draw RVA8E715, return8E72B;
monster-critical before draw32EC9 and after draw32EEE. DLL SHA256 and
instruction signatures protect all addresses. These are local debugger
observations, not REA Evidence. All work reused broker24896; no new UAC or
foreground interaction was required.

Report:fallen-attack-success-native.json, raw source hashes included.
Fixtures:core/src/test/resources/native/fallen-attack-hit-roll-repeat.csv
and fallen-attack-physical-roll.csv, each with SHA256 provenance manifest.
NativeMeleeDamageReplayTest checks all64 decisions, physical range draws,
production NativePhysicalDamageMath from recorded base stats, independently
checkpointed monster-critical draws, NativeMitigationMath and actual HP
loss.13 focused Java tests and31 Python tests pass.

Important limit:the physical post-seed and critical pre-seed differ by
EXACTLY ONE additional RNG advance in all three successes. That intervening
consumer has not been traced. Tests deliberately restore the critical
checkpoint; they do NOT demonstrate an uninterrupted production combat
stream. No runtime gameplay change is justified yet. Next priority is to
identify this intervening native consumer and compare the equivalent host
path before claiming same-seed end-to-end parity. Nonzero mitigation and a
directly captured successful monster-critical branch also remain untested.

## Located and repaired the intervening RNG consumer (2026-11-18)

Run20261118-084928-541fd1fc reused the resident broker and hidden original
session. It completed1041 contiguous samples,64 attack checks,5 successful
hits; fixtures removed, patches restored and debugger detached. Between
physical return and MonStats.Crit, bounded probes of native random helpers
captured exactly one fixture-seed call in each success:
- helper RVA0x1E4D0, caller return RVA0x8F1AF;
- bound1, result0, with both seed words updated;
- input seeds equal physical post-seeds; output seeds equal critical pre-seeds.

Local PE/Capstone inspection identifies call0x8F1AA in
SUNITDMG_FillDamageValues' unfinished burn branch. Constants316 and317
produce a range of1. The supplied D2MOO reference leaves this in a TODO
comment, explaining why following the reference missed its RNG side effect.
These are debugger and local binary observations, not REA Evidence.
Raw attack-intermediate-rng.jsonl includes function/caller RVAs, seeds,
return value and bounded stack words; no inferred symbolic stack is claimed.
Report:fallen-burn-draw-native.json. CSV/hash provenance:
core/src/test/resources/native/fallen-attack-intermediate-rng.*.

Production NativeAttackResolver now calls
NativeCombatRolls.consumeMeleeBurnPlaceholder immediately before monster
critical on the existing audited native source-stat melee path, excluding
missiles. It consumes a bound1 attacker draw; it does not implement burn
settlement or add burn damage. Missed/blocked attacks return before it.

Added nativeMeleeResolverPreservesStreamFromHitThroughDamageAndMonsterCritical:
the previous3 captured successes now run through the production resolver
from the original hit seed, without restoring intermediate checkpoints.
Before correction this failed on the final seed:the host was one draw behind.
The test also compares hit roll, physical damage and native HP loss.
A second regression replays all5 new bound1 draws and both seed words.
Updated two existing source-order tests to include the measured burn draw.

Scope remains ordinary Fallen A1 against this stationary CXSorc fixture.
This closes the measured hit-through-monster-critical RNG gap, not every
random consumer after critical, natural AI decisions or all monster skills.
Burn damage settlement and nonzero physical mitigation remain unverified.
Final verification for this correction:313 server-domain regressions,
106 explicit monsterCombatParityTest audit cases,15 focused core replay/RNG
cases and32 Python observations tests all passed. Native x86 collector
recompiled with /W4 successfully. Logs:fallen-burn-draw-before.log proves
the failing seed regression;fallen-burn-draw-final.log records the passing
combined run. No foreground interaction or new UAC was required.

## Cross-monster autonomous runtime chain (2026-11-18)

Added MultiMonsterRuntimeAttackChainTest for Zombie, Skeleton, Fallen,
CorruptRogue, Goatman and Brute. Each scenario uses the production
ServerGameplayContext, real AnimDataResolver/SequenceHandler/AnimStepper,
autonomous AI updates and native attack keyframes; no injected keyframes or
manual Actioneer calls. Every monster reached an attack marker, emitted a
melee attempt, produced a successful hit and exactly one authoritative
DamageEvent whose value equaled the player's HP loss. Unit nativeSeed was
initialized for every run. Six parameterized tests passed together with the
four Fallen runtime-chain tests.

The first draft asserted Casting was cleared immediately after the first
marker and failed for all six monsters; that assertion was removed because
D2 keeps the attack animation/casting component alive through its end phase.
The actual parity contract is the marker->attempt->single packet->HP chain.
