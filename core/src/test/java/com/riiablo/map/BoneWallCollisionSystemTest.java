package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.BoneWallCollisionSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import org.junit.jupiter.api.Test;

class BoneWallCollisionSystemTest {
  @Test
  void livingWallBlocksWalkAndRemovalOrDeathClearsItsFootprint() {
    Map map = new Map(0, 0);
    Map.Zone zone = new Map.Zone();
    zone.map = map;
    zone.x = 0;
    zone.y = 0;
    zone.width = 16;
    zone.height = 16;
    zone.tilesX = 4;
    zone.tilesY = 4;
    zone.flags = new byte[zone.width * zone.height];
    map.zones.add(zone);
    World world = new World(new WorldConfigurationBuilder()
        .with(new BoneWallCollisionSystem()).build().register("map", map));
    try {
      int wall = createWall(world, map, zone, 5, 5);
      world.process();
      assertBlocked(map, 5, 5, true);
      assertEquals(0, map.staticFlags(5, 5) & DT1.Tile.FLAG_BLOCK_WALK);

      world.getMapper(AttributesWrapper.class).get(wall).attrs
          .base().put(Stat.hitpoints, 0);
      world.getMapper(AttributesWrapper.class).get(wall).attrs.reset();
      world.process();
      assertBlocked(map, 5, 5, false);

      int replacement = createWall(world, map, zone, 9, 9);
      world.process();
      assertBlocked(map, 9, 9, true);
      world.delete(replacement);
      world.process();
      assertBlocked(map, 9, 9, false);
    } finally {
      world.dispose();
      map.zones.clear();
    }
  }

  private static int createWall(
      World world, Map map, Map.Zone zone, float x, float y) {
    int id = world.create();
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "bonewall";
    world.getMapper(Monster.class).create(id).monstats = row;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Size.class).create(id).size = Size.MEDIUM;
    world.getMapper(MapWrapper.class).create(id).set(map, zone);
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 100);
    attrs.base().put(Stat.maxhp, 100);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    return id;
  }

  private static void assertBlocked(Map map, int x, int y, boolean expected) {
    assertEquals(expected ? DT1.Tile.FLAG_BLOCK_WALK : 0,
        map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK);
  }
}
