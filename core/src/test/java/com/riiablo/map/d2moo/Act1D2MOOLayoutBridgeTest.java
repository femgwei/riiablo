package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.D2DrlgOutdoorRoomStrc;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
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
    String firstSummary = summarize(first.drlg);
    System.out.println("[ACT1-DIAG] seed=" + seed + " diff=" + difficulty + " " + firstSummary);
    exportAndReport(first.drlg);
    DrlgDrlg.freeDrlg(first.drlg);
    Act1D2MOOLayoutBridge.releaseDataTables();

    Act1D2MOOLayoutBridge.LayoutAndDrlg second =
        Act1D2MOOLayoutBridge.getLayoutAndDrlg(seed, difficulty, burialId);
    assertNotNull(second, "Second fixed-seed generation failed");
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

  private static String summarize(D2DrlgStrc drlg) {
    StringBuilder out = new StringBuilder();
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_STONYFIELD,
        D2LevelIds.LEVEL_COLDPLAINS,
        D2LevelIds.LEVEL_BLOODMOOR,
        D2LevelIds.LEVEL_ROGUEENCAMPMENT,
        D2LevelIds.LEVEL_BURIALGROUNDS }) {
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
        D2LevelIds.LEVEL_BLOODMOOR }) {
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
      int width = Math.max(1, level.getLevelCoords().getNWidth());
      int height = Math.max(1, level.getLevelCoords().getNHeight());
      applier.putGrid(levelId, new TileGrid(width, height));
      applier.resetLastExportedFloorCount();
      int attempted = DrlgExport.exportLevelTiles(drlg, levelId, applier);
      SourceGridStats source = sourceGridStats(level);
      assertTrue(attempted > 0, "D2MOO exported no floor tiles for level " + levelId);
      assertEquals(attempted, applier.getLastExportedFloorCount(),
          "not every exported floor tile was written for level " + levelId);
      assertEquals(0, applier.getMissingGridCount(), "missing target grid for level " + levelId);
      assertEquals(0, applier.getOutOfBoundsCount(), "out-of-bounds tile for level " + levelId);
      assertEquals(0, applier.getInvalidTileCount(), "invalid tile id for level " + levelId);
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
              + applier.getExportedWallCount() + applier.getExportedShadowCount(),
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
