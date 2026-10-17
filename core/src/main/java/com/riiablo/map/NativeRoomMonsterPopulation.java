package com.riiablo.map;

import java.util.Random;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.engine.server.UnitCollisionGrid;

/** Deferred normal packs for the native Act II-V room topology. */
final class NativeRoomMonsterPopulation {
  private NativeRoomMonsterPopulation() {}

  static int addGroup(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int count, int packId, Random random) {
    return addGroupAt(zone, room, monster, count, packId, random,
        Integer.MIN_VALUE, Integer.MIN_VALUE, true);
  }

  static int addGroupAt(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int count, int packId, Random random, int markerX, int markerY) {
    // DS1 preset groups (MonsterRegion cases 24/26) use their marker directly,
    // unlike sub_6FC677D0's normal population search with a7=1.
    return addGroupAt(zone, room, monster, count, packId, random, markerX, markerY, false);
  }

  private static int addGroupAt(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int count, int packId, Random random, int markerX, int markerY, boolean excludeWarps) {
    if (count <= 0 || room.width <= 1 || room.height <= 1 || room.isMonsterPopulationSpawned()) return 0;
    Map.MonsterSpawn leader = null;
    // MonsterRegion.cpp::sub_6FC66260 tries random positions in the population
    // region up to twenty times. A density hit is a budget, not a fixed cell.
    for (int attempt = 0; attempt < 20 && leader == null; attempt++) {
      int x = markerX == Integer.MIN_VALUE ? room.x + 1 + random.nextInt(room.width - 1)
          : markerX + (attempt == 0 ? 0 : random.nextInt(7) - 3);
      int y = markerY == Integer.MIN_VALUE ? room.y + 1 + random.nextInt(room.height - 1)
          : markerY + (attempt == 0 ? 0 : random.nextInt(7) - 3);
      if (excludeWarps && zone.excludesNativeMonsterSpawn(x, y)) continue;
      if (canPlace(zone, room, monster, x, y)) {
        room.addIntegerMonsterSpawn(monster.hcIdx, x, y, packId, false);
        leader = room.getPendingMonsterSpawns().peek();
      }
    }
    if (leader == null) return 0;
    com.badlogic.gdx.utils.Array<Map.MonsterSpawn> members = new com.badlogic.gdx.utils.Array<>();
    members.add(leader);
    int created = 1;
    for (int i = 1; i < count; i++) {
      if (addCompanion(zone, room, monster, leader, packId, false, random)) {
        members.add(room.getPendingMonsterSpawns().peek());
        created++;
      }
    }
    // MonsterSpawn.cpp::sub_6FC69C00 runs for each normal unit; companions
    // carry flag 0x40 so they do not recursively create their own parties.
    for (Map.MonsterSpawn member : members) {
      int party = Act1MapBuilderD2MOD.partySize(monster.PartyMin, monster.PartyMax, random.nextInt(Integer.MAX_VALUE));
      for (int i = 0; i < party; i++) {
        String id = Act1MapBuilderD2MOD.partyMinion(monster, i);
        MonStats.Entry minion = id == null ? null : zone.map.files().monstats.get(id);
        if (minion != null && addCompanion(zone, room, minion, member, packId, true, random)) created++;
      }
    }
    return created;
  }

  private static boolean addCompanion(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      Map.MonsterSpawn leader, int packId, boolean minion, Random random) {
    // MonsterSpawn's normal-pack search stays near the leader, retrying around
    // obstacles rather than discarding a member after one jittered coordinate.
    for (int attempt = 0; attempt < 20; attempt++) {
      int x = Math.round(leader.x) + random.nextInt(7) - 3;
      int y = Math.round(leader.y) + random.nextInt(7) - 3;
      if (!canPlace(zone, room, monster, x, y)) continue;
      room.addIntegerMonsterSpawn(monster.hcIdx, x, y, packId, minion);
      return true;
    }
    return false;
  }

  private static boolean canPlace(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster, int x, int y) {
    if (!room.contains(x, y)) return false;
    MonStats2.Entry stats = zone.map.files().monstats2.get(monster.MonStatsEx);
    int size = stats == null ? 2 : Math.max(1, stats.SizeX);
    if (zone.map.isBlockedForPath(zone, x, y,
        NativeGroundPathProfile.forUnit(false, monster).terrainFlags, size, false)) return false;
    if (NativeGroundPathProfile.hasNoPresence(monster) || stats != null && stats.critter) return true;
    // Deferred units must reserve native presence markers before the room is
    // activated. Checking only equal centres allowed large pack members to
    // overlap, including across adjoining RoomEx boundaries.
    for (int r = 0; r < zone.getRoomsEx().size; r++) {
      Map.RoomEx otherRoom = zone.getRoomsEx().get(r);
      for (Map.MonsterSpawn other : otherRoom.getPendingMonsterSpawns()) {
        int ox = Math.round(other.x), oy = Math.round(other.y);
        if (Math.abs(ox - x) > 2 || Math.abs(oy - y) > 2) continue;
        MonStats.Entry otherMonster = zone.map.files().monstats.get(other.monsterId);
        if (otherMonster == null || NativeGroundPathProfile.hasNoPresence(otherMonster)) continue;
        MonStats2.Entry otherStats = zone.map.files().monstats2.get(otherMonster.MonStatsEx);
        if (otherStats != null && otherStats.critter) continue;
        int otherSize = otherStats == null ? 2 : Math.max(1, otherStats.SizeX);
        if (UnitCollisionGrid.placementOverlapsMarker(x, y, size, ox, oy, otherSize)) return false;
      }
    }
    return true;
  }
}

