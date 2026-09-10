package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.PaladinSkills;
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

  @Test
  void smiteUsesNativeShieldAttackFunctionAndStunFormula() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.SMITE);
    assertNotNull(skill);
    assertEquals("Smite", skill.skill);
    assertEquals(0, skill.srvstfunc);
    assertEquals(150, skill.srvdofunc);
    assertTrue(SkillFormula.evaluate(skill.calc2, skill, 1) > 0,
        "Smite must carry a native stun duration formula");
  }

  @Test
  void chargeUsesNativeStartDoFunctionsAndScalesDamageAndAttackRating() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.CHARGE);
    assertNotNull(skill);
    assertEquals("Charge", skill.skill);
    assertEquals(31, skill.srvstfunc);
    assertEquals(67, skill.srvdofunc);
    int level1Damage = PaladinSkills.getChargeDamagePercent(skill, 1);
    int level5Damage = PaladinSkills.getChargeDamagePercent(skill, 5);
    assertTrue(level1Damage > 0, "Charge must carry a native damage bonus formula");
    assertTrue(level5Damage >= level1Damage, "Charge damage must not decrease with level");
    int level1Ar = PaladinSkills.getChargeAttackRating(skill, 1, 100);
    int level5Ar = PaladinSkills.getChargeAttackRating(skill, 5, 100);
    assertTrue(level5Ar >= level1Ar, "Charge attack rating must not decrease with level");
    assertTrue(PaladinSkills.getChargeVelocityBonus(skill) >= 0,
        "Charge velocity bonus must be a non-negative native Param1 value");
  }
}
