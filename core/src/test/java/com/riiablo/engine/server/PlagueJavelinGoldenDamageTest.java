package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Thirty-eighth DMG-04 level-by-level audit: one Plague Javelin poison cloud. */
class PlagueJavelinGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_MIN_RATE_FIXED = {
      80, 128, 176, 224, 272, 320, 368, 416, 512, 608,
      704, 800, 896, 992, 1088, 1184, 1344, 1504, 1664, 1824
  };
  private static final int[] D2MOO_MAX_RATE_FIXED = {
      128, 176, 224, 272, 320, 368, 416, 464, 560, 656,
      752, 848, 944, 1040, 1136, 1232, 1392, 1552, 1712, 1872
  };
  private static final int[] D2MOO_DURATION_FRAMES = {
      75, 85, 95, 105, 115, 125, 135, 145, 155, 165,
      175, 185, 195, 205, 215, 225, 235, 245, 255, 265
  };
  private static final int[] D2MOO_MIN_TOTAL = {
      23, 42, 65, 91, 122, 156, 194, 235, 310, 391,
      481, 578, 682, 794, 913, 1040, 1233, 1439, 1657, 1888
  };
  private static final int[] D2MOO_MAX_TOTAL = {
      37, 58, 83, 111, 143, 179, 219, 262, 339, 422,
      514, 612, 719, 832, 954, 1082, 1277, 1485, 1705, 1937
  };

  @Test
  void levelOneToTwentyMatchesD2mooPoisonRateDurationAndTotal() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.PLAGUE_JAVELIN);
    Missiles.Entry root = Riiablo.files.Missiles.get("plaguejavelin");
    Missiles.Entry cloud = Riiablo.files.Missiles.get("plaguejavcloud");
    assertNotNull(skill);
    assertNotNull(root);
    assertNotNull(cloud);
    assertEquals("Plague Javelin", skill.skill);
    assertEquals(25, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("plaguejavelin", skill.srvmissile);
    assertEquals(128, skill.SrcDam);
    assertEquals(3, skill.HitShift);
    assertEquals(30, skill.ToHit);
    assertEquals(9, skill.LevToHit);
    assertEquals("pois", skill.EType);
    assertEquals(10, skill.EMin);
    assertEquals(16, skill.EMax);
    assertArrayEquals(new int[] {6, 12, 20, 40, 60}, skill.EMinLev);
    assertArrayEquals(new int[] {6, 12, 20, 40, 60}, skill.EMaxLev);
    assertEquals("(skill('Poison Javelin'.blvl))*par8", skill.EDmgSymPerCalc);
    assertEquals(75, skill.ELen);
    assertArrayEquals(new int[] {10, 10, 10}, skill.ELevLen);
    assertEquals(2, root.pSrvHitFunc);
    assertEquals("plaguejavcloud", root.HitSubMissile[0]);
    assertEquals(3, cloud.pSrvDoFunc);

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      Missile projectile = new Missile();
      projectile.missile = cloud;
      assertTrue(MissileDamageResolver.initializeSkillPoisonArea(
          projectile, skill, owner, true, level, name -> 0));
      assertTrue(projectile.fixedPoisonRate);
      assertEquals(D2MOO_MIN_RATE_FIXED[level - 1], projectile.poisonMinRateFixed,
          "fixed poison min rate level " + level);
      assertEquals(D2MOO_MAX_RATE_FIXED[level - 1], projectile.poisonMaxRateFixed,
          "fixed poison max rate level " + level);
      assertEquals(D2MOO_DURATION_FRAMES[level - 1], projectile.poisonDurationFrames,
          "duration level " + level);
      assertEquals(D2MOO_MIN_TOTAL[level - 1], fullDurationTotal(
          projectile.poisonMinRateFixed, projectile.poisonDurationFrames),
          "full-duration min total level " + level);
      assertEquals(D2MOO_MAX_TOTAL[level - 1], fullDurationTotal(
          projectile.poisonMaxRateFixed, projectile.poisonDurationFrames),
          "full-duration max total level " + level);
    }
  }

  private static int fullDurationTotal(int rateFixed, int durationFrames) {
    return rateFixed * durationFrames / 256;
  }
}
