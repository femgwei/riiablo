package com.riiablo.map;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.server.item.GroundDropPosition;
import java.util.Collections;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TravincalWaterPlacementResourceTest extends RiiabloTest {
  @ParameterizedTest @ValueSource(ints = {127897359, 1, 2})
  void real110fPoolEdgesRejectObjectsAndDropsButKeepPaving(int seed) {
    boolean previous = DT1.loadData;
    DT1.loadData = false;
    Map map = new Map(seed, 0);
    try {
      map.generate(2);
      Map.Zone zone = map.findZone(Riiablo.files.Levels.get(83));
      assertNotNull(zone);
      TileGrid grid = zone.nativeTileGrid();
      zone.tiles[Map.FLOOR_OFFSET] = Map.Zone.obtainTileArray(grid.width * grid.height);
      zone.dt1s = new DT1s();
      for (int i = 1; i <= 255; i++) {
        String source = grid.sourceFile((byte) i);
        if (source != null) zone.dt1s.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
      }
      Act1MapBuilderD2MOD.applyExportedTileGrid(zone, grid);
      // game.log: random chest 186 at world=(1773,-2145), local=(133,135).
      // This pool edge has material=0, unlike its material=2 interior.
      int edgeX = zone.x() + 133, edgeY = zone.y() + 135;
      DT1.Tile edge = zone.get(Map.FLOOR_OFFSET, zone.tx + 26, zone.ty + 27);
      assertNotNull(edge, "requires real DT1 headers, not an empty floor array");
      assertEquals(29, edge.mainIndex);
      assertEquals(2, edge.subIndex);
      assertEquals(0, edge.materialFlags());
      assertTrue(zone.isWater(edgeX, edgeY), "untagged shoreline was missed");
      assertFalse(NativeObjectPopulation.isDryFootprint(zone, edgeX, edgeY, 1, 1));
      // A dry center cannot permit a wide container to extend into the pool.
      int dryX = zone.x() + 133, dryY = zone.y() + 132;
      assertFalse(zone.isWater(dryX, dryY));
      assertFalse(NativeObjectPopulation.isDryFootprint(zone, dryX, dryY, 1, 7));

      // The previous style-20 workaround incorrectly rejected normal paving.
      int pavingX = zone.x() + 143, pavingY = zone.y() + 125;
      DT1.Tile paving = zone.get(Map.FLOOR_OFFSET, zone.tx + 28, zone.ty + 25);
      assertEquals(20, paving.mainIndex);
      assertFalse(zone.isWater(pavingX, pavingY));
      assertTrue(NativeObjectPopulation.isDryFootprint(zone, pavingX, pavingY, 1, 1));

      int waterWatchers = 0;
      for (Map.NativeMonster monster : zone.getNativeMonsters()) {
        if (monster.monsterId != 261) continue;
        waterWatchers++;
        assertTrue(zone.isWater(zone.x() + Math.round(monster.x),
            zone.y() + Math.round(monster.y)), "native water watcher must remain in its pool");
      }
      assertEquals(4, waterWatchers);

      // Runtime mask clears must not erase the terrain-based water decision.
      zone.resetNativeCollisionMask(edgeX, edgeY, 0xFFFF);
      assertTrue(zone.isWater(edgeX, edgeY));
      Vector2 drop = GroundDropPosition.findFree(edgeX, edgeY, Collections.emptySet(), 50,
          new Vector2(), (x, y) -> zone.contains(x, y) && !zone.isWater(x, y)
              && (zone.staticFlagsWorld(x, y) & DT1.Tile.FLAG_BLOCK_WALK) == 0);
      assertFalse(zone.isWater(Math.round(drop.x), Math.round(drop.y)));
      assertNotEquals(new Vector2(edgeX, edgeY), drop);

      int before = zone.getNativeObjects().size;
      NativeObjectPopulation population = new NativeObjectPopulation();
      int added = population.populate(zone, null);
      assertTrue(added > 0, "dry-land population must remain enabled");
      for (int i = before; i < zone.getNativeObjects().size; i++) {
        Map.NativeObject object = zone.getNativeObjects().get(i);
        com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(object.presetIndex);
        assertTrue(NativeObjectPopulation.isDryFootprint(zone, zone.x() + object.x,
            zone.y() + object.y, base.SizeX, base.SizeY), object.sourceFile);
      }
      assertEquals(0, population.populate(zone, null));
    } finally {
      map.dispose();
      DT1.loadData = previous;
    }
  }
}
