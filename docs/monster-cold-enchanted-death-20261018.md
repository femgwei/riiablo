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

Limits: these tests do not establish actual cold collision/life-loss/chill,
retail target overlap and NextDelay parity, fixed-point precision, slow-missile
interaction or in-game presentation. Native deleted-source attribution and
the shared hit-target-set policy still need collision-level acceptance.
