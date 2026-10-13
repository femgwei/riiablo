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
  public static class ZoneProbe extends net.mostlyoriginal.api.system.core.PassiveSystem {
    int changes;
    Map.Zone last;
    @net.mostlyoriginal.api.event.common.Subscribe
    public void changed(com.riiablo.engine.server.event.ZoneChangeEvent event) {
      changes++;
      last = event.zone;
    }
  }

  @Test void walkingZoneChangesNotifyOnceInEachDirectionNotForRoomOnlyChanges() throws Exception {
    Map map = new Map(12345, 0);
    Map.Zone town = new Map.Zone(), outdoor = new Map.Zone();
    java.lang.reflect.Field townFlag = Map.Zone.class.getDeclaredField("town");
    townFlag.setAccessible(true);
    townFlag.setBoolean(town, true);
    java.lang.reflect.Field flags = Map.Zone.class.getDeclaredField("flags");
    flags.setAccessible(true);
    flags.set(town, new byte[0]);
    flags.set(outdoor, new byte[0]);
    town.setNativeTileGrid(new TileGrid(2, 2), 0);
    outdoor.setNativeTileGrid(new TileGrid(2, 2), 0);
    outdoor.setPosition(10, 0);
    map.getZones().add(town);
    map.getZones().add(outdoor);
    ZoneProbe probe = new ZoneProbe();
    World world = new World(new WorldConfigurationBuilder().with(
        new net.mostlyoriginal.api.event.common.EventSystem(), probe,
        new RoomEntityTrackingSystem(), new ZoneMovementModesChanger()).build());
    try {
      int player = world.create();
      world.getMapper(com.riiablo.engine.server.component.Player.class).create(player);
      Position position = world.getMapper(Position.class).create(player);
      position.position.set(5, 5);
      world.getMapper(MapWrapper.class).create(player).set(map, town);
      com.riiablo.engine.server.component.MovementModes modes = world.getMapper(
          com.riiablo.engine.server.component.MovementModes.class).create(player);
      modes.NU = com.riiablo.engine.Engine.Player.MODE_TN;
      world.process(); world.process();
      assertEquals(0, probe.changes);
      position.position.set(12, 5);
      world.process(); world.process();
      assertEquals(1, probe.changes);
      assertSame(outdoor, probe.last);
      assertEquals(com.riiablo.engine.Engine.Player.MODE_NU, modes.NU);
      world.process();
      assertEquals(1, probe.changes);
      position.position.set(5, 5);
      world.process(); world.process();
      assertEquals(2, probe.changes);
      assertSame(town, probe.last);
      assertEquals(com.riiablo.engine.Engine.Player.MODE_TN, modes.NU);
    } finally { world.dispose(); }
  }

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
