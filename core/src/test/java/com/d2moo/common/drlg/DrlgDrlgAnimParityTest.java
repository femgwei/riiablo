package com.d2moo.common.drlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.d2cmp.D2TileData;
import com.d2moo.common.d2cmp.D2TileLibraryHashNodeStrc;
import com.d2moo.common.d2cmp.D2TileLibraryHashRefStrc;
import com.d2moo.common.d2cmp.D2TileLibraryHashStrc;

import org.junit.jupiter.api.Test;

class DrlgDrlgAnimParityTest {
  private static final int MAPTILE_HIDDEN = 0x8;
  private static final int TILE_FLAGS_LAVA = 0x100;

  @Test
  void allocatesFramesByRarityAndUsesNativeDefaultFixedPointSpeed() {
    D2TileData frame0 = frame(0);
    D2TileData frame1 = frame(1);
    D2TileData frame2 = frame(2);
    D2DrlgRoom room = roomWithFloorCapacity(3);
    room.getTiles()[0] = hash(0, 5, 7, frame2, frame0, frame1);
    D2DrlgTileDataStrc base = room.getTileGrid().getPTiles().getPFloorTiles()[0];
    base.setPTile(frame2);
    base.setNTileType(DrlgRoomTile.TILETYPE_FLOOR);
    base.setNPosX(0);
    base.setNPosY(0);
    base.setDwFlags(1 << 14);
    room.getTileGrid().setNFloors(1);
    D2DrlgGridStrc grid = new D2DrlgGridStrc();
    DrlgDrlgGrid.initializeGridCells(null, grid, 1, 1);
    D2C_PackedTileInformation packed = new D2C_PackedTileInformation();
    packed.setBIsFloor(true);
    packed.setNTileStyle(5);
    packed.setNTileSequence(7);
    DrlgDrlgGrid.alterGridFlag(grid, 0, 0, packed.getNPackedValue(),
        DrlgDrlgGrid.FlagOperation.OVERWRITE);

    DrlgDrlgAnim.allocAnimationTileGrid(
        room, 0, room.getTileGrid().getPTiles().getPFloorTiles(), 1, grid, 1);

    D2DrlgAnimTileGridStrc animation = room.getTileGrid().getPAnimTiles();
    assertEquals(3, animation.getNFrames());
    assertEquals(80, animation.getNAnimationSpeed());
    assertSame(frame0, animation.getPpMapTileData()[0].getPTile());
    assertSame(frame1, animation.getPpMapTileData()[1].getPTile());
    assertSame(frame2, animation.getPpMapTileData()[2].getPTile());
    assertTrue((animation.getPpMapTileData()[1].getDwFlags() & MAPTILE_HIDDEN) != 0);
    assertTrue((animation.getPpMapTileData()[2].getDwFlags() & MAPTILE_HIDDEN) != 0);
    assertEquals(3, room.getTileGrid().getNFloors());
  }

  @Test
  void advancesEightBitFixedPointFrameAndTogglesVisibility() {
    D2DrlgTileDataStrc first = new D2DrlgTileDataStrc();
    D2DrlgTileDataStrc second = new D2DrlgTileDataStrc();
    second.setDwFlags(MAPTILE_HIDDEN);
    D2DrlgAnimTileGridStrc animation = new D2DrlgAnimTileGridStrc();
    animation.setPpMapTileData(new D2DrlgTileDataStrc[] {first, second});
    animation.setNFrames(2);
    animation.setNAnimationSpeed(256);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setFlags(D2DrlgRoomFlags.ANIMATED_FLOOR);
    room.setTileGrid(new D2DrlgTileGrid());
    room.getTileGrid().setPAnimTiles(animation);
    room.setPpRoomsNear(new D2DrlgRoom[] {room});
    room.setNRoomsNear(1);

    DrlgDrlgAnim.animateTiles(room);

    assertEquals(256, animation.getNCurrentFrame());
    assertTrue((first.getDwFlags() & MAPTILE_HIDDEN) != 0);
    assertEquals(0, second.getDwFlags() & MAPTILE_HIDDEN);

    DrlgDrlgAnim.animateTiles(room);
    assertEquals(0, animation.getNCurrentFrame());
    assertEquals(0, first.getDwFlags() & MAPTILE_HIDDEN);
    assertTrue((second.getDwFlags() & MAPTILE_HIDDEN) != 0);
  }

  @Test
  void copiesSourceFixedPointFrameToEveryAdjacentAnimation() {
    D2DrlgRoom source = animatedRoom(377);
    source.setPpRoomsNear(new D2DrlgRoom[] {source});
    source.setNRoomsNear(1);
    D2DrlgRoom targetA = animatedRoom(0);
    D2DrlgRoom targetB = animatedRoom(100);
    D2DrlgRoom target = new D2DrlgRoom();
    target.setPpRoomsNear(new D2DrlgRoom[] {targetA, targetB});
    target.setNRoomsNear(2);

    DrlgDrlgAnim.updateFrameInAdjacentRooms(source, target);

    assertEquals(377, targetA.getTileGrid().getPAnimTiles().getNCurrentFrame());
    assertEquals(377, targetB.getTileGrid().getPAnimTiles().getNCurrentFrame());
  }

  private static D2DrlgRoom roomWithFloorCapacity(int capacity) {
    D2DrlgRoom room = new D2DrlgRoom();
    room.setLevel(new D2DrlgLevel());
    room.getLevel().setDrlg(new D2DrlgStrc());
    room.setTileGrid(new D2DrlgTileGrid());
    D2DrlgTileDataStrc[] floors = new D2DrlgTileDataStrc[capacity];
    for (int i = 0; i < capacity; i++) floors[i] = new D2DrlgTileDataStrc();
    room.getTileGrid().getPTiles().setPFloorTiles(floors);
    return room;
  }

  private static D2DrlgRoom animatedRoom(int currentFrame) {
    D2DrlgAnimTileGridStrc animation = new D2DrlgAnimTileGridStrc();
    animation.setNCurrentFrame(currentFrame);
    D2DrlgRoom room = new D2DrlgRoom();
    room.setTileGrid(new D2DrlgTileGrid());
    room.getTileGrid().setPAnimTiles(animation);
    return room;
  }

  private static D2TileData frame(int rarity) {
    D2TileData tile = new D2TileData();
    tile.setNRarity(rarity);
    tile.setDwFlags(TILE_FLAGS_LAVA);
    return tile;
  }

  private static D2TileLibraryHashStrc hash(
      int type, int style, int sequence, D2TileData... frames) {
    D2TileLibraryHashNodeStrc node = new D2TileLibraryHashNodeStrc();
    node.setNType(type);
    node.setNStyle(style);
    node.setNSequence(sequence);
    D2TileLibraryHashRefStrc previous = null;
    for (int i = frames.length - 1; i >= 0; i--) {
      D2TileLibraryHashRefStrc ref = new D2TileLibraryHashRefStrc();
      ref.setPTile(frames[i]);
      ref.setPPrev(previous);
      previous = ref;
    }
    node.setPRef(previous);
    D2TileLibraryHashStrc hash = new D2TileLibraryHashStrc();
    hash.setPNode(0, node);
    return hash;
  }
}
