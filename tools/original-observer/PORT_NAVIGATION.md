# Using riiablo terrain to navigate the original game

The port exports its production player-terrain collision grid at footprint 2,
without moving-unit occupancy. The original census walks the resulting cell
paths through ordinary window-local mouse messages. A port route is a hint:
the original player's position and current RoomEx determine actual coverage.

## Map identity gate

Before sending navigation clicks, `Navigation.bind` requires the same unsigned
map seed, difficulty, level, dimensions, every local RoomEx rectangle, preset
definitions, and each observed selected preset-file variant. Both maps may
have different world origins. Pathfinding uses local subtiles and converts
back through the original level origin. Room count alone is insufficient.

For the current Cold Plains fixture the seed is **127897359**, difficulty **0**.
The original level origin is `(4600,4920)` and the riiablo origin `(360,-640)`.
These offsets are checked against a live original layout on every batch.
Changing seed requires regenerating the port plan; a mismatch stops the census
instead of falling back to guessed room-center clicks.

## Generate the plan

Use JDK 17 with this repository's Gradle wrapper. Choose a new output directory
for each export because the existing map tour refuses to overwrite logs.

```powershell
./gradlew.bat :server:d2gs:headlessMapTour `
  '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  -PtourSeed=127897359 -PtourDifficulty=0 -PtourLevels=3 `
  -PtourFirstAct=1 -PtourLastAct=1 -PtourObjectLoads `
  -PtourNavigationExport -PtourOutput=server/d2gs/build/navigation-cold-plains-20261116

python tools/original-observer/port_navigation.py `
  server/d2gs/build/navigation-cold-plains-20261116/scene-level-3.txt `
  tools/original-observer/plans/cold-plains-seed127897359.json
```

The NAV_ROW records contain half-open free spans in local coordinates. The
Python planner builds an eight-neighbour cell graph without cutting blocked
corners, searches for an unvisited room interior and emits short waypoints
that stop at bends. Original runtime blockers are not removed.

## Run through the existing broker

```powershell
python tools/original-observer/broker_client.py submit --op run_job `
  --job census-cold-plains-port-navigation
```

This job enters Cold Plains with CensusSorc and runs a bounded 240-second
census. Its `navigation_plan` field names a JSON file under `plans/`; arbitrary
paths are rejected. The current resident broker reloads the job contracts and
starts the new collector without another UAC prompt.

After that probe, `census-cold-plains-port-navigation-full` reuses the same
character and currently open level, checks map identity again, and traverses
for up to 900 seconds. Its room-visit union starts afresh, so its coverage can
be interpreted independently of the earlier probe. It does not restart the
original map or change its seed.

Artifacts include the live original layout, `navigation-identity.json`, and
`room-census.json`. A stalled click is recorded as a rejected navigation hint,
not proof that a whole original room is impassable. Exhausting the port graph
is a partial result. Observed type-1 runtime records are not a proven whole-map
enemy population, and this flow does not assert pixel or collision parity.

## Continue the same live census

`resume_census` names a JSON checkpoint under `plans/`. It contains the native
PID, map seed, difficulty, every native RoomEx pointer and local rectangle,
visited room pointers, the observed unit-record union, and the source report
path. Before using it, the collector checks the current process, seed,
difficulty and complete native room-instance list. A restarted game must not
reuse this checkpoint even if its map seed is unchanged.

`census-cold-plains-port-navigation-resume` continues the 2026-11-16 900-second
run, which visited 91/96 rooms with 802 navigation clicks, zero recorded
navigation stalls and no errors. Its checkpoint was reconstructed from actual
snapshot room pointers and checked against the final reported coverage. The
continuation rechecks live map identity and preserves these 91 visits; it does
not restart the game. Its JSON reports `rooms_visited_before_resume` and
`visited_room_pointers` so subsequent coverage can be audited directly.

Elapsed times stored on retained unit records belong to their source batch;
they are not a single continuous timeline. All counts remain observed runtime
records, including units created during play, rather than a verified initial
population.

The continuation completed all **96/96 rooms** in 53.015 seconds using 49
additional navigation clicks, with no recorded navigation stalls, navigation
events or errors. Its live identity gate again matched seed 127897359,
difficulty 0, all 96 rectangles and 56 observed preset-file variants. The
combined record union contains 472 type-1 records;
`full_level_population_verified` remains false. Final evidence:
`broker/20261116-014209-3a0c898c/runs/20261116-225758-c42d9f35/orchestrator/process-session/001-riiablo-navigation-cold-plains/room-census.json`.

That run's outer scheduler timed out waiting for its stop acknowledgement;
the census itself had completed. The session status now stores a compact
census summary and evidence path instead of embedding the entire 11 MB
report, and its Windows atomic replacement retries transient sharing errors.
A final check (`20261116-230115-fdb7eba5`) reused PID 31116, validated the
complete checkpoint, confirmed 96/96 rooms with zero additional movement,
and finished with orchestrator `passed`, session `stopped`, broker `completed`.

## Dry Hills result (seed 127897359)

The same workflow reached Act II Dry Hills (level 42). The live identity gate
matched all **95/95 RoomEx rectangles** and 78 selected preset-file variants.
The riiablo population export materialized 128 hostile units across all 95
rooms. The original walk visited all 95 rooms; after GUID de-duplication it
observed 111 hostile units and 302 neutral scorpion units. Ten verified
teleport escape hops were logged from edge-cell dead ends; ordinary navigation
recorded no stalls or errors.

Per-class hostile counts were: original `pantherwoman2=29`, `vulture2=44`,
`slinger5=38`; riiablo `pantherwoman2=53`, `vulture2=37`,
`sandleaper2=38`. This is an observed population difference, not yet a proof
of an initial-population mismatch: the map seed is identical, but the port's
monster selection/population RNG stream is not aligned to the original
game's `dwMonSeed`/RoomEx seed consumption. The comparison therefore flags a
likely monster-roster/RNG defect while keeping `exact_initial_population_verified=false`.

Evidence:
`broker/20261116-014209-3a0c898c/runs/20261116-234042-7ad05c97/orchestrator/process-session/001-riiablo-navigation-dry-hills/room-census.json`
and `dry-hills-population-comparison.json` in the same directory. The full
riiablo population source is
`server/d2gs/build/population-dry-hills-detail-20261116/scene-level-42.txt`.
