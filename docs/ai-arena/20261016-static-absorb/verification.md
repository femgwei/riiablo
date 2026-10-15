# Static Field delayed-death absorb — PARTIAL

Base SHA is in source-base-sha.txt. Isolated git-archive source overlays only
ServerSkillSystem.java and SorceressStaticFieldIntegrationTest.java. Unrelated
path-port changes are excluded; cache/build/JavaCompile/source-set outputs are
isolated by the init script.

Reference: D2MOO D2Game/src/UNIT/SUnitDmg.cpp:1292 excludes DEATH_DELAY from
absorbed-life restoration. Static Field now checks that state before restoring
life, including fully absorbed packets. Its damage, difficulty floor and native
last-life gate remain unchanged. A delayed target can still take remaining damage.

Three real Static Field ECS fixtures use 50 HP and maxhp 100. Raw damage is
12 HP. Full absorb must leave a delayed target at 50 HP, partial 50% absorb
must leave 44 HP without healing, and a normal fully absorbing target heals to
62 HP. Existing Static Field and golden damage tests are included in the combined
regression runner along with prior fire/Nova/curse/aura/arena coverage.

baseline.log/xml preserve the new tests against old production. final/ and repeat/
preserve repaired runs. The runner refuses evidence overwrites. Reproduce by
archiving the recorded base to a fresh source root, overlaying the test only for
baseline or both files for repair, and using the absolute init-script path with
D2_HOME set to the local 1.10F MPQ directory. No natural AI run is claimed.

Scope remains PARTIAL: other direct skills and resolvers, native per-entity RNG,
full death-mode/callback parity, mercenary/boss scalars and client/network behavior
are outside this correction. Existing Static Field mitigation formula differences
are not certified by these absorb-state tests.

Final verification: both runs passed 276 tests with zero failures, errors or
skips. The 64 existing Nova sample pairs match across runs, and both modified
source/test files match the isolated snapshot by SHA256. See verification.log.
The behavioral baseline has 8 tests and 2 expected failures with zero errors.
