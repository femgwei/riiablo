# Vulture approach boundary — 2026-10-18

The user reports approaching the player, stopping short, animating in place for several seconds, then attacking in a normal map. The supplied game.log does not contain flight state or frame samples, so it cannot establish which state caused that particular pause.

Reproduced a separate concrete approach defect: source (83, -45.9), target (86.99, -45.03), both size 2. The source is outside live melee range. Before the fix walkInRadius requests (83, -45), leaving the monster short of melee range. The regression fails against the old code (before.xml). With the fix it requests (85, -45), inside melee range.

walkInRadius now uses the full source-size coordinate distance from D2MOO AITACTICS_MoveInRadiusToTarget / AIUTIL_GetDistanceToCoordinates_FullUnitSize, rather than the combat distance table, and rounds host map coordinates consistently with the live melee predicate. A second regression distinguishes the two distance formulas. No attack range or decision probability changed.

Added rate-limited VULTURE_APPROACH samples containing game frame, positions, melee status, flight countdown, waypoint, landing status, path and action state. Compilation and focused tests pass; emission in the user's normal-map runtime remains to be checked. The arena's event logs do not capture these logger samples.

Native airborne behavior also permits a delay: when close enough, each eligible 12-frame decision has a 15% chance to land early. Several seconds can therefore occur without a fixed timer. Do not treat the distance reproduction as proof that this native wait caused or explains the observed gameplay event.

Validation:

- Distance fix: 72 Vulture/Scarab/Baboon tests, zero failures/errors (tests-72.zip).
- After logging addition: 12 Vulture tests, zero failures/errors (tests-final-12.zip).
- Four variants, seed 1, difficulty 0, 1600 ticks: all four flight-cycle scenes record natural takeoff, landing, and post-landing damage; logs included. Four near scenes observe actions; four far scenes record movement only.
- Gradle reports BUILD SUCCESSFUL. These are focused tests and synthetic arena scenes, not a manual normal-map reproduction or full native-equivalence certification.

Command: `./gradlew.bat -I build/ai-vulture-boundary.init.gradle :core:test --tests '*VultureApproachBoundaryTest' --tests '*VultureFlightTest' :server:d2gs:monsterAiArena -ParenaOutput=build/vulture-boundary-trace-20261018 -ParenaMonsters=vulture1,vulture2,vulture3,vulture4 -ParenaTicks=1600 -ParenaSeed=1 -ParenaDifficulty=0 --max-workers=1 --console=plain` with D2_HOME set to the local 1.10F assets. The 72-test run additionally selects `*Scarab*Test` and `*Baboon*Test`.
