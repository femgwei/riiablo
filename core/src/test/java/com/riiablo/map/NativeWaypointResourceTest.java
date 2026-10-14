package com.riiablo.map;

import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real MPQ coverage: reaching a level does not prove its waypoint exists. */
class NativeWaypointResourceTest extends RiiabloTest {
  @Test void allAct2ThroughAct5WaypointTargetsOwnANativeRoom() {
    List<String> missing = new ArrayList<>();
    for (String text : System.getProperty("map.audit.seeds", "127897359").split(",")) {
      int seed = Integer.parseInt(text.trim());
      int checked = 0;
      for (int act = 1; act <= 4; act++) {
        Map map = new Map(seed, 0);
        if (act == 1) Act2MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 2) Act3MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 3) Act4MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 4) Act5MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        MapManager manager = new MapManager();
        manager.map = map;
        for (Map.Zone zone : map.getZones()) {
          if (zone.level.Waypoint == 0xFF) continue;
          checked++;
          String context = "seed=" + seed + " level=" + zone.level.Id;
          assertTrue(zone.hasNativeRoomTopology(), context);
          if (manager.findNativeWaypointRoom(zone) == null) missing.add(context);
        }
      }
      assertEquals(30, checked, "must audit every A2-A5 waypoint destination");
    }
    assertTrue(missing.isEmpty(), "Missing native waypoint rooms: " + missing);
  }

  @Test void painWaypointTravelMaterializesDeferredRoomAndReusesExactCenter() {
    Map map = new Map(127897359, 0);
    Act5MapBuilderD2MOD.INSTANCE.generate(map, map.seed, 0);
    Map.Zone zone = map.findZone(Riiablo.files.Levels.get(123));
    MapManager manager = new MapManager();
    ObjectFactory factory = new ObjectFactory();
    World world = new World(new WorldConfigurationBuilder().with(manager, factory).build()
        .register("map", map).register("factory", factory));
    try {
      assertEquals(0, zone.entities.size, "waypoint starts deferred");
      Vector2 first = manager.findWaypointPosition(zone.level, new Vector2());
      assertNotNull(first, "production lookup must materialize the waypoint");
      assertTrue(zone.contains((int) first.x, (int) first.y));
      assertTrue(manager.findNativeWaypointRoom(zone).isPresetUnitsSpawned());
      int count = zone.entities.size;
      assertTrue(count > 0);
      Vector2 second = manager.findWaypointPosition(zone.level, new Vector2());
      assertEquals(first, second);
      assertEquals(count, zone.entities.size, "repeated travel must not duplicate objects");
    } finally { world.dispose(); }
  }

  /** No GL: isolate production room materialization and waypoint ownership. */
  private static final class ObjectFactory extends EntityFactory {
    @Override public int createStaticObjectByClassId(int classId, float x, float y) {
      int id = world.create();
      com.riiablo.engine.server.component.Object object =
          world.getMapper(com.riiablo.engine.server.component.Object.class).create(id);
      object.base = Riiablo.files.objects.get(classId);
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(id).position.set(x, y);
      world.getMapper(com.riiablo.engine.server.component.CofReference.class).create(id);
      return id;
    }
    @Override public int createStaticObject(int act, int preset, float x, float y) {
      return createStaticObjectByClassId(Riiablo.files.obj.getObjectId(act, preset), x, y);
    }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createPlayer(com.riiablo.save.CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int monster, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
