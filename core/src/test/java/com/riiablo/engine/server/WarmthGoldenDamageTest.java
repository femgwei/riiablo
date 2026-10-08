package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

/** Fiftieth DMG-04 audit: Warmth is a non-damaging mana-recovery passive. */
class WarmthGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_MANA_RECOVERY_BONUS = {
      30, 42, 54, 66, 78, 90, 102, 114, 126, 138,
      150, 162, 174, 186, 198, 210, 222, 234, 246, 258
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeManaRecoveryBonuses() {
    Skills.Entry skill = Riiablo.files.skills.get("Warmth");
    assertNotNull(skill);
    assertEquals(37, skill.Id);
    assertTrue(skill.passive);
    assertEquals(0, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals(0, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    assertEquals("warmth", skill.passivestate);
    assertEquals("manarecoverybonus", skill.passivestat[0]);
    assertEquals("ln12", skill.passivecalc[0]);
    assertEquals(30, skill.Param[0]);
    assertEquals(12, skill.Param[1]);

    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_MANA_RECOVERY_BONUS[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.passivecalc[0], skill, level),
          "native PassiveCalc level " + level);
      assertEquals(expected, SorceressSkills.calculateWarmthManaRegen(level),
          "legacy riiablo helper level " + level);
      assertEquals(expected, SorceressSkills.getWarmthManaRecoveryBonus(skill, level),
          "riiablo native passive value level " + level);

      StateList states = new StateList(100 + level);
      UnitState state = SorceressSkills.applyWarmthState(states, skill, level, 7);
      assertNotNull(state, "native passive state level " + level);
      assertEquals(StateId.WARMTH, state.stateId);
      assertEquals(level, state.level);
      assertTrue(state.isPermanent());
      assertEquals(expected, state.getStatContributionValue(Stat.manarecoverybonus));
      assertEquals(expected, states.getTotalManaRecoveryModifier());
      assertEquals(100 + expected,
          ManaRecoverySystem.recoveryPerTickEncoded(750_000, 300, expected, 0),
          "authoritative mana recovery level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
