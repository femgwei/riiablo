package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.DifficultyLevels;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

/** 1.10f Skills/States/DifficultyLevels contract for native Necromancer curses. */
class NativeNecromancerCurseDataTest extends RiiabloTest {
  @Test
  void allTenCurseRowsResolveTheirNativeTargetState() {
    int[] ids = {SkillId.AMPLIFY_DAMAGE, SkillId.DIM_VISION, SkillId.WEAKEN,
        SkillId.IRON_MAIDEN, SkillId.TERROR, SkillId.CONFUSE, SkillId.LIFE_TAP,
        SkillId.ATTRACT, SkillId.DECREPIFY, SkillId.LOWER_RESIST};
    int[] states = {StateId.AMPLIFYDAMAGE, StateId.DIMVISION, StateId.WEAKEN,
        StateId.IRONMAIDEN, StateId.TERROR, StateId.CONFUSE, StateId.LIFETAP,
        StateId.ATTRACT, StateId.DECREPIFY, StateId.LOWERRESIST};
    for (int i = 0; i < ids.length; i++) {
      Skills.Entry skill = Riiablo.files.skills.get(ids[i]);
      assertNotNull(skill, "missing curse id=" + ids[i]);
      assertEquals(states[i], NecromancerSkills.resolveCurseStateId(
          skill.auratargetstate, skill.skill), skill.skill);
      int expectedFunction = ids[i] == SkillId.ATTRACT ? 59
          : ids[i] == SkillId.CONFUSE ? 61 : 30;
      assertEquals(expectedFunction, skill.srvdofunc, skill.skill);
      assertTrue(SkillFormula.evaluate(skill.auralencalc, skill, 1) > 0,
          skill.skill + " must have a positive native duration");
      if (ids[i] != SkillId.ATTRACT) {
        assertTrue(SkillFormula.evaluate(skill.aurarangecalc, skill, 1) > 0,
            skill.skill + " must have a positive native radius");
      }
      assertTrue(skill.aurafilter != 0, skill.skill + " aura filter missing");
      for (String stat : skill.aurastat) {
        if (stat != null && !stat.isEmpty()) {
          assertTrue(NecromancerSkills.resolveCurseStatId(stat) >= 0,
              skill.skill + " has unsupported aura stat " + stat);
        }
      }
    }
  }

  @Test
  void aiCurseDivisorAppliesToNativeAiCurses() {
    DifficultyLevels.Entry nightmare = Riiablo.files.DifficultyLevels.get(1);
    assertNotNull(nightmare);
    assertTrue(nightmare.AiCurseDivisor > 1);
    Skills.Entry dim = Riiablo.files.skills.get(SkillId.DIM_VISION);
    Skills.Entry terror = Riiablo.files.skills.get(SkillId.TERROR);
    Skills.Entry confuse = Riiablo.files.skills.get(SkillId.CONFUSE);
    assertEquals(SkillFormula.evaluate(dim.auralencalc, dim, 1) / nightmare.AiCurseDivisor,
        NecromancerSkills.curseDuration(dim, 1, nightmare));
    assertEquals(SkillFormula.evaluate(terror.auralencalc, terror, 1) / nightmare.AiCurseDivisor,
        NecromancerSkills.curseDuration(terror, 1, nightmare));
    assertEquals(SkillFormula.evaluate(confuse.auralencalc, confuse, 1)
            / nightmare.AiCurseDivisor,
        NecromancerSkills.curseDuration(confuse, 1, nightmare));
  }
}
