# Ordinary missile sub-1-HP death boundary — PARTIAL

Base: `6bdabb0d616fd0057474f62bcb67f917367264e6`.
An archived source snapshot overlays only MissileCollisionSystem and the Nova
integration test. Project cache, build directories, JavaCompile destinations and
source-set outputs are isolated using this directory's Gradle init script.

## Native reference and change

`F:/3rd_src/D2MOO/source/D2Game/src/UNIT/SUnitDmg.cpp:1292–1308` restores absorbed
life first. When total damage is positive, it subtracts that packet, then sets
remaining HP below 256 fixed-point units (1 HP) to zero. Exactly 1 HP survives.
The zero-damage path does not perform this snap.

Ordinary missile collisions now apply this threshold after absorb healing and
after the damage event's applied damage is resolved. The existing negative-HP
clamp is retained; a canceled event with zero applied damage cannot invoke the
new threshold. The existing death/shatter/event path handles the resulting zero
HP. This common path also serves integer missile packets and does not change
their mitigation formulas. Impact logs capture life immediately before damage,
instead of reconstructing it by adding packet damage to post-clamp life, which
would conceal the discarded fractional remainder.

DamageEvent continues to carry the resolved damage packet. The fractional
remainder discarded by the native death threshold is not added to that packet.
Therefore event damage and final HP delta need not be equal on threshold deaths;
the DeathEvent and HP zeroing carry the death decision.

## Tests and reproduction

Five new ECS cases check positive damage leaving 255/256 HP, exactly 1 HP and
257/256 HP, partial absorb leaving less than 1 HP after healing and damage, and
a zero-damage DEATH_DELAY target remaining at 0.5 HP. The existing ordinary and
absorb collision fixture now records death counts before and after six extra
ticks, requiring exactly one death for lethal packets and none for survivors.
It retains immune-target protection, shared hit claims, damage channel checks,
the original 64 resistance sample pairs and caller RNG restoration.

Against old production, the final Nova class has 21 tests and two failures:
the sub-1-HP direct and partial-absorb cases fail to emit their expected death.
Exactly-1-HP and zero-damage controls pass. Evidence is `baseline.log/xml`.

Use `prepare-source.ps1` with a fresh source root. Without `-Repaired`, run only
`:core:test --tests '*SorceressNovaIntegrationTest'` using the absolute init path.
With `-Repaired`, use `run-tests.ps1` twice with unused pass names. `verify.ps1`
checks test counts, 64 paired sample equality and SHA256 equality of both changed
implementation/test files. The test runner refuses to overwrite pass evidence.

## Limits

This verifies the ordinary missile life/death boundary using controlled Nova ECS
fixtures, not natural monster AI or complete native death handling. Independent
area/rate/direct-hit resolvers, native entity RNG, upstream skill/mastery
precision, mercenary/boss classification and scalars, classic/expansion selection,
all native zero-damage callbacks, full death-mode parity, clients and networking
remain outside acceptance. No new natural arena run or monster probability change
is claimed; the arena unit suite supplies regression coverage only.

Final verification: both final-source runs passed 251 tests with zero failures,
errors or skips. All 64 paired samples agree, and SHA256 checks confirm both
changed implementation/test files match the tested snapshot. Full records are
in `verification.log` and the `final/` and `repeat/` XML reports.
