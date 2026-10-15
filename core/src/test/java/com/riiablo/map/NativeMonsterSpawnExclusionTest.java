package com.riiablo.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonsterSpawnExclusionTest {
  @Test void captureCopiesNativeGraphWithoutActivatingRoomOrAdvancingSeeds() {
    com.d2moo.common.drlg.D2DrlgStrc drlg = new com.d2moo.common.drlg.D2DrlgStrc();
    com.d2moo.common.drlg.D2DrlgLevel level = new com.d2moo.common.drlg.D2DrlgLevel();
    level.setLevelId(2); level.setDrlg(drlg); drlg.setLevel(level);
    level.getLevelCoords().setNPosX(100); level.getLevelCoords().setNPosY(200);
    level.getLevelCoords().setNWidth(40); level.getLevelCoords().setNHeight(40);
    level.setSeed(new com.d2moo.common.drlg.D2Seed(123, 456));
    com.d2moo.common.drlg.D2DrlgRoom room = new com.d2moo.common.drlg.D2DrlgRoom();
    room.setLevel(level); level.setFirstRoomEx(room);
    room.setNTileXPos(100); room.setNTileYPos(200);
    room.setNTileWidth(40); room.setNTileHeight(40);
    level.getRoomCenterWarpX()[0] = 510; level.getRoomCenterWarpY()[0] = 1020;
    level.setNRoomCoords(1);
    Map.Zone zone = new Map.Zone();
    zone.x = -50; zone.y = -60;
    zone.level = new com.riiablo.codec.excel.Levels.Entry();
    zone.level.Id = 2; zone.level.WarpDist = 1;
    long seedBefore = level.getSeed().getLSeed();
    NativeMonsterSpawnExclusion.capture(level, zone);
    assertNull(room.getRoom(), "metadata capture must not initialize an active room");
    assertEquals(seedBefore, level.getSeed().getLSeed());
    assertTrue(zone.excludesNativeMonsterSpawn(-40, -40)); // Copied center.
    assertTrue(zone.excludesNativeMonsterSpawn(50, 40)); // Fallback tile spawn, no +3.
    level.getRoomCenterWarpX()[0] = 999;
    assertTrue(zone.excludesNativeMonsterSpawn(-40, -40));
  }

  @Test void projectsCopiedRoomCentersAndSpawnWithDifferentUnits() {
    int[] x = {510}, y = {1020};
    NativeMonsterSpawnExclusion data = NativeMonsterSpawnExclusion.project(
        x, y, 1, 106, 208, 100, 200, -50, -60);
    x[0] = y[0] = 0; // Native graph may now be freed/reused.
    assertTrue(data.excludes(-40, -40, 25)); // Warp: already subtiles.
    assertTrue(data.excludes(-20, -20, 25)); // Spawn: tiles * 5, no Ex +3.
    assertFalse(data.excludes(-35, -40, 25)); // Strict boundary at 5^2.
    assertTrue(data.excludes(-36, -40, 25));
    assertFalse(data.excludes(510, 1020, 25));
  }

  @Test void ignoresUnusedSlotsAndNonpositiveNativeSpawnBeforeProjection() {
    NativeMonsterSpawnExclusion data = NativeMonsterSpawnExclusion.project(
        new int[] {10, 20}, new int[] {10, 20}, 1, 0, 10, 0, 0, 0, 0);
    assertTrue(data.excludes(10, 10, 1));
    assertFalse(data.excludes(20, 20, 1));
    assertFalse(data.excludes(0, 50, 1));
    assertFalse(data.excludes(10, 10, 0));
    assertFalse(data.excludes(10, 10, -1));
  }

  @Test void spawnIsCheckedEvenWithoutWarpRoomCenters() {
    NativeMonsterSpawnExclusion data = NativeMonsterSpawnExclusion.project(
        new int[9], new int[9], 0, 10, 20, 0, 0, 0, 0);
    assertTrue(data.excludes(50, 100, 1));
    assertFalse(data.excludes(53, 103, 1));
  }
}
