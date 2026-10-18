package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.server.monster.MonsterRank;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMonsterElitePopulationResourceTest extends RiiabloTest {
  @Test void originalTablesProduceChampionPeersAndUniqueServantsWithoutNormalPartyExpansion() {
    NativeMonsterEliteRules rules = NativeMonsterEliteRules.load(Riiablo.mpqs);
    boolean sawTwo = false, sawFour = false, sawThree = false, sawSix = false;
    for (int seed = 0; seed < 40; seed++) {
      for (boolean champion : new boolean[] {true, false}) {
        Map.Zone zone = emptyZone();
        Map.RoomEx room = zone.getRoomsEx().first();
        // Unraveler normal parties are 2..8 skeletons; elites must use the
        // independent champion / unique rules instead of expanding this.
        MonStats.Entry monster = Riiablo.files.monstats.get("unraveler1");
        Random random = new Random(seed) {
          @Override public int nextInt(int bound) {
            return bound == 100 ? (champion ? 19 : 20) : super.nextInt(bound);
          }
        };
        int increment = NativeRoomMonsterPopulation.addElite(zone, room, monster, 17, random, rules);
        int count = room.getPendingMonsterSpawns().size;
        assertEquals(champion ? count : 1, increment);
        assertTrue(count >= (champion ? 2 : 4) && count <= (champion ? 4 : 7));
        Map.MonsterSpawn leader = room.getPendingMonsterSpawns().first();
        assertEquals(champion ? MonsterRank.CHAMPION : MonsterRank.UNIQUE, leader.rank);
        assertFalse(leader.minion);
        if (!champion) assertNotEquals(0L, leader.affixes, "native eligible unique modifier must be retained");
        for (int i = 1; i < count; i++) {
          Map.MonsterSpawn follower = room.getPendingMonsterSpawns().get(i);
          assertEquals(17, follower.packId);
          assertTrue(follower.hasIntegerSelection());
          assertEquals(!champion, follower.minion);
          assertEquals(champion ? MonsterRank.CHAMPION : MonsterRank.MINION, follower.rank);
          assertEquals(champion ? monster.hcIdx : Riiablo.files.monstats.get(monster.minion1).hcIdx,
              follower.monsterId);
          if (champion) assertEquals(MonsterRank.CHAMP_CHAMPION, follower.championType);
          else assertEquals(0L, follower.affixes & ~leader.affixes);
        }
        if (champion) { sawTwo |= count == 2; sawFour |= count == 4; }
        else { sawThree |= count == 4; sawSix |= count == 7; }
      }
    }
    assertTrue(sawTwo && sawFour && sawThree && sawSix, "exercise both inclusive size bounds");
  }

  @Test void fullyBlockedEliteDoesNotConsumeBudgetOrRollModifiers() {
    Map.Zone zone = emptyZone();
    java.util.Arrays.fill(zone.flags, (byte) 1);
    assertEquals(0, NativeRoomMonsterPopulation.addElite(zone, zone.getRoomsEx().first(),
        Riiablo.files.monstats.get("skeleton1"), 1, new Random(1), null));
    assertEquals(0, zone.getRoomsEx().first().getPendingMonsterSpawns().size);
  }

  @Test void originalActTwoNormalPartySizesAreNotReplacedWithAFixedThree() {
    for (String id : new String[] {"scarab1", "unraveler1", "swarm1"}) {
      MonStats.Entry monster = Riiablo.files.monstats.get(id);
      boolean single = com.riiablo.engine.server.monster.NativeMonsterRegion
          .isSingleMemberNormalGroup(monster.BaseId, monster.Id);
      int min = single ? 1 : monster.MinGrp, max = single ? 1 : monster.MaxGrp;
      boolean varied = false;
      int previous = -1;
      for (int seed = 0; seed < 40; seed++) {
        Map.Zone zone = emptyZone();
        Random random = new Random(seed);
        int planned = min + random.nextInt(max - min + 1);
        int count = NativeRoomMonsterPopulation.addGroup(zone, zone.getRoomsEx().first(),
            monster, planned, 1, random);
        assertTrue(count >= min * (1 + monster.PartyMin) && count <= max * (1 + monster.PartyMax), id);
        varied |= previous >= 0 && previous != count;
        previous = count;
      }
      assertTrue(varied, id + " must not always have three members");
    }
  }

  @Test void realActTwoTerrainQueuesBothEliteKindsOnceInAllDifficulties() {
    boolean loadData = DT1.loadData;
    DT1.loadData = false;
    try {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        Map map = new Map(1, difficulty);
        map.act2Builder().generate(map, 1, difficulty);
        int champions = 0, uniques = 0, minions = 0;
        // All five outdoor desert levels exercise real collision, warp
        // exclusions, density and native Normal/NM/Hell region thresholds.
        for (Map.Zone zone : map.getZones()) {
          if (zone.levelId() < 41 || zone.levelId() > 45) continue;
          TileGrid grid = zone.nativeTileGrid();
          assertNotNull(grid);
          zone.dt1s = new DT1s();
          for (int index = 1; index <= 255; index++) {
            String source = grid.sourceFile((byte) index);
            if (source != null) zone.dt1s.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
          }
          Act1MapBuilderD2MOD.applyExportedTileGrid(zone, grid);
          zone.generator.init(zone);
          zone.generator.finish(zone);
          int pending = pending(zone);
          assertTrue(pending > 0);
          for (Map.RoomEx room : zone.getRoomsEx()) for (Map.MonsterSpawn spawn : room.getPendingMonsterSpawns()) {
            if (spawn.rank == MonsterRank.CHAMPION) champions++;
            if (spawn.rank == MonsterRank.UNIQUE) uniques++;
            if (spawn.rank == MonsterRank.MINION) minions++;
          }
          zone.generator.finish(zone);
          assertEquals(pending, pending(zone), "finish must not queue a second population");
        }
        System.out.println("[A2_ELITE_POPULATION] difficulty=" + difficulty
            + " champions=" + champions + " uniques=" + uniques + " minions=" + minions);
        assertTrue(champions > 0 && uniques > 0 && minions > 0, "difficulty=" + difficulty);
      }
    } finally {
      DT1.loadData = loadData;
    }
  }

  private static int pending(Map.Zone zone) {
    int count = 0;
    for (Map.RoomEx room : zone.getRoomsEx()) count += room.getPendingMonsterSpawns().size;
    return count;
  }
  private static Map.Zone emptyZone() {
    Map.Zone zone = new Map.Zone();
    zone.map = new Map(1, 0); zone.width = zone.height = 40; zone.flags = new byte[1600];
    zone.addRoomEx(0, 0, 40, 40).setAdjacentRoomIds(new int[0]);
    return zone;
  }
}
