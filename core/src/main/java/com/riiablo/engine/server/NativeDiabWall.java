package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;

/** SkillMonst.cpp SrvDo102 / MissileInit_DiabWall, not Sorceress Fire Wall. */
public final class NativeDiabWall {
  private NativeDiabWall() {}

  public static boolean supports(Skills.Entry skill) {
    return skill != null && skill.Id == 197 && skill.srvdofunc == 102;
  }

  public static void initialize(Missile maker, int level, int ordinal,
      int originX, int originY, int targetX, int targetY, Vector2 direction) {
    maker.diabWallMaker = true;
    maker.skillId = 0; // Native zero-initialized params deliberately omit nSkill.
    maker.damageLevel = level;
    maker.nativeLifetimeFrames = Math.min(77, Missile.nativeRangeFrames(maker.missile, level));
    maker.chargedBoltTargetX = targetX;
    maker.chargedBoltTargetY = targetY;
    long roll = AssassinTrapSystem.chargedBoltRoll(ordinal + targetX, 666);
    maker.chargedBoltSeedLow = (int) roll;
    maker.chargedBoltSeedHigh = (int) (roll >>> 32);
    maker.chargedBoltPath = Integer.remainderUnsigned((int) roll, 100) >= 20;
    maker.chargedBoltMainDirection = AssassinTrapSystem.chargedBoltMainDirection(
        originX, originY, targetX, targetY);
    if (maker.chargedBoltPath) {
      // PATH_ComputePathChargedBolt consumes another roll for its first point.
      roll = AssassinTrapSystem.chargedBoltRoll(maker.chargedBoltSeedLow, maker.chargedBoltSeedHigh);
      maker.chargedBoltSeedLow = (int) roll;
      maker.chargedBoltSeedHigh = (int) (roll >>> 32);
      AssassinTrapSystem.chargedBoltVector(AssassinTrapSystem.chargedBoltDirection(
          maker.chargedBoltMainDirection, (int) roll), direction);
    }
    maker.chargedBoltNextTurnDistance = 2f;
  }
}
