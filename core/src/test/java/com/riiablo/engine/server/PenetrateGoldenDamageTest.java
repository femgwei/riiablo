package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

/** Thirty-sixth DMG-04 audit: Penetrate is a non-damaging attack-rating passive. */
class PenetrateGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_TO_HIT_PERCENT = {
      35, 45, 55, 65, 75, 85, 95, 105, 115, 125,
      135, 145, 155, 165, 175, 185, 195, 205, 215, 225
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeAttackRatingBonuses() {
    Skills.Entry skill = Riiablo.files.skills.get("Penetrate");
    assertNotNull(skill);
    assertEquals(23, skill.Id);
    assertTrue(skill.passive);
    assertEquals(0, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertTrue(blank(skill.EType));
    assertEquals(0, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    assertEquals("penetrate", skill.passivestate);
    assertEquals("item_tohit_percent", skill.passivestat[0]);
    assertEquals("ln12", skill.passivecalc[0]);
    assertEquals(35, skill.Param[0]);
    assertEquals(10, skill.Param[1]);

    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_TO_HIT_PERCENT[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.passivecalc[0], skill, level),
          "native PassiveCalc level " + level);
      assertEquals(expected, AmazonSkills.calculatePenetrateBonus(level),
          "riiablo passive bonus level " + level);

      StateList states = new StateList(100 + level);
      UnitState state = AmazonSkills.applyPassiveState(states, skill, level, 7);
      assertNotNull(state, "native passive state level " + level);
      assertEquals(StateId.PENETRATE, state.stateId);
      assertEquals(level, state.level);
      assertTrue(state.isPermanent());
      assertEquals(expected, state.getStatContributionValue(Stat.item_tohit_percent));
      assertEquals(expected, states.getTotalStatContribution(Stat.item_tohit_percent));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
