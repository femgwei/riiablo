package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Array;
import com.riiablo.codec.excel.Levels;
import org.junit.jupiter.api.Test;

class AutomapManagerTest {
  @Test
  void nativeLayerIndexUsesZeroBasedLevelsTxtValue() {
    Levels.Entry act1 = new Levels.Entry();
    act1.Layer = 0;
    assertEquals(0, AutomapManager.nativeLayerIndex(act1));

    Levels.Entry act2 = new Levels.Entry();
    act2.Layer = 27;
    assertEquals(27, AutomapManager.nativeLayerIndex(act2));
  }

  @Test
  void nativeLayerIndexRejectsValuesOutsideMaDirectory() {
    Levels.Entry beforeDirectory = new Levels.Entry();
    beforeDirectory.Layer = -1;
    assertEquals(-1, AutomapManager.nativeLayerIndex(beforeDirectory));

    Levels.Entry afterDirectory = new Levels.Entry();
    afterDirectory.Layer = AutomapExplorationStore.LAYER_COUNT;
    assertEquals(-1, AutomapManager.nativeLayerIndex(afterDirectory));
    assertEquals(-1, AutomapManager.nativeLayerIndex(null));
  }

  @Test
  void nativeCellCoordinatesRestoreToOriginalWorldTileCenter() {
    assertNativeCellRestores(282, 82);
    assertNativeCellRestores(82, 282);
  }

  private static void assertNativeCellRestores(int worldX, int worldY) {
    int tileX = Math.floorDiv(worldX, 5);
    int tileY = Math.floorDiv(worldY, 5);
    short nativeX = (short) (8 * (tileX - tileY));
    short nativeY = (short) (4 * (tileX + tileY));
    Array<AutomapExplorationStore.Cell> cells = new Array<>();
    cells.add(new AutomapExplorationStore.Cell(1, nativeX, nativeY));

    AutomapLayer restored = new AutomapLayer(2);
    AutomapManager.restoreNativeCells(restored, cells);

    assertTrue(restored.isExplored(worldX, worldY));
    assertEquals(1, restored.getExploredCount());
  }
}
