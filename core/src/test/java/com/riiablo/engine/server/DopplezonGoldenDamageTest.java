package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.NativeSkillBehavior;
import com.riiablo.engine.server.skill.NativeSkillBehaviorRegistry;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Forty-first DMG-04 audit: Dopplezon is a non-damaging summon skill. */
class DopplezonGoldenDamageTest extends RiiabloTest {
  @Test
  void levelOneToTwentyAreNondamagingNativeDecoySummons() {
    Skills.Entry skill = Riiablo.files.skills.get("Dopplezon");
    assertNotNull(skill);
    assertEquals(28, skill.Id);
    assertEquals("Dopplezon", skill.skill);
    assertEquals("ama", skill.charclass);
    assertEquals(0, skill.srvstfunc);
    assertEquals(15, skill.srvdofunc);
    assertEquals("dopplezon", skill.summon);
    assertEquals("dopplezon", skill.pettype);
    assertEquals("1", skill.petmax);
    assertEquals("NU", skill.summode);
    assertEquals("lvl*par4", skill.calc1);
    assertEquals("ln12", skill.calc2);
    assertEquals("par3", skill.calc3);
    assertEquals(250, skill.Param[0]);
    assertEquals(125, skill.Param[1]);
    assertEquals(50, skill.Param[2]);
    assertEquals(10, skill.Param[3]);
    assertEquals(4, skill.Param[6]);

    assertTrue(!skill.passive);
    assertTrue(!skill.aura);
    assertTrue(!skill.periodic);
    assertEquals("dopplezon", skill.aurastate);
    assertEquals("fireresist", skill.aurastat[0]);
    assertEquals("\"min(lvl*par7,85)\"", skill.aurastatcalc[0]);
    assertEquals("lightresist", skill.aurastat[1]);
    assertEquals("coldresist", skill.aurastat[2]);
    assertEquals("poisonresist", skill.aurastat[3]);
    assertEquals(0, skill.sumoverlay);
    assertEquals(0, skill.sumumod);

    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertTrue(blank(skill.EType));
    assertEquals(0, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);
    assertEquals(0, skill.ELen);
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveAmazon(skill);
    assertNotNull(behavior);
    assertEquals("summon.decoy", behavior.family);
    assertEquals(0, behavior.serverMissiles.length);

    for (int level = 1; level <= 20; level++) {
      assertEquals(250 + (level - 1) * 125,
          SkillFormula.evaluate(skill.calc2, skill, level),
          "native duration frames level " + level);
      assertEquals(50, SkillFormula.evaluate(skill.calc3, skill, level),
          "owner hitpoint percentage level " + level);
      assertEquals(level * 10, SkillFormula.evaluate(skill.calc1, skill, level),
          "native bonus HP formula level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
