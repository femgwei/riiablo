package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRoomMonsterPopulationResourceTest extends RiiabloTest {
  @Test void nativeActTwoThroughFiveBridgesRetainWarpRoomCentersAfterDrlgRelease() throws Exception {
    java.lang.reflect.Field metadata = Map.Zone.class.getDeclaredField("nativeMonsterSpawnExclusion");
    metadata.setAccessible(true);
    int checked = 0;
    for (int act = 1; act <= 4; act++) {
      Map map = new Map(1, 0);
      if (act == 1) map.act2Builder().generate(map, 1, 0);
      if (act == 2) map.act3Builder().generate(map, 1, 0);
      if (act == 3) map.act4Builder().generate(map, 1, 0);
      if (act == 4) map.act5Builder().generate(map, 1, 0);
      int nativeZones = 0;
      for (Map.Zone zone : map.getZones()) {
        if (zone.isTown() || zone.nativeTileGrid() == null || !zone.hasNativeRoomTopology()) continue;
        nativeZones++;
        assertNotNull(metadata.get(zone), "missing metadata act=" + act + " level=" + zone.levelId());
        int originalWarpDist = zone.level.WarpDist;
        // Some original levels disable the filter (WarpDist=0). A one-cell
        // fixture still verifies that their bridge retained the native data.
        zone.level.WarpDist = 1;
        try {
          for (Map.RoomEx room : zone.getRoomsEx()) {
            int flags = room.populationFlags();
            boolean waypoint = (flags & com.d2moo.common.drlg.D2DrlgRoomFlags.HAS_WAYPOINT_MASK) != 0;
            boolean warp = false;
            for (int slot = 0; slot < 8; slot++)
              if ((flags & (com.d2moo.common.drlg.D2DrlgRoomFlags.HAS_WARP_0 << slot)) != 0
                  && zone.level.Warp[slot] != -1) warp = true;
            if (!waypoint && !warp) continue;
            checked++;
            int x = room.x + (room.width / 5 / 2) * 5;
            int y = room.y + (room.height / 5 / 2) * 5;
            assertTrue(zone.excludesNativeMonsterSpawn(x, y), "act=" + act + " level=" + zone.levelId());
          }
        } finally {
          zone.level.WarpDist = originalWarpDist;
        }
      }
      assertTrue(nativeZones > 0, "must exercise real native exports in act=" + act);
    }
    assertTrue(checked > 0, "must check available native room centers as well as metadata presence");
  }

  @Test void rejectedWarpCandidateRetriesAndFollowersAreNotFiltered() {
    Map.Zone zone = zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry();
    zone.level.Id = 2; zone.level.WarpDist = 25;
    zone.setNativeMonsterSpawnExclusion(NativeMonsterSpawnExclusion.project(
        new int[] {10}, new int[] {10}, 1, -1, -1, 0, 0, 0, 0));
    Random random = rolls(9, 9, 14, 9, 1, 3);
    assertEquals(2, NativeRoomMonsterPopulation.addGroup(zone, zone.getRoomsEx().first(),
        Riiablo.files.monstats.get("skeleton1"), 2, 1, random));
    assertEquals(15, zone.getRoomsEx().first().getPendingMonsterSpawns().get(0).x);
    assertEquals(13, zone.getRoomsEx().first().getPendingMonsterSpawns().get(1).x);
    for (Map.MonsterSpawn spawn : zone.getRoomsEx().first().getPendingMonsterSpawns()) {
      assertTrue(spawn.hasIntegerSelection());
      assertEquals(spawn.x, (float) spawn.integerSelectionX());
      assertEquals(spawn.y, (float) spawn.integerSelectionY());
    }
  }

  @Test void exclusionUsesTwentyAttemptsButDoesNotAffectPresetMarkers() {
    Map.Zone zone = zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry();
    zone.level.Id = 2; zone.level.WarpDist = 10000;
    zone.setNativeMonsterSpawnExclusion(NativeMonsterSpawnExclusion.project(
        new int[9], new int[9], 0, 2, 2, 0, 0, 0, 0));
    int[] calls = {0};
    Random random = new Random() {
      @Override public int nextInt(int bound) { calls[0]++; return bound / 2; }
    };
    MonStats.Entry row = Riiablo.files.monstats.get("skeleton1");
    assertEquals(0, NativeRoomMonsterPopulation.addGroup(zone, zone.getRoomsEx().first(), row, 1, 1, random));
    assertEquals(40, calls[0], "twenty candidates, two coordinate rolls each");
    assertEquals(1, NativeRoomMonsterPopulation.addGroupAt(zone, zone.getRoomsEx().first(), row,
        1, 1, random, 10, 10));
  }

  @Test void missingMetadataAndLevelZeroDoNotInventAnExclusion() {
    Map.Zone zone = zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry();
    zone.level.Id = 2; zone.level.WarpDist = 10000;
    assertFalse(zone.excludesNativeMonsterSpawn(10, 10));
    zone.setNativeMonsterSpawnExclusion(NativeMonsterSpawnExclusion.project(
        new int[] {10}, new int[] {10}, 1, -1, -1, 0, 0, 0, 0));
    assertTrue(zone.excludesNativeMonsterSpawn(10, 10));
    zone.level.Id = 0;
    assertFalse(zone.excludesNativeMonsterSpawn(10, 10));
  }

  private static Random rolls(int... values) {
    return new Random() {
      int index;
      @Override public int nextInt(int bound) {
        return index < values.length ? Math.floorMod(values[index++], bound) : bound / 2;
      }
    };
  }

  @Test void retriesBlockedLeaderAndKeepsFollowersNearTheSuccessfulLeader() {
    Map.Zone zone = zone();
    java.util.Arrays.fill(zone.flags, (byte) 1);
    for (int y = 10; y < 20; y++) for (int x = 20; x < 30; x++) zone.flags[y * 40 + x] = 0;
    Random random = new Random() {
      int index;
      int[] rolls = {0,0,23,13,6,3,3,6};
      @Override public int nextInt(int bound) {
        return index < rolls.length ? Math.floorMod(rolls[index++], bound) : bound / 2;
      }
    };
    MonStats.Entry monster = Riiablo.files.monstats.get("skeleton1");
    assertNotNull(monster);
    assertEquals(3, NativeRoomMonsterPopulation.addGroup(zone, zone.getRoomsEx().first(), monster, 3, 7, random));
    for (Map.MonsterSpawn spawn : zone.getRoomsEx().first().getPendingMonsterSpawns()) {
      assertTrue(spawn.x >= 20 && spawn.x < 30 && spawn.y >= 10 && spawn.y < 20);
      assertEquals(7, spawn.packId);
    }
  }
  @Test void fullyBlockedRoomDoesNotForceSpawns() {
    Map.Zone zone = zone();
    java.util.Arrays.fill(zone.flags, (byte) 1);
    assertEquals(0, NativeRoomMonsterPopulation.addGroup(zone, zone.getRoomsEx().first(),
        Riiablo.files.monstats.get("skeleton1"), 5, 1, new Random(1)));
    assertEquals(0, zone.getRoomsEx().first().getPendingMonsterSpawns().size);
  }
  @Test void includesPartyMinionsAndPreservesPackOwnership() {
    Map.Zone zone = zone();
    MonStats.Entry template = Riiablo.files.monstats.get("skeleton1");
    MonStats.Entry monster = new MonStats.Entry();
    monster.Id = template.Id; monster.MonStatsEx = template.MonStatsEx; monster.hcIdx = template.hcIdx;
    monster.PartyMin = monster.PartyMax = 2; monster.minion1 = "skeleton1";
    Map.RoomEx room = zone.getRoomsEx().first();
    assertEquals(3, NativeRoomMonsterPopulation.addGroup(zone, room, monster, 1, 9, new Random(1)));
    assertFalse(room.getPendingMonsterSpawns().get(0).minion);
    assertTrue(room.getPendingMonsterSpawns().get(1).minion);
    assertTrue(room.getPendingMonsterSpawns().get(2).minion);
    for (Map.MonsterSpawn spawn : room.getPendingMonsterSpawns()) assertEquals(9, spawn.packId);
    assertTrue(room.claimMonsterPopulation());
    assertEquals(0, NativeRoomMonsterPopulation.addGroup(zone, room, monster, 1, 10, new Random(1)));
  }
  private static Map.Zone zone() {
    Map.Zone zone = new Map.Zone();
    zone.map = new Map(1, 0); zone.width = zone.height = 40; zone.flags = new byte[1600];
    zone.addRoomEx(0, 0, 40, 40).setAdjacentRoomIds(new int[0]);
    return zone;
  }
}
