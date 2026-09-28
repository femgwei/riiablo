package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.NativeSkills;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NativeSkillBehavior;
import com.riiablo.engine.server.skill.NativeSkillBehaviorRegistry;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.SkillExecutor;
import org.junit.jupiter.api.Test;

/**
 * Exact 1.10f Amazon row gate.  This is intentionally separate from the ECS
 * scenarios: a behavior-family test must fail before a callback is silently
 * routed through a generic handler.
 */
class NativeAmazonSkillMatrixTest extends RiiabloTest {
  private static final Row[] ROWS = {
      row(6, "Magic Arrow", 0, 0, "missile.arrow", true, false, 128, ""),
      row(7, "Fire Arrow", 4, 0, "missile.elemental-arrow", false, true, 128, "fire"),
      row(8, "Inner Sight", 0, 6, "state.point-area", false, false, 0, ""),
      row(9, "Critical Strike", 0, 0, "passive.stat-list", false, false, 0, ""),
      row(10, "Jab", 5, 7, "melee.multi-hit", false, false, 128, ""),
      row(11, "Cold Arrow", 4, 0, "missile.elemental-arrow-freeze", false, true, 128, "cold"),
      row(12, "Multiple Shot", 4, 8, "missile.multishot", false, true, 96, ""),
      row(13, "Dodge", 0, 0, "passive.stat-list", false, false, 0, ""),
      row(14, "Power Strike", 6, 2, "melee.elemental", false, false, 128, "ltng"),
      row(15, "Poison Javelin", 4, 0, "missile.poison-javelin", false, true, 128, "pois"),
      row(16, "Exploding Arrow", 4, 0, "missile.impact-area", false, true, 128, "fire"),
      row(17, "Slow Missiles", 0, 6, "state.point-area", false, false, 0, ""),
      row(18, "Avoid", 0, 0, "passive.stat-list", false, false, 0, ""),
      row(19, "Impale", 7, 2, "melee.durability", false, false, 128, ""),
      row(20, "Lightning Bolt", 4, 0, "missile.elemental-javelin", false, true, 96, "ltng"),
      row(21, "Ice Arrow", 4, 0, "missile.freeze", false, true, 128, "cold"),
      row(22, "Guided Arrow", 4, 10, "missile.guided", false, true, 128, ""),
      row(23, "Penetrate", 0, 0, "passive.stat-list", false, false, 0, ""),
      row(24, "Charged Strike", 6, 11, "melee.multibolt", false, false, 0, "ltng"),
      row(25, "Plague Javelin", 4, 0, "missile.poison-cloud", false, true, 128, "pois"),
      row(26, "Strafe", 8, 12, "missile.multishot", false, false, 96, ""),
      row(27, "Immolation Arrow", 4, 0, "missile.impact-area-periodic", false, true, 128, "fire"),
      row(28, "Dopplezon", 0, 15, "summon.decoy", false, false, 0, ""),
      row(29, "Evade", 0, 0, "passive.stat-list", false, false, 0, ""),
      row(30, "Fend", 9, 13, "melee.multi-target", false, false, 128, ""),
      row(31, "Freezing Arrow", 4, 0, "missile.impact-area-freeze", false, true, 128, "cold"),
      row(32, "Valkyrie", 0, 16, "summon.valkyrie", false, false, 0, ""),
      row(33, "Pierce", 0, 0, "passive.stat-list", false, false, 0, ""),
      row(34, "Lightning Strike", 10, 14, "melee.chain", false, false, 0, "ltng"),
      row(35, "Lightning Fury", 4, 0, "missile.split", false, true, 128, "ltng"),
  };

  @Test
  void allAmazonRowsMatchNativeProjectionAndBehaviorRegistry() {
    assertEquals(30, ROWS.length);
    assertEquals(30, NativeSkillBehaviorRegistry.amazonSize());
    for (Row expected : ROWS) {
      Skills.Entry skill = Riiablo.files.skills.get(expected.id);
      assertNotNull(skill, expected.name);
      assertEquals(expected.id, skill.Id, expected.name);
      assertEquals(expected.name, skill.skill, expected.name);
      assertEquals("ama", skill.charclass, expected.name);
      assertEquals(expected.srvst, skill.srvstfunc, expected.name + ":srvstfunc");
      assertEquals(expected.srvdo, skill.srvdofunc, expected.name + ":srvdofunc");
      assertEquals(expected.noammo, skill.noammo, expected.name + ":noammo");
      assertEquals(expected.decquant, skill.decquant, expected.name + ":decquant");
      assertEquals(expected.srcDam, skill.SrcDam, expected.name + ":SrcDam");
      assertEquals(expected.eType, skill.EType == null ? "" : skill.EType,
          expected.name + ":EType");

      NativeSkills.Entry nativeRow = Riiablo.files.NativeSkills.get(expected.id);
      assertNotNull(nativeRow, expected.name + ":NativeSkills");
      assertEquals(expected.id, nativeRow.txtId, expected.name + ":native id");
      assertEquals(expected.srvst, nativeInt(nativeRow, "srvstfunc"), expected.name + ":native srvst");
      assertEquals(expected.srvdo, nativeInt(nativeRow, "srvdofunc"), expected.name + ":native srvdo");
      assertEquals(skill.reqlevel, nativeInt(nativeRow, "reqlevel"), expected.name + ":reqlevel");
      assertEquals(skill.mana, nativeInt(nativeRow, "mana"), expected.name + ":mana");
      assertEquals(skill.lvlmana, nativeInt(nativeRow, "lvlmana"), expected.name + ":lvlmana");
      assertEquals(expected.noammo, nativeRow.bool("noammo"), expected.name + ":native noammo");
      assertEquals(expected.decquant, nativeRow.bool("decquant"), expected.name + ":native decquant");

      NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveAmazon(skill);
      assertNotNull(behavior, expected.name + ":behavior");
      assertEquals(expected.family, behavior.family, expected.name + ":family");
      assertTrue(behavior.matches(skill), expected.name + ":exact callback match");
      for (String missileName : behavior.serverMissiles) {
        assertNotNull(Riiablo.files.Missiles.get(missileName),
            expected.name + ":missing missile " + missileName);
        assertTrue(containsServerMissile(skill, missileName),
            expected.name + ":registry missile is not in Skills.txt: " + missileName);
      }

      SkillExecutor.SkillData data = NativeSkillResolver.toSkillData(skill);
      assertNotNull(data, expected.name + ":skill data");
      assertEquals(expected.family, data.nativeBehaviorFamily, expected.name + ":skill data family");
      assertEquals(expected.srvst, data.nativeSrvStartFunction, expected.name + ":skill data srvst");
      assertEquals(expected.srvdo, data.nativeSrvDoFunction, expected.name + ":skill data srvdo");
    }
  }

  @Test
  void powerAndChargedStrikeUseTheNativeMeleeDurabilityPath() {
    assertTrue(NativeSkillResolver.isAmazonJavelinSkill(
        Riiablo.files.skills.get("Power Strike")));
    assertTrue(NativeSkillResolver.isAmazonJavelinSkill(
        Riiablo.files.skills.get("Charged Strike")));
    assertTrue(com.riiablo.engine.server.skill.AmazonSkills.usesNativeMeleeDurability(
        Riiablo.files.skills.get("Power Strike")));
    assertTrue(com.riiablo.engine.server.skill.AmazonSkills.usesNativeMeleeDurability(
        Riiablo.files.skills.get("Charged Strike")));
    assertTrue(!com.riiablo.engine.server.skill.AmazonSkills.usesNativeMeleeDurability(
        Riiablo.files.skills.get("Jab")));
  }

  @Test
  void callbackMismatchFailsClosedInsteadOfUsingTheFamily() {
    Skills.Entry jab = Riiablo.files.skills.get("Jab");
    assertNotNull(jab);
    int original = jab.srvdofunc;
    try {
      jab.srvdofunc = 999;
      assertNull(NativeSkillBehaviorRegistry.resolveAmazon(jab));
      SkillExecutor.SkillData data = NativeSkillResolver.toSkillData(jab);
      assertNull(data.nativeBehaviorFamily);
      assertEquals(-1, data.nativeSrvDoFunction);
    } finally {
      jab.srvdofunc = original;
    }
  }

  private static Row row(int id, String name, int srvst, int srvdo, String family,
      boolean noammo, boolean decquant, int srcDam, String eType) {
    return new Row(id, name, srvst, srvdo, family, noammo, decquant, srcDam, eType);
  }

  private static int nativeInt(NativeSkills.Entry row, String field) {
    Integer value = row.integer(field);
    return value == null ? 0 : value;
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
    final boolean noammo;
    final boolean decquant;
    final int srcDam;
    final String eType;

    Row(int id, String name, int srvst, int srvdo, String family,
        boolean noammo, boolean decquant, int srcDam, String eType) {
      this.id = id;
      this.name = name;
      this.srvst = srvst;
      this.srvdo = srvdo;
      this.family = family;
      this.noammo = noammo;
      this.decquant = decquant;
      this.srcDam = srcDam;
      this.eType = eType;
    }
  }
}
