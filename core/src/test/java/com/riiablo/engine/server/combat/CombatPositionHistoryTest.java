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
