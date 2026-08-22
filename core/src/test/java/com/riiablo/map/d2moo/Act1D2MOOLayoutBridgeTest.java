package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.D2DrlgOutdoorInfoStrc;
import com.d2moo.common.drlg.D2DrlgOutdoorPackedGrid2InfoStrc;
import com.d2moo.common.drlg.D2DrlgOutdoorRoomStrc;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2DrlgVertexStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2PresetUnit;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import com.riiablo.drlg.TileGrid;

/** Fixed-seed DRLG smoke test and diagnostic report for the Act1 bridge. */
public class Act1D2MOOLayoutBridgeTest extends RiiabloTest {
  private static final int DEFAULT_DIFFICULTY = 0;
  /** Reproduction seed from the town-exit black-map game log. */
  private static final String DEFAULT_SEED = "0x171A6100";

  @Test
  public void fixedSeedAct1LayoutIsStableAndExportable() {
    int seed = Integer.decode(System.getProperty("d2.seed", DEFAULT_SEED));
    int difficulty = Integer.getInteger("d2.difficulty", DEFAULT_DIFFICULTY);
    int burialId = findLevelId("Burial Grounds");
    assertTrue(burialId > 0, "Burial Grounds is missing from Levels.txt");

    Act1D2MOOLayoutBridge.LayoutAndDrlg first =
        Act1D2MOOLayoutBridge.getLayoutAndDrlg(seed, difficulty, burialId);
    assertNotNull(first, "D2MOO Act1 layout failed; inspect ACT1_D2MOO logs");
    assertFixedSeedOutdoorCoverage(first.drlg);
    String firstSummary = summarize(first.drlg);
    System.out.println("[ACT1-DIAG] seed=" + seed + " diff=" + difficulty + " " + firstSummary);
    exportAndReport(first.drlg);
    assertPresetUnitListsAreAcyclic(first.drlg);
    DrlgDrlg.freeDrlg(first.drlg);
    Act1D2MOOLayoutBridge.releaseDataTables();

    Act1D2MOOLayoutBridge.LayoutAndDrlg second =
        Act1D2MOOLayoutBridge.getLayoutAndDrlg(seed, difficulty, burialId);
    assertNotNull(second, "Second fixed-seed generation failed");
    assertFixedSeedOutdoorCoverage(second.drlg);
    assertEquals(firstSummary, summarize(second.drlg),
        "same seed must produce the same Act1 layout summary");
    DrlgDrlg.freeDrlg(second.drlg);
    Act1D2MOOLayoutBridge.releaseDataTables();
  }

  private static int findLevelId(String name) {
    for (Levels.Entry entry : Riiablo.files.Levels) {
      if (entry != null && name.equals(entry.LevelName)) return entry.Id;
    }
    return -1;
  }

  /** Guards the LvlSub wall-grid regression that blanked most outdoor cells. */
  private static void assertFixedSeedOutdoorCoverage(D2DrlgStrc drlg) {
    assertEquals(91, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_STONYFIELD).getRooms(),
        "fixed seed Stony Field native border/LvlSub room count changed");
    assertEquals(96, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_COLDPLAINS).getRooms(),
        "fixed seed Cold Plains native border/LvlSub room count changed");
    assertEquals(80, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR).getRooms(),
        "fixed seed Blood Moor native border/LvlSub room count changed");
    assertEquals(30, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BURIALGROUNDS).getRooms(),
        "fixed seed Burial Grounds native graveyard room count changed");
    assertEquals(94, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLACKMARSH).getRooms(),
        "fixed seed Black Marsh native border/LvlSub room count changed");
    assertEquals(95, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_TAMOEHIGHLAND).getRooms(),
        "fixed seed Tamoe Highland native border/LvlSub room count changed");
    assertBloodMoorNativeLinks(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_STONYFIELD));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_COLDPLAINS));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BURIALGROUNDS));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLACKMARSH));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_TAMOEHIGHLAND));
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_STONYFIELD), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_COLDPLAINS), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLACKMARSH), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_TAMOEHIGHLAND), false);
    assertPresetUnitListsAreAcyclic(drlg);
  }

  private static void assertNativeDirtPaths(D2DrlgLevel level, boolean roomsInitialized) {
    D2DrlgOutdoorInfoStrc outdoors = level.getOutdoors();
    assertNotNull(outdoors, "outdoor data missing for level " + level.getLevelId());
    assertTrue(outdoors.getNVertices() > 0,
        "native dirt path has no endpoints for level " + level.getLevelId());

    int paths = 0;
    for (int i = 0; i < outdoors.getNVertices(); i++) {
      if (outdoors.getPPathStarts(i) != null) paths++;
    }
    assertTrue(paths > 0, "native dirt path search failed for every endpoint in level "
        + level.getLevelId());

    int topologyCells = 0;
    for (int y = 0; y < outdoors.getNGridHeight(); y++) {
      for (int x = 0; x < outdoors.getNGridWidth(); x++) {
        D2DrlgOutdoorPackedGrid2InfoStrc packed =
            new D2DrlgOutdoorPackedGrid2InfoStrc(outdoors.getPGrid(2).getFlag(x, y));
        if (packed.isNUnkb07()) topologyCells++;
      }
    }
    assertTrue(topologyCells > 0,
        "native dirt path did not mark Grid2 topology for level " + level.getLevelId());

    if (!roomsInitialized) return;
    int floorCells = 0;
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      if (!(room.getMazeOrOutdoor() instanceof D2DrlgOutdoorRoomStrc)) continue;
      D2DrlgGridStrc floor =
          ((D2DrlgOutdoorRoomStrc) room.getMazeOrOutdoor()).getPFloorGrid();
      if (floor == null || floor.getPCellsFlags() == null) continue;
      for (int flags : floor.getPCellsFlags()) {
        if ((flags & 0xFF) == 0x82 && ((flags >>> 8) & 0xFF) != 0) floorCells++;
      }
    }
    assertTrue(floorCells > 0,
        "native dirt path did not reach RoomEx floor grids for level " + level.getLevelId());
  }

  private static void assertBloodMoorNativeLinks(D2DrlgLevel bloodMoor) {
    D2DrlgOutdoorInfoStrc outdoors = bloodMoor.getOutdoors();
    assertNotNull(outdoors, "Blood Moor outdoor data missing");
    D2DrlgVertexStrc head = outdoors.getPVertex();
    assertNotNull(head, "Blood Moor boundary vertices missing");

    int flaggedEdges = 0;
    boolean southTownPresetEdge = false;
    D2DrlgVertexStrc vertex = head;
    do {
      if ((vertex.getDwFlags() & 1) != 0) {
        flaggedEdges++;
        if ((vertex.getDwFlags() & 2) != 0
            && vertex.getNPosY() == outdoors.getNGridHeight() - 1) {
          southTownPresetEdge = true;
        }
      }
      vertex = vertex.getPNext();
    } while (vertex != head);
    assertEquals(2, flaggedEdges,
        "Blood Moor must retain both Cold Plains and town boundary edges");
    assertTrue(southTownPresetEdge,
        "Rogue Encampment preset link must remain on Blood Moor's south edge");

    int levelLinks = 0;
    int pickedFile3Links = 0;
    for (int y = 0; y < outdoors.getNGridHeight(); y++) {
      for (int x = 0; x < outdoors.getNGridWidth(); x++) {
        D2DrlgOutdoorPackedGrid2InfoStrc packed =
            new D2DrlgOutdoorPackedGrid2InfoStrc(outdoors.getPGrid(2).getFlag(x, y));
        if (packed.isBLvlLink()) {
          levelLinks++;
          if (packed.getNPickedFile() == 3) pickedFile3Links++;
        }
      }
    }
    assertEquals(1, levelLinks,
        "native preset town edge is flag=3 and must not become a second bLvlLink");
    assertEquals(1, pickedFile3Links,
        "Blood Moor's ordinary outdoor link must select native file variant 3");
  }

  private static void assertPresetUnitListsAreAcyclic(D2DrlgStrc drlg) {
    for (D2DrlgLevel level = drlg.getLevel(); level != null; level = level.getPNextLevel()) {
      for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
          room = room.getDrlgRoomNext()) {
        java.util.IdentityHashMap<D2PresetUnit, Boolean> seen = new java.util.IdentityHashMap<>();
        for (D2PresetUnit unit = room.getPresetUnits(); unit != null; unit = unit.getPNext()) {
          assertTrue(seen.put(unit, Boolean.TRUE) == null,
              "cyclic preset-unit list in level " + level.getLevelId() + " room ("
                  + room.getNTileXPos() + ',' + room.getNTileYPos() + ')');
        }
      }
    }
  }

  /** Level links may use a non-random File4, but must never select a "0" placeholder. */
  private static void assertPresetFileSelectionsResolveToDs1(D2DrlgLevel level) {
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      if (!(room.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) continue;
      D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
      assertNotNull(preset.getPMap(), "preset room has no map");
      assertNotNull(preset.getPMap().getPLvlPrestTxtRecord(), "preset map has no LvlPrest");
      int pickedFile = preset.getPMap().getNPickedFile();
      String path = preset.getPMap().getPLvlPrestTxtRecord().getSzFile(pickedFile);
      assertTrue(path != null && !path.isEmpty() && !"0".equals(path),
          "level " + level.getLevelId() + " preset " + preset.getNLevelPrest()
              + " selected unresolved file " + pickedFile + " path=" + path);
    }
  }

  private static String summarize(D2DrlgStrc drlg) {
    StringBuilder out = new StringBuilder();
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_STONYFIELD,
        D2LevelIds.LEVEL_COLDPLAINS,
        D2LevelIds.LEVEL_BLOODMOOR,
        D2LevelIds.LEVEL_ROGUEENCAMPMENT,
        D2LevelIds.LEVEL_BURIALGROUNDS,
        D2LevelIds.LEVEL_BLACKMARSH,
        D2LevelIds.LEVEL_TAMOEHIGHLAND }) {
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
      assertNotNull(level, "missing D2MOO level " + levelId);
      assertNotNull(level.getLevelCoords(), "missing coordinates for level " + levelId);
      if (levelId != D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
        assertTrue(level.getRooms() > 0, "no rooms generated for level " + levelId);
      }
      out.append(levelId).append(':')
          .append(level.getDrlgType()).append(':')
          .append(level.getLevelCoords().getNPosX()).append(',')
          .append(level.getLevelCoords().getNPosY()).append(',')
          .append(level.getLevelCoords().getNWidth()).append('x')
          .append(level.getLevelCoords().getNHeight()).append(':')
          .append(level.getRooms()).append(';');
    }
    return out.toString();
  }

  private static void exportAndReport(D2DrlgStrc drlg) {
    D2MooTileApplier applier = new D2MooTileApplier();
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_STONYFIELD,
        D2LevelIds.LEVEL_COLDPLAINS,
        D2LevelIds.LEVEL_BLOODMOOR,
        D2LevelIds.LEVEL_BURIALGROUNDS,
        D2LevelIds.LEVEL_BLACKMARSH,
        D2LevelIds.LEVEL_TAMOEHIGHLAND }) {
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
      int width = Math.max(1, level.getLevelCoords().getNWidth());
      int height = Math.max(1, level.getLevelCoords().getNHeight());
      applier.putGrid(levelId, new TileGrid(width, height));
      applier.resetLastExportedFloorCount();
      int attempted = DrlgExport.exportLevelTiles(drlg, levelId, applier);
      if (levelId != D2LevelIds.LEVEL_BURIALGROUNDS) {
        assertNativeDirtPaths(level, true);
      }
      SourceGridStats source = sourceGridStats(level);
      assertTrue(attempted > 0, "D2MOO exported no floor tiles for level " + levelId);
      assertEquals(expectedFixedSeedFloors(levelId), attempted,
          "fixed-seed floor coverage changed for level " + levelId);
      assertEquals(0, applier.getMissingGridCount(), "missing target grid for level " + levelId);
      assertEquals(0, applier.getOutOfBoundsCount(), "out-of-bounds tile for level " + levelId);
      assertEquals(0, applier.getInvalidTileCount(), "invalid tile id for level " + levelId);
      assertEquals(expectedFixedSeedClippedFloors(levelId),
          applier.getClippedBoundaryFloorCount(),
          "fixed-seed native shared-boundary floor count changed for level " + levelId);
      assertEquals(attempted - applier.getClippedBoundaryFloorCount(),
          applier.getLastExportedFloorCount(),
          "not every exported floor tile was written for level " + levelId);
      assertEquals(0, applier.getDuplicatePositionCount(),
          "multiple rooms exported the same floor coordinate for level " + levelId);
      assertEquals(0, applier.getNonFloorOrientationCount(),
          "floor export referenced non-floor DT1 entries for level " + levelId);
      assertEquals(0, applier.getWallLayerOverflowCount(),
          "more than four walls occupied one coordinate for level " + levelId);
      assertEquals(0, applier.getNonWallOrientationCount(),
          "wall export referenced an incompatible orientation for level " + levelId);
      assertEquals(0, applier.getNonShadowOrientationCount(),
          "shadow export referenced a non-shadow DT1 entry for level " + levelId);
      assertTrue(applier.getExportedWallCount() > 0,
          "Act1 picked presets produced no wall tiles for level " + levelId);
      assertTrue(source.presetRooms > 0,
          "Act1 picked preset cells produced no preset rooms for level " + levelId);
      assertEquals(applier.getCallbackCount(), attempted
              + applier.getExportedWallCount() + applier.getExportedShadowCount()
              + applier.getClippedBoundaryCount() - applier.getClippedBoundaryFloorCount(),
          "layer callback accounting mismatch for level " + levelId);
      System.out.println("[ACT1-DIAG] level=" + levelId
          + " size=" + width + 'x' + height
          + " attemptedFloor=" + attempted
          + " callbacks=" + applier.getCallbackCount()
          + " writtenFloor=" + applier.getLastExportedFloorCount()
          + " writtenWall=" + applier.getExportedWallCount()
          + " writtenShadow=" + applier.getExportedShadowCount()
          + " ignoredLayer=" + applier.getIgnoredLayerCount()
          + " missingGrid=" + applier.getMissingGridCount()
          + " outOfBounds=" + applier.getOutOfBoundsCount()
          + " clippedBoundary=" + applier.getClippedBoundaryCount()
          + " clippedFloor=" + applier.getClippedBoundaryFloorCount()
          + " invalidTile=" + applier.getInvalidTileCount()
          + " duplicatePosition=" + applier.getDuplicatePositionCount()
          + " duplicateShadow=" + applier.getDuplicateShadowCount()
          + " wallOverflow=" + applier.getWallLayerOverflowCount()
          + " nonFloorOrientation=" + applier.getNonFloorOrientationCount()
          + " nonWallOrientation=" + applier.getNonWallOrientationCount()
          + " nonShadowOrientation=" + applier.getNonShadowOrientationCount()
          + " zeroTileId=" + applier.getZeroTileIdCount()
          + " uniqueFloorIds=" + applier.getUniqueFloorIdCount()
          + " uniqueWallIds=" + applier.getUniqueWallIdCount()
          + " uniqueShadowIds=" + applier.getUniqueShadowIdCount()
          + " sourceWallFlags=" + source.wallFlags
          + " sourceShadowFlags=" + source.shadowFlags
          + " sourceNonzeroWallCells=" + source.nonzeroWallCells
          + " outdoorRooms=" + source.outdoorRooms
          + " presetRooms=" + source.presetRooms);
    }
  }

  private static int expectedFixedSeedFloors(int levelId) {
    switch (levelId) {
      case D2LevelIds.LEVEL_STONYFIELD: return 5824;
      case D2LevelIds.LEVEL_COLDPLAINS: return 6144;
      case D2LevelIds.LEVEL_BLOODMOOR: return 5122;
      case D2LevelIds.LEVEL_BURIALGROUNDS: return 1920;
      case D2LevelIds.LEVEL_BLACKMARSH: return 6016;
      case D2LevelIds.LEVEL_TAMOEHIGHLAND: return 6080;
      default: throw new IllegalArgumentException("unexpected level " + levelId);
    }
  }

  private static int expectedFixedSeedClippedFloors(int levelId) {
    return levelId == D2LevelIds.LEVEL_BLOODMOOR ? 2 : 0;
  }

  private static SourceGridStats sourceGridStats(D2DrlgLevel level) {
    SourceGridStats stats = new SourceGridStats();
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null; room = room.getDrlgRoomNext()) {
      if (room.getMazeOrOutdoor() instanceof D2DrlgOutdoorRoomStrc) {
        stats.outdoorRooms++;
        D2DrlgOutdoorRoomStrc outdoor = (D2DrlgOutdoorRoomStrc) room.getMazeOrOutdoor();
        collectGridStats(outdoor.getPWallGrid(), stats, true);
        collectGridStats(outdoor.getPFloorGrid(), stats, false);
      } else if (room.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc) {
        stats.presetRooms++;
        D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
        for (D2DrlgGridStrc grid : preset.getPWallGrid()) collectGridStats(grid, stats, true);
        for (D2DrlgGridStrc grid : preset.getPFloorGrid()) collectGridStats(grid, stats, false);
        collectGridStats(preset.getPCellGrid(), stats, false);
      }
    }
    return stats;
  }

  private static void collectGridStats(D2DrlgGridStrc grid, SourceGridStats stats, boolean wallGrid) {
    if (grid == null || grid.getPCellsFlags() == null) return;
    for (int value : grid.getPCellsFlags()) {
      if (wallGrid && value != 0) stats.nonzeroWallCells++;
      if ((value & 0x00000001) != 0) stats.wallFlags++;
      if ((value & 0x08000000) != 0) stats.shadowFlags++;
    }
  }

  private static final class SourceGridStats {
    int wallFlags;
    int shadowFlags;
    int nonzeroWallCells;
    int outdoorRooms;
    int presetRooms;
  }
}
