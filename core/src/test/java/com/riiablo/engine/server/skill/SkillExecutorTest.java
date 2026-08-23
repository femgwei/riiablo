package com.riiablo.engine.server.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SkillExecutorTest {
  @Test
  public void rejectsSkillWhenManaIsInsufficient() {
    SkillExecutor executor = new SkillExecutor();
    SkillExecutor.SkillContext context = context(56, 30, 0, 0);

    SkillExecutor.SkillResult result = executor.execute(context);

    assertEquals(SkillExecutor.RESULT_NO_MANA, result.resultCode);
    assertTrue(!result.success);
  }

  @Test
  public void cooldownUsesCastFrameInsteadOfCooldownDuration() {
    SkillExecutor executor = new SkillExecutor();
    executor.setCooldownManager(new SkillCooldownManager());

    SkillExecutor.SkillContext first = context(56, 30, 100, 200);
    SkillExecutor.SkillResult firstResult = executor.execute(first);
    assertTrue(firstResult.success);

    SkillExecutor.SkillContext second = context(56, 30, 101, 200);
    SkillExecutor.SkillResult secondResult = executor.execute(second);
    assertEquals(SkillExecutor.RESULT_ON_COOLDOWN, secondResult.resultCode);
  }

  private static SkillExecutor.SkillContext context(int skillId, int level,
      long frame, int mana) {
    SkillExecutor.SkillContext context = new SkillExecutor.SkillContext();
    context.casterId = 7;
    context.skillId = skillId;
    context.skillLevel = 1;
    context.casterLevel = level;
    context.currentFrame = frame;
    context.currentMana = mana;
    context.casterX = 0;
    context.casterY = 0;
    context.targetX = 1;
    context.targetY = 0;
    return context;
  }
}
