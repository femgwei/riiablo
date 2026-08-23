package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class D2MooAct1NativeParityTest {

  @Test
  void tileTypesMatchNativeD2CmpEnum() {
    assertEquals(0, DrlgRoomTile.TILETYPE_FLOOR);
    assertEquals(7, DrlgRoomTile.TILETYPE_WALL_BOTTOM_RIGHT);
    assertEquals(8, DrlgRoomTile.TILETYPE_WALL_LEFT_DOOR);
    assertEquals(9, DrlgRoomTile.TILETYPE_WALL_RIGHT_DOOR);
    assertEquals(10, DrlgRoomTile.TILETYPE_WALL_LEFT_EXIT);
    assertEquals(11, DrlgRoomTile.TILETYPE_WALL_RIGHT_EXIT);
    assertEquals(12, DrlgRoomTile.TILETYPE_COLUMN);
    assertEquals(13, DrlgRoomTile.TILETYPE_SHADOW);
    assertEquals(14, DrlgRoomTile.TILETYPE_TREE);
    assertEquals(15, DrlgRoomTile.TILETYPE_ROOF);
    assertEquals(19, DrlgRoomTile.TILETYPE_FRONT_WALL_DOWN);
  }

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
    assertEquals(139, D2LvlPrestIds.LVLPREST_ACT1_CRYPT_PREV_W);
    assertEquals(158, D2LvlPrestIds.LVLPREST_ACT1_CRYPT_PORTAL_N);
    assertEquals(160, D2LvlPrestIds.LVLPREST_ACT1_CAIRN_STONES);
    assertEquals(163, D2LvlPrestIds.LVLPREST_ACT1_TOWER_1);
    assertEquals(164, D2LvlPrestIds.LVLPREST_ACT1_TOWER_2);
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
  void coordinateDirectionsMatchNativeD2DirectionEnum() {
    D2DrlgCoord center = coord(100, 100, 40, 40);

    assertEquals(0, DrlgDrlg.getDirectionFromCoordinates(
        center, coord(60, 110, 40, 20)), "west must be DIRECTION_SOUTHWEST");
    assertEquals(1, DrlgDrlg.getDirectionFromCoordinates(
        center, coord(110, 60, 20, 40)), "north must be DIRECTION_NORTHWEST");
    assertEquals(2, DrlgDrlg.getDirectionFromCoordinates(
        center, coord(140, 110, 40, 20)), "east must be DIRECTION_SOUTHEAST");
    assertEquals(3, DrlgDrlg.getDirectionFromCoordinates(
        center, coord(110, 140, 20, 40)), "south must be DIRECTION_NORTHEAST");
  }

  @Test
  void createVerticesRetainsNorthAndSouthLevelLinks() {
    D2DrlgCoord level = coord(100, 200, 56, 96);
    D2DrlgLevel northLevel = new D2DrlgLevel();
    northLevel.setPLevelCoords(coord(100, 120, 56, 80));
    D2DrlgLevel southTown = new D2DrlgLevel();
    southTown.setPLevelCoords(coord(100, 296, 56, 40));

    D2DrlgOrth north = new D2DrlgOrth();
    north.setPLevel(northLevel);
    north.setPBox(northLevel.getLevelCoords());
    north.setNDirection((byte) DrlgDrlg.getDirectionFromCoordinates(
        level, northLevel.getLevelCoords()));

    D2DrlgOrth south = new D2DrlgOrth();
    south.setPLevel(southTown);
    south.setPBox(southTown.getLevelCoords());
    south.setNDirection((byte) DrlgDrlg.getDirectionFromCoordinates(
        level, southTown.getLevelCoords()));
    south.setBPreset(true);
    north.setPNext(south);

    D2DrlgVertexStrc[] vertices = new D2DrlgVertexStrc[1];
    DrlgDrlgVer.createVertices(null, vertices, level, (byte) 0, north);

    List<D2DrlgVertexStrc> ring = ring(vertices[0]);
    assertEquals(4, ring.size());
    assertVertex(ring.get(1), 0, 0, 1);
    assertVertex(ring.get(3), 55, 95, 3);
  }

  @Test
  void act1CornerBorderColumnMatchesNativeBooleanExpression() {
    assertEquals(1, DrlgOutPlace.getAct1BorderColumn(0));
    assertEquals(0, DrlgOutPlace.getAct1BorderColumn(1));
  }

  @Test
  void outdoorLinkDirectionIndexMatchesNativeCornerPrecedence() {
    assertEquals(1, DrlgOutdoors.getOutLinkDirectionIndex(0, 0, 8, 6));
    assertEquals(0, DrlgOutdoors.getOutLinkDirectionIndex(0, 3, 8, 6));
    assertEquals(1, DrlgOutdoors.getOutLinkDirectionIndex(3, 0, 8, 6));
    assertEquals(2, DrlgOutdoors.getOutLinkDirectionIndex(7, 0, 8, 6));
    assertEquals(2, DrlgOutdoors.getOutLinkDirectionIndex(7, 3, 8, 6));
    assertEquals(3, DrlgOutdoors.getOutLinkDirectionIndex(7, 5, 8, 6));
    assertEquals(3, DrlgOutdoors.getOutLinkDirectionIndex(3, 5, 8, 6));
    assertEquals(-1, DrlgOutdoors.getOutLinkDirectionIndex(3, 3, 8, 6));
  }

  @Test
  void vertexGridSegmentMatchesNativeEndpointRulesWithoutRecursion() {
    D2DrlgGridStrc grid = new D2DrlgGridStrc();
    DrlgDrlgGrid.initializeGridCells(null, grid, 6, 6);
    D2DrlgVertexStrc first = vertex(1, 1);
    D2DrlgVertexStrc second = vertex(4, 1);
    D2DrlgVertexStrc third = vertex(4, 4);
    first.setPNext(second);
    second.setPNext(third);

    DrlgDrlgGrid.sub_6FD75DE0(
        grid, first, 0x40, DrlgDrlgGrid.FlagOperation.OR, false);
    assertArrayEquals(new int[] {0x40, 0x40, 0x40, 0},
        new int[] {
            grid.getFlag(1, 1), grid.getFlag(2, 1),
            grid.getFlag(3, 1), grid.getFlag(4, 1)
        });

    DrlgDrlgGrid.sub_6FD75DE0(
        grid, first, 0x80, DrlgDrlgGrid.FlagOperation.OR, true);
    assertEquals(0x80, grid.getFlag(4, 1));
    assertEquals(0, grid.getFlag(4, 2),
        "alterNextVertex must not paint the following segment");

    D2DrlgGridStrc verticalGrid = new D2DrlgGridStrc();
    DrlgDrlgGrid.initializeGridCells(null, verticalGrid, 6, 6);
    D2DrlgVertexStrc bottom = vertex(2, 4);
    D2DrlgVertexStrc top = vertex(2, 1);
    bottom.setPNext(top);
    DrlgDrlgGrid.sub_6FD75DE0(
        verticalGrid, bottom, 0x20, DrlgDrlgGrid.FlagOperation.OR, false);
    assertArrayEquals(new int[] {0, 0x20, 0x20, 0x20},
        new int[] {
            verticalGrid.getFlag(2, 1), verticalGrid.getFlag(2, 2),
            verticalGrid.getFlag(2, 3), verticalGrid.getFlag(2, 4)
        });
  }

  @Test
  void burialGroundsValidationUsesFlatRandUnionFirstRow() {
    D2DrlgLevelLinkDataStrc data = new D2DrlgLevelLinkDataStrc();
    for (int i = 0; i < 5; i++) {
      data.setPLevelCoord(i, coord(i * 100, 0, 10, 10));
    }
    data.getNRand(0)[2] = 7;
    data.getNRand(0)[4] = 7;
    data.getNRand(2)[2] = 1;
    data.getNRand(2)[4] = 2;

    assertFalse(DrlgOutPlace.sub_6FD82050(data, 4),
        "same first-row rand on sibling links must be rejected");

    data.getNRand(0)[4] = 8;
    assertTrue(DrlgOutPlace.sub_6FD82050(data, 4));
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

  @Test
  void fillNewCellFlagsPreservesSourceStrideForSubregions() {
    int[] source = {
        0, 1, 2, 3, 4,
        5, 6, 7, 8, 9,
        10, 11, 12, 13, 14,
        15, 16, 17, 18, 19,
    };
    D2DrlgCoord region = coord(1, 1, 2, 2);
    D2DrlgGridStrc grid = new D2DrlgGridStrc();

    DrlgDrlgGrid.fillNewCellFlags(null, grid, source, region, 5);

    assertArrayEquals(new int[] {6, 7, 11, 12}, grid.getPCellsFlags());
    assertArrayEquals(new int[] {0, 2}, grid.getPCellsRowOffsets());
    assertEquals(6, DrlgDrlgGrid.getGridEntry(grid, 0, 0));
    assertEquals(12, DrlgDrlgGrid.getGridEntry(grid, 1, 1));
  }

  @Test
  void nativeDirtPathDirectionUsesD2CardinalOrdering() {
    assertEquals(0, DrlgOutPlace.nativeCardinalDirection(0, 0, 5, 0));
    assertEquals(1, DrlgOutPlace.nativeCardinalDirection(0, 0, 0, 5));
    assertEquals(2, DrlgOutPlace.nativeCardinalDirection(0, 0, -5, 0));
    assertEquals(3, DrlgOutPlace.nativeCardinalDirection(0, 0, 0, -5));
  }

  @Test
  void nativeDirtPathSearchConnectsEndpointToHubWithoutCrossingPresetCells() {
    D2DrlgLevel level = new D2DrlgLevel();
    level.setPLevelCoords(coord(100, 200, 48, 48));
    D2DrlgOutdoorInfoStrc outdoors = new D2DrlgOutdoorInfoStrc();
    outdoors.setNGridWidth(6);
    outdoors.setNGridHeight(6);
    outdoors.setNVertices(1);
    level.setPresetOrOutdoorsOrMaze(outdoors);
    for (int i = 0; i < outdoors.getPGrid().length; i++) {
      DrlgDrlgGrid.initializeGridCells(null, outdoors.getPGrid(i), 6, 6);
    }

    outdoors.getPVertices(6).setNPosX(100 + 8);
    outdoors.getPVertices(6).setNPosY(200 + 8);
    outdoors.getPVertices(12).setNPosX(100 + 32);
    outdoors.getPVertices(12).setNPosY(200 + 32);
    D2DrlgOutdoorPackedGrid2InfoStrc blocked = new D2DrlgOutdoorPackedGrid2InfoStrc();
    blocked.setBHasPickedFile(true);
    DrlgDrlgGrid.alterGridFlag(outdoors.getPGrid(2), 2, 2,
        blocked.getNPackedValue(), DrlgDrlgGrid.FlagOperation.OVERWRITE);

    assertTrue(DrlgOutPlace.buildAct1DirtPath(level, 0));
    D2DrlgVertexStrc path = outdoors.getPPathStarts(0);
    assertNotNull(path);
    assertEquals(4, path.getNPosX());
    assertEquals(4, path.getNPosY());
    boolean reachedStart = false;
    int length = 0;
    for (D2DrlgVertexStrc vertex = path; vertex != null; vertex = vertex.getPNext()) {
      assertFalse(vertex.getNPosX() == 2 && vertex.getNPosY() == 2,
          "native dirt path crossed a picked preset cell");
      if (vertex.getNPosX() == 1 && vertex.getNPosY() == 1) reachedStart = true;
      assertTrue(++length < 64, "native dirt path contains a cycle");
    }
    assertTrue(reachedStart, "native dirt path did not reach its endpoint");
  }

  private static D2DrlgCoord coord(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNPosX(x);
    coord.setNPosY(y);
    coord.setNWidth(width);
    coord.setNHeight(height);
    return coord;
  }

  private static D2DrlgVertexStrc vertex(int x, int y) {
    D2DrlgVertexStrc vertex = new D2DrlgVertexStrc();
    vertex.setNPosX(x);
    vertex.setNPosY(y);
    return vertex;
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
