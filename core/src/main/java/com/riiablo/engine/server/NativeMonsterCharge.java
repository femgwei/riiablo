package com.riiablo.engine.server;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Velocity;

/** SKILLS_SrvSt31: Charge uses MonStats.Run, not ordinary walk Velocity. */
public final class NativeMonsterCharge {
  private NativeMonsterCharge() {}

  public static boolean supports(Skills.Entry skill) {
    return skill != null && skill.srvstfunc == 31 && skill.srvdofunc == 67;
  }

  public static int nativeVelocity(MonStats.Entry monster, Skills.Entry skill, Velocity velocity) {
    int percent = Math.max(50, Math.round(100f * (velocity.baseSpeedMultiplier
        + velocity.modeSpeedBonusMultiplier) + velocity.nativeMonsterVelocityBonus));
    int bonus = skill.Param == null || skill.Param.length == 0 ? 0 : skill.Param[0];
    return (int) ((long) (monster.Run << 8) * (bonus + percent) / 100);
  }

  public static float requestedSpeed(MonStats.Entry monster, Skills.Entry skill, Velocity velocity) {
    float speed = nativeVelocity(monster, skill, velocity) * 25f / 4096f;
    // VelocityAdder applies the ordinary movement multiplier once afterward;
    // Charge's native percentage already includes velocity state bonuses.
    if (velocity.nativeMonsterSpeed) {
      float requested = Math.max(Velocity.MIN_SPEED_MULTIPLIER,
          velocity.baseSpeedMultiplier + velocity.modeSpeedBonusMultiplier);
      float effective = Math.max(Velocity.MIN_SPEED_MULTIPLIER,
          velocity.baseSpeedMultiplier + velocity.modeSpeedBonusMultiplier
              + velocity.nativeMonsterVelocityBonus / 100f);
      speed *= requested / effective;
    }
    return speed;
  }
}
