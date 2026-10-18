package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class NativeMonsterChargeTest {
  @Test void chargeUsesRunAndSkillBonusInsteadOfSlowWalk() {
    MonStats.Entry monster = new MonStats.Entry();
    monster.Velocity = 1; monster.Run = 8;
    Skills.Entry skill = new Skills.Entry(); skill.Param = new int[] {150};
    Velocity velocity = new Velocity().setMonsterFromNativeTable(1);
    assertEquals(4608, NativeMonsterCharge.nativeVelocity(monster, skill, velocity));
    assertEquals(28.125f, NativeMonsterCharge.requestedSpeed(monster, skill, velocity));
    assertEquals(1.171875f, velocity.speed(false));
  }

  @Test void nativeMinimumAndStateBonusAreAppliedOnce() {
    MonStats.Entry monster = new MonStats.Entry(); monster.Run = 8;
    Skills.Entry skill = new Skills.Entry(); skill.Param = new int[] {150};
    Velocity velocity = new Velocity().setMonsterFromNativeTable(1);
    velocity.nativeMonsterVelocityBonus = -100;
    assertEquals(4096, NativeMonsterCharge.nativeVelocity(monster, skill, velocity));
    assertEquals(25f, NativeMonsterCharge.requestedSpeed(monster, skill, velocity)
        * velocity.movementMultiplier(), .001f);
    velocity.nativeMonsterVelocityBonus = 25;
    assertEquals(5120, NativeMonsterCharge.nativeVelocity(monster, skill, velocity));
    assertEquals(31.25f, NativeMonsterCharge.requestedSpeed(monster, skill, velocity)
        * velocity.movementMultiplier(), .001f);
  }
}
