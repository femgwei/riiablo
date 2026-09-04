package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import org.junit.jupiter.api.Test;

/** Native Skills.txt contract tests for Barbarian corpse tools. */
class BarbarianCorpseSkillTest extends RiiabloTest {
  @Test
  void nativeFunctionsAndChanceFormulasAreLoaded() {
    Skills.Entry potion = Riiablo.files.skills.get("Find Potion");
    Skills.Entry item = Riiablo.files.skills.get("Find Item");
    Skills.Entry ward = Riiablo.files.skills.get("Grim Ward");
    assertEquals(69, potion.srvdofunc);
    assertEquals(72, item.srvdofunc);
    assertEquals(75, ward.srvdofunc);
    assertTrue(BarbarianSkills.getFindPotionChance(potion, 1) > 0);
    assertTrue(BarbarianSkills.getFindItemChance(item, 1) > 0);
  }

  @Test
  void findItemUsesNativeFourQualityBuckets() {
    Skills.Entry item = Riiablo.files.skills.get("Find Item");
    assertEquals(1, BarbarianSkills.resolveFindItemBucket(item, 0));
    assertEquals(4, BarbarianSkills.resolveFindItemBucket(item, 99));
  }
}
