package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import java.util.HashMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TravincalMonsterPopulationResourceTest extends RiiabloTest {
  @ParameterizedTest @ValueSource(ints = {127897359, 1, 2})
  void packsStayWithinOneNativeCoordinateListAfterDrlgRelease(int seed) {
    boolean previous = DT1.loadData;
    DT1.loadData = false;
    Map map = new Map(seed, 0);
    try {
      map.generate(2);
      Map.Zone zone = map.findZone(Riiablo.files.Levels.get(83));
      TileGrid grid = zone.nativeTileGrid();
      assertNotNull(grid);
      zone.tiles[Map.FLOOR_OFFSET] = Map.Zone.obtainTileArray(grid.width * grid.height);
      zone.dt1s = new DT1s();
      for (int i = 1; i <= 255; i++) {
        String source = grid.sourceFile((byte) i);
        if (source != null) zone.dt1s.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
      }
      Act1MapBuilderD2MOD.applyExportedTileGrid(zone, grid);
      byte[] collision = zone.flags.clone();
      if (seed == 127897359) {
        Map.RoomEx room = zone.getRoomsEx().get(30);
        assertEquals(1680, room.x); assertEquals(-2120, room.y);
        assertEquals(40, room.width); assertEquals(40, room.height);
        NativeMonsterPopulationRegion region = containing(room, 1697, -2089);
        assertNotNull(region, "the logged room30 location remains eligible for population");
        assertTrue(region.contains(1698, -2088));
        assertFalse(region.contains(1700, -2085), "the old whole-room search crossed into another list");
        assertFalse(region.contains(1699, -2096), "waypoint and this pack are in different lists");
      }
      zone.generator.init(zone);
      zone.generator.finish(zone);
      int population = 0;
      for (Map.RoomEx room : zone.getRoomsEx()) {
        HashMap<Integer, NativeMonsterPopulationRegion> packs = new HashMap<>();
        for (Map.MonsterSpawn spawn : room.getPendingMonsterSpawns()) {
          if (spawn.packId < 0) continue; // DS1 preset units have their own placement rules.
          population++;
          NativeMonsterPopulationRegion region = packs.get(spawn.packId);
          if (region == null) {
            region = containing(room, Math.round(spawn.x), Math.round(spawn.y));
            assertNotNull(region, "leader outside native population regions");
            packs.put(spawn.packId, region);
          }
          if (spawn.minion && spawn.rank == com.riiablo.engine.server.monster.MonsterRank.NORMAL) continue;
          assertTrue(region.contains(Math.round(spawn.x), Math.round(spawn.y)),
              "pack crossed a coordinate-list boundary: room=" + room.id + " pack=" + spawn.packId);
        }
      }
      assertTrue(population > 0, "legal terrain must still produce random monsters");
      assertArrayEquals(collision, zone.flags, "population must not change terrain collision");
      int before = population;
      zone.generator.finish(zone);
      population = 0;
      for (Map.RoomEx room : zone.getRoomsEx()) for (Map.MonsterSpawn spawn : room.getPendingMonsterSpawns())
        if (spawn.packId >= 0) population++;
      assertEquals(before, population, "population is queued only once");
    } finally { map.dispose(); DT1.loadData = previous; }
  }

  private static NativeMonsterPopulationRegion containing(Map.RoomEx room, int x, int y) {
    for (NativeMonsterPopulationRegion region : room.populationRegions()) if (region.contains(x, y)) return region;
    return null;
  }
}
