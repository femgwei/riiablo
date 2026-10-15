# Nova native mitigation order — PARTIAL

Base: `0fd5b9701c9d9d11724fbab0d6d8690a57356d01`.
The archived source snapshot overlays only CombatSystem and the two Nova test files.
Gradle cache, build directories and JavaCompile/source-set destinations are isolated.

## Reference and resulting behavior

Reference: `F:/3rd_src/D2MOO/source/D2Game/src/UNIT/SUnitDmg.cpp`.
`SUNITDMG_CalculateTotalDamage` applies the 17% player-v-player scalar before
calling resistance/absorb rows (approximately lines 819–876 and 927).
`SUNITDMG_ApplyResistancesAndAbsorb`, lines 939–1060, checks monster immunity before
pierce, caps non-monster resistance at the raised maximum (at most 95), clamps
negative resistance at -100, subtracts MDR before resistance, then applies percent
absorb capped at 40 and flat absorb in 8.8 units.

The opted-in Nova lightning packet now follows that order, retains the 1/256 HP
truncation at each percentage operation and uses wide intermediate products.
Ordinary monsters retain 90% resistance instead of using the player cap; >=100%
monster immunity prevents passive pierce from breaking immunity. Pierce above
100 is no longer prematurely capped for this packet. DefenderData's default
elemental maximums are explicitly 75, so the native packet can distinguish an
explicit zero maximum from the previous zero-initialized unset value.
Legacy integer attacks and other elemental channels retain their ordering.

## Evidence

Old production plus the new tests: 19 tests, 6 failures, saved in `baseline/`
and `baseline.log`. These are five packet failures (MDR ordering, PvP ordering,
monster resistance cap, absorb cap and >100 pierce) and one ECS collision failure.
The ECS case expected 2.71875 HP but old production applied 0.80859375 HP.
For the PvP case the independently calculated packet is:
2560 * 17 / 100 = 435; MDR -256 = 179; 50% resistance = 89;
25% absorb = 22; final damage = 67/256, absorbed life = 22/256.

The ECS fixture casts actual Nova radial missiles and uses an ordinary target's
MDR and absorb attributes. It compares the collision HP loss and lightning event
against the no-resistance sample's fixed-point roll, keeps the immune second
target, requires two shared claims and checks that later overlapping paths do
not replay damage. The existing 64 paired seeds and RNG restoration remain.
The target starts at max HP, so absorption healing clamps before damage; this
test certifies the damage/absorb calculation, not low-life healing behavior.

`first/` is the preliminary repaired pass. Final source includes only a subsequent
comment/parenthesis cleanup; `final/` and `repeat/` are the final-source passes.
`verify.ps1` checks suite counts, 64 sample equality and source SHA256 equality.
Both final-source runs passed 239 tests with zero failures, errors or skips.
All 64 paired samples agree, and all three changed implementation/test files
match the isolated snapshot by SHA256. Full results are in `verification.log`.
Run `prepare-source.ps1 -Repaired` with a fresh source root and then
`run-tests.ps1 -SourceRoot <root> -Pass <unused-name>` twice to reproduce.
For the behavioral baseline omit `-Repaired` and select only the two Nova test
classes with the absolute init-script path.

## Limits

This is a normal player/ordinary-monster Nova packet correction, not full damage
parity. Native per-entity RNG, fractional upstream skill/mastery bounds, hireling
classification and boss/revive scalars, bypass flags, expansion/classic difficulty
selection, zero-damage/full-absorb healing, other skills and networking/rendering
remain outside acceptance. Existing difficulty/state data extraction is reused;
this batch adds no three-difficulty or state-change acceptance claim.
No new natural AI arena run, monster probability change or forced natural skill
event is claimed. Arena unit tests are included only for regression coverage.
