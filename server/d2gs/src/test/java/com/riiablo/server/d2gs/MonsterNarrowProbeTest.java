package com.riiablo.server.d2gs;

import static org.junit.Assert.*;
import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.NativePathIdentity;
import org.junit.Test;

public class MonsterNarrowProbeTest {
  @Test public void holdRequiresBothEndpointsToHaveLegalNarrowGeometry() {
    MonsterNarrowProbe.State a = new MonsterNarrowProbe.State();
    MonsterNarrowProbe.State b = new MonsterNarrowProbe.State();
    a.movement.narrowSpanX = 2; b.movement.narrowSpanX = 2;
    assertTrue(MonsterNarrowProbe.narrowHold(a, b, 0));
    a.movement.narrowSpanX = 0;
    assertFalse(MonsterNarrowProbe.narrowHold(a, b, 0));
    a.movement.narrowSpanX = 6;
    assertFalse(MonsterNarrowProbe.narrowHold(a, b, 0));
    a.movement.narrowSpanX = 2; b.movement.blocked = true;
    assertFalse(MonsterNarrowProbe.narrowHold(a, b, 0));
  }
  @Test public void recoveryNeedsARealHoldContinuousProgressAndSameLifetimes() {
    World world = new World();
    try {
      int actor = world.create(), target = world.create();
      NativePathIdentity.Token identity = world.getMapper(NativePathIdentity.class).create(actor).allocated(world, actor).token();
      NativePathIdentity.Token targetIdentity = world.getMapper(NativePathIdentity.class).create(target).allocated(world, target).token();
      MonsterNarrowProbe.State first = state(actor, target, identity, targetIdentity, 10, 10, true);
      MonsterNarrowProbe.State last = state(actor, target, identity, targetIdentity, 15, 10, true);
      MonsterNarrowProbe.State before = state(actor, target, identity, targetIdentity, 20, 10.5f, false);
      MonsterNarrowProbe.State now = state(actor, target, identity, targetIdentity, 25, 12, false);
      assertTrue(MonsterNarrowProbe.stationary(first, last));
      assertTrue(MonsterNarrowProbe.recovered(first, last, before, now, 1));
      last.movement.tick = 14;
      assertFalse(MonsterNarrowProbe.recovered(first, last, before, now, 1));
      last.movement.tick = 15;
      now.dynamicBlocked = true;
      assertFalse(MonsterNarrowProbe.recovered(first, last, before, now, 1));
      now.dynamicBlocked = false;
      now.movement.tick = 40;
      assertFalse(MonsterNarrowProbe.recovered(first, last, before, now, 1));
      now.movement.tick = 25;
      now.targetIdentity = NativePathIdentity.retiredToken(world, target);
      assertFalse(MonsterNarrowProbe.recovered(first, last, before, now, 1));
      now.targetIdentity = targetIdentity;
      now.identity = NativePathIdentity.retiredToken(world, actor);
      assertFalse(MonsterNarrowProbe.recovered(first, last, before, now, 1));
    } finally { world.dispose(); }
  }

  @Test public void holdRejectsMovementStaleSamplesAndUnconfirmedDynamicBlock() {
    World world = new World();
    try {
      int id = world.create();
      NativePathIdentity.Token identity = world.getMapper(NativePathIdentity.class).create(id).allocated(world, id).token();
      MonsterNarrowProbe.State a = state(id, id, identity, identity, 10, 10, true);
      MonsterNarrowProbe.State b = state(id, id, identity, identity, 15, 11, true);
      assertFalse(MonsterNarrowProbe.stationary(a, b));
      b.movement.position.set(a.movement.position); b.movement.tick = 30;
      assertFalse(MonsterNarrowProbe.stationary(a, b));
      b.movement.tick = 15; b.dynamicBlocked = false;
      assertFalse(MonsterNarrowProbe.stationary(a, b));
    } finally { world.dispose(); }
  }

  private static MonsterNarrowProbe.State state(int id, int target, NativePathIdentity.Token identity,
      NativePathIdentity.Token targetIdentity, long tick, float x, boolean blocked) {
    MonsterNarrowProbe.State state = new MonsterNarrowProbe.State();
    state.entity = id; state.target = target; state.identity = identity; state.targetIdentity = targetIdentity;
    state.dynamicBlocked = blocked;
    MapTourProbe.Sample movement = state.movement;
    movement.level = 62; movement.tick = tick; movement.position = new Vector2(x, 10);
    movement.nativeTerrain = true; movement.unitSize = 3; movement.maximumMovementSpeed = 10;
    movement.narrowSpanY = 1;
    return state;
  }
}
