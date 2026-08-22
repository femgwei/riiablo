package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2DrlgLevel;
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

  @Test
  public void fixedSeedAct1LayoutIsStableAndExportable() {
    int seed = Integer.decode(System.getProperty("d2.seed", "0x13579BDF"));
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

    Act1D2MOOLayoutBridge.LayoutAndDrlg second =
        Act1D2MOOLayoutBridge.getLayoutAndDrlg(seed, difficulty, burialId);
    assertNotNull(second, "Second fixed-seed generation failed");
    assertEquals(firstSummary, summarize(second.drlg),
        "same seed must produce the same Act1 layout summary");
    DrlgDrlg.freeDrlg(second.drlg);
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
      assertTrue(attempted > 0, "D2MOO exported no floor tiles for level " + levelId);
      assertEquals(attempted, applier.getLastExportedFloorCount(),
          "not every exported floor tile was written for level " + levelId);
      assertEquals(0, applier.getMissingGridCount(), "missing target grid for level " + levelId);
      assertEquals(0, applier.getOutOfBoundsCount(), "out-of-bounds tile for level " + levelId);
      assertEquals(0, applier.getInvalidTileCount(), "invalid tile id for level " + levelId);
      System.out.println("[ACT1-DIAG] level=" + levelId
          + " size=" + width + 'x' + height
          + " attemptedFloor=" + attempted
          + " callbacks=" + applier.getCallbackCount()
          + " writtenFloor=" + applier.getLastExportedFloorCount()
          + " ignoredLayer=" + applier.getIgnoredLayerCount()
          + " missingGrid=" + applier.getMissingGridCount()
          + " outOfBounds=" + applier.getOutOfBoundsCount()
          + " invalidTile=" + applier.getInvalidTileCount());
    }
  }
}
