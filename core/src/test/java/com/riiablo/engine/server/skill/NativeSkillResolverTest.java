package com.riiablo.engine.server.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Regression coverage for the data-driven seven-class skill gate. */
public class NativeSkillResolverTest {
  @Test
  public void classOwnershipComesFromCharclassCell() {
    Skills.Entry amazon = new Skills.Entry();
    amazon.charclass = "ama";
    assertTrue(NativeSkillResolver.belongsToClass(amazon, Riiablo.AMAZON));
    assertFalse(NativeSkillResolver.belongsToClass(amazon, Riiablo.SORCERESS));

    Skills.Entry system = new Skills.Entry();
    system.charclass = "";
    assertTrue(NativeSkillResolver.belongsToClass(system, Riiablo.BARBARIAN));
  }

  @Test
  public void weaponActionsAreNotAllowedInTownWithoutNativeTable() {
    Skills.Entry attack = new Skills.Entry();
    attack.Id = com.riiablo.skill.SkillCodes.attack;
    attack.skill = "Attack";
    assertFalse(NativeSkillResolver.isAllowedInTown(attack));

    Skills.Entry utility = new Skills.Entry();
    utility.Id = -1;
    utility.skill = "Scroll of Townportal";
    assertTrue(NativeSkillResolver.isAllowedInTown(utility));
  }

  @Test
  public void inputGateAllowsUtilityOnlyOutsideTownRestriction() {
    Skills.Entry attack = new Skills.Entry();
    attack.Id = com.riiablo.skill.SkillCodes.attack;
    attack.skill = "Attack";
    assertFalse(NativeSkillResolver.isAllowedInTown(attack, true));
    assertTrue(NativeSkillResolver.isAllowedInTown(attack, false));

    Skills.Entry utility = new Skills.Entry();
    utility.Id = -1;
    utility.skill = "Scroll of Townportal";
    assertTrue(NativeSkillResolver.isAllowedInTown(utility, true));
  }

  @Test
  public void playerSummonsAreAllowedInTownButGroundBoneSkillsAreNotSummons() {
    Skills.Entry skeleton = new Skills.Entry();
    skeleton.Id = 70;
    skeleton.skill = "Raise Skeleton";
    skeleton.srvdofunc = 31;
    skeleton.summon = "necroskeleton";
    skeleton.pettype = "skeleton";
    assertTrue(NativeSkillResolver.isPlayerSummonSkill(skeleton));
    assertTrue(NativeSkillResolver.isAllowedInTown(skeleton, true));

    Skills.Entry hydra = new Skills.Entry();
    hydra.Id = 62;
    hydra.skill = "Hydra";
    hydra.srvdofunc = 144;
    assertTrue(NativeSkillResolver.isPlayerSummonSkill(hydra),
        "Hydra must remain recognized when a reduced table omits Summon");
    assertTrue(NativeSkillResolver.isAllowedInTown(hydra, true));

    Skills.Entry boneWall = new Skills.Entry();
    boneWall.Id = 78;
    boneWall.skill = "Bone Wall";
    boneWall.srvdofunc = 60;
    boneWall.summon = "bonewall";
    boneWall.pettype = "bonewall";
    assertFalse(NativeSkillResolver.isPlayerSummonSkill(boneWall));
  }

  @Test
  public void basicAttackRetainsTargetableOnlyFallback() {
    Skills.Entry attack = new Skills.Entry();
    attack.Id = com.riiablo.skill.SkillCodes.attack;
    assertTrue(NativeSkillResolver.isTargetableOnly(attack));

    Skills.Entry groundSkill = new Skills.Entry();
    groundSkill.Id = -1;
    assertFalse(NativeSkillResolver.isTargetableOnly(groundSkill));
  }

  @Test
  public void amazonJavelinTreeRequiresTheActiveJavelinWeaponSet() {
    Skills.Entry jab = new Skills.Entry();
    jab.skill = "Jab";
    assertTrue(NativeSkillResolver.isAmazonJavelinSkill(jab));
    assertTrue(NativeSkillResolver.requiresThrowableWeapon(jab));

    Skills.Entry lightningFury = new Skills.Entry();
    lightningFury.skill = "Lightning Fury";
    assertTrue(NativeSkillResolver.isAmazonJavelinSkill(lightningFury));

    int[] javelinIds = {15, 20, 25, 35};
    for (int id : javelinIds) {
      Skills.Entry nativeRow = new Skills.Entry();
      nativeRow.Id = id;
      nativeRow.skill = "";
      assertTrue(NativeSkillResolver.isAmazonJavelinSkill(nativeRow),
          "native Amazon javelin skill id=" + id);
    }

    Skills.Entry throwSkill = new Skills.Entry();
    throwSkill.Id = com.riiablo.skill.SkillCodes.throw_;
    assertTrue(NativeSkillResolver.isAmazonJavelinWeaponSkill(throwSkill, Riiablo.AMAZON));
    assertFalse(NativeSkillResolver.isAmazonJavelinWeaponSkill(throwSkill, Riiablo.BARBARIAN));

    Skills.Entry bow = new Skills.Entry();
    bow.skill = "Magic Arrow";
    assertFalse(NativeSkillResolver.isAmazonJavelinSkill(bow));
    assertFalse(NativeSkillResolver.requiresThrowableWeapon(bow));
  }

  @Test
  public void manaUsesNativeFixedPointFormula() {
    Skills.Entry skill = new Skills.Entry();
    skill.mana = 12;
    skill.lvlmana = -1;
    skill.minmana = 5;
    skill.manashift = 8;
    assertEquals(12f, NativeSkillResolver.manaCost(skill, 1), 0.0001f);
    assertEquals(10f, NativeSkillResolver.manaCost(skill, 3), 0.0001f);
    skill.lvlmana = -10;
    assertEquals(5f, NativeSkillResolver.manaCost(skill, 3), 0.0001f);
  }

  @Test
  public void manaAvailabilityUsesTheSameFractionalBoundaryAsCasting() {
    assertTrue(NativeSkillResolver.hasEnoughMana(6f, 6f));
    assertTrue(NativeSkillResolver.hasEnoughMana(0f, 0f));
    assertFalse(NativeSkillResolver.hasEnoughMana(5.996f, 6f));
  }

  @Test
  public void calcReadsTheSelectedCalcColumn() {
    Skills.Entry skill = new Skills.Entry();
    skill.calc1 = "ln12";
    skill.Param = new int[] {3, 2};
    assertEquals(5, NativeSkillResolver.calc(skill, 2, 1));
    assertEquals(0, NativeSkillResolver.calc(skill, 2, 4));
  }

  @Test
  public void playerCastRequiresLearnedClassSkillAndLevel() {
    Skills.Entry skill = new Skills.Entry();
    skill.Id = 10;
    skill.charclass = "ama";
    skill.reqlevel = 6;
    CharData amazon = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);

    assertEquals(NativeSkillResolver.LEVEL_TOO_LOW,
        NativeSkillResolver.validatePlayerCast(amazon, skill, 1, 1));
    assertEquals(NativeSkillResolver.NOT_LEARNED,
        NativeSkillResolver.validatePlayerCast(amazon, skill, 0, 6));
    CharData sorc = CharData.createRemote("sorc", (byte) Riiablo.SORCERESS);
    sorc.setSkillLevel(skill.Id, 1);
    assertEquals(NativeSkillResolver.WRONG_CLASS,
        NativeSkillResolver.validatePlayerCast(sorc, skill, 1, 6));

    // An effective level without a base level represents an item-granted
    // cross-class skill (oskill) and is legal in the native rules.
    CharData oskill = CharData.createRemote("oskill", (byte) Riiablo.SORCERESS);
    assertEquals(NativeSkillResolver.OK,
        NativeSkillResolver.validatePlayerCast(oskill, skill, 1, 6));
  }

  @Test
  public void executorCanImportRowsForAllClassesWithoutSkillIdRanges() {
    Skills.Entry amazon = new Skills.Entry();
    amazon.Id = 300;
    amazon.skill = "Native Amazon Skill";
    amazon.charclass = "ama";
    amazon.reqlevel = 1;
    amazon.mana = 10;
    Skills.Entry assassin = new Skills.Entry();
    assassin.Id = 301;
    assassin.skill = "Native Assassin Skill";
    assassin.charclass = "ass";
    assassin.reqlevel = 1;

    SkillExecutor executor = new SkillExecutor();
    assertEquals(2, executor.registerNativeSkills(java.util.Arrays.asList(amazon, assassin)));
    assertEquals("Native Amazon Skill", executor.getSkillData(300).skillName);
    assertEquals("Native Assassin Skill", executor.getSkillData(301).skillName);
  }
}
