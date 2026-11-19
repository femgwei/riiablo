package com.riiablo.engine.server.combat;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponSkillDamageTest extends RiiabloTest {
  @Test void bashLevelsOneThroughFortyKeepFractionalBonusAndLateFlatAddition() {
    Skills.Entry bash = Riiablo.files.skills.get(SkillId.BASH);
    for (int level = 1; level <= 40; level++) {
      WeaponSkillAttackProfile p = WeaponSkillAttackProfile.create(bash, level, name -> 0);
      int expectedPercent = 50 + (level - 1) * 5;
      assertEquals(expectedPercent, p.enhancedDamage, "Bash level " + level);
      assertEquals(level, p.flatPhysicalDamage, "Bash level " + level);
      int expectedFixed = 256 + 256 * expectedPercent / 100 + level * 256;
      CombatSystem.CombatResult result = hit(attrs(), attrs(), p, null, false, false);
      assertEquals(expectedFixed, result.physicalDamage,
          "Bash level " + level + ": calc2 HP is shifted to fixed point after damage bonuses");
      assertEquals(level * 256, result.latePhysicalDamage);
      assertEquals((expectedFixed - level * 256) / 256f, result.totalDamageHp(),
          "Bash calc2 must not change the cached life damage");
    }
  }

  @Test void barbarianSrvDo002WeaponSkillsUseTheSkillsTxtDamageFormula() {
    for (int id : new int[] {SkillId.BASH, SkillId.STUN, SkillId.CONCENTRATE}) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill);
      WeaponSkillAttackProfile profile = profile(id);
      assertEquals(SkillFormula.evaluate(skill.calc1, skill, 1, name -> 0),
          profile.enhancedDamage, skill.skill);
      assertEquals(SkillFormula.evaluate(skill.calc2, skill, 1, name -> 0),
          profile.flatPhysicalDamage, skill.skill);
      assertEquals(128, profile.sourceDamage, skill.skill);
    }
    assertEquals(50, profile(SkillId.BASH).enhancedDamage);
    assertEquals(1, profile(SkillId.BASH).flatPhysicalDamage);
  }

  @Test void sixNativeLevelOneProfilesUseTheirActualDamageFormulaSlots() {
    int[] ids = {SkillId.FRENZY, SkillId.BERSERK, SkillId.ZEAL, SkillId.SACRIFICE, SkillId.JAB, SkillId.FEND};
    int[] ed = {90, 150, 0, 180, -15, 70};
    for (int i = 0; i < ids.length; i++) {
      WeaponSkillAttackProfile p = profile(ids[i]);
      assertEquals(ed[i], p.enhancedDamage, "skill " + ids[i]);
      assertEquals(128, p.sourceDamage, "skill " + ids[i]);
    }
    assertEquals(100, profile(SkillId.BERSERK).conversionPercent);
    assertEquals(6, WeaponSkillAttackProfile.create(Riiablo.files.skills.get(SkillId.ZEAL), 2,
        name -> 0).enhancedDamage);
    assertEquals(0, com.riiablo.engine.server.skill.PaladinSkills.getZealDamagePercent(
        Riiablo.files.skills.get(SkillId.ZEAL), 1));
    assertThrows(IllegalArgumentException.class, () -> profile(SkillId.SMITE));
  }

  @Test void hardPointSynergiesAreIncludedWithoutMultiplyingEquipmentElements() {
    WeaponSkillAttackProfile berserk = WeaponSkillAttackProfile.create(
        Riiablo.files.skills.get(SkillId.BERSERK), 1,
        name -> "Howl".equals(name) ? 3 : "Shout".equals(name) ? 4 : 0);
    assertEquals(220, berserk.enhancedDamage);
    WeaponSkillAttackProfile zeal = WeaponSkillAttackProfile.create(
        Riiablo.files.skills.get(SkillId.ZEAL), 1, name -> "Sacrifice".equals(name) ? 2 : 0);
    assertEquals(24, zeal.enhancedDamage);
    Attributes a = attrs(); a.aggregate().put(Stat.firemindam, 1); a.aggregate().put(Stat.firemaxdam, 1);
    CombatSystem.CombatResult hit = hit(a, attrs(), profile(SkillId.SACRIFICE), null, false, false);
    assertEquals(716, hit.physicalDamage); // 256 + floor(256*180/100)
    assertEquals(256, hit.elementalDamage[CombatSystem.DAMAGE_FIRE]);
  }

  @Test void skillAndItemAndStateARBonusesAreAdditive() {
    Attributes a = attrs(); a.aggregate().put(Stat.item_tohit_percent, 20);
    StateList states = new StateList(1);
    states.addState(StateId.MIGHT, 100, 1, 1).setNativeModifier(Stat.item_tohit_percent, 30);
    CombatSystem.CombatResult hit = hit(a, attrs(), profile(SkillId.JAB), states, false, false);
    assertEquals(16000, hit.attackRating); // base 10000 * (100+20+30+10)%
  }

  @Test void sacrificeDoesNotApplyLateDemonOrUndeadEDAndDoesNotCrit() {
    Attributes a = attrs(); a.aggregate().put(Stat.item_demondamage_percent, 500);
    a.aggregate().put(Stat.item_undeaddamage_percent, 500);
    a.aggregate().put(Stat.item_deadlystrike, 100);
    a.aggregate().put(Stat.passive_critical_strike, 100);
    CombatSystem.CombatResult hit = hit(a, attrs(), profile(SkillId.SACRIFICE), null, true, true);
    assertEquals(716, hit.physicalDamage);
    assertFalse(hit.critical); assertFalse(hit.deadlyStrike);
  }

  @Test void berserkConversionHappensBeforeSeparatePhysicalAndMagicResistance() {
    Attributes a = attrs(), d = attrs();
    a.aggregate().put(Stat.strength, 10);
    a.aggregate().put(Stat.firemindam, 1); a.aggregate().put(Stat.firemaxdam, 1);
    d.aggregate().put(Stat.damageresist, 100); d.aggregate().put(Stat.magicresist, 50);
    CombatSystem.CombatResult hit = hit(a, d, profile(SkillId.BERSERK), null, false, false);
    assertEquals(0, hit.physicalDamage);
    assertEquals(332, hit.elementalDamage[CombatSystem.DAMAGE_MAGIC]); // floor(665/2)
    assertEquals(256, hit.elementalDamage[CombatSystem.DAMAGE_FIRE]);
  }

  @Test void jabPenaltyAndFendBonusKeepFractionalDamage() {
    assertEquals(218, hit(attrs(), attrs(), profile(SkillId.JAB), null, false, false).physicalDamage);
    assertEquals(435, hit(attrs(), attrs(), profile(SkillId.FEND), null, false, false).physicalDamage);
    assertEquals(256, hit(attrs(), attrs(), profile(SkillId.ZEAL), null, false, false).physicalDamage);
  }

  private static WeaponSkillAttackProfile profile(int id) {
    return WeaponSkillAttackProfile.create(Riiablo.files.skills.get(id), 1, name -> 0);
  }
  private static CombatSystem.CombatResult hit(Attributes a, Attributes d, WeaponSkillAttackProfile p,
      StateList states, boolean demon, boolean undead) {
    return new CombatSystem(n -> 0).calculateWeaponSkillAttack(a, d, true, false,
        new WeaponAttackSource(a, null, null, false, false), p, 10000,
        states, null, false, NativeCombatContext.normal(), demon, undead, true);
  }
  private static Attributes attrs() {
    Attributes a = Attributes.obtainStandard();
    a.base().put(Stat.level, 1); a.base().put(Stat.mindamage, 1); a.base().put(Stat.maxdamage, 2);
    a.base().put(Stat.tohit, 10000); a.base().put(Stat.hitpoints, 1000f); a.base().put(Stat.maxhp, 1000f);
    a.reset(); return a;
  }
}
