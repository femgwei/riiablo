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

/** Thirty-first DMG-04 audit: Avoid prevents incoming ranged damage. */
class AvoidGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_AVOID_CHANCE = {
      24, 31, 37, 41, 45, 48, 50, 52, 54, 56,
      57, 59, 60, 61, 62, 63, 63, 64, 65, 65
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeRangedAvoidanceChances() {
    Skills.Entry skill = Riiablo.files.skills.get("Avoid");
    assertNotNull(skill);
    assertEquals(18, skill.Id);
    assertTrue(skill.passive);
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

    assertEquals("avoid", skill.passivestate);
    assertEquals("passive_avoid", skill.passivestat[0]);
    assertEquals("dm12", skill.passivecalc[0]);
    assertEquals(15, skill.Param[0]);
    assertEquals(75, skill.Param[1]);

    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_AVOID_CHANCE[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.passivecalc[0], skill, level),
          "native PassiveCalc level " + level);
      assertEquals(expected, AmazonSkills.getAvoidChance(level),
          "riiablo passive chance level " + level);

      StateList states = new StateList(100 + level);
      UnitState state = AmazonSkills.applyPassiveState(states, skill, level, 7);
      assertNotNull(state, "native passive state level " + level);
      assertEquals(StateId.AVOID, state.stateId);
      assertEquals(level, state.level);
      assertTrue(state.isPermanent());
      assertEquals(expected, state.getStatContributionValue(Stat.passive_avoid));
      assertEquals(expected, states.getTotalStatContribution(Stat.passive_avoid));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
