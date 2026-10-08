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

/** Forty-second DMG-04 audit: Evade prevents incoming attacks while moving. */
class EvadeGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_EVADE_CHANCE = {
      18, 25, 30, 34, 37, 40, 42, 44, 46, 47,
      49, 50, 51, 52, 53, 54, 54, 55, 55, 56
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeMovingAvoidanceChances() {
    Skills.Entry skill = Riiablo.files.skills.get("Evade");
    assertNotNull(skill);
    assertEquals(29, skill.Id);
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

    assertEquals("evade", skill.passivestate);
    assertEquals("passive_evade", skill.passivestat[0]);
    assertEquals("dm12", skill.passivecalc[0]);
    assertEquals(10, skill.Param[0]);
    assertEquals(65, skill.Param[1]);

    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_EVADE_CHANCE[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.passivecalc[0], skill, level),
          "native PassiveCalc level " + level);
      assertEquals(expected, AmazonSkills.getEvadeChance(level),
          "riiablo passive chance level " + level);

      StateList states = new StateList(100 + level);
      UnitState state = AmazonSkills.applyPassiveState(states, skill, level, 7);
      assertNotNull(state, "native passive state level " + level);
      assertEquals(StateId.EVADE, state.stateId);
      assertEquals(level, state.level);
      assertTrue(state.isPermanent());
      assertEquals(expected, state.getStatContributionValue(Stat.passive_evade));
      assertEquals(expected, states.getTotalStatContribution(Stat.passive_evade));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
