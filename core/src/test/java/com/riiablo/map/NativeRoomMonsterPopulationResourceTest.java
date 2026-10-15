package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRoomMonsterPopulationResourceTest extends RiiabloTest {
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
