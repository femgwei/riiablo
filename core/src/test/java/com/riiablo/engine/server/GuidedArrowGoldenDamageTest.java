package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Thirty-fifth DMG-04 audit: Guided Arrow owns one enhanced weapon record. */
class GuidedArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_PHYSICAL_DAMAGE_PERCENT = {
      0, 5, 10, 15, 20, 25, 30, 35, 40, 45,
      50, 55, 60, 65, 70, 75, 80, 85, 90, 95
  };

  @Test
  void levelOneToTwentyUseNativeWeaponPercentWithoutOwningFixedDamage() {
    Skills.Entry skill = Riiablo.files.skills.get("Guided Arrow");
    Missiles.Entry row = Riiablo.files.Missiles.get("guidedarrow");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(22, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(10, skill.srvdofunc);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(0, skill.ToHit);
    assertEquals(0, skill.LevToHit);
    assertEquals("ln34", skill.calc1);
    assertEquals(0, skill.Param[2]);
    assertEquals(5, skill.Param[3]);
    assertTrue(blank(skill.srvmissile));
    assertEquals("guidedarrow", skill.srvmissilea);
    assertEquals("guidedarrow", skill.srvmissileb);
    assertEquals("guidedarrow", skill.srvmissilec);
    assertTrue(blank(skill.srvmissiled));

    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertTrue(blank(skill.EType));
    assertEquals(0, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);
    assertEquals(7, row.pSrvDoFunc);
    assertEquals(10, row.pSrvHitFunc);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expected = D2MOO_PHYSICAL_DAMAGE_PERCENT[level - 1];
      assertEquals(expected, SkillFormula.evaluate(skill.calc1, skill, level),
          "native Calc1 level " + level);
      assertEquals(expected, AmazonSkills.getPhysicalDamagePercent(skill, level),
          "riiablo data formula level " + level);
      assertEquals(expected, AmazonSkills.calculateGuidedArrowDamageBonus(level),
          "legacy helper must preserve the native curve at level " + level);

      Missile projectile = new Missile().set(row, new Vector2(), 40).setOwner(1);
      assertFalse(MissileDamageResolver.initializeSkill(projectile, skill, owner, level),
          "zero weapon source leaves no fixed Guided Arrow packet at level " + level);
      assertEquals(0, statInt(projectile, Stat.mindamage));
      assertEquals(0, statInt(projectile, Stat.maxdamage));
    }
  }

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }
}
