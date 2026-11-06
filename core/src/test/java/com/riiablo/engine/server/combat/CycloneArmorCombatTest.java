package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

class CycloneArmorCombatTest extends RiiabloTest {
  private final CombatSystem combat = new CombatSystem(bound -> 0);

  @Test void retailFormulaUsesHardPointSynergiesAndRecastReplacesItsPool() {
    Skills.Entry skill = Riiablo.files.skills.get(235);
    assertTrue(DruidSkills.isCycloneArmor(skill));
    StateList states = new StateList(1);
    UnitState armor = DruidSkills.applyCycloneArmorState(states, skill, 5, 1,
        name -> "Twister".equals(name) ? 3 : 0);
    int expected = SkillFormula.evaluate(skill.aurastatcalc[0], skill, 5,
        name -> "Twister".equals(name) ? 3 : 0);
    assertEquals(expected, CycloneArmor.capacity(states));
    assertEquals(0, armor.duration, "empty native AuraLenCalc is permanent");
    assertEquals(expected, armor.getStatContributionValue(Stat.bonearmormax));
    CycloneArmor.consume(states, expected / 2);
    UnitState refreshed = DruidSkills.applyCycloneArmorState(states, skill, 1, 1, name -> 0);
    assertNotNull(refreshed);
    assertEquals(40 * 256, CycloneArmor.capacity(states));
    assertEquals(1, refreshed.level);
    assertEquals(235, refreshed.skillId);
  }

  @Test void directHitSpendsRawPoolBeforeResistanceMdrAndItemHealingAbsorb() {
    StateList states = shield(40 * 256);
    Attributes defender = attributes();
    defender.base().put(Stat.fireresist, 75);
    defender.base().put(Stat.magic_damage_reduction, 10);
    defender.base().put(Stat.item_absorbfire, 3);
    defender.reset();
    CombatSystem.CombatResult hit = combat.calculateNativeFixedElementalDamage(
        defender, true, false, CombatSystem.DAMAGE_FIRE, 100, 0, states,
        NativeCombatContext.normal());
    // This direct API returns integer HP: 100 - shield40 - MDR10 = 50;
    // resist75 -> 12; item absorb3 -> 9. Native rate packets retain fractions.
    assertEquals(9f, hit.totalDamageHp(), 0.0001f);
    assertEquals(3f, hit.absorbedLifeHp(), 0.0001f);
    assertEquals(40 * 256, hit.cycloneArmorConsumedFixed);
    assertFalse(states.hasState(StateId.CYCLONEARMOR));
  }

  @Test void attackPacketConsumesFireThenColdThenLightningAndPreservesOtherChannels() {
    StateList states = shield(100 * 256);
    Attributes defender = attributes();
    defender.base().put(Stat.coldresist, 50);
    defender.reset();
    int[] elements = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    elements[CombatSystem.DAMAGE_FIRE] = 60;
    elements[CombatSystem.DAMAGE_COLD] = 60;
    elements[CombatSystem.DAMAGE_LIGHTNING] = 60;
    elements[CombatSystem.DAMAGE_MAGIC] = 10;
    CombatSystem.CombatResult hit = combat.calculateAttackWithContext(
        attributes(), defender, false, true, true, 0, 0, 0, true,
        elements, elements, 20, 0, null, states, NativeCombatContext.normal());
    assertEquals(0f, hit.elementalDamageHp(CombatSystem.DAMAGE_FIRE));
    assertEquals(10f, hit.elementalDamageHp(CombatSystem.DAMAGE_COLD));
    assertEquals(60f, hit.elementalDamageHp(CombatSystem.DAMAGE_LIGHTNING));
    assertEquals(10f, hit.elementalDamageHp(CombatSystem.DAMAGE_MAGIC));
    assertFalse(states.hasState(StateId.CYCLONEARMOR));
  }

  @Test void pvpPrecedesPoolAndFractionalRateHitsKeepEightBitRemainders() {
    StateList states = shield(10 * 256);
    CombatSystem.CombatResult pvp = combat.calculateNativeFixedElementalDamage(
        attributes(), true, true, CombatSystem.DAMAGE_LIGHTNING, 100, 0, states,
        NativeCombatContext.normal());
    assertEquals(7f, pvp.totalDamageHp());
    assertFalse(states.hasState(StateId.CYCLONEARMOR));

    states = shield(257);
    CombatSystem.FixedElementalDamageResult first = combat.calculateFixedElementalRateDamage(
        attributes(), true, false, CombatSystem.DAMAGE_FIRE, 128, 0, 0, states,
        NativeCombatContext.normal());
    assertEquals(0, first.damageFixed);
    assertEquals(129, CycloneArmor.capacity(states));
    CombatSystem.FixedElementalDamageResult second = combat.calculateFixedElementalRateDamage(
        attributes(), true, false, CombatSystem.DAMAGE_FIRE, 256, 0, 0, states,
        NativeCombatContext.normal());
    assertEquals(127, second.damageFixed);
    assertFalse(states.hasState(StateId.CYCLONEARMOR));
  }

  @Test void staticFieldAndImmunePacketsStillVisitArmorBeforeResistance() {
    StateList states = shield(40 * 256);
    Attributes defender = attributes();
    defender.base().put(Stat.lightresist, 75);
    defender.reset();
    CombatSystem.StaticFieldDamageResult field = combat.calculateStaticFieldDamage(
        defender, true, false, CombatSystem.DAMAGE_LIGHTNING, 100 * 256, states,
        NativeCombatContext.normal());
    assertEquals(15 * 256, field.damageFixed);
    assertFalse(states.hasState(StateId.CYCLONEARMOR));

    states = shield(40 * 256);
    defender.base().put(Stat.fireresist, 100);
    defender.reset();
    CombatSystem.FixedElementalDamageResult immune = combat.calculateFixedElementalRateDamage(
        defender, false, false, CombatSystem.DAMAGE_FIRE, 100 * 256, 0, 0, states,
        NativeCombatContext.normal());
    assertTrue(immune.immune);
    assertEquals(40 * 256, immune.cycloneArmorConsumedFixed);
    assertFalse(states.hasState(StateId.CYCLONEARMOR));
  }

  @Test void poisonMagicAndPhysicalCannotSpendElementalPoolAndBoneArmorIsIndependent() {
    StateList states = shield(40 * 256);
    UnitState bone = states.addStateLayer(StateId.BONEARMOR, 0, 1, 1, 68);
    bone.setStatContribution(Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, 30);
    combat.calculateNativeFixedElementalDamage(attributes(), true, false,
        CombatSystem.DAMAGE_MAGIC, 20, 0, states, NativeCombatContext.normal());
    combat.calculateNativeFixedElementalDamage(attributes(), true, false,
        CombatSystem.DAMAGE_POISON, 20, 0, states, NativeCombatContext.normal());
    combat.calculateNativeFixedPhysicalDamage(attributes(), true, 20, states);
    assertEquals(40 * 256, CycloneArmor.capacity(states));
    assertEquals(30, states.getState(StateId.BONEARMOR).getStatContributionValue(Stat.bonearmor));
  }

  static StateList shield(int fixed) {
    StateList states = new StateList(1);
    UnitState armor = states.addStateLayer(StateId.CYCLONEARMOR, 0, 1, 1, 235);
    armor.setStatContribution(Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, fixed);
    armor.setStatContribution(Stat.bonearmormax, 0, NativeStatResolver.Operation.ADD, fixed);
    armor.runtimeValue = fixed;
    return states;
  }

  static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 20);
    attrs.base().put(Stat.hitpoints, 1000);
    attrs.base().put(Stat.maxhp, 1000);
    attrs.reset();
    return attrs;
  }
}
