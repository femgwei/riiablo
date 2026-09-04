package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import org.junit.jupiter.api.Test;

class DruidRabiesFireClawsTest extends RiiabloTest {
  @Test
  void rabiesIsWolfOnlyAndUsesPoisonDuration() {
    Skills.Entry rabies = Riiablo.files.skills.get(SkillId.RABIES);
    StateList states = new StateList(1);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(rabies, states));
    states.addState(StateId.WOLF, 100, 1, 1);
    assertTrue(DruidSkills.isSkillAllowedInCurrentShape(rabies, states));
    int[] poison = DruidSkills.getRabiesPoisonDamage(rabies, 1, name -> 0);
    assertEquals(48, poison[0]);
    assertEquals(112, poison[1]);
    assertEquals(100, DruidSkills.getRabiesPoisonDuration(rabies, 1, name -> 0));
  }

  @Test
  void fireClawsAcceptsWolfAndBearAndAddsFireOnce() {
    Skills.Entry claws = Riiablo.files.skills.get(SkillId.FIRE_CLAWS);
    StateList states = new StateList(2);
    assertFalse(DruidSkills.isSkillAllowedInCurrentShape(claws, states));
    states.addState(StateId.BEAR, 100, 1, 2);
    assertTrue(DruidSkills.isSkillAllowedInCurrentShape(claws, states));
    int[] fire = DruidSkills.getFireClawsFireDamage(claws, 1, name -> 0);
    Attributes attacker = Attributes.obtainStandard();
    attacker.base().put(Stat.level, 1);
    attacker.base().put(Stat.tohit, 1000);
    attacker.base().put(Stat.mindamage, 10);
    attacker.base().put(Stat.maxdamage, 10);
    attacker.base().put(Stat.strength, 0);
    attacker.reset();
    Attributes defender = Attributes.obtainStandard();
    defender.base().put(Stat.level, 1);
    defender.base().put(Stat.armorclass, 0);
    defender.base().put(Stat.hitpoints, 1000);
    defender.reset();
    int[] mins = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] maxs = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    mins[CombatSystem.DAMAGE_FIRE] = fire[0];
    maxs[CombatSystem.DAMAGE_FIRE] = fire[1];
    CombatSystem.CombatResult result = CombatSystem.INSTANCE
        .calculatePrecomputedMeleeElementalAttack(attacker, defender, true, false,
            10, 10, 1000, mins, maxs, 0, 0, states, null, false);
    assertTrue(result.hit);
    assertTrue(result.elementalDamage[CombatSystem.DAMAGE_FIRE] >= fire[0]);
    assertTrue(result.elementalDamage[CombatSystem.DAMAGE_FIRE] <= fire[1]);
    assertEquals(10 + result.elementalDamage[CombatSystem.DAMAGE_FIRE], result.totalDamage);
  }
}
