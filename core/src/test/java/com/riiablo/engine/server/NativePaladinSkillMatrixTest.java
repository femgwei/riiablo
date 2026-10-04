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

/** Exact-ID/callback audit for the Paladin rows admitted by DM-NV-14. */
class NativePaladinSkillMatrixTest extends RiiabloTest {
  private static final Row[] ROWS = {
      row(112, "Blessed Hammer", 0, 73, "missile.blessed-hammer", "blessedhammer"),
      row(114, "Holy Freeze", 0, 81, "aura.holy-freeze"),
      row(116, "Conversion", 32, 79, "state.conversion"),
      row(117, "Holy Shield", 36, 18, "state.holy-shield"),
      row(121, "Fist of the Heavens", 0, 80,
          "missile.fist-of-heavens", "fistoftheheavensdelay"),
      row(98, "Might", 0, 65, "aura.selected-party-stat"),
      row(99, "Prayer", 0, 65, "aura.selected-party-periodic"),
      row(100, "Resist Fire", 0, 65, "aura.selected-party-stat"),
      row(103, "Thorns", 0, 65, "aura.selected-party-stat"),
      row(104, "Defiance", 0, 65, "aura.selected-party-stat"),
      row(105, "Resist Cold", 0, 65, "aura.selected-party-stat"),
      row(108, "Blessed Aim", 0, 65, "aura.selected-party-stat"),
      row(109, "Cleansing", 0, 65, "aura.selected-party-periodic"),
      row(110, "Resist Lightning", 0, 65, "aura.selected-party-stat"),
      row(115, "Vigor", 0, 65, "aura.selected-party-stat"),
      row(120, "Meditation", 0, 65, "aura.selected-party-periodic"),
      row(124, "Redemption", 0, 82, "aura.selected-corpse-periodic"),
      row(125, "Salvation", 0, 65, "aura.selected-party-stat"),
  };

  @Test
  void admittedPaladinRowsMatchExactCallbacksAndMissileChains() {
    assertEquals(ROWS.length, NativeSkillBehaviorRegistry.paladinSize());
    for (Row expected : ROWS) {
      Skills.Entry skill = Riiablo.files.skills.get(expected.id);
      assertNotNull(skill, expected.name);
      assertEquals(expected.id, skill.Id, expected.name);
      assertEquals(expected.name, skill.skill, expected.name);
      assertEquals("pal", skill.charclass, expected.name);
      assertEquals(expected.srvst, skill.srvstfunc, expected.name + ":srvstfunc");
      assertEquals(expected.srvdo, skill.srvdofunc, expected.name + ":srvdofunc");

      NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolvePaladin(skill);
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
  void changedPaladinCallbackFailsClosedInsteadOfUsingTheSkillName() {
    Skills.Entry skill = Riiablo.files.skills.get(117);
    assertNotNull(skill);
    int original = skill.srvdofunc;
    try {
      skill.srvdofunc = original + 1;
      assertNull(NativeSkillBehaviorRegistry.resolvePaladin(skill));
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
