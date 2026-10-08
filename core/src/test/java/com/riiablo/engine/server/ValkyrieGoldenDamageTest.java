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

/** DMG-04 audit probe for the non-damaging Valkyrie summon skill. */
class ValkyrieGoldenDamageTest extends RiiabloTest {
  @Test
  void levelOneToTwentyAreNondamagingNativeValkyrieSummons() {
    Skills.Entry s = Riiablo.files.skills.get("Valkyrie");
    assertNotNull(s);
    assertEquals(32, s.Id);
    assertEquals("Valkyrie", s.skill);
    assertEquals("ama", s.charclass);
    assertEquals(30, s.reqlevel);
    assertEquals(20, s.maxlvl);
    assertEquals(0, s.srvstfunc);
    assertEquals(16, s.srvdofunc);
    assertEquals("valkyrie", s.summon);
    assertEquals("valkyrie", s.pettype);
    assertEquals("1", s.petmax);
    assertEquals("NU", s.summode);
    assertEquals("par1 * (lvl - 1) + skill('Dopplezon'.blvl) * par8", s.calc1);
    assertEquals("ln56", s.calc2);
    assertTrue(s.calc3 == null || s.calc3.isEmpty());
    assertTrue(!s.passive);
    assertTrue(!s.aura);
    assertTrue(!s.periodic);
    assertEquals(0, s.SrcDam);
    assertEquals(8, s.HitShift);
    assertEquals(0, s.MinDam);
    assertEquals(0, s.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, s.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, s.MaxLevDam);
    assertTrue(s.EType == null || s.EType.isEmpty());
    assertEquals(0, s.EMin);
    assertEquals(0, s.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, s.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, s.EMaxLev);
    assertEquals(0, s.ELen);
    assertTrue(blank(s.srvmissile));
    assertTrue(blank(s.srvmissilea));
    assertTrue(blank(s.srvmissileb));
    assertTrue(blank(s.srvmissilec));
    assertTrue(blank(s.srvmissiled));

    NativeSkillBehavior behavior = NativeSkillBehaviorRegistry.resolveAmazon(s);
    assertNotNull(behavior);
    assertEquals("summon.valkyrie", behavior.family);
    assertEquals(0, behavior.serverMissiles.length);
    for (int level = 1; level <= 20; level++) {
      assertEquals(1, SkillFormula.evaluate(s.petmax, s, level));
      assertTrue(SkillFormula.evaluate(s.calc2, s, level) > 0,
          "native item-level formula level " + level);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
