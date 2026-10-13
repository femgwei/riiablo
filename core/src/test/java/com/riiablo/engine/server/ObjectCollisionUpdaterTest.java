package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.codec.excel.Objects;
import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectCollisionUpdaterTest {
  @Test void prewarmedObjectBlocksLandingBeforeItsFirstSubscriptionTick() throws Exception {
    Map map = new Map(12345, 0);
    Map.Zone zone = new Map.Zone();
    java.lang.reflect.Field flags = Map.Zone.class.getDeclaredField("flags");
    flags.setAccessible(true);
    flags.set(zone, new byte[0]);
    zone.setNativeTileGrid(new TileGrid(5, 5), 0);
    ObjectCollisionUpdater collision = new ObjectCollisionUpdater();
    World world = new World(new WorldConfigurationBuilder().with(collision).build().register("map", map));
    try {
      int id = world.create();
      Objects.Entry base = new Objects.Entry();
      base.SizeX = base.SizeY = 3;
      base.HasCollision = new boolean[] {true, false, false, false, false, false, false, false};
      world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = base;
      world.getMapper(Position.class).create(id).position.set(10, 10);
      world.getMapper(MapWrapper.class).create(id).set(map, zone);
      assertFalse(map.isBlockedForPath(zone, 10, 10, NativeGroundPathProfile.PLAYER.terrainFlags, 2, false));
      collision.synchronizeCreatedObject(id);
      assertTrue(map.isBlockedForPath(zone, 10, 10, NativeGroundPathProfile.PLAYER.terrainFlags, 2, false));
      world.process();
      world.delete(id);
      world.process();
      assertFalse(map.isBlockedForPath(zone, 10, 10, NativeGroundPathProfile.PLAYER.terrainFlags, 2, false),
          "Subscription processing must not add the footprint a second time");
    } finally {
      world.dispose();
    }
  }
}
