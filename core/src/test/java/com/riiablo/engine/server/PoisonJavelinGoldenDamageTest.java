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

/**
 * Twenty-eighth DMG-04 level-by-level audit: one Poison Javelin cloud applied
 * to one target for its complete native poison duration.
 *
 * <p>The constants are the Diablo II 1.10f 8.8 poison rate and duration
 * converted to an integer full-duration total with floor(rate * frames / 256).
 * D2MOO references {@code MissMode.cpp:782 MISSMODE_SrvDo02_PlagueJavelin_PoisonJavelin_PoisonTrap},
 * {@code MissMode.cpp:800 MISSMODE_SrvDo03_PoisonCloud_Blizzard_ThunderStorm_HandOfGod},
 * {@code D2Skills.cpp SKILLS_GetMinElemDamage/SKILLS_GetMaxElemDamage}, and
 * {@code D2Common/Units/Missile.cpp MISSILE_CalculateDamageData}.</p>
 */
class PoisonJavelinGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_MIN_RATE_FIXED = {
      32, 48, 64, 80, 96, 112, 128, 144, 176, 208,
      240, 272, 304, 336, 368, 400, 448, 496, 544, 592
  };
  private static final int[] D2MOO_MAX_RATE_FIXED = {
      48, 64, 80, 96, 112, 128, 144, 160, 196, 232,
      268, 304, 340, 376, 412, 448, 500, 552, 604, 656
  };
  private static final int[] D2MOO_DURATION_FRAMES = {
      200, 250, 300, 350, 400, 450, 500, 550, 600, 650,
      700, 750, 800, 850, 900, 950, 1000, 1050, 1100, 1150
  };
  private static final int[] D2MOO_MIN_TOTAL = {
      25, 46, 75, 109, 150, 196, 250, 309, 412, 528,
      656, 796, 950, 1115, 1293, 1484, 1750, 2034, 2337, 2659
  };
  private static final int[] D2MOO_MAX_TOTAL = {
      37, 62, 93, 131, 175, 225, 281, 343, 459, 589,
      732, 890, 1062, 1248, 1448, 1662, 1953, 2264, 2595, 2946
  };

  @Test
  void levelOneToTwentyMatchesD2mooPoisonRateDurationAndTotal() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.POISON_JAVELIN);
    Missiles.Entry root = Riiablo.files.Missiles.get("poisonjav");
    Missiles.Entry cloud = Riiablo.files.Missiles.get("poisonjavcloud");
    assertNotNull(skill);
    assertNotNull(root);
    assertNotNull(cloud);
    assertEquals("Poison Javelin", skill.skill);
    assertEquals(15, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("poisonjav", skill.srvmissile);
    assertEquals(128, skill.SrcDam);
    assertEquals(0, skill.HitShift);
    assertEquals("pois", skill.EType);
    assertEquals(32, skill.EMin);
    assertEquals(48, skill.EMax);
    assertArrayEquals(new int[] {16, 32, 48, 64, 96}, skill.EMinLev);
    assertArrayEquals(new int[] {16, 36, 52, 68, 84}, skill.EMaxLev);
    assertEquals(200, skill.ELen);
    assertArrayEquals(new int[] {50, 50, 50}, skill.ELevLen);
    assertEquals("(skill('Plague Javelin'.blvl)) * par8", skill.EDmgSymPerCalc);
    assertEquals(2, root.pSrvDoFunc);
    assertEquals("poisonjavcloud", root.SubMissile[0]);
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
