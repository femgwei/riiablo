package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

/** Fifty-second DMG-04 audit: Enchant contributes one timed fire-damage packet. */
class EnchantGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIRE_MIN = {
      8, 9, 11, 12, 14, 15, 17, 18, 22, 25,
      29, 32, 36, 39, 43, 46, 52, 57, 63, 68
  };
  private static final int[] D2MOO_FIRE_MAX = {
      10, 12, 15, 17, 20, 22, 25, 27, 32, 36,
      41, 45, 50, 54, 59, 63, 70, 76, 83, 89
  };
  private static final int[] D2MOO_DURATION_FRAMES = {
      3600, 4200, 4800, 5400, 6000, 6600, 7200, 7800, 8400, 9000,
      9600, 10200, 10800, 11400, 12000, 12600, 13200, 13800, 14400, 15000
  };
  private static final int[] D2MOO_ATTACK_RATING_PERCENT = {
      20, 29, 38, 47, 56, 65, 74, 83, 92, 101,
      110, 119, 128, 137, 146, 155, 164, 173, 182, 191
  };

  @Test
  void levelOneToTwentyMatchD2mooTimedFireContribution() {
    Skills.Entry skill = Riiablo.files.skills.get("Enchant");
    assertNotNull(skill);
    assertEquals(SkillId.ENCHANT, skill.Id);
    assertEquals(0, skill.srvstfunc);
    assertEquals(25, skill.srvdofunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals("fire", skill.EType);
    assertEquals(7, skill.HitShift);
    assertEquals(16, skill.EMin);
    assertEquals(20, skill.EMax);
    assertArrayEquals(new int[] {3, 7, 11, 15, 19}, skill.EMinLev);
    assertArrayEquals(new int[] {5, 9, 13, 17, 21}, skill.EMaxLev);
    assertEquals("(skill('Warmth'.blvl))*par8", skill.EDmgSymPerCalc);
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    assertEquals("enchant", skill.aurastate);
    assertEquals("ln12", skill.auralencalc);
    assertEquals("firemindam", skill.aurastat[0]);
    assertEquals("enma", skill.aurastatcalc[0]);
    assertEquals("firemaxdam", skill.aurastat[1]);
    assertEquals("exma", skill.aurastatcalc[1]);
    assertEquals("item_tohit_percent", skill.aurastat[2]);
    assertEquals("toht", skill.aurastatcalc[2]);
    assertEquals(20, skill.ToHit);
    assertEquals(9, skill.LevToHit);
    assertArrayEquals(new int[] {3600, 600, 33, 0, 0, 0, 0, 9}, skill.Param);

    for (int level = 1; level <= 20; level++) {
      int minimum = D2MOO_FIRE_MIN[level - 1];
      int maximum = D2MOO_FIRE_MAX[level - 1];
      int duration = D2MOO_DURATION_FRAMES[level - 1];
      int attackRating = D2MOO_ATTACK_RATING_PERCENT[level - 1];
      assertArrayEquals(new int[] {minimum, maximum},
          SorceressSkills.getEnchantDamage(skill, level, name -> 0, 0),
          "native enma/exma level " + level);
      assertEquals(duration, SorceressSkills.getEnchantDuration(skill, level),
          "native AuraLenCalc level " + level);
      assertEquals(attackRating,
          SorceressSkills.getEnchantAttackRatingPercent(skill, level),
          "native toht level " + level);

      StateList states = new StateList(100 + level);
      UnitState state = SorceressSkills.applyEnchantState(
          states, skill, level, 7, name -> 0, 0);
      assertNotNull(state, "native enchant state level " + level);
      assertEquals(StateId.ENCHANT, state.stateId);
      assertEquals(level, state.level);
      assertEquals(duration, state.duration);
      assertEquals(minimum, state.getStatContributionValue(Stat.firemindam));
      assertEquals(maximum, state.getStatContributionValue(Stat.firemaxdam));
      assertEquals(attackRating,
          state.getStatContributionValue(Stat.item_tohit_percent));
      assertEquals(minimum, states.getTotalStatContribution(Stat.firemindam));
      assertEquals(maximum, states.getTotalStatContribution(Stat.firemaxdam));
    }
  }

  @Test
  void synergyAndMasteryKeepNativeFixedPointUntilEnmaExmaReturn() {
    Skills.Entry skill = Riiablo.files.skills.get("Enchant");
    assertArrayEquals(new int[] {11, 14},
        SorceressSkills.getEnchantDamage(
            skill, 1, name -> "Warmth".equals(name) ? 1 : 0, 30));
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
