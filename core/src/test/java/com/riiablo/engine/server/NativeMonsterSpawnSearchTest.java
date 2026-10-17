package com.riiablo.engine.server;

import com.d2moo.common.drlg.D2Seed;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonsterSpawnSearchTest {
  static com.d2moo.common.drlg.D2ActiveRoom waterRoom(int count) {
    com.d2moo.common.drlg.D2ActiveRoom room = new com.d2moo.common.drlg.D2ActiveRoom();
    room.setNTileXPos(20); room.setNTileYPos(40);
    com.d2moo.common.drlg.D2DrlgRoomTilesStrc tiles = new com.d2moo.common.drlg.D2DrlgRoomTilesStrc();
    com.d2moo.common.drlg.D2DrlgTileDataStrc[] floors = new com.d2moo.common.drlg.D2DrlgTileDataStrc[count];
    for (int i = 0; i < count; i++) {
      floors[i] = new com.d2moo.common.drlg.D2DrlgTileDataStrc();
      floors[i].setNPosX(i);
      com.d2moo.common.d2cmp.D2TileData tile = new com.d2moo.common.d2cmp.D2TileData();
      tile.setDwFlags(2); floors[i].setPTile(tile);
    }
    tiles.setPFloorTiles(floors); room.setPRoomTiles(tiles);
    return room;
  }

  @Test void waterFloorChecksOrderedBerthsButSpawnsAtCenter() {
    D2Seed seed = new D2Seed(0, 666);
    ArrayList<String> probes = new ArrayList<>();
    int[] selected = new int[2];
    assertTrue(NativeMonsterSpawnSearch.searchSpecialFloor(seed, waterRoom(3), (x, y, size, mask) -> {
      probes.add(x + "," + y + "," + size + "," + Integer.toHexString(mask));
      return size == 1 || probes.size() == 4;
    }, (x, y) -> { selected[0] = x; selected[1] = y; return true; }));
    assertEquals(java.util.Arrays.asList("108,203,1,100", "108,200,2,1c09",
        "111,203,2,1c09", "108,206,2,1c09"), probes);
    assertArrayEquals(new int[] {108, 203}, selected);
    assertEquals(666, seed.getNLowSeed()); assertEquals(0, seed.getNHighSeed());
  }

  @Test void waterFloorTraversalKeepsNativeSkippedPredecessor() {
    ArrayList<Integer> probes = new ArrayList<>();
    assertFalse(NativeMonsterSpawnSearch.searchSpecialFloor(new D2Seed(0, 666), waterRoom(4),
        (x, y, size, mask) -> { probes.add(x); return false; }, (x, y) -> true));
    assertEquals(java.util.Arrays.asList(113, 118, 103), probes);
  }

  @Test void allFourBlockedBerthsAdvanceToNextFloorWithoutExtraRolls() {
    D2Seed seed = new D2Seed(0, 666);
    ArrayList<String> berths = new ArrayList<>();
    assertFalse(NativeMonsterSpawnSearch.searchSpecialFloor(seed, waterRoom(3), (x, y, size, mask) -> {
      if (size == 1) return true;
      berths.add(x + "," + y); return false;
    }, (x, y) -> { fail("all berths blocked"); return true; }));
    assertEquals(java.util.Arrays.asList("108,200", "111,203", "108,206", "105,203",
        "113,200", "116,203", "113,206", "110,203"), berths);
    assertEquals(666, seed.getNLowSeed()); assertEquals(0, seed.getNHighSeed());
  }

  @Test void missingAndSingletonFloorMetadataFailSafelyWithCorrectRollBudget() {
    D2Seed seed = new D2Seed(0, 666);
    assertFalse(NativeMonsterSpawnSearch.searchSpecialFloor(seed, null,
        (x, y, size, mask) -> { fail("no collision probe"); return true; }, (x, y) -> true));
    assertEquals(0, seed.getNLowSeed());
    assertFalse(NativeMonsterSpawnSearch.searchSpecialFloor(seed, waterRoom(1),
        (x, y, size, mask) -> { fail("native singleton index is invalid"); return true; }, (x, y) -> true));
    assertEquals(666, seed.getNLowSeed());
  }

  @Test void dryAndNullFloorRecordsDoNotBecomeWaterCandidates() {
    com.d2moo.common.drlg.D2ActiveRoom room = waterRoom(3);
    room.getPRoomTiles().getPFloorTiles()[1] = null;
    ((com.d2moo.common.d2cmp.D2TileData) room.getPRoomTiles().getPFloorTiles()[2].getPTile()).setDwFlags(1);
    assertFalse(NativeMonsterSpawnSearch.searchSpecialFloor(new D2Seed(0, 666), room,
        (x, y, size, mask) -> { fail("not water"); return true; }, (x, y) -> true));
  }

  @Test void tentaclesAndHellMeteorUseNormalBranchEvenWithSpawnColumnOne() {
    assertTrue(NativeMonsterSpawnSearch.usesSpecialFloor(257, 1));
    for (int id = 258; id <= 263; id++) assertFalse(NativeMonsterSpawnSearch.usesSpecialFloor(id, 1));
    assertTrue(NativeMonsterSpawnSearch.usesSpecialFloor(264, 1));
    assertFalse(NativeMonsterSpawnSearch.usesSpecialFloor(153, 1));
    assertFalse(NativeMonsterSpawnSearch.usesSpecialFloor(257, 0));
  }

  @Test void exactSearchStillConsumesThreeRoomRolls() {
    D2Seed seed = new D2Seed(0, 666);
    assertTrue(NativeMonsterSpawnSearch.search(seed, 100, 200, 0, (x, y) -> x == 100 && y == 200));
    assertEquals(3979650335L, Integer.toUnsignedLong(seed.getNLowSeed()));
    assertEquals(1403592407, seed.getNHighSeed());
  }

  @Test void binaryVerifiedStartAndAdvanceBeforeProbeArePreserved() {
    D2Seed seed = new D2Seed(0, 666);
    ArrayList<String> visited = new ArrayList<>();
    assertTrue(NativeMonsterSpawnSearch.search(seed, 100, 200, 6, (x, y) -> {
      visited.add(x + "," + y);
      return visited.size() == 6;
    }));
    // 1.10F uses initial +X even on the positive Y edge; do not normalize
    // the direction or deduplicate its probes into a different traversal.
    assertEquals(java.util.Arrays.asList("99,203", "100,203", "101,203", "102,203", "103,203", "102,203"), visited);
    assertEquals(101162930, seed.getNLowSeed());
    assertEquals(1659881787, seed.getNHighSeed());
  }

  @Test void failedRingsHaveNativeRadiusAndAttemptBounds() {
    int[] attempts = {0};
    assertFalse(NativeMonsterSpawnSearch.search(new D2Seed(1, 666), 100, 100, 6, (x, y) -> {
      assertTrue(Math.max(Math.abs(x - 100), Math.abs(y - 100)) == 3
          || Math.max(Math.abs(x - 100), Math.abs(y - 100)) == 6);
      attempts[0]++; return false;
    }));
    assertEquals(24 + 48, attempts[0]);
  }

  @Test void spawnColumnSelectsNativeMaskNotMovementProfile() {
    assertEquals(0x3c01, NativeMonsterSpawnSearch.collisionMask(0));
    assertEquals(0x1c0, NativeMonsterSpawnSearch.collisionMask(1));
    assertEquals(0x3f11, NativeMonsterSpawnSearch.collisionMask(2));
    assertEquals(0, NativeMonsterSpawnSearch.collisionMask(3));
  }
}
