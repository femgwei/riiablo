package com.riiablo.server.d2gs;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class MapTourWalkingTest {
  @Test public void townWarpCannotSubstituteForARealWalkingConnection() throws Exception {
    com.badlogic.gdx.Application previousApp = Gdx.app;
    com.riiablo.Files previousFiles = Riiablo.files;
    com.badlogic.gdx.files.FileHandle previousHome = Riiablo.home;
    com.riiablo.mpq.MPQFileHandleResolver previousMpqs = Riiablo.mpqs;
    com.badlogic.gdx.backends.headless.HeadlessApplication app = new com.badlogic.gdx.backends.headless.HeadlessApplication(
        new com.badlogic.gdx.ApplicationAdapter() {});
    com.riiablo.engine.server.DynamicUnitCollisionSystem collision =
        new com.riiablo.engine.server.DynamicUnitCollisionSystem();
    World world = new World(new WorldConfigurationBuilder().with(collision).build());
    try {
      Riiablo.home = com.riiablo.util.InstallationFinder.getInstance().defaultHomeDir();
      Riiablo.mpqs = new com.riiablo.mpq.MPQFileHandleResolver();
      Riiablo.files = new com.riiablo.Files();
      assertEquals("Native Harrogath gate must not resolve to debris", 61,
          Riiablo.files.objects.get(449).OperateFn);
      assertEquals("2v", Riiablo.files.objects.get(449).Token);
      Map map = new Map(0, 0);
      Map.Zone town = zone(map, 40, 0), outdoor = zone(map, 41, 20);
      assertFalse(D2GS.allowsMovementZone(map, town, outdoor));
      int player = world.create();
      world.getMapper(Position.class).create(player).position.set(10, 10);
      world.getMapper(Size.class).create(player).size = 2;
      world.getMapper(Velocity.class).create(player);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(player).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      world.getMapper(MapWrapper.class).create(player).set(map, town);
      int fakeWarp = world.create();
      world.getMapper(Position.class).create(fakeWarp).position.set(10, 10);
      world.getMapper(Warp.class).create(fakeWarp).set(10, null, outdoor.level);
      world.getMapper(MapWrapper.class).create(fakeWarp).set(map, town);
      world.getMapper(Interactable.class).create(fakeWarp).range = 1;
      world.process();
      MapTourProbe.Sample missing = MapTourProbe.inspect(world, map, player, 41, false);
      assertEquals("MISSING_TOWN_WALK", missing.transition);
      assertNull(missing.target);
      map.addWarpDestinationOverride(40, 0, 41);
      town.setNativeSeamAnchor(0, 19, 10);
      assertTrue(D2GS.allowsMovementZone(map, town, outdoor));
      MapTourProbe.Sample walk = MapTourProbe.inspect(world, map, player, 41, false);
      assertEquals("BOUNDARY_WALK", walk.transition);
      assertEquals(-1, walk.warp);
      assertNotNull(walk.target);
      assertTrue(town.contains(Math.round(walk.target.x), Math.round(walk.target.y)));
      assertTrue(outdoor.contains(Math.round(walk.destination.x), Math.round(walk.destination.y)));
      int occupant = world.create();
      world.getMapper(Position.class).create(occupant).position.set(walk.destination);
      world.getMapper(Size.class).create(occupant).size = 2;
      world.getMapper(Velocity.class).create(occupant);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(occupant).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      world.getMapper(MapWrapper.class).create(occupant).set(map, outdoor);
      world.process(); collision.refreshNow();
      MapTourProbe.Sample alternate = MapTourProbe.inspect(world, map, player, 41, false);
      assertEquals("BOUNDARY_WALK", alternate.transition);
      assertFalse("Do not click into an occupied seam landing", walk.destination.equals(alternate.destination));
      assertTrue(collision.isFreeForPath(outdoor, player, -1,
          Math.round(alternate.destination.x), Math.round(alternate.destination.y), 2));
      walk = alternate;
      world.getMapper(Position.class).get(player).position.set(walk.target);
      assertFalse(MapTourProbe.route(world, map, player, walk.destination).isEmpty());
      Map.Zone detached = zone(map, 55, 0);
      assertFalse(D2GS.allowsMovementZone(map, town, detached));
      assertTrue(D2GS.allowsMovementZone(map, town, town));
    } finally {
      world.dispose(); app.exit(); Gdx.app = previousApp;
      Riiablo.files = previousFiles; Riiablo.home = previousHome; Riiablo.mpqs = previousMpqs;
    }
  }

  private static Map.Zone zone(Map map, int level, int x) throws Exception {
    Map.Zone zone = new Map.Zone();
    zone.level = Riiablo.files.Levels.get(level);
    for (String name : new String[] {"width", "height", "tilesX", "tilesY", "flags"}) {
      java.lang.reflect.Field field = Map.Zone.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(zone, "flags".equals(name) ? new byte[400]
          : "tilesX".equals(name) || "tilesY".equals(name) ? 4 : 20);
    }
    zone.setPosition(x, 0);
    java.lang.reflect.Field field = Map.class.getDeclaredField("zones");
    field.setAccessible(true);
    ((com.badlogic.gdx.utils.Array<Map.Zone>) field.get(map)).add(zone);
    return zone;
  }
}
