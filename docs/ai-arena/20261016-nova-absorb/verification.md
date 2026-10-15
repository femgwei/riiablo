# Fully absorbed missile life restoration — PARTIAL

Base: `68497588a1a3ec6511fc0778ca16758613e1e126`.
An archived source snapshot overlays only MissileCollisionSystem and the Nova
integration test. Gradle cache, build directories, JavaCompile destinations and
source-set outputs are isolated.

## Native reference and implementation

`F:/3rd_src/D2MOO/source/D2Game/src/UNIT/SUnitDmg.cpp:1292–1308` restores absorbed
life before testing whether total damage is positive. It clamps to maximum life
and excludes dead/DEATH_DELAY defenders. Therefore a fully absorbed hit still
heals a living target, and partial absorb may save a low-life target before the
remaining damage is deducted.

The common ordinary missile collision path now handles absorbed life even when
immediate damage is zero. Both zero- and positive-damage paths share a helper
with positive-HP and DEATH_DELAY guards, max-HP clamping, packet-unit conversion
and the existing missile multiplier. Positive packets retain event dispatch before
healing, then subtract damage. Zero packets restore life without manufacturing a
DamageEvent/pain reaction. Shared Nova target claims still prevent replay.
This common-path fix also applies to integer missile packets; their mitigation
formulas remain unchanged. Separate fixed-area/direct-hit resolvers are unchanged.

## Tests and reproduction

Seven new ECS tests cover fractional full absorb, max-HP clamping, partial absorb
saving an otherwise lethal target, clamping healing before damage, full and
partial absorb during DEATH_DELAY, and zero-HP targets. They cast actual Nova
missiles with ordinary target absorb stats, run six collision ticks, check the
immune second target and shared claims, then run six further ticks to reject
repeated healing/events. Zero packets emit zero DamageEvents; positive packets
emit exactly one with the expected lightning/total damage. Test RNG is restored
in finally blocks. The original 64 paired resistance seeds remain intact.

`preliminary-baseline.log/xml` capture the first 15-test baseline with two
full-absorb failures. A positive-damage DEATH_DELAY regression was then added;
`baseline.log/xml` record the complete final test class against old production.
That final baseline has 16 tests and 3 failures, with zero errors/skips.
Expected full absorb restores 50 HP to 60.0625 HP, while old code leaves 50 HP;
near max HP should restore 995 to 1000 but remains 995. The partial DEATH_DELAY
case must deduct damage without adding absorbed life.

Use `prepare-source.ps1` with a fresh source root. Without `-Repaired`, run only
`:core:test --tests '*SorceressNovaIntegrationTest'` with the absolute init-script
path for the failing baseline. With `-Repaired`, run `run-tests.ps1` twice with
unused pass names. `verify.ps1` checks counts, sample reproducibility and source
hashes. Evidence is write-once. No natural monster AI run is claimed; the arena
unit suite is regression coverage only.

## Remaining limits

The absorption calculation and per-skill mitigation remain subject to each
resolver's existing implementation. Native entity RNG, upstream skill/mastery
precision, mercenary/boss classification and scalars, classic/expansion selection,
separate rate/direct resolvers, all zero-damage native callbacks, client/network
behavior and full death-mode parity remain outside this acceptance. In particular
the native rule reducing positive-damage survivors below 1 HP to zero is separate
from absorption and is not changed here. No monster probabilities or collision
gates were disabled, and the ECS tests are controlled skill fixtures.

Final verification: both final-source runs passed 246 tests with zero failures,
errors or skips. The 64 paired samples agree across runs; SHA256 checks confirm
both changed implementation/test files match the tested source snapshot.
See `verification.log` and the `final/` and `repeat/` XML reports.
