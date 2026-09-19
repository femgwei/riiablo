package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.codec.excel.AutoMap;
import org.junit.jupiter.api.Test;

/** 无资源验证 D2MOO AutoMap.txt 查询语义。 */
class AutomapTileRendererTest {
  @Test void outdoorFloorFeaturesExcludeGenericPathFrames() {
    assertFalse(AutomapTileRenderer.isOutdoorRoadCell(-1));
    assertTrue(AutomapTileRenderer.isOutdoorRoadCell(0));
    assertTrue(AutomapTileRenderer.isOutdoorRoadCell(3));
    assertFalse(AutomapTileRenderer.isOutdoorRoadCell(4));

    assertFalse(AutomapTileRenderer.isOutdoorFloorFeature(-1));
    assertFalse(AutomapTileRenderer.isOutdoorFloorFeature(0));
    assertFalse(AutomapTileRenderer.isOutdoorFloorFeature(3));
    assertTrue(AutomapTileRenderer.isOutdoorFloorFeature(4));
    assertTrue(AutomapTileRenderer.isOutdoorFloorFeature(79));
  }

  private static AutoMap.Entry entry(String level, String tile, int style, int start, int end, int... cel) {
    AutoMap.Entry e = new AutoMap.Entry();
    e.LevelName = level;
    e.TileName = tile;
    e.Style = style;
    e.StartSequence = start;
    e.EndSequence = end;
    e.Cel = cel;
    return e;
  }

  @Test void levelAndTileMustMatch() {
    AutoMap.Entry e = entry("1 Wilderness", "fl", 2, 0, 3, 7);
    assertTrue(AutomapTileRenderer.matches(e, "1 Wilderness", "fl", 2, 1));
    assertFalse(AutomapTileRenderer.matches(e, "2 Wilderness", "fl", 2, 1));
    assertFalse(AutomapTileRenderer.matches(e, "1 Wilderness", "wl", 2, 1));
  }

  @Test void wildcardStyleAndSequenceMatch() {
    AutoMap.Entry e = entry("1 Cave", "wr", -1, -1, -1, 8, -1, 9);
    assertTrue(AutomapTileRenderer.matches(e, "1 Cave", "wr", 99, 500));
    int selected = AutomapTileRenderer.selectCellId(e.Cel, 1L);
    assertTrue(selected == 8 || selected == 9);
  }

  @Test void sequenceRangeIsInclusive() {
    AutoMap.Entry e = entry("1 Town", "fl", 0, 2, 4, 3);
    assertFalse(AutomapTileRenderer.matches(e, "1 Town", "fl", 0, 1));
    assertTrue(AutomapTileRenderer.matches(e, "1 Town", "fl", 0, 2));
    assertTrue(AutomapTileRenderer.matches(e, "1 Town", "fl", 0, 4));
    assertFalse(AutomapTileRenderer.matches(e, "1 Town", "fl", 0, 5));
  }

  @Test void invalidCelsReturnMinusOneAndSelectionIsStable() {
    assertEquals(-1, AutomapTileRenderer.selectCellId(new int[] {-1, -1}, 42L));
    int[] cels = {4, -1, 9};
    assertEquals(AutomapTileRenderer.selectCellId(cels, 1234L),
        AutomapTileRenderer.selectCellId(cels, 1234L));
    int selected = AutomapTileRenderer.selectCellId(cels, 1234L);
    assertTrue(selected == 4 || selected == 9);
  }

  @Test void orientationNamesMatchD2MooVocabulary() {
    assertEquals("fl", AutomapTileRenderer.tileNameForOrientation(com.riiablo.map.Orientation.FLOOR));
    assertEquals("wtlr", AutomapTileRenderer.tileNameForOrientation(com.riiablo.map.Orientation.RIGHT_NORTH_CORNER_WALL));
    assertEquals("wld", AutomapTileRenderer.tileNameForOrientation(com.riiablo.map.Orientation.LEFT_WALL_DOOR));
    assertEquals("rf", AutomapTileRenderer.tileNameForOrientation(com.riiablo.map.Orientation.ROOF));
  }

  @Test void anyStyleFallbackIsLimitedToActThreeCompatibilityLevels() {
    assertFalse(AutomapTileRenderer.allowsAnyStyleFallback("1 Town"));
    assertFalse(AutomapTileRenderer.allowsAnyStyleFallback("1 Wilderness"));
    assertTrue(AutomapTileRenderer.allowsAnyStyleFallback("3 Jungle"));
    assertTrue(AutomapTileRenderer.allowsAnyStyleFallback("3 Kurast"));
  }
}
