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

/** Exact-ID/callback audit for the Barbarian rows admitted by DM-NV-14. */
class NativeBarbarianSkillMatrixTest extends RiiabloTest {
  private static final Row[] ROWS = {
      row(130, "Howl", 0, 22, "warcry.howl"),
      row(131, "Find Potion", 33, 69, "corpse.find-potion"),
      row(137, "Taunt", 0, 71, "warcry.taunt"),
      row(138, "Shout", 0, 68, "warcry.shout"),
      row(142, "Find Item", 34, 72, "corpse.find-item"),
      row(146, "Battle Cry", 0, 68, "warcry.battle-cry"),
      row(149, "Battle Orders", 0, 68, "warcry.battle-orders"),
      row(150, "Grim Ward", 33, 75, "corpse.grim-ward"),
      row(151, "Whirlwind", 38, 76, "melee.whirlwind"),
      row(152, "Berserk", 39, 2, "melee.berserk"),
      row(154, "War Cry", 0, 68, "warcry.war-cry"),
      row(155, "Battle Command", 0, 68, "warcry.battle-command"),
  };

  @Test
  void admittedBarbarianRowsMatchExactCallbacks() {
    assertEquals(ROWS.length, NativeSkillBehaviorRegistry.barbarianSize());
    for (Row expected : ROWS) {
      Skills.Entry skill = Riiablo.files.skills.get(expected.id);
      assertNotNull(skill, expected.name);
      assertEquals(expected.id, skill.Id, expected.name);
      assertEquals(expected.name, skill.skill, expected.name);
      assertEquals("bar", skill.charclass, expected.name);
      assertEquals(expected.srvst, skill.srvstfunc, expected.name + ":srvstfunc");
      assertEquals(expected.srvdo, skill.srvdofunc, expected.name + ":srvdofunc");

      NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveBarbarian(skill);
      assertNotNull(behavior, expected.name + ":behavior");
      assertEquals(expected.family, behavior.family, expected.name + ":family");
      assertTrue(behavior.matches(skill), expected.name + ":exact callback match");
      assertEquals(expected.family,
          NativeSkillResolver.toSkillData(skill).nativeBehaviorFamily,
          expected.name + ":resolver family");
    }
  }

  @Test
  void changedBarbarianCallbackFailsClosedInsteadOfUsingTheSkillName() {
    Skills.Entry skill = Riiablo.files.skills.get(150);
    assertNotNull(skill);
    int original = skill.srvdofunc;
    try {
      skill.srvdofunc = original + 1;
      assertNull(NativeSkillBehaviorRegistry.resolveBarbarian(skill));
      assertNull(NativeSkillResolver.toSkillData(skill).nativeBehaviorFamily);
    } finally {
      skill.srvdofunc = original;
    }
  }

  private static Row row(int id, String name, int srvst, int srvdo, String family) {
    return new Row(id, name, srvst, srvdo, family);
  }

  private static final class Row {
    final int id;
    final String name;
    final int srvst;
    final int srvdo;
    final String family;

    Row(int id, String name, int srvst, int srvdo, String family) {
      this.id = id;
      this.name = name;
      this.srvst = srvst;
      this.srvdo = srvdo;
      this.family = family;
    }
  }
}
