package com.riiablo.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

/** Regression coverage for native SkillDesc damage rows used by Amazon skills. */
class SkillDetailsDamageTest extends RiiabloTest {
  @Test
  void amazonElementalSkillsExposeCurrentDamageRanges() {
    assertDamage("Fire Arrow", 10, "Fire Damage: 1-4");
    assertDamage("Power Strike", 10, "Lightning Damage: 1-16");
    assertDamage("Charged Strike", 10, "Lightning Damage: 1-30");
    assertDamage("Lightning Fury", 10, "Lightning Damage: 1-40");
    assertDamage("Freezing Arrow", 10, "Cold Damage: 40-50");
  }

  @Test
  void amazonPoisonSkillsExposeTotalDamageAndDuration() {
    assertDamage("Poison Javelin", 14, "Poison Damage: 25-37 over 8 seconds");
    assertDamage("Plague Javelin", 14, "Poison Damage: 23-37 over 3 seconds");
  }

  @Test
  void weaponDamageRowsUseBothNativeFormulaOperands() {
    Skills.Entry strafe = Riiablo.files.skills.get("Strafe");
    SkillDesc.Entry desc = Riiablo.files.skilldesc.get(strafe.skilldesc);
    int row = rowOf(desc.dsc2line, 73);
    String line = SkillDetails.formatLine(73, "", "",
        desc.dsc2calca[row], desc.dsc2calcb[row], strafe, 1, desc.str_mana);
    assertEquals("Weapon Damage: 75%", line);
  }

  @Test
  void sourceDamageSkillsExposeWeaponContribution() {
    Skills.Entry skill = Riiablo.files.skills.get("Fire Arrow");
    SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
    String line = SkillDetails.formatLine(11, "", "", "", "",
        skill, 1, desc.str_mana);
    assertNotNull(line);
    assertTrue(line.startsWith("Weapon Damage: "));
  }

  private static void assertDamage(String skillName, int type, String expected) {
    Skills.Entry skill = Riiablo.files.skills.get(skillName);
    SkillDesc.Entry desc = Riiablo.files.skilldesc.get(skill.skilldesc);
    int row = rowOf(desc.descline, type);
    String line = SkillDetails.formatLine(type, desc.desctexta[row], desc.desctextb[row],
        desc.desccalca[row], desc.desccalcb[row], skill, 1, desc.str_mana);
    assertEquals(expected, line);
  }

  private static int rowOf(int[] lines, int type) {
    for (int i = 0; i < lines.length; i++) {
      if (lines[i] == type) return i;
    }
    throw new AssertionError("Missing SkillDesc line type " + type);
  }
}
