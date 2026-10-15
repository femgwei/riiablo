# Telekinesis delayed-death absorb — PARTIAL

Base SHA is recorded in source-base-sha.txt. The git-archive snapshot overlays
only ServerSkillSystem.java and SorceressTelekinesisIntegrationTest.java. Other
working-tree changes are excluded. Gradle cache/build/JavaCompile/source-set
outputs are isolated. prepare-source.ps1 recreates the snapshot with tests only
for baseline, or both changed files with -Repaired.

D2MOO D2Game/src/UNIT/SUnitDmg.cpp:1292 excludes DEATH_DELAY from absorbed-life
restoration. Telekinesis now honors that exclusion, including fully absorbed
hits. Remaining damage is still applied; ordinary targets still heal. The
existing mitigation/RNG/targeting/event/death rules remain unchanged.

Three new actual-skill ECS fixtures use level-20 Telekinesis (20 lightning HP),
target life 50/max 100, 50% absorb and optional flat absorb 100. Delayed full
absorb must leave 50 HP and emit zero DamageEvents. Delayed partial absorb must
leave 40 HP and emit one 10-HP lightning event. Normal full absorb must heal to
70 HP and emit no damage event. Existing target filtering/PvP/mastery tests and
Telekinesis golden damage tests run in the combined regression suite.

baseline.log/xml preserve old production with the new tests. final/ and repeat/
preserve independent repaired runs; verify.ps1 checks counts, original 64 Nova
sample equality and both changed-file SHA256 hashes. Evidence cannot be
overwritten. For baseline select SorceressTelekinesisIntegrationTest with the
absolute init-script path; for repair use run-tests.ps1 twice with unused names.

Scope remains PARTIAL: Telekinesis integer mitigation/order, native entity RNG,
fractional skill bounds, its sub-1-HP death boundary and other direct skills are
not certified by this fix. No natural AI, client/network or full death-mode
acceptance is claimed. No monster probabilities or collision gates were changed.

Final result: both repaired runs passed 284 tests, zero failures/errors/skips.
The baseline has 6 tests and 2 expected failures. All 64 existing Nova sample
pairs match between runs. SHA256 confirms both modified files match the tested
snapshot; verification.log records these checks.
