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

/** Exact-ID/callback audit for the dark-magic Assassin trap-family rows. */
class NativeAssassinSkillMatrixTest extends RiiabloTest {
  private static final Row[] ROWS = {
      row(251, "Fire Trauma", 0, 0, "missile.fire-trauma", "bomb in air"),
      row(256, "Shock Field", 0, 43, "missile.shock-field", "shock field in air"),
      row(257, "Blade Sentinel", 0, 44, "trap.blade-sentinel", "blade creeper"),
      row(261, "Charged Bolt Sentry", 0, 45, "trap.charged-bolt-sentry"),
      row(262, "Wake of Fire Sentry", 0, 45, "trap.wake-of-fire-sentry"),
      row(266, "Blade Fury", 26, 48, "missile.blade-fury", "bladefragment1"),
      row(271, "Lightning Sentry", 0, 45, "trap.lightning-sentry"),
      row(272, "Inferno Sentry", 0, 45, "trap.inferno-sentry"),
      row(276, "Death Sentry", 0, 45, "trap.death-sentry"),
      row(277, "Blade Shield", 28, 54, "state.blade-shield", "blade shield attachment"),
  };

  @Test
  void admittedAssassinRowsMatchExactCallbacksAndMissileChains() {
    assertEquals(ROWS.length, NativeSkillBehaviorRegistry.assassinSize());
    for (Row expected : ROWS) {
      Skills.Entry skill = Riiablo.files.skills.get(expected.id);
      assertNotNull(skill, expected.name);
      assertEquals(expected.id, skill.Id, expected.name);
      assertEquals(expected.name, skill.skill, expected.name);
      assertEquals("ass", skill.charclass, expected.name);
      assertEquals(expected.srvst, skill.srvstfunc, expected.name + ":srvstfunc");
      assertEquals(expected.srvdo, skill.srvdofunc, expected.name + ":srvdofunc");

      NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveAssassin(skill);
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
  void trapRowsRetainNativePetTypeAndBladeLifecycleFields() {
    for (int id : new int[] {257, 261, 262, 271, 272, 276}) {
      Skills.Entry skill = Riiablo.files.skills.get(id);
      assertNotNull(skill);
      assertEquals("assassintrap", skill.pettype, "pettype id=" + id);
      assertEquals("5", skill.petmax, "petmax id=" + id);
    }
    assertEquals("bladefragment1", Riiablo.files.skills.get(266).srvmissilea);
    assertEquals("bladefragment2", Riiablo.files.skills.get(266).cltmissilea);
    assertEquals("blade shield attachment", Riiablo.files.skills.get(277).srvmissilea);
    assertEquals("ln12", Riiablo.files.skills.get(277).auralencalc);
  }

  @Test
  void changedAssassinCallbackFailsClosedInsteadOfUsingTheSkillName() {
    Skills.Entry skill = Riiablo.files.skills.get(276);
    assertNotNull(skill);
    int original = skill.srvdofunc;
    try {
      skill.srvdofunc = original + 1;
      assertNull(NativeSkillBehaviorRegistry.resolveAssassin(skill));
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
