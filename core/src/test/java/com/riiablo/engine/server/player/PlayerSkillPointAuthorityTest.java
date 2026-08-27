package com.riiablo.engine.server.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Server-side skill allocation rules using the real seven-class Skills.txt data. */
class PlayerSkillPointAuthorityTest extends RiiabloTest {
  @Test
  void validClassSkillConsumesExactlyOnePoint() {
    CharData data = character(CharacterClass.AMAZON, 99, 2);
    Skills.Entry skill = firstWithoutPrerequisite(CharacterClass.AMAZON);
    assertNotNull(skill);
    assertEquals(PlayerStatsManager.SKILL_OK,
        PlayerStatsManager.INSTANCE.validateSkillPoint(data, skill.Id));
    assertTrue(PlayerStatsManager.INSTANCE.spendSkillPoint(data, skill.Id));
    assertEquals(1, data.getBaseSkillLevel(skill.Id));
    assertEquals(1, PlayerStatsManager.INSTANCE.getAvailableSkillPoints(data));
    System.out.println("[SKILL_POINT_AUTH] phase=accept class=AMAZON skill=" + skill.skill
        + " level=1 points=1 status=PASS");
  }

  @Test
  void rejectsWrongClassLevelPrerequisiteAndCapWithoutMutation() {
    CharData data = character(CharacterClass.AMAZON, 1, 10);
    assertEquals(PlayerStatsManager.SKILL_WRONG_CLASS,
        PlayerStatsManager.INSTANCE.validateSkillPoint(
            data, CharacterClass.SORCERESS.firstSpell));

    Skills.Entry levelLocked = null;
    Skills.Entry prerequisiteLocked = null;
    for (int id = CharacterClass.AMAZON.firstSpell; id < CharacterClass.AMAZON.lastSpell; id++) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      if (skill.reqlevel > 1 && levelLocked == null) levelLocked = skill;
      if (skill.reqskill1 != null && !skill.reqskill1.isEmpty() && prerequisiteLocked == null) {
        prerequisiteLocked = skill;
      }
    }
    assertNotNull(levelLocked);
    assertEquals(PlayerStatsManager.SKILL_LEVEL_REQUIRED,
        PlayerStatsManager.INSTANCE.validateSkillPoint(data, levelLocked.Id));

    setProgress(data, 99, 10);
    assertNotNull(prerequisiteLocked);
    assertEquals(PlayerStatsManager.SKILL_PREREQUISITE,
        PlayerStatsManager.INSTANCE.validateSkillPoint(data, prerequisiteLocked.Id));

    Skills.Entry capped = firstWithoutPrerequisite(CharacterClass.AMAZON);
    int cap = capped.maxlvl > 0 ? capped.maxlvl : 20;
    data.setSkillLevel(capped.Id, cap);
    assertEquals(PlayerStatsManager.SKILL_MAX_LEVEL,
        PlayerStatsManager.INSTANCE.validateSkillPoint(data, capped.Id));
    assertEquals(10, PlayerStatsManager.INSTANCE.getAvailableSkillPoints(data));
    System.out.println("[SKILL_POINT_AUTH] phase=reject wrongClass=true level=true "
        + "prerequisite=true cap=true pointsUnchanged=10 status=PASS");
  }

  private static Skills.Entry firstWithoutPrerequisite(CharacterClass clazz) {
    for (int id = clazz.firstSpell; id < clazz.lastSpell; id++) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      if (skill != null && (skill.reqskill1 == null || skill.reqskill1.isEmpty())
          && (skill.reqskill2 == null || skill.reqskill2.isEmpty())
          && (skill.reqskill3 == null || skill.reqskill3.isEmpty())) return skill;
    }
    return null;
  }

  private static CharData character(CharacterClass clazz, int level, int points) {
    CharData data = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "SkillHero", (byte) clazz.id);
    setProgress(data, level, points);
    return data;
  }

  private static void setProgress(CharData data, int level, int points) {
    data.level = (byte) level;
    data.getStats().base().put(Stat.level, level);
    data.getStats().aggregate().put(Stat.level, level);
    data.getStats().base().put(Stat.newskills, points);
    data.getStats().aggregate().put(Stat.newskills, points);
  }
}
