package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.item.Item;
import org.junit.jupiter.api.Test;

/** Native Amazon bow/javelin ammo policy checks. */
class NativeAmazonAmmoPolicyTest extends RiiabloTest {
  @Test
  void bowSkillsConsumeAQuiverButJavelinSkillsDoNotConsumeArrows() {
    Item bow = item("sbw");
    String[] bowSkills = {
        "Magic Arrow", "Fire Arrow", "Cold Arrow", "Multiple Shot", "Exploding Arrow",
        "Ice Arrow", "Guided Arrow", "Strafe", "Immolation Arrow", "Freezing Arrow"
    };
    for (String name : bowSkills) {
      Skills.Entry skill = Riiablo.files.skills.get(name);
      assertTrue(NativeSkillResolver.isAmazonBowSkill(skill), name);
      assertTrue(ServerSkillSystem.requiresRangedAmmo(skill, bow) == !skill.noammo, name);
    }

    String[] javelinSkills = {
        "Jab", "Power Strike", "Poison Javelin", "Impale", "Lightning Bolt",
        "Charged Strike", "Plague Javelin", "Fend", "Lightning Strike", "Lightning Fury"
    };
    for (String name : javelinSkills) {
      Skills.Entry skill = Riiablo.files.skills.get(name);
      assertTrue(NativeSkillResolver.isAmazonJavelinSkill(skill), name);
      assertFalse(ServerSkillSystem.requiresRangedAmmo(skill, bow), name);
    }
  }

  @Test
  void noammoOrNonBowRowsNeverConsumeAQuiver() {
    Item bow = item("sbw");
    Skills.Entry magicArrow = Riiablo.files.skills.get("Magic Arrow");
    Skills.Entry innerSight = Riiablo.files.skills.get("Inner Sight");
    Skills.Entry slowMissiles = Riiablo.files.skills.get("Slow Missiles");

    assertTrue(magicArrow.noammo);
    assertTrue(NativeSkillResolver.isAmazonBowSkill(magicArrow));
    assertFalse(ServerSkillSystem.requiresRangedAmmo(magicArrow, bow));
    assertFalse(ServerSkillSystem.requiresRangedAmmo(innerSight, bow));
    assertFalse(ServerSkillSystem.requiresRangedAmmo(slowMissiles, bow));
  }

  @Test
  void nonRangedWeaponsNeverSelectQuiverConsumption() {
    Item melee = item("hax");
    Skills.Entry fireArrow = Riiablo.files.skills.get("Fire Arrow");

    assertFalse(ServerSkillSystem.requiresRangedAmmo(fireArrow, melee));
  }

  private static Item item(String code) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.weapons.get(code));
    return item;
  }
}
