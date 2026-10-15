# Fixed elemental rate life boundaries — PARTIAL

The base SHA is recorded in source-base-sha.txt. A git-archive snapshot overlays
only MissileCollisionSystem and SorceressFireAreaIntegrationTest. Unrelated
working-tree path-port changes were excluded. Build/cache/JavaCompile/source-set
outputs are isolated. prepare-source.ps1 reconstructs either old production with
the new tests, or the repaired snapshot with -Repaired.

Reference: D2MOO D2Game/src/UNIT/SUnitDmg.cpp:1292–1308. Absorbed life excludes
DEATH_DELAY; positive damage snaps remaining life below 256 fixed units to zero.
The fixed elemental rate collision entry now applies those same guards. Its RNG,
mitigation, event and existing max-life clamping behavior are unchanged.

Six controlled ECS cases use a real Inferno missile with its rate bounds fixed
to 256 for deterministic boundary coverage. They check 255/256 remaining HP,
exactly 1 HP, full absorb, maximum-life clamp, DEATH_DELAY and zero damage on a
fractional-life target. Death counts are checked. These are controlled fixtures,
not an assertion that native skill damage always rolls one HP.

baseline.log/xml: 10 tests, 3 expected failures, no errors/skips. Old code leaves
0.99609375 HP alive and heals DEATH_DELAY targets from 50 to 51 and 0.5 to 1.5.
Use the absolute init-script path and select SorceressFireAreaIntegrationTest to
reproduce the baseline. run-tests.ps1 runs the combined skill/arena regression
plus FireWall/Blaze golden tests and Firestorm. final/ and repeat/ preserve two
independent test invocations; verification.log checks counts and source hashes.

Scope remains PARTIAL: other direct/area resolvers, full native callbacks/death
modes, native entity RNG parity across all skills, hireling/boss scalars and
client/network behavior remain outside acceptance. No natural AI run or monster
probability change is claimed. Arena tests are regression coverage only.

Final result: both runs passed 266 tests with zero failures, errors or skips.
The existing 64 Nova sample pairs agree between runs; both changed files match
the isolated tested snapshot by SHA256. See verification.log for the checks.
