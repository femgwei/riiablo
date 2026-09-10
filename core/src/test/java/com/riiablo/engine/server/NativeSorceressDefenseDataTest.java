package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

/** Executable 1.10f contract for Enchant, Fire Mastery and the cold armors. */
class NativeSorceressDefenseDataTest extends RiiabloTest {
  @Test
  void frozenArmorRetainsNativeEventAndFormulaColumns() {
    Skills.Entry skill = skill(SkillId.FROZEN_ARMOR);
    assertEquals("Frozen Armor", skill.skill);
    assertEquals(18, skill.srvdofunc);
    assertEquals("frozenarmor", skill.aurastate);
    assertEquals("ln34+(skill('Shiver Armor'.blvl)+skill('Chilling Armor'.blvl))*par7",
        skill.auralencalc);
    assertEquals("damagedinmelee", skill.auraevent[0]);
    assertEquals(2, skill.auraeventfunc[0]);
    assertEquals("skill_armor_percent", skill.aurastat[0]);
    assertEquals("ln12", skill.aurastatcalc[0]);
    assertEquals("ln56*(100+((skill('Shiver Armor'.blvl)+skill('Chilling Armor'.blvl))*par8))/100",
        skill.calc1);
    assertEquals(3500, SorceressSkills.getDefensiveArmorDuration(
        skill, 1, name -> 1));
    assertEquals(30, SorceressSkills.getDefensiveArmorDefensePercent(skill, 1));
    assertEquals(33, SorceressSkills.getFrozenArmorFreezeLength(
        skill, 1, name -> 1));
  }

  @Test
  void shiverAndChillingArmorRetainDistinctNativeReactions() {
    Skills.Entry shiver = skill(SkillId.SHIVER_ARMOR);
    assertEquals("attackedinmelee", shiver.auraevent[0]);
    assertEquals(3, shiver.auraeventfunc[0]);
    assertEquals("cold", shiver.EType);
    assertEquals(12, shiver.EMin);
    assertEquals(16, shiver.EMax);
    assertEquals(100, shiver.ELen);
    int[] shiverDamage = SorceressSkills.getArmorColdDamage(
        shiver, 1, name -> 1);
    assertEquals(7, shiverDamage[0]);
    assertEquals(9, shiverDamage[1]);
    assertEquals(100, SorceressSkills.getArmorColdLength(
        shiver, 1, name -> 1));

    Skills.Entry chilling = skill(SkillId.CHILLING_ARMOR);
    assertEquals("hitbymissile", chilling.auraevent[0]);
    assertEquals(1, chilling.auraeventfunc[0]);
    assertEquals("chillingarmorbolt", chilling.srvmissilea);
    assertEquals(4100, SorceressSkills.getDefensiveArmorDuration(
        chilling, 1, name -> 1));
    Missiles.Entry bolt = Riiablo.files.Missiles.get(chilling.srvmissilea);
    assertNotNull(bolt);
    assertEquals("Chilling Armor", bolt.Skill);
    assertEquals(3, bolt.CollideType);
    assertTrue(bolt.CollideKill);
    assertFalse(bolt.ReturnFire, "the return bolt must not recursively trigger Chilling Armor");
  }

  @Test
  void enchantAndFireMasteryRetainNativeStatLists() {
    Skills.Entry enchant = skill(SkillId.ENCHANT);
    assertTrue(SorceressSkills.isEnchant(enchant));
    assertEquals(25, enchant.srvdofunc);
    assertEquals("enchant", enchant.aurastate);
    assertEquals("ln12", enchant.auralencalc);
    assertEquals("firemindam", enchant.aurastat[0]);
    assertEquals("enma", enchant.aurastatcalc[0]);
    assertEquals("firemaxdam", enchant.aurastat[1]);
    assertEquals("exma", enchant.aurastatcalc[1]);
    assertEquals("item_tohit_percent", enchant.aurastat[2]);
    assertEquals("toht", enchant.aurastatcalc[2]);
    assertEquals(3600, SorceressSkills.getEnchantDuration(enchant, 1));
    assertEquals(20, SorceressSkills.getEnchantAttackRatingPercent(enchant, 1));
    int[] damage = SorceressSkills.getEnchantDamage(
        enchant, 1, name -> "Warmth".equals(name) ? 1 : 0, 30);
    assertEquals(10, damage[0]);
    assertEquals(13, damage[1]);

    Skills.Entry mastery = skill(SkillId.FIRE_MASTERY);
    assertEquals(0, mastery.srvdofunc);
    assertTrue(mastery.passive);
    assertEquals("firemastery", mastery.passivestate);
    assertEquals("passive_fire_mastery", mastery.passivestat[0]);
    assertEquals("ln12", mastery.passivecalc[0]);
    assertEquals(30, SorceressSkills.getFireMasteryPercent(mastery, 1));
    assertEquals(37, SorceressSkills.getFireMasteryPercent(mastery, 2));
  }

  @Test
  void coldArmorStatesRemainOneExclusiveVisualGroup() {
    assertArmorState(StateId.FROZENARMOR, "frozenarmor");
    assertArmorState(StateId.SHIVERARMOR, "shiverarmor");
    assertArmorState(StateId.CHILLINGARMOR, "chillarmor");
  }

  private static void assertArmorState(int stateId, String overlay) {
    States.Entry state = Riiablo.files.States.get(stateId);
    assertNotNull(state);
    assertEquals(1, state.group);
    assertEquals(overlay, state.overlays[0]);
  }

  private static Skills.Entry skill(int id) {
    Skills.Entry skill = Riiablo.files.skills.get(id);
    assertNotNull(skill, "missing 1.10f Skills.txt row " + id);
    return skill;
  }
}
