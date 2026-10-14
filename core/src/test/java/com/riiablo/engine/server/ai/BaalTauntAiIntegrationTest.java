package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows + ECS decisions for AITHINK_Fn136_BaalTaunt. */
class BaalTauntAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsResolveDedicatedAi() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"BaalTaunt".equalsIgnoreCase(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof BaalTaunt);
    }
    assertTrue(rows > 0, "1.10f contains no MonStats row for BaalTaunt");
  }

  @Test
  void neutralTargetCountsSightFramesThenStartsTauntMode() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(10f)) {
      f.ai.params[BaalTaunt.PARAM_FRAMES_IN_SIGHT_BEFORE_TAUNT] = 1;
      f.ai.params[BaalTaunt.PARAM_IN_RANGE_DISTANCE] = 0;
      f.ai.SLEEP = Animation.FRAME_DURATION;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("APPROACH", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("CAST", f.ai.getState());
      assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
    }
  }

  @Test
  void closeTargetStallsAndMissingTargetDoesNotAttack() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1f)) {
      f.ai.params[BaalTaunt.PARAM_FRAMES_IN_SIGHT_BEFORE_TAUNT] = 99;
      f.ai.params[BaalTaunt.PARAM_IN_RANGE_DISTANCE] = 2;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(10f)) {
      f.world.delete(f.target);
      f.world.process();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    String rowId = rowIdForAi("BaalTaunt");
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
