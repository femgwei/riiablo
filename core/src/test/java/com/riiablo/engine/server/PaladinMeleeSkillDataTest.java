package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Native data contract for the first Paladin melee tail skill. */
class PaladinMeleeSkillDataTest extends RiiabloTest {
  @Test
  void sacrificeUsesNativeStartAndDoFunctions() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.SACRIFICE);
    assertNotNull(skill);
    assertEquals("Sacrifice", skill.skill);
    assertEquals(29, skill.srvstfunc);
    assertEquals(64, skill.srvdofunc);
    int bonus = SkillFormula.evaluate(skill.calc1, skill, 1);
    int self = SkillFormula.evaluate(skill.calc2, skill, 1);
    assertTrue(bonus > 0, "Sacrifice must carry a native damage bonus formula");
    assertTrue(self > 0, "Sacrifice must carry a native self-damage formula");
  }
}
