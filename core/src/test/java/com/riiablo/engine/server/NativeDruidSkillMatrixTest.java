package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NativeSkillBehavior;
import com.riiablo.engine.server.skill.NativeSkillBehaviorRegistry;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import org.junit.jupiter.api.Test;

/** Exact-ID/callback audit for the Druid rows admitted by DM-NV-14. */
class NativeDruidSkillMatrixTest extends RiiabloTest {
  private static final Row[] ROWS = {
      row(223, "Wearwolf", 0, 116, "state.werewolf"),
      row(225, "Firestorm", 0, 117, "missile.firestorm"),
      row(228, "Wearbear", 0, 116, "state.werebear"),
      row(229, "Molten Boulder", 0, 0, "missile.molten-boulder"),
      row(232, "Feral Rage", 56, 120, "melee.feral-rage"),
      row(233, "Maul", 56, 120, "melee.maul"),
      row(234, "Eruption", 0, 28, "missile.fissure"),
      row(238, "Rabies", 57, 121, "melee.rabies", "rabiesplague"),
      row(239, "Fire Claws", 58, 2, "melee.fire-claws"),
      row(242, "Hunger", 0, 122, "melee.hunger"),
      row(243, "Shock Wave", 0, 8, "missile.shock-wave", "shockwave"),
      row(244, "Volcano", 0, 123, "missile.volcano"),
      row(248, "Fury", 37, 13, "melee.fury"),
      row(249, "Armageddon", 0, 124, "state.armageddon"),
      row(250, "Hurricane", 0, 124, "state.hurricane"),
  };

  @Test
  void admittedDruidRowsMatchExactCallbacksAndMissileChains() {
    assertEquals(ROWS.length, NativeSkillBehaviorRegistry.druidSize());
    for (Row expected : ROWS) {
      Skills.Entry skill = Riiablo.files.skills.get(expected.id);
      assertNotNull(skill, expected.name);
      assertEquals(expected.id, skill.Id, expected.name);
      assertEquals(expected.name, skill.skill, expected.name);
      assertEquals("dru", skill.charclass, expected.name);
      assertEquals(expected.srvst, skill.srvstfunc, expected.name + ":srvstfunc");
      assertEquals(expected.srvdo, skill.srvdofunc, expected.name + ":srvdofunc");

      NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveDruid(skill);
      assertNotNull(behavior, expected.name + ":behavior");
      assertEquals(expected.family, behavior.family, expected.name + ":family");
      assertTrue(behavior.matches(skill), expected.name + ":exact callback match");
      for (String missileName : behavior.serverMissiles) {
        assertNotNull(Riiablo.files.Missiles.get(missileName),
            expected.name + ":missing missile " + missileName);
        assertTrue(containsServerMissile(skill, missileName),
            expected.name + ":registry missile is not in Skills.txt: " + missileName);
      }

      assertEquals(expected.family,
          NativeSkillResolver.toSkillData(skill).nativeBehaviorFamily,
          expected.name + ":resolver family");
    }
  }

  @Test
  void changedDruidCallbackFailsClosedInsteadOfUsingTheSkillName() {
    Skills.Entry skill = Riiablo.files.skills.get(248);
    assertNotNull(skill);
    int original = skill.srvdofunc;
    try {
      skill.srvdofunc = original + 1;
      assertNull(NativeSkillBehaviorRegistry.resolveDruid(skill));
      assertNull(NativeSkillResolver.toSkillData(skill).nativeBehaviorFamily);
    } finally {
      skill.srvdofunc = original;
    }
  }

  private static Row row(int id, String name, int srvst, int srvdo, String family,
      String... missiles) {
    return new Row(id, name, srvst, srvdo, family, missiles);
  }

  private static boolean containsServerMissile(Skills.Entry skill, String expected) {
    return expected.equals(skill.srvmissile) || expected.equals(skill.srvmissilea)
        || expected.equals(skill.srvmissileb) || expected.equals(skill.srvmissilec)
        || expected.equals(skill.srvmissiled);
  }

  private static final class Row {
    final int id;
    final String name;
    final int srvst;
    final int srvdo;
    final String family;
    final String[] missiles;

    Row(int id, String name, int srvst, int srvdo, String family, String[] missiles) {
      this.id = id;
      this.name = name;
      this.srvst = srvst;
      this.srvdo = srvdo;
      this.family = family;
      this.missiles = missiles;
    }
  }
}
