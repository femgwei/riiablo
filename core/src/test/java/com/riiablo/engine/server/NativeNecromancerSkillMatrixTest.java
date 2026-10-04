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

/** Exact-ID/callback audit for the Necromancer rows admitted by DM-NV-14. */
class NativeNecromancerSkillMatrixTest extends RiiabloTest {
  private static final Row[] ROWS = {
      row(66, "Amplify Damage", 0, 30, "curse.area"),
      row(70, "Raise Skeleton", 15, 31, "summon.skeleton"),
      row(73, "Poison Dagger", 16, 32, "melee.poison-dagger"),
      row(74, "Corpse Explosion", 17, 55, "corpse.explosion"),
      row(80, "Raise Skeletal Mage", 15, 31, "summon.skeletal-mage"),
      row(83, "Poison Explosion", 17, 63, "corpse.poison-explosion",
          "poisonexplosioncloud"),
      row(92, "Poison Nova", 0, 22, "missile.poison-nova", "poisonnova"),
      row(72, "Weaken", 0, 30, "state.point-area-curse"),
      row(75, "Clay Golem", 0, 56, "summon.golem"),
      row(85, "BloodGolem", 0, 56, "summon.golem"),
      row(90, "IronGolem", 20, 57, "summon.golem"),
      row(94, "FireGolem", 0, 56, "summon.golem"),
      row(95, "Revive", 21, 58, "summon.targeted-corpse"),
  };

  @Test
  void admittedNecromancerRowsMatchExactCallbacksAndMissileChains() {
    assertEquals(ROWS.length, NativeSkillBehaviorRegistry.necromancerSize());
    for (Row expected : ROWS) {
      Skills.Entry skill = Riiablo.files.skills.get(expected.id);
      assertNotNull(skill, expected.name);
      assertEquals(expected.id, skill.Id, expected.name);
      assertEquals(expected.name, skill.skill, expected.name);
      assertEquals("nec", skill.charclass, expected.name);
      assertEquals(expected.srvst, skill.srvstfunc, expected.name + ":srvstfunc");
      assertEquals(expected.srvdo, skill.srvdofunc, expected.name + ":srvdofunc");

      NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveNecromancer(skill);
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
  void changedNecromancerCallbackFailsClosedInsteadOfUsingTheSkillName() {
    Skills.Entry skill = Riiablo.files.skills.get(74);
    assertNotNull(skill);
    int original = skill.srvdofunc;
    try {
      skill.srvdofunc = original + 1;
      assertNull(NativeSkillBehaviorRegistry.resolveNecromancer(skill));
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
