package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ObjectCollisionUpdater;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;

class ObjectCollisionUpdaterTest {

  @Test
  void headlessDoorFollowsNativeModeCollisionAndRestoresAfterRecreation() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry door = object(3, 3, true, false, false);
      int entity = fixture.create(door, 5, 5, Engine.Object.MODE_NU);

      fixture.world.process();
      fixture.assertBlocked(5, 5, true);

      fixture.mode(entity, Engine.Object.MODE_OP);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);

      fixture.mode(entity, Engine.Object.MODE_ON);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);

      fixture.world.delete(entity);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);

      fixture.create(door, 5, 5, Engine.Object.MODE_NU);
      fixture.world.process();
      fixture.assertBlocked(5, 5, true);
    } finally {
      fixture.dispose();
    }
  }

  @Test
  void overlappingObjectsKeepCollisionUntilEveryReferenceIsRemoved() {
    Fixture fixture = new Fixture();
    try {
      Objects.Entry solid = object(3, 3, true, false, false);
      int first = fixture.create(solid, 5, 5, Engine.Object.MODE_NU);
      int second = fixture.create(solid, 5, 5, Engine.Object.MODE_NU);
      fixture.world.process();
      fixture.assertBlocked(5, 5, true);

      fixture.mode(first, Engine.Object.MODE_ON);
      fixture.world.process();
      fixture.assertBlocked(5, 5, true);

      fixture.world.delete(second);
      fixture.world.process();
      fixture.assertBlocked(5, 5, false);
    } finally {
      fixture.dispose();
    }
  }

  @Test
  void dynamicObjectsNeverClearOrBecomeStaticTerrain() {
    Fixture fixture = new Fixture();
    try {
      fixture.zone.or(2, 2, DT1.Tile.FLAG_BLOCK_WALK);
      int entity = fixture.create(
          object(1, 1, true, false, false), 2, 2, Engine.Object.MODE_NU);
      fixture.world.process();

      assertEquals(DT1.Tile.FLAG_BLOCK_WALK,
          fixture.map.staticFlags(2, 2) & DT1.Tile.FLAG_BLOCK_WALK);
      fixture.mode(entity, Engine.Object.MODE_ON);
      fixture.world.process();
      fixture.assertBlocked(2, 2, true);

      int dynamicOnly = fixture.create(
          object(1, 1, true, false, false), 8, 8, Engine.Object.MODE_NU);
      fixture.world.process();
      fixture.assertBlocked(8, 8, true);
      assertEquals(0,
          fixture.map.staticFlags(8, 8) & DT1.Tile.FLAG_BLOCK_WALK);
      fixture.world.delete(dynamicOnly);
      fixture.world.process();
      fixture.assertBlocked(8, 8, false);
    } finally {
      fixture.dispose();
    }
  }

  private static Objects.Entry object(int width, int height,
      boolean nu, boolean op, boolean on) {
    Objects.Entry row = new Objects.Entry();
    row.SizeX = width;
    row.SizeY = height;
    row.HasCollision = new boolean[8];
    row.HasCollision[Engine.Object.MODE_NU] = nu;
    row.HasCollision[Engine.Object.MODE_OP] = op;
    row.HasCollision[Engine.Object.MODE_ON] = on;
    return row;
  }

  private static final class Fixture {
    final Map map = new Map(0, 0);
    final Map.Zone zone = new Map.Zone();
    final World world;

    Fixture() {
      zone.map = map;
      zone.x = 0;
      zone.y = 0;
      zone.width = 12;
      zone.height = 12;
      zone.tilesX = 3;
      zone.tilesY = 3;
      zone.flags = new byte[zone.width * zone.height];
      map.zones.add(zone);
      world = new World(new WorldConfigurationBuilder()
          .with(new ObjectCollisionUpdater())
          .build().register("map", map));
    }

    int create(Objects.Entry base, float x, float y, byte mode) {
      int entity = world.create();
      world.getMapper(com.riiablo.engine.server.component.Object.class)
          .create(entity).base = base;
      world.getMapper(Position.class).create(entity).position.set(x, y);
      world.getMapper(CofReference.class).create(entity).set("DR", mode);
      world.getMapper(MapWrapper.class).create(entity).set(map, zone);
      return entity;
    }

    void mode(int entity, byte mode) {
      world.getMapper(CofReference.class).get(entity).mode = mode;
    }

    void assertBlocked(int x, int y, boolean expected) {
      int actual = map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK;
      assertEquals(expected ? DT1.Tile.FLAG_BLOCK_WALK : 0, actual);
    }

    void dispose() {
      world.dispose();
      // The test Zone is not pool-owned and has deliberately minimal fields,
      // so Map.dispose() is not appropriate here.
      map.zones.clear();
    }
  }
}
