package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

class SummonOwnerWarpTest {
  @Test
  void skeletonDropsCachedTargetAfterOwnerWarp() {
    Skeleton ai = new Skeleton(7);
    ai.targetId = 91;
    ai.nextAction = 4f;
    ai.time = 3f;

    ai.onOwnerWarp();

    assertEquals(Engine.INVALID_ENTITY, ai.targetId);
    assertEquals(0f, ai.nextAction);
    assertEquals(0f, ai.time);
    assertEquals("IDLE", ai.getState());
  }

  @Test
  void skeletonMageDropsCachedTargetAfterOwnerWarp() {
    SkeletonMage ai = new SkeletonMage(8);
    ai.targetId = 92;
    ai.nextAction = 4f;
    ai.time = 3f;

    ai.onOwnerWarp();

    assertEquals(Engine.INVALID_ENTITY, ai.targetId);
    assertEquals(0f, ai.nextAction);
    assertEquals(0f, ai.time);
    assertEquals("IDLE", ai.getState());
  }

  @Test
  void necroPetDropsCachedTargetAfterOwnerWarp() {
    NecroPet ai = new NecroPet(9);
    ai.targetId = 93;
    ai.nextThink = 4f;
    ai.state = "APPROACH";

    ai.onOwnerWarp();

    assertEquals(Engine.INVALID_ENTITY, ai.targetId);
    assertEquals(0f, ai.nextThink);
    assertEquals("IDLE", ai.getState());
  }
}
