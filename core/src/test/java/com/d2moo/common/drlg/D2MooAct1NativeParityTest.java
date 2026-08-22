package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class D2MooAct1NativeParityTest {

  @Test
  void act1PresetIdsMatchNativeD2MooEnum() {
    assertEquals(0, DrlgOutWild.LVLSUB_ACT1_BORDER_CLIFFS);
    assertEquals(1, DrlgOutWild.LVLSUB_ACT1_BORDER_MIDDLE);
    assertEquals(2, DrlgOutWild.LVLSUB_ACT1_BORDER_CORNER);
    assertEquals(3, DrlgOutWild.LVLSUB_ACT1_BORDER_BORDER);
    assertEquals(24, D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_CAVE_RIGHT);
    assertEquals(25, D2LvlPrestIds.LVLPREST_ACT1_WILD_CLIFF_CAVE_LEFT);
    assertEquals(28, D2LvlPrestIds.LVLPREST_ACT1_BRIDGE);
    assertEquals(51, D2LvlPrestIds.LVLPREST_ACT1_CAVE_ENTRANCE);
    assertEquals(52, D2LvlPrestIds.LVLPREST_ACT1_DOE_ENTRANCE);
    assertEquals(108, D2LvlPrestIds.LVLPREST_ACT1_GRAVEYARD);
    assertEquals(160, D2LvlPrestIds.LVLPREST_ACT1_CAIRN_STONES);
    assertEquals(300, D2LvlPrestIds.LVLPREST_ACT1_TRISTRAM);
  }

  @Test
  void actNumbersAreZeroBasedAndBloodMoorPreservesVertexDirections() {
    assertEquals(D2C_Acts.ACT_I, DrlgDrlg.getActNoFromLevelId(D2LevelIds.LEVEL_BLOODMOOR));
    assertTrue(DrlgOutWild.preservesInitialDirections(D2LevelIds.LEVEL_BLOODMOOR));
    assertTrue(DrlgOutWild.preservesInitialDirections(D2LevelIds.LEVEL_COLDPLAINS));
    assertTrue(DrlgOutWild.preservesInitialDirections(D2LevelIds.LEVEL_BURIALGROUNDS));
    assertFalse(DrlgOutWild.preservesInitialDirections(D2LevelIds.LEVEL_STONYFIELD));
  }

  @Test
  void createVerticesSplicesNativeLevelLinkInterval() {
    D2DrlgCoord level = coord(100, 200, 80, 80);
    D2DrlgLevel linkedLevel = new D2DrlgLevel();
    linkedLevel.setPLevelCoords(coord(116, 180, 24, 20));

    D2DrlgOrth link = new D2DrlgOrth();
    link.setPLevel(linkedLevel);
    link.setPBox(linkedLevel.getLevelCoords());
    link.setNDirection((byte) 1); // ALTDIR_NORTH
    link.setBPreset(true);

    D2DrlgVertexStrc[] vertices = new D2DrlgVertexStrc[1];
    DrlgDrlgVer.createVertices(null, vertices, level, (byte) 0, link);

    List<D2DrlgVertexStrc> ring = ring(vertices[0]);
    assertEquals(6, ring.size());
    assertVertex(ring.get(0), 0, 79, 0);
    assertVertex(ring.get(1), 0, 0, 0);
    assertVertex(ring.get(2), 16, 0, 3);
    assertVertex(ring.get(3), 39, 0, 0);
    assertVertex(ring.get(4), 79, 0, 0);
    assertVertex(ring.get(5), 79, 79, 0);
    assertSame(vertices[0], ring.get(5).getPNext());
  }

  @Test
  void bridgeLookupFindsOnlyPickedNativeAct1Bridge() {
    D2DrlgLevel level = new D2DrlgLevel();
    D2DrlgOutdoorInfoStrc outdoors = new D2DrlgOutdoorInfoStrc();
    outdoors.setNGridWidth(10);
    outdoors.setNGridHeight(8);
    level.setPresetOrOutdoorsOrMaze(outdoors);
    for (int i = 0; i < 4; i++) {
      DrlgDrlgGrid.initializeGridCells(null, outdoors.getPGrid(i), 10, 8);
    }

    int bridgeX = 4;
    int bridgeY = 3;
    DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(0), bridgeX, bridgeY,
        D2LvlPrestIds.LVLPREST_ACT1_BRIDGE, DrlgDrlgGrid.FlagOperation.OVERWRITE);
    D2DrlgOutdoorPackedGrid2InfoStrc picked = new D2DrlgOutdoorPackedGrid2InfoStrc();
    picked.setBHasPickedFile(true);
    picked.setNPickedFile(1);
    DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), bridgeX, bridgeY,
        picked.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OVERWRITE);

    int[] x = {-1};
    int[] y = {-1};
    DrlgOutWild.getBridgeCoords(level, x, y);
    assertEquals(bridgeX, x[0]);
    assertEquals(bridgeY, y[0]);

    picked.setNPickedFile(2);
    DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), bridgeX, bridgeY,
        picked.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OVERWRITE);
    DrlgOutWild.getBridgeCoords(level, x, y);
    assertEquals(-1, x[0]);
    assertEquals(-1, y[0]);
  }

  private static D2DrlgCoord coord(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNPosX(x);
    coord.setNPosY(y);
    coord.setNWidth(width);
    coord.setNHeight(height);
    return coord;
  }

  private static List<D2DrlgVertexStrc> ring(D2DrlgVertexStrc head) {
    List<D2DrlgVertexStrc> result = new ArrayList<>();
    D2DrlgVertexStrc vertex = head;
    do {
      result.add(vertex);
      vertex = vertex.getPNext();
    } while (vertex != head && result.size() < 32);
    return result;
  }

  private static void assertVertex(D2DrlgVertexStrc vertex, int x, int y, int flags) {
    assertEquals(x, vertex.getNPosX());
    assertEquals(y, vertex.getNPosY());
    assertEquals(flags, vertex.getDwFlags());
  }
}
