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
That result-flag boundary is not exposed by the current damage event, so this
batch does not implement that soft-hit branch. Also unverified:
nLastAnimMode flag behavior, exact native EVENT replacement policy, charged-bolt
path initialization and missile damage/collision remain unverified in this batch.
Scarab still has its earlier damage-event adapter and needs separate repair.
This does not establish complete lightning-enchanted equivalence or live-client
GH animation acceptance.
