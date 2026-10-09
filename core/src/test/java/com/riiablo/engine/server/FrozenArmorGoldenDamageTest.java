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

/** Fifty-first DMG-04 audit: Frozen Armor is a non-damaging defensive state. */
class FrozenArmorGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_DEFENSE_PERCENT = {
      30, 35, 40, 45, 50, 55, 60, 65, 70, 75,
      80, 85, 90, 95, 100, 105, 110, 115, 120, 125
  };
  private static final int[] D2MOO_DURATION_FRAMES = {
      3000, 3300, 3600, 3900, 4200, 4500, 4800, 5100, 5400, 5700,
      6000, 6300, 6600, 6900, 7200, 7500, 7800, 8100, 8400, 8700
  };
  private static final int[] D2MOO_FREEZE_FRAMES = {
      30, 33, 36, 39, 42, 45, 48, 51, 54, 57,
      60, 63, 66, 69, 72, 75, 78, 81, 84, 87
  };

  @Test
  void levelOneToTwentyAreNondamagingNativeDefenseAndFreezeStates() {
    Skills.Entry skill = Riiablo.files.skills.get("Frozen Armor");
    assertNotNull(skill);
    assertEquals(40, skill.Id);
    assertEquals(0, skill.srvstfunc);
    assertEquals(18, skill.srvdofunc);
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

    assertEquals("frozenarmor", skill.aurastate);
    assertEquals("damagedinmelee", skill.auraevent[0]);
    assertEquals(2, skill.auraeventfunc[0]);
    assertEquals("skill_armor_percent", skill.aurastat[0]);
    assertEquals("ln12", skill.aurastatcalc[0]);
    assertEquals("ln34+(skill('Shiver Armor'.blvl)+skill('Chilling Armor'.blvl))*par7",
        skill.auralencalc);
    assertEquals("ln56*(100+((skill('Shiver Armor'.blvl)+skill('Chilling Armor'.blvl))*par8))/100",
        skill.calc1);
    assertArrayEquals(new int[] {30, 5, 3000, 300, 30, 3, 250, 5}, skill.Param);

    for (int level = 1; level <= 20; level++) {
      int defense = D2MOO_DEFENSE_PERCENT[level - 1];
      int duration = D2MOO_DURATION_FRAMES[level - 1];
      int freeze = D2MOO_FREEZE_FRAMES[level - 1];
      assertEquals(defense,
          SkillFormula.evaluate(skill.aurastatcalc[0], skill, level),
          "native AuraStatCalc level " + level);
      assertEquals(duration,
          SkillFormula.evaluate(skill.auralencalc, skill, level,
              name -> 0, name -> Riiablo.files.skills.get(name)),
          "native AuraLenCalc level " + level);
      assertEquals(freeze,
          SkillFormula.evaluate(skill.calc1, skill, level,
              name -> 0, name -> Riiablo.files.skills.get(name)),
          "native Calc1 level " + level);
      assertEquals(defense,
          SorceressSkills.getDefensiveArmorDefensePercent(skill, level));
      assertEquals(duration,
          SorceressSkills.getDefensiveArmorDuration(skill, level, name -> 0));
      assertEquals(freeze,
          SorceressSkills.getFrozenArmorFreezeLength(skill, level, name -> 0));

      StateList states = new StateList(100 + level);
      UnitState state = SorceressSkills.applyDefensiveArmorState(
          states, skill, level, 7, name -> 0);
      assertNotNull(state, "native defensive state level " + level);
      assertEquals(StateId.FROZENARMOR, state.stateId);
      assertEquals(level, state.level);
      assertEquals(duration, state.duration);
      assertEquals(defense,
          state.getStatContributionValue(Stat.skill_armor_percent));
      assertEquals(defense,
          states.getTotalStatContribution(Stat.skill_armor_percent));
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
