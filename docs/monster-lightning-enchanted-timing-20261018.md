# Lightning Enchanted struck callback timing

D2MOO MonsterUnique.cpp:1657 (LightningEnchantedModeChange) requires unique
quality and MONMODE_GETHIT, then schedules MONUMOD for frame + 2.
CastLightUniqueMissile at :1135 compares game frame against dwDurielFlag and
skips when the absolute difference is below 10. On release it records the frame
and produces eight lightunique missiles at max(monsterLevel / 2, 1).

The earlier adapter emitted immediately on any positive DamageEvent and shared
a two-frame cooldown with Scarab. That LE branch is removed. ModeChangeEvent GH
now schedules the deferred callback with an immutable lifetime token; duplicate
pending callbacks on the same identity are coalesced. Release applies an
independent ten-frame interval. First release before game frame 10 is suppressed,
matching the zero-initialized native last-frame field.

ServerSkillSystem now processes each simulation tick so its elapsed-frame clock
keeps advancing during idle intervals. Existing cast-delay maps still decrement
in the same processSystem implementation. No ordinary AI think poll is involved.

Regression covers non-GH modes, two-frame delay and releases below/exactly at
the ten-frame boundary. Fire/cold death tests are rerun for shared queue changes.

Limits: native callback index 4 (sub_6FC6CD30) also releases immediately for
GETHIT/SOFTHIT result paths when the actual mode is not GH (SUnitDmg.cpp:2128-2144).
Follow-up: MonsterSoftHitEvent now connects Actioneer's active-casting reaction
suppression and MissileCollisionSystem's started-sequence reaction suppression.
It is dispatched at the reaction decision, not from arbitrary positive damage.
The LE subscriber skips GH/death/non-unique units and immediately runs the same
ten-frame release guard. Blocked packets with suppressed block animation are
excluded before they can fall into this soft reaction path.

Timing regression verifies immediate soft release, interval suppression,
GH duplicate suppression, death exclusion and non-unique exclusion. This tests
the event consumer; production near-melee and missile reaction producers still
need end-to-end scenario coverage.

The adapter's active-action suppression policy is not a complete translation of
native damage result flags or sub_6FCC1870. No claim of full native SOFTHIT
classification is made. Also unverified:
nLastAnimMode flag behavior, exact native EVENT replacement policy, charged-bolt
path initialization and missile damage/collision remain unverified in this batch.
Scarab still has its earlier damage-event adapter and needs separate repair.
This does not establish complete lightning-enchanted equivalence or live-client
GH animation acceptance.

## Missile table flag follow-up

MissMode.cpp:4690-4698 gives GetHit precedence over SoftHit and suppresses
both for STATE_UNINTERRUPTABLE. Successful surviving ordinary missile hits now
read those flags before requesting a reaction. SoftHit preserves the current
mode, sets monster AI state 19, and dispatches the immediate callback; neither
flag requests no reaction. Death modes are excluded.

LightningEnchantedTimingTest exercises the production missile reaction method
through EventSystem into the LE consumer, including neither-flag and death
exclusion and action preservation. This is a reaction-path test, not a complete
missile collision scenario. Block/dodge flag rewriting in MissMode.cpp:4707,
GETHIT recovery thresholds/RNG in sub_6FCC1870, and melee classification remain
pending. The previous active-action approximation still applies to GetHit.
