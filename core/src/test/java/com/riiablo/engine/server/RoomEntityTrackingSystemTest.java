package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomEntityTrackingSystemTest {
  @Test void offsetBoundaryTriggersKeepTheirPresetOwnerWhileMobileUnitsChangeZones() throws Exception {
    Map map = new Map(12345, 0);
    Map.Zone source = new Map.Zone(), neighbor = new Map.Zone();
    java.lang.reflect.Field flags = Map.Zone.class.getDeclaredField("flags");
    flags.setAccessible(true);
    flags.set(source, new byte[0]);
    flags.set(neighbor, new byte[0]);
    source.setNativeTileGrid(new TileGrid(2, 2), 0);
    neighbor.setNativeTileGrid(new TileGrid(2, 2), 0);
    neighbor.setPosition(10, 0);
    map.getZones().add(source);
    map.getZones().add(neighbor);
    World world = new World(new WorldConfigurationBuilder().with(new RoomEntityTrackingSystem()).build());
    try {
      int warp = world.create(), object = world.create(), mobile = world.create();
      for (int id : new int[] {warp, object, mobile}) {
        world.getMapper(Position.class).create(id).position.set(10, 5);
        world.getMapper(MapWrapper.class).create(id).set(map, source);
      }
      world.getMapper(Warp.class).create(warp);
      world.getMapper(com.riiablo.engine.server.component.Object.class).create(object);
      world.process();
      assertSame(source, world.getMapper(MapWrapper.class).get(warp).zone);
      assertSame(source, world.getMapper(MapWrapper.class).get(object).zone);
      assertSame(neighbor, world.getMapper(MapWrapper.class).get(mobile).zone);
    } finally {
      world.dispose();
    }
  }
}
