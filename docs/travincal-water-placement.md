# Travincal pool placement follow-up (2026-10-31)

## Evidence

- User `game.log`, seed `127897359`, level 83: room 42 runs random object
  population, then creates entity 191 / Objects class 186 at `(1773,-2145)`.
  This is local subtile `(133,135)`, not a DS1 chest preset.
- Real 1.10f MPQs select `Act3/Kurast/sets.dt1`, floor style 29 / sequence 2
  at that position. Its material is **0**, not WATER (2). Pool interior
  sequence 12 has WATER; the border/corner sequences do not.
- The exported static collision at `(133,135)` has WALL (1). The four
  native Water Watcher markers also lie on blocked style-29 pool cells.
- `Act3/Kurast/Floors.dt1` style 20 is normal paving. The earlier
  `isTravincalWaterPool` style-20 workaround incorrectly excluded dry ground
  and did not identify the actual pool borders.

## D2MOO reference and scope

`D2Game/OBJECTS/Objects.cpp::OBJECTS_CreateObject` uses
`COLLIDE_MASK_PLACEMENT`, not the DT1 material. `D2Common/D2Collision.cpp`
allocates terrain collision from floor/wall/roof flag arrays. There is no
generic material-based pool exclusion in that object routine.

The follow-up keeps the host's explicit water exclusion, but recognizes
untagged Kurast pool subtiles using the exact style/source and immutable
exported WALL bit. Walkable portions of border tiles remain dry; dynamic
shared-mask resets cannot change this classification. Ordinary paving is no
longer excluded. Random objects check their complete collision footprint.

Death and container drop callers already use `Zone.isWater` / `Map.isWater`,
so they inherit the corrected shoreline classification. Preset objects,
quest objects, water-monster creation and drop RNG are not changed here.

## Verification

`TravincalWaterPlacementResourceTest` loads actual DT1 headers and covers
seeds `127897359`, `1`, `2`: reported chest coordinate, dry paving, wide
footprint, all four Water Watchers, runtime mask clearing, dry drop search,
and random population/re-entry. An explicitly allocated floor layer prevents
an empty headless tile array from silently making every material test false.

Run with `:core:test --tests com.riiablo.map.TravincalWaterPlacementResourceTest
--tests com.riiablo.map.NativeObjectPopulationResourceTest
--tests com.riiablo.engine.server.item.GroundDropPositionTest` and
`-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F`.

Rendered-client verification on the other computer remains required; restart
the game/session after pulling so already-created objects are not reused.
