package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows + ECS decisions for AITHINK_Fn139_BaalTentacle. */
class BaalTentacleAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsResolveDedicatedAi() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"BaalTentacle".equalsIgnoreCase(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof BaalTentacle);
    }
    assertTrue(rows > 0, "1.10f contains no MonStats row for BaalTentacle");
  }

  @Test
  void ownerAndLifetimeGateAttackLoop() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      int owner = owner(f);
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner;
      f.ai.params[BaalTentacle.PARAM_ATTACK_CHANCE] = 100;
      f.ai.params[BaalTentacle.PARAM_STALL_DURATION] = 1;
      f.ai.params[BaalTentacle.PARAM_STAY_ALIVE_RANDOM_RANGE_SECONDS] = 2;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      int owner = owner(f);
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner;
      f.ai.params[BaalTentacle.PARAM_STAY_ALIVE_RANDOM_RANGE_SECONDS] = 1;
      f.ai.update(60 * Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
    }
  }

  @Test
  void deadOwnerDoesNotFallbackToPlayerCombat() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      int owner = owner(f);
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner;
      f.world.getMapper(Corpse.class).create(owner);
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
      assertFalse(f.world.getMapper(com.riiablo.engine.server.component.Casting.class)
          .has(f.actor));
    }
  }

  @Test void restoredLifetimeExpiresAtOriginalBoundaryWithoutRerolling() {
    AiRuntimeState image;
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner(f);
      f.ai.params[BaalTentacle.PARAM_ATTACK_CHANCE] = 0;
      f.ai.params[BaalTentacle.PARAM_STALL_DURATION] = 1;
      f.ai.params[BaalTentacle.PARAM_STAY_ALIVE_RANDOM_RANGE_SECONDS] = 1;
      f.ai.update(10 * Animation.FRAME_DURATION);
      image = AiRuntimeState.capture(f.ai, f.actor);
      f.ai.update(15 * Animation.FRAME_DURATION);
      assertNotEquals("DEAD", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.world.getMapper(Monster.class).get(f.actor).minionOwnerId = owner(f);
      image.restore(f.ai, f.actor);
      assertArrayEquals(image.bytes(), AiRuntimeState.capture(f.ai, f.actor).bytes());
      com.riiablo.engine.server.NativeUnitRandom rng = com.riiablo.engine.server.NativeUnitRandom.monster(
          f.world.getMapper(Monster.class).get(f.actor), 1, f.actor);
      com.riiablo.engine.server.NativeUnitRandom.Snapshot before = rng.snapshot();
      f.ai.update(0); // Before the next decision: lifetime initialization must not draw again.
      assertEquals(before.lowSeed, rng.state()); assertEquals(before.highSeed, rng.high());
      f.ai.update(15 * Animation.FRAME_DURATION);
      assertNotEquals("DEAD", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
    }
  }

  private static int owner(Act1NativeAiIntegrationTest.Fixture f) {
    int owner = f.world.create();
    MonStats.Entry row = Riiablo.files.monstats.get("baalcrab");
    if (row == null) row = Riiablo.files.monstats.get("mephisto");
    f.world.getMapper(Monster.class).create(owner).set(row,
        Riiablo.files.monstats2.get(row.MonStatsEx));
    f.world.getMapper(Position.class).create(owner).position.set(10, 10);
    f.world.getMapper(CofReference.class).create(owner).mode = Engine.Monster.MODE_NU;
    f.world.process();
    return owner;
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    String rowId = rowIdForAi("BaalTentacle");
    return new Act1NativeAiIntegrationTest.Fixture(rowId, 0, distance);
  }

  private static String rowIdForAi(String aiName) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (aiName.equalsIgnoreCase(row.AI)) return row.Id;
    }
    fail("1.10f contains no MonStats row for AI " + aiName);
    return null;
  }
}
