package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.riiablo.drlg.TileGrid;
import com.riiablo.codec.excel.Levels;

/** Signed native coordinates must align with automap tile projection. */
public class ZoneCoordinateAlignmentTest {
  @Test public void nativeWarpPositionRetainsSignedSubtilesRatherThanItsRoundedSpecialCell() {
    Map.Zone zone = new Map.Zone();
    zone.level = new Levels.Entry();
    zone.flags = new byte[0];
    zone.setNativeTileGrid(new TileGrid(8, 8), 0);
    zone.setPosition(-100, 50);
    zone.addNativeWarpMarker(2, 31, -6);
    com.badlogic.gdx.math.Vector2 exact = zone.nativeWarpPosition(2, new com.badlogic.gdx.math.Vector2());
    assertEquals(-69f, exact.x);
    assertEquals(44f, exact.y);
    assertEquals(null, zone.nativeWarpPosition(3, new com.badlogic.gdx.math.Vector2()));
    zone.specials.clear();
    zone.restoreNativeWarpMarkers();
    assertEquals(exact, zone.nativeWarpPosition(2, new com.badlogic.gdx.math.Vector2()));
  }
  @Test
  public void selectedTownSourceIsAvailableBeforeDs1AssetLoading() {
    Map.Zone zone = new Map.Zone();
    zone.presets = new Map.Preset[][] {{Map.Preset.of(
        new com.riiablo.codec.excel.LvlPrest.Entry(), "expansion/town/town.ds1")}};
    assertEquals(Map.TILES_PATH + "expansion/town/town.ds1", zone.presetSourceFile());
    assertEquals(null, zone.presets[0][0].getDS1());
  }

  @Test
  public void townStaticObjectDedupRequiresTheSameRawDs1Source() {
    Map.Zone zone = new Map.Zone();
    zone.town = true;
    zone.presets = new Map.Preset[][] {{Map.Preset.of(
        new com.riiablo.codec.excel.LvlPrest.Entry(), "expansion/town/town.ds1")}};
    Map.NativeObject same = new Map.NativeObject(0, 1, 0, 10, 10, true, false,
        "DATA\\GLOBAL\\TILES\\expansion\\town\\town.ds1", true);
    assertTrue(MapManager.isCompatibilityTownStaticObject(zone, same));
    Map.NativeObject other = new Map.NativeObject(0, 1, 0, 10, 10, true, false,
        "expansion/town/other.ds1", true);
    org.junit.jupiter.api.Assertions.assertFalse(MapManager.isCompatibilityTownStaticObject(zone, other));
    Map.NativeObject generated = new Map.NativeObject(0, 1, 0, 10, 10, false, false,
        same.sourceFile, true);
    org.junit.jupiter.api.Assertions.assertFalse(MapManager.isCompatibilityTownStaticObject(zone, generated));
    zone.town = false;
    org.junit.jupiter.api.Assertions.assertFalse(MapManager.isCompatibilityTownStaticObject(zone, same));
  }

  @Test
  public void nativeMazeDimensionsReplaceCompatibilityBoundsBeforeWarpProjection() {
    Map.Zone zone = new Map.Zone();
    zone.level = new Levels.Entry();
    zone.tilesX = zone.tilesY = 2;
    zone.width = zone.height = 10;
    zone.flags = new byte[100];
    zone.gridSizeX = zone.gridSizeY = 2;
    zone.gridsX = zone.gridsY = 1;
    zone.presets = new Map.Preset[1][1];
    TileGrid grid = new TileGrid(7, 5);
    zone.setNativeTileGrid(grid, 0);
    assertEquals(35, zone.width());
    assertEquals(25, zone.height());
    assertTrue(zone.flags.length >= 875);
    assertEquals(4, zone.gridsX);
    assertEquals(3, zone.gridsY);
    zone.addNativeWarpMarker(2, 30, 20);
    assertTrue(zone.specials.containsKey(Map.Zone.tileHashCode(Map.WALL_OFFSET, 6, 4)));
    zone.specials.clear();
    zone.restoreNativeWarpMarkers();
    assertEquals(1, zone.specials.size);
    assertTrue(zone.specials.containsKey(Map.Zone.tileHashCode(Map.WALL_OFFSET, 6, 4)));
    zone.restoreNativeWarpMarkers();
    assertEquals(1, zone.specials.size);
  }

  @Test
  public void nativeResizeSupportsUnpartitionedDiagnosticZone() {
    Map.Zone zone = new Map.Zone();
    zone.flags = new byte[0];
    zone.setNativeTileGrid(new TileGrid(3, 4), 0);
    assertEquals(1, zone.gridsX);
    assertEquals(1, zone.gridsY);
    assertTrue(zone.flags.length >= 300);
  }

  @Test
  public void unrelatedSpecialStyleDoesNotSuppressRetainedWarpSlot() {
    Map.Zone zone = new Map.Zone();
    zone.level = new Levels.Entry();
    zone.flags = new byte[0];
    zone.setNativeTileGrid(new TileGrid(8, 8), 0);
    zone.addNativeWarpMarker(2, 30, 20);
    zone.specials.clear();
    DS1.Cell decoration = new DS1.Cell();
    decoration.mainIndex = 2;
    decoration.orientation = Orientation.SPECIAL_10;
    decoration.id = DT1.Tile.Index.create(Orientation.SPECIAL_10, 2, 42);
    zone.specials.put(Map.Zone.tileHashCode(Map.WALL_OFFSET, 1, 1), decoration);
    zone.restoreNativeWarpMarkers();
    assertEquals(2, zone.specials.size);
    assertTrue(Map.ID.isWarp(zone.specials.get(
        Map.Zone.tileHashCode(Map.WALL_OFFSET, 6, 4)).id));
  }

  @Test
  public void negativeSubtileOriginUsesFloorTileDivision() {
    Map.Zone zone = new Map.Zone();
    zone.setPosition(-1, -6);

    assertEquals(0, zone.getLocalTX(-1));
    assertEquals(1, zone.getLocalTX(0));
    assertEquals(0, zone.getLocalTY(-2));
    assertEquals(1, zone.getLocalTY(-1));
  }

  @Test
  public void adjacentZoneWinsOverBoundaryWallAtSharedEdge() throws IOException {
    Map map = new Map(0, 0);
    Map.Zone adjacent = new Map.Zone();
    adjacent.setPosition(10760, -560);
    adjacent.width = 320;
    adjacent.height = 90;
    adjacent.tilesX = 64;
    adjacent.tilesY = 18;

    Map.Zone nativeZone = new Map.Zone();
    nativeZone.setPosition(10760, -760);
    nativeZone.width = 280;
    nativeZone.height = 200;
    nativeZone.tilesX = 56;
    nativeZone.tilesY = 40;
    nativeZone.putBoundaryWall(Map.WALL_OFFSET, 32, 40,
        tile(Orientation.LEFT_WALL, 4, 0));
    adjacent.flags = new byte[adjacent.width * adjacent.height];

    // The adjacent zone is deliberately inserted first, matching the Act I
    // layout where Monastery Gate follows Outer Cloister's bottom edge.
    map.zones.add(adjacent);
    map.zones.add(nativeZone);

    assertSame(adjacent, map.getZone(10760 + 32 * 5, -760 + 40 * 5));
    assertSame(nativeZone, map.getRenderZone(10760 + 32 * 5, -760 + 40 * 5));
    assertTrue(map.areZonesAdjacent(adjacent, nativeZone));
    assertEquals(Orientation.LEFT_WALL,
        nativeZone.get(Map.WALL_OFFSET, nativeZone.tx + 32, nativeZone.ty + 40).orientation);
    assertEquals(0, map.flags(10760 + 32 * 5, -760 + 40 * 5));
  }

  @Test
  public void renderOnlyBoundaryWallCannotReadOutsideCollisionArray() throws IOException {
    Map map = new Map(0, 0);
    Map.Zone zone = new Map.Zone();
    zone.setPosition(100, -200);
    zone.width = zone.height = 10;
    zone.tilesX = zone.tilesY = 2;
    zone.flags = new byte[100];
    zone.putBoundaryWall(Map.WALL_OFFSET, 1, 2, tile(Orientation.LEFT_WALL, 4, 0));
    map.zones.add(zone);
    assertSame(zone, map.getZone(105, -190));
    assertEquals(0xFF, map.flags(105, -190));
    assertTrue(map.isBlockedForPath(105, -190, DT1.Tile.FLAG_BLOCK_WALK, false));
    assertTrue(map.isBlockedForPath(105, -191, DT1.Tile.FLAG_BLOCK_WALK, 2, false));
    assertEquals(0xFF, zone.flags(-1, 0));
    assertEquals(0xFF, zone.staticFlags(10, 0));
    assertEquals(0, zone.flags(9, 9));
  }

  @Test
  public void nativeWallTypeUsesWorldCoordinatesAndExactTileFallback() throws IOException {
    Map.Zone zone = new Map.Zone();
    zone.setPosition(100, 200);

    TileGrid grid = new TileGrid(4, 4);
    grid.wallTileTypes[1][2][3] = Orientation.RIGHT_WALL;
    zone.setNativeTileGrid(grid, 0);

    assertEquals(Orientation.RIGHT_WALL,
        zone.nativeWallTileType(1, zone.tx + 3, zone.ty + 2));

    DT1.Tile tile = tile(Orientation.LEFT_WALL, 8, 3);
    int tileId = DT1.Tile.Index.create(tile.orientation, tile.mainIndex, tile.subIndex);
    zone.putNativeWallTileTypeByTile(1, 2, tileId, Orientation.LEFT_WALL_DOOR);
    assertEquals(Orientation.LEFT_WALL_DOOR,
        zone.nativeWallTileType(0, zone.tx + 1, zone.ty + 2, tile));

    // Town DS1 graphics may use a different tile identity than the native
    // export.  A single native wall at the coordinate must still provide its
    // logical type when the exact tile-id match is unavailable.
    Map.Zone fallback = new Map.Zone();
    fallback.setPosition(100, 200);
    fallback.putNativeWallTileTypeByTile(1, 2, tileId, Orientation.RIGHT_WALL_DOOR);
    DT1.Tile differentTile = tile(Orientation.LEFT_WALL, 9, 4);
    assertEquals(Orientation.RIGHT_WALL_DOOR,
        fallback.nativeWallTileType(0, fallback.tx + 1, fallback.ty + 2, differentTile));

    Map.Zone sparse = new Map.Zone();
    sparse.setPosition(100, 200);
    sparse.putNativeWallTileType(2, 1, 2, Orientation.LOWER_RIGHT_WALL);
    assertEquals(Orientation.LOWER_RIGHT_WALL,
        sparse.nativeWallTileType(2, sparse.tx + 1, sparse.ty + 2));

    sparse.clearNativeWallTileTypes();
    assertEquals(-1, sparse.nativeWallTileType(2, sparse.tx + 1, sparse.ty + 2));
  }

  @Test public void syntheticRuntimeMarkerIsNotAnExactNativeUnitPosition() {
    Map.Zone zone = new Map.Zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry();
    zone.level.Id = 63;
    zone.tilesX = zone.tilesY = 20;
    zone.addRuntimeWarpMarker(1, 27, 12);
    assertNull(zone.nativeWarpPosition(1, new com.badlogic.gdx.math.Vector2()));
    zone.addNativeWarpMarker(1, 122, 272);
    assertEquals(new com.badlogic.gdx.math.Vector2(122, 272),
        zone.nativeWarpPosition(1, new com.badlogic.gdx.math.Vector2()));
    zone.setNativeSeamAnchor(1, 20, 30);
    zone.dispose();
    assertNull(zone.nativeSeamAnchor(1));
    assertNull(zone.nativeWarpPosition(1, new com.badlogic.gdx.math.Vector2()));
  }

  private static DT1.Tile tile(int orientation, int mainIndex, int subIndex)
      throws IOException {
    byte[] bytes = new byte[DT1.Tile.SIZE];
    ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(20, orientation);
    buffer.putInt(24, mainIndex);
    buffer.putInt(28, subIndex);
    buffer.putInt(32, 1);
    return new DT1.Tile(new ByteArrayInputStream(bytes));
  }
}
