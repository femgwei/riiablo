# Cold Enchanted death nova

Evidence in local D2MOO source:

- `MonsterUnique.cpp:1666`: MonUMod 18 requires unique quality and DEATH;
  schedules MONUMOD at game frame + 4. This is not a nonlethal struck effect.
- `MonsterUnique.cpp:1202`: uses `coldunique`, level max(monsterLevel / 2, 1),
  velocity from the level-zero `frostnova` missile row.
- `Skills.cpp:3203 sub_6FD14170`: emits 64 missiles at the listed native radial
  offsets; nSkill is zero, so the missile table owns damage.

Implementation adds a cold callback to the existing four-frame death queue.
Fire and cold flags schedule independently, allowing both effects on a monster
with both affixes. It retains immutable NativePathIdentity tokens and claims
duplicate deaths separately. The cold burst uses the native 64 directions,
table damage snapshots, frostnova velocity and a shared nova hit-target set,
consistent with the existing ordinary nova adapter.

Regression scope: four-frame delay, duplicate suppression, identity removal,
64 coldunique missiles, half-level initialization, positive cold damage and
cold duration, and frostnova velocity. Existing fire scheduling tests rerun.

Follow-up collision regression uses the real MissileCollisionSystem and
StateUpdater with a dead source (zero HP), a level-20 monster and a player at
the emission point. At 0% cold resistance the packet causes 44..46 life loss
and installs COLD for 300 frames. At 50% it causes 22..23 life loss and installs
COLD for 150 frames. All 64 overlapping missiles produce one damage packet;
three further ticks do not add another packet. Both scenarios passed, along
with the previous missile-generation test. No production change was required.

Limits: the collision fixture does not integrate movement or use a retail game
oracle. Native overlap/NextDelay equivalence, fixed-point precision,
slow-missile interaction, movement-speed reduction and in-game presentation
remain unverified. It confirms the current shared hit gate works, not that the
gate exactly reproduces all native overlap cases. Deleted-source attribution
also remains unaudited; a dead but retained source is covered.
