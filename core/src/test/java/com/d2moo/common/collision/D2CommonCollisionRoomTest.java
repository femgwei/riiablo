package com.d2moo.common.collision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.d2moo.common.d2cmp.D2TileData;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgRoomTilesStrc;
import com.d2moo.common.drlg.D2DrlgTileDataStrc;
import com.d2moo.common.dungeon.Dungeon;

class D2CommonCollisionRoomTest {
  @Test
  void mergesDt1FlagsWithNativeYFlipAndMapTileMasks() {
    byte[] flags = new byte[25];
    flags[20] = (byte) D2Collision.COLLIDE_VISIBLE;
    flags[4] = (byte) D2Collision.COLLIDE_NOPLAYER;
    D2DrlgTileDataStrc tile = tile(0, 0, dt1(flags), 0x02 | 0x40 | 0x80);
    D2ActiveRoom room = activeRoom(coords(0, 0, 1, 1), roomTiles(tile));

    D2CommonCollision.allocRoomCollisionGrid(room);

    assertEquals(D2Collision.COLLIDE_VISIBLE | D2Collision.COLLIDE_PRESET
            | D2Collision.COLLIDE_WALL | D2Collision.COLLIDE_MISSILE_BARRIER,
        room.getPCollisionGrid().getFlag(0, 0));
    assertEquals(D2Collision.COLLIDE_NOPLAYER | D2Collision.COLLIDE_PRESET
            | D2Collision.COLLIDE_WALL | D2Collision.COLLIDE_MISSILE_BARRIER,
        room.getPCollisionGrid().getFlag(4, 4));
    assertEquals(D2Collision.COLLIDE_PRESET | D2Collision.COLLIDE_WALL
            | D2Collision.COLLIDE_MISSILE_BARRIER,
        room.getPCollisionGrid().getFlag(2, 2));
  }

  @Test
  void clipsTileFlagsAtRoomSubtileBoundary() {
    byte[] flags = new byte[25];
    flags[20] = (byte) D2Collision.COLLIDE_WALL;
    flags[22] = (byte) D2Collision.COLLIDE_VISIBLE;
    D2ActiveRoom room = activeRoom(
        coordsWithSubtileOrigin(0, 0, 1, 1, 2, 0, 3, 5),
        roomTiles(tile(0, 0, dt1(flags), 0)));

    D2CommonCollision.allocRoomCollisionGrid(room);

    assertEquals(D2Collision.COLLIDE_VISIBLE, room.getPCollisionGrid().getFlag(0, 0));
    assertEquals(0, room.getPCollisionGrid().getFlag(1, 0));
  }

  @Test
  void queriesAndMutatesAcrossAdjacentRoomBoundary() {
    D2ActiveRoom first = activeRoom(coords(0, 0, 1, 1), null);
    D2ActiveRoom second = activeRoom(coords(1, 0, 1, 1), null);
    connect(first, second);
    D2CommonCollision.allocRoomCollisionGrid(first);
    D2CommonCollision.allocRoomCollisionGrid(second);
    second.getPCollisionGrid().setFlag(0, 2, D2Collision.COLLIDE_WALL);

    assertSame(second, D2CommonCollision.getRoomBySubtileCoordinates(first, 5, 2));
    assertEquals(D2Collision.COLLIDE_WALL,
        D2CommonCollision.checkMask(first, 5, 2, D2Collision.COLLIDE_WALL));
    assertEquals(D2Collision.COLLIDE_WALL,
        D2CommonCollision.checkMaskWithSizeXY(first, 4, 2, 3, 1, D2Collision.COLLIDE_WALL));
    assertEquals(D2Collision.COLLIDE_WALL,
        D2CommonCollision.checkMaskWithSize(
            first, 4, 2, D2Collision.UNIT_SIZE_SMALL, D2Collision.COLLIDE_MASK_SPAWN));

    D2CommonCollision.setMask(first, 5, 3, D2Collision.COLLIDE_OBJECT);
    assertEquals(D2Collision.COLLIDE_OBJECT, second.getPCollisionGrid().getFlag(0, 3));
    D2CommonCollision.resetMask(first, 5, 3, D2Collision.COLLIDE_OBJECT);
    assertEquals(0, second.getPCollisionGrid().getFlag(0, 3));
    assertEquals(D2Collision.COLLIDE_MASK_INVALID,
        D2CommonCollision.checkMask(first, 10, 2, D2Collision.COLLIDE_WALL));
  }

  @Test
  void dynamicReplacementRemovesOldFlagsAndAppliesNewFlags() {
    byte[] oldFlags = new byte[25];
    oldFlags[20] = (byte) (D2Collision.COLLIDE_WALL | D2Collision.COLLIDE_VISIBLE);
    byte[] newFlags = new byte[25];
    newFlags[20] = (byte) D2Collision.COLLIDE_MISSILE_BARRIER;
    D2DrlgTileDataStrc tile = tile(0, 0, dt1(oldFlags), 0);
    D2ActiveRoom room = activeRoom(coords(0, 0, 1, 1), roomTiles(tile));
    D2CommonCollision.allocRoomCollisionGrid(room);

    D2CommonCollision.firstFn(room, tile, dt1(newFlags));

    assertEquals(D2Collision.COLLIDE_MISSILE_BARRIER,
        room.getPCollisionGrid().getFlag(0, 0));
  }

  @Test
  void dungeonCallbackObservesPopulatedGridAndFreeClearsIt() {
    byte[] flags = new byte[25];
    flags[20] = (byte) D2Collision.COLLIDE_WALL;
    D2DrlgRoom drlgRoom = drlgRoom(0, 0, 1, 1);
    drlgRoom.setPpRoomsNear(new D2DrlgRoom[] {drlgRoom});
    drlgRoom.setNRoomsNear(1);
    D2DrlgAct act = new D2DrlgAct();
    AtomicBoolean callbackSawCollision = new AtomicBoolean();
    act.setPfnActCallBack(room -> callbackSawCollision.set(
        room.getPCollisionGrid() != null
            && room.getPCollisionGrid().getFlag(0, 0) == D2Collision.COLLIDE_WALL));

    D2ActiveRoom room = Dungeon.allocRoom(act, drlgRoom, coords(0, 0, 1, 1),
        roomTiles(tile(0, 0, dt1(flags), 0)), 1, 0);

    assertNotNull(room.getPCollisionGrid());
    assertTrue(callbackSawCollision.get());
    D2CommonCollision.freeRoomCollisionGrid(room);
    assertNull(room.getPCollisionGrid());
  }

  private static D2ActiveRoom activeRoom(D2DrlgCoords coords, D2DrlgRoomTilesStrc tiles) {
    D2ActiveRoom room = new D2ActiveRoom();
    room.setCoords(coords);
    room.setPRoomTiles(tiles);
    room.setPpRoomList(new D2ActiveRoom[] {room});
    room.setNNumRooms(1);
    return room;
  }

  private static void connect(D2ActiveRoom first, D2ActiveRoom second) {
    D2ActiveRoom[] rooms = {first, second};
    first.setPpRoomList(rooms);
    first.setNNumRooms(2);
    second.setPpRoomList(rooms);
    second.setNNumRooms(2);
  }

  private static D2DrlgRoomTilesStrc roomTiles(D2DrlgTileDataStrc tile) {
    D2DrlgRoomTilesStrc tiles = new D2DrlgRoomTilesStrc();
    tiles.setPFloorTiles(new D2DrlgTileDataStrc[] {tile});
    return tiles;
  }

  private static D2DrlgTileDataStrc tile(int x, int y, Object dt1, int mapFlags) {
    D2DrlgTileDataStrc tile = new D2DrlgTileDataStrc();
    tile.setNPosX(x);
    tile.setNPosY(y);
    tile.setPTile(dt1);
    tile.setDwFlags(mapFlags);
    return tile;
  }

  private static D2TileData dt1(byte[] flags) {
    D2TileData tile = new D2TileData();
    tile.setPSubTileFlags(flags);
    return tile;
  }

  private static D2DrlgRoom drlgRoom(int x, int y, int width, int height) {
    D2DrlgCoord coord = new D2DrlgCoord();
    coord.setNTileXPos(x);
    coord.setNTileYPos(y);
    coord.setNTileWidth(width);
    coord.setNTileHeight(height);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setDrlgCoord(coord);
    return room;
  }

  private static D2DrlgCoords coords(int x, int y, int width, int height) {
    return coordsWithSubtileOrigin(x, y, width, height,
        x * 5, y * 5, width * 5, height * 5);
  }

  private static D2DrlgCoords coordsWithSubtileOrigin(
      int x, int y, int width, int height,
      int subtileX, int subtileY, int subtileWidth, int subtileHeight) {
    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNTileXPos(x);
    coords.setNTileYPos(y);
    coords.setNTileWidth(width);
    coords.setNTileHeight(height);
    coords.setNSubtileX(subtileX);
    coords.setNSubtileY(subtileY);
    coords.setNSubtileWidth(subtileWidth);
    coords.setNSubtileHeight(subtileHeight);
    return coords;
  }
}
