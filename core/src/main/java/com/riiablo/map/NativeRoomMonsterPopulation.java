package com.riiablo.map;

import java.util.Random;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.engine.server.monster.MonsterRank;

/** Deferred normal and random elite packs for the native Act II-V room topology. */
final class NativeRoomMonsterPopulation {
  private NativeRoomMonsterPopulation() {}

  static int addGroup(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int count, int packId, Random random) {
    for (NativeMonsterPopulationRegion region : room.populationRegions()) {
      int created = addGroup(zone, room, monster, count, packId, random, region);
      if (created > 0) return created;
    }
    return 0;
  }

  static int addGroup(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int count, int packId, Random random, NativeMonsterPopulationRegion region) {
    return addGroupAt(zone, room, monster, count, packId, random,
        Integer.MIN_VALUE, Integer.MIN_VALUE, true, region);
  }

  /**
   * Native normal-pack path.  D2GAME chooses the leader with the active room
   * seed, initializes the leader from pGameSeed, then rolls MinGrp/MaxGrp from
   * that leader seed.  Each accepted companion consumes another pGameSeed draw
   * before it is initialized as a unit.
   */
  static int addNativeGroup(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int minGroup, int maxGroup, int packId, NativeD2SeedRandom roomRandom,
      NativeD2SeedRandom gameRandom, NativeMonsterPopulationRegion region) {
    if (minGroup <= 0 || maxGroup < minGroup) return 0;
    if (room.width <= 1 || room.height <= 1 || room.isMonsterPopulationSpawned()) return 0;
    if (region != null && (region.width <= 1 || region.height <= 1)) return 0;
    Map.MonsterSpawn leader = null;
    for (int attempt = 0; attempt < 20 && leader == null; attempt++) {
      int x = region.x + 1 + roomRandom.nextInt(region.width - 1);
      int y = region.y + 1 + roomRandom.nextInt(region.height - 1);
      if (zone.excludesNativeMonsterSpawn(x, y)) continue;
      if (canPlace(zone, room, monster, x, y, region)) {
        // SUNIT_InitSeed(pMonster, &pGame->pGameSeed).
        NativeD2SeedRandom leaderSeed = new NativeD2SeedRandom(gameRandom.raw(), 666);
        room.addIntegerMonsterSpawn(monster.hcIdx, x, y, packId, false);
        leader = room.getPendingMonsterSpawns().peek();
        com.badlogic.gdx.utils.Array<Map.MonsterSpawn> members = new com.badlogic.gdx.utils.Array<>();
        com.badlogic.gdx.utils.Array<NativeD2SeedRandom> memberSeeds =
            new com.badlogic.gdx.utils.Array<>();
        members.add(leader);
        memberSeeds.add(leaderSeed);
        int count = minGroup == maxGroup ? minGroup
            : minGroup + leaderSeed.nextInt(maxGroup - minGroup + 1);
        int created = 1;
        for (int i = 1; i < count; i++) {
          NativeD2SeedRandom memberSeed = addNativeCompanion(zone, room, monster, leader, packId,
              roomRandom, gameRandom, region, false);
          if (memberSeed != null) {
            members.add(room.getPendingMonsterSpawns().peek());
            memberSeeds.add(memberSeed);
            created++;
          }
        }
        // Normal party minions are uncommon in Dry Hills, but retain the
        // existing attachment behavior for tables that use PartyMin/PartyMax.
        for (int memberIndex = 0; memberIndex < members.size; memberIndex++) {
          Map.MonsterSpawn member = members.get(memberIndex);
          NativeD2SeedRandom memberSeed = memberSeeds.get(memberIndex);
          int party = Act1MapBuilderD2MOD.partySize(monster.PartyMin, monster.PartyMax,
              memberSeed.raw());
          for (int i = 0; i < party; i++) {
            String id = Act1MapBuilderD2MOD.partyMinion(monster, i);
            MonStats.Entry minion = id == null ? null : zone.map.files().monstats.get(id);
            if (minion != null) addNativeCompanion(zone, room, minion, member, packId,
                roomRandom, gameRandom, null, true);
          }
        }
        return created;
      }
    }
    return 0;
  }

  private static NativeD2SeedRandom addNativeCompanion(Map.Zone zone, Map.RoomEx room,
      MonStats.Entry monster, Map.MonsterSpawn leader, int packId,
      NativeD2SeedRandom roomRandom, NativeD2SeedRandom gameRandom,
      NativeMonsterPopulationRegion region, boolean minion) {
    // D2GAME_SpawnNormalMonster searches a square perimeter around the owner
    // for field_20=3 (normal pack companions) or field_20=4 (party minions).
    // The perimeter is randomized once per radius; failed cells do not draw
    // another random coordinate.  This matters because the room seed is also
    // used by subsequent monster selection.
    int maxRadius = minion ? 12 : 9;
    for (int radius = 3; radius <= maxRadius; radius += 3) {
      int perimeter = 8 * radius;
      // The native routine consumes four room-seed rolls per ring: choose the
      // side, choose an offset in [0, radius), then choose the sign of the
      // offset and the sign of the fixed radius coordinate.  It then walks
      // the perimeter in one direction without drawing again for each cell.
      boolean verticalSide = (roomRandom.raw() & 1) != 0;
      int offset = roomRandom.nextInt(radius);
      boolean offsetNegative = (roomRandom.raw() & 1) != 0;
      boolean sideNegative = (roomRandom.raw() & 1) != 0;
      int centerX = Math.round(leader.x), centerY = Math.round(leader.y);
      int startX, startY;
      if (verticalSide) {
        startX = centerX + (sideNegative ? -radius : radius);
        startY = centerY + (offsetNegative ? -offset : offset);
      } else {
        startX = centerX + (offsetNegative ? -offset : offset);
        startY = centerY + (sideNegative ? -radius : radius);
      }
      int start = squareRingIndex(centerX, centerY, radius, startX, startY);
      for (int step = 0; step < perimeter; step++) {
        int index = start + step;
        index %= perimeter;
        if (index < 0) index += perimeter;
        int[] point = squareRingPoint(centerX, centerY, radius, index);
        int x = point[0], y = point[1];
        if (!canPlace(zone, room, monster, x, y, region)) continue;
        // Every successful normal monster allocation advances pGameSeed.
        NativeD2SeedRandom unitSeed = new NativeD2SeedRandom(gameRandom.raw(), 666);
        room.addIntegerMonsterSpawn(monster.hcIdx, x, y, packId, minion);
        return unitSeed;
      }
    }
    return null;
  }

  private static int[] squareRingPoint(int centerX, int centerY, int radius, int index) {
    int side = index / (2 * radius);
    int offset = index % (2 * radius);
    switch (side) {
      case 0: return new int[] {centerX - radius + offset, centerY - radius};
      case 1: return new int[] {centerX + radius, centerY - radius + offset};
      case 2: return new int[] {centerX + radius - offset, centerY + radius};
      default: return new int[] {centerX - radius, centerY + radius - offset};
    }
  }

  private static int squareRingIndex(int centerX, int centerY, int radius, int x, int y) {
    if (y == centerY - radius) return x - (centerX - radius);
    if (x == centerX + radius) return 2 * radius + (y - (centerY - radius));
    if (y == centerY + radius) return 4 * radius + (centerX + radius - x);
    return 6 * radius + (centerY + radius - y);
  }

  /** Returns the native region unique-count increment, not the total unit count. */
  static int addElite(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int packId, Random random, NativeMonsterEliteRules rules) {
    for (NativeMonsterPopulationRegion region : room.populationRegions()) {
      int created = addElite(zone, room, monster, packId, random, rules, region);
      if (created > 0) return created;
    }
    return 0;
  }

  static int addElite(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int packId, Random random, NativeMonsterEliteRules rules, NativeMonsterPopulationRegion region) {
    if (region.width <= 1 || region.height <= 1 || room.isMonsterPopulationSpawned()) return 0;
    for (int attempt = 0; attempt < 20; attempt++) {
      int x = region.x + 1 + random.nextInt(region.width - 1);
      int y = region.y + 1 + random.nextInt(region.height - 1);
      if (zone.excludesNativeMonsterSpawn(x, y) || !canPlace(zone, room, monster, x, y, region)) continue;
      NativeMonsterEliteRules.Roll roll = rules.roll(monster,
          zone.map.files().monstats2.get(monster.MonStatsEx), zone.diff, random);
      room.addIntegerMonsterSpawn(monster.hcIdx, x, y, packId, false,
          roll.champion ? MonsterRank.CHAMPION : MonsterRank.UNIQUE, roll.affixes, roll.championType);
      Map.MonsterSpawn leader = room.getPendingMonsterSpawns().peek();
      int eliteCount = 1;
      // MonsterUnique::SpawnMinions only creates servants for uniques; the
      // champion peers are added by MonsterRegion and each counts as a boss.
      int followers = roll.champion ? 1 + random.nextInt(3) : 3 + random.nextInt(4);
      MonStats.Entry follower = roll.champion || monster.minion1 == null ? null
          : zone.map.files().monstats.get(monster.minion1);
      if (follower == null) follower = monster;
      for (int i = 0; i < followers; i++) {
        boolean created = addCompanion(zone, room, follower, leader, packId, !roll.champion, random,
            roll.champion ? MonsterRank.CHAMPION : MonsterRank.MINION,
            roll.champion ? 0L : roll.minionAffixes,
            roll.champion ? MonsterRank.CHAMP_CHAMPION : -1, region);
        if (created && roll.champion) eliteCount++;
      }
      return eliteCount;
    }
    return 0;
  }

  static int addGroupAt(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int count, int packId, Random random, int markerX, int markerY) {
    // DS1 preset groups (MonsterRegion cases 24/26) use their marker directly,
    // unlike sub_6FC677D0's normal population search with a7=1.
    return addGroupAt(zone, room, monster, count, packId, random, markerX, markerY, false, null);
  }

  private static int addGroupAt(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int count, int packId, Random random, int markerX, int markerY, boolean excludeWarps,
      NativeMonsterPopulationRegion region) {
    if (count <= 0 || room.width <= 1 || room.height <= 1 || room.isMonsterPopulationSpawned()) return 0;
    if (region != null && (region.width <= 1 || region.height <= 1)) return 0;
    Map.MonsterSpawn leader = null;
    // MonsterRegion.cpp::sub_6FC66260 tries random positions in the population
    // region up to twenty times. A density hit is a budget, not a fixed cell.
    for (int attempt = 0; attempt < 20 && leader == null; attempt++) {
      int x = markerX == Integer.MIN_VALUE ? region.x + 1 + random.nextInt(region.width - 1)
          : markerX + (attempt == 0 ? 0 : random.nextInt(7) - 3);
      int y = markerY == Integer.MIN_VALUE ? region.y + 1 + random.nextInt(region.height - 1)
          : markerY + (attempt == 0 ? 0 : random.nextInt(7) - 3);
      if (excludeWarps && zone.excludesNativeMonsterSpawn(x, y)) continue;
      if (canPlace(zone, room, monster, x, y, region)) {
        room.addIntegerMonsterSpawn(monster.hcIdx, x, y, packId, false);
        leader = room.getPendingMonsterSpawns().peek();
      }
    }
    if (leader == null) return 0;
    com.badlogic.gdx.utils.Array<Map.MonsterSpawn> members = new com.badlogic.gdx.utils.Array<>();
    members.add(leader);
    int created = 1;
    for (int i = 1; i < count; i++) {
      if (addCompanion(zone, room, monster, leader, packId, false, random, region)) {
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
        // sub_6FC69C00 explicitly clears pRoomCoordList for normal parties.
        // These attached minions can use nearby terrain outside the pack's list.
        if (minion != null && addCompanion(zone, room, minion, member, packId, true, random, null)) created++;
      }
    }
    return created;
  }

  private static boolean addCompanion(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      Map.MonsterSpawn leader, int packId, boolean minion, Random random, NativeMonsterPopulationRegion region) {
    return addCompanion(zone, room, monster, leader, packId, minion, random,
        MonsterRank.NORMAL, 0L, -1, region);
  }

  private static boolean addCompanion(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      Map.MonsterSpawn leader, int packId, boolean minion, Random random,
      int rank, long affixes, int championType, NativeMonsterPopulationRegion region) {
    // MonsterSpawn's normal-pack search stays near the leader, retrying around
    // obstacles rather than discarding a member after one jittered coordinate.
    for (int attempt = 0; attempt < 20; attempt++) {
      int x = Math.round(leader.x) + random.nextInt(7) - 3;
      int y = Math.round(leader.y) + random.nextInt(7) - 3;
      if (!canPlace(zone, room, monster, x, y, region)) continue;
      room.addIntegerMonsterSpawn(monster.hcIdx, x, y, packId, minion, rank, affixes, championType);
      return true;
    }
    return false;
  }

  private static boolean canPlace(Map.Zone zone, Map.RoomEx room, MonStats.Entry monster,
      int x, int y, NativeMonsterPopulationRegion region) {
    if (!room.contains(x, y) || (region != null && !region.contains(x, y))) return false;
    MonStats2.Entry stats = zone.map.files().monstats2.get(monster.MonStatsEx);
    int size = stats == null ? 2 : Math.max(1, stats.SizeX);
    if (zone.map.isBlockedForPath(zone, x, y,
        NativeGroundPathProfile.forUnit(false, monster).terrainFlags, size, false)) return false;
    for (Map.MonsterSpawn other : room.getPendingMonsterSpawns())
      if (Math.round(other.x) == x && Math.round(other.y) == y) return false;
    return true;
  }
}

