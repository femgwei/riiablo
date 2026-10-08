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

/** DMG-04 audit: Pierce modifies projectile delivery without owning damage. */
class PierceGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_PIERCE_CHANCE = {
      24, 34, 43, 49, 55, 59, 63, 66, 69, 71,
      74, 76, 77, 79, 80, 82, 83, 84, 85, 86
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeProjectilePierceChances() {
    Skills.Entry skill = Riiablo.files.skills.get("Pierce");
    assertNotNull(skill);
    assertEquals(33, skill.Id);
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

    assertEquals("pierce", skill.passivestate);
    assertEquals("skill_pierce", skill.passivestat[0]);
    assertEquals("dm12", skill.passivecalc[0]);
    assertEquals(10, skill.Param[0]);
    assertEquals(100, skill.Param[1]);

    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_PIERCE_CHANCE[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.passivecalc[0], skill, level),
          "native PassiveCalc level " + level);
      assertEquals(expected, AmazonSkills.getPierceChance(level),
          "riiablo passive chance level " + level);

      StateList states = new StateList(100 + level);
      UnitState state = AmazonSkills.applyPassiveState(states, skill, level, 7);
      assertNotNull(state, "native passive state level " + level);
      assertEquals(StateId.PIERCE, state.stateId);
      assertEquals(level, state.level);
      assertTrue(state.isPermanent());
      assertEquals(expected, state.getStatContributionValue(Stat.skill_pierce));
      assertEquals(expected, states.getTotalStatContribution(Stat.skill_pierce));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
