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
  public void calcReadsTheSelectedCalcColumn() {
    Skills.Entry skill = new Skills.Entry();
    skill.calc1 = "ln12";
    skill.Param = new int[] {3, 2};
    assertEquals(7, NativeSkillResolver.calc(skill, 2, 1));
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
