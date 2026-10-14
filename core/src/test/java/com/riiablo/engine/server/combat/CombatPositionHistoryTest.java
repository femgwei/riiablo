package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class CombatPositionHistoryTest {
  @Test
  void meleeMayCrossOnlyAnInstalledNativeSeamAndStillObeysRangeAndBarrier() throws Exception {
    com.badlogic.gdx.Application previousApp = com.badlogic.gdx.Gdx.app;
    com.badlogic.gdx.Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
        com.badlogic.gdx.Application.class.getClassLoader(), new Class<?>[] {com.badlogic.gdx.Application.class},
        (proxy, method, args) -> null);
    final boolean[] barrier = {false};
    Map map = new Map(0, 0) {
      @Override public int playerFlyingFlags(int x, int y) {
        return barrier[0] && x == 20 ? DT1.Tile.FLAG_BLOCK_JUMP : 0;
      }
    };
    Map.Zone source = zone(27, 0), destination = zone(26, 20);
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int attacker = unit(world, 18, 10, 1), target = unit(world, 24, 10, 1);
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).create(attacker).set(map, source);
      world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).create(target).set(map, destination);
      world.process();
      CombatPositionHistory history = new CombatPositionHistory(map, 4);
      history.capture(world, 1);
      assertEquals(CombatPositionHistory.RangeResult.DIFFERENT_ZONE, history.meleeRange(attacker, target, 10, 0, 1));
      map.addWarpDestinationOverride(27, 0, 26);
      source.setNativeSeamAnchor(0, 20, 10);
      assertEquals(CombatPositionHistory.RangeResult.IN_RANGE, history.meleeRange(attacker, target, 10, 0, 1));
      assertEquals(CombatPositionHistory.RangeResult.IN_RANGE, history.meleeRange(target, attacker, 10, 0, 1));
      assertEquals(CombatPositionHistory.RangeResult.OUT_OF_RANGE, history.meleeRange(attacker, target, 0, 0, 1));
      barrier[0] = true;
      assertEquals(CombatPositionHistory.RangeResult.BLOCKED, history.meleeRange(attacker, target, 10, 0, 1));
      barrier[0] = false;
      java.lang.reflect.Field town = Map.Zone.class.getDeclaredField("town");
      town.setAccessible(true); town.setBoolean(destination, true);
      assertEquals(CombatPositionHistory.RangeResult.DIFFERENT_ZONE, history.meleeRange(attacker, target, 10, 0, 1));
      town.setBoolean(destination, false);
      destination.setPosition(0, 0); // overlapping detached level cannot use the seam exception
      assertEquals(CombatPositionHistory.RangeResult.DIFFERENT_ZONE, history.meleeRange(attacker, target, 10, 0, 1));
    } finally { world.dispose(); com.badlogic.gdx.Gdx.app = previousApp; }
  }

  private static Map.Zone zone(int id, int x) throws Exception {
    Map.Zone zone = new Map.Zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry();
    zone.level.Id = id;
    for (String name : new String[] {"width", "height"}) {
      java.lang.reflect.Field field = Map.Zone.class.getDeclaredField(name);
      field.setAccessible(true); field.setInt(zone, 20);
    }
    zone.setPosition(x, 0);
    return zone;
  }
  @Test
  void sharedMeleeRayTrimsNativeEndpointsAndDoesNotUseWalkingFlags() {
    Map map = new Map(0, 0) {
      @Override public int playerFlyingFlags(int x, int y) {
        return x == 0 || x == 6 ? DT1.Tile.FLAG_BLOCK_JUMP : 0;
      }
      @Override public int flags(int x, int y) { return DT1.Tile.FLAG_BLOCK_WALK; }
    };
    org.junit.jupiter.api.Assertions.assertFalse(CombatPositionHistory.hasPlayerFlyingBarrier(
        map, 0, 0, 2, 6, 0, 2));
    org.junit.jupiter.api.Assertions.assertTrue(CombatPositionHistory.hasPlayerFlyingBarrier(
        map, 0, 0, 0, 6, 0, 0));
  }

  @Test
  void laterMovementCannotMutateAnEarlierAttackFrame() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int attacker = unit(world, 0, 0, Size.MEDIUM);
      int target = unit(world, 5, 0, Size.MEDIUM);
      world.process();
      CombatPositionHistory history = new CombatPositionHistory(null, 4);
      history.capture(world, 1);

      world.getMapper(Position.class).get(target).position.set(20, 0);
      history.capture(world, 2);

      assertEquals(5, history.snapshot(target, 1).x);
      assertEquals(20, history.snapshot(target, 2).x);
      assertEquals(CombatPositionHistory.RangeResult.IN_RANGE,
          history.meleeRange(attacker, target, 0, 3, 1));
      assertEquals(CombatPositionHistory.RangeResult.OUT_OF_RANGE,
          history.meleeRange(attacker, target, 0, 3, 2));
    } finally {
      world.dispose();
    }
  }

  @Test
  void ringEvictsOnlyFramesOlderThanItsCapacity() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int entity = unit(world, 1, 2, Size.SMALL);
      world.process();
      CombatPositionHistory history = new CombatPositionHistory(null, 2);
      history.capture(world, 1);
      history.capture(world, 2);
      history.capture(world, 3);
      assertNull(history.snapshot(entity, 1));
      assertEquals(2, history.snapshot(entity, 2).y);
      assertEquals(3, history.latestTick());
    } finally {
      world.dispose();
    }
  }

  @Test
  void missingEntityOrFrameIsRejectedExplicitly() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int entity = unit(world, 1, 2, Size.SMALL);
      world.process();
      CombatPositionHistory history = new CombatPositionHistory();
      history.capture(world, 1);
      assertEquals(CombatPositionHistory.RangeResult.MISSING_SNAPSHOT,
          history.meleeRange(entity, 9999, 0, 0, 1));
      assertEquals(CombatPositionHistory.RangeResult.MISSING_SNAPSHOT,
          history.meleeRange(entity, entity, 0, 0, 2));
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativePlayerFlyingBarrierBlocksAnOtherwiseValidMeleeRay() {
    Map barrierMap = new Map(0, 0) {
      @Override
      public int playerFlyingFlags(int x, int y) {
        return x == 3 ? DT1.Tile.FLAG_BLOCK_JUMP : 0;
      }
    };
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int attacker = unit(world, 0, 0, Size.INSIGNIFICANT);
      int target = unit(world, 6, 0, Size.INSIGNIFICANT);
      world.process();
      CombatPositionHistory history = new CombatPositionHistory(barrierMap, 2);
      history.capture(world, 1);
      assertEquals(CombatPositionHistory.RangeResult.BLOCKED,
          history.meleeRange(attacker, target, 20, 0, 1));
    } finally {
      world.dispose();
    }
  }

  private static int unit(World world, float x, float y, int size) {
    int entity = world.create();
    world.getMapper(Position.class).create(entity).position.set(x, y);
    world.getMapper(Size.class).create(entity).size = size;
    return entity;
  }
}
