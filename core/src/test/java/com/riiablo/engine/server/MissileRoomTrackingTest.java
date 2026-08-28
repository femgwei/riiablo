package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class MissileRoomTrackingTest {
  @Test
  void authoritativeMissileContinuesAndUpdatesNativeRoomOwnership() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    try {
      Map map = new Map(0, 0);
      Map.Zone zone = new Map.Zone();
      Map.RoomEx first = zone.addRoomEx(0, 0, 40, 40);
      Map.RoomEx second = zone.addRoomEx(40, 0, 40, 40);
      first.setAdjacentRoomIds(new int[] {second.id});
      second.setAdjacentRoomIds(new int[] {first.id});

      int entityId = world.create();
      Missiles.Entry row = new Missiles.Entry();
      row.Missile = "room-tracking-test";
      row.Town = true;
      Missile missile = world.getMapper(Missile.class).create(entityId);
      missile.missile = row;
      missile.ownerId = -1;
      missile.range = 200;
      missile.roomId = first.id;
      world.getMapper(Position.class).create(entityId).position.set(10, 10);
      world.getMapper(Velocity.class).create(entityId).velocity.set(40, 0);
      world.getMapper(MapWrapper.class).create(entityId).set(map, zone);

      world.setDelta(1f);
      world.process();
      assertEquals(50f, world.getMapper(Position.class).get(entityId).position.x, 0.001f);
      assertEquals(second.id, missile.roomId,
          "D2MOO missile events continue while UNITS_GetRoom follows the crossing");
    } finally {
      world.dispose();
    }
  }
}
