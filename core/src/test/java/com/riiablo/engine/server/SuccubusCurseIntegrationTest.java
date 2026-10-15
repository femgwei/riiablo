package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

/** Real Skills/States rows; this verifies state application, not Blood Mana spending. */
class SuccubusCurseIntegrationTest extends RiiabloTest {
  @Test void defenseCurseAppliesItsRealArmorPenalty() {
    verify("Defense Curse", StateId.DEFENSE_CURSE);
  }

  @Test void bloodManaAppliesItsRealState() {
    verify("Blood Mana", StateId.BLOOD_MANA);
  }

  private void verify(String name, int stateId) {
    Skills.Entry skill = Riiablo.files.skills.get(name);
    assertNotNull(skill);
    assertEquals(30, skill.srvdofunc);
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      StateList states = new StateList(7);
      UnitState state = NecromancerSkills.applyCurse(states, Riiablo.files.States,
          skill, 1, 1, Riiablo.files.DifficultyLevels.get(difficulty), null, true);
      assertNotNull(state, name);
      assertEquals(stateId, state.stateId);
      assertEquals(skill.Id, state.skillId);
      assertEquals(1, state.sourceEntityId);
      assertEquals(SkillFormula.evaluate(skill.auralencalc, skill, 1), state.duration);
      assertTrue(states.hasCurse(), "Fn118 must see the applied curse");
      assertTrue(StateId.isCurse(stateId));
      if (stateId == StateId.DEFENSE_CURSE) {
        int armorStat = com.riiablo.attributes.Stat.index(skill.aurastat[0]);
        assertTrue(armorStat >= 0, skill.aurastat[0]);
        assertEquals(armorStat, NecromancerSkills.resolveCurseStatId(skill.aurastat[0]), skill.aurastat[0]);
        assertEquals(SkillFormula.evaluate(skill.aurastatcalc[0], skill, 1),
            state.getStatContributionValue(armorStat));
      }
    }
  }
}
