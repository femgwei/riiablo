package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.Test;

/** Twenty-fourth DMG-04 audit: one skill-linked Cold Arrow missile. */
class ColdArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_COLD_MIN = {
      3, 5, 7, 9, 11, 13, 15, 17, 19, 22,
      24, 27, 29, 32, 34, 37, 41, 45, 49, 53
  };
  private static final int[] D2MOO_COLD_MAX = {
      4, 6, 8, 10, 12, 14, 16, 18, 20, 23,
      25, 28, 30, 33, 35, 38, 42, 47, 51, 56
  };
  private static final int[] D2MOO_COLD_LENGTH = {
      100, 130, 160, 190, 220, 250, 280, 310, 340, 370,
      400, 430, 460, 490, 520, 550, 580, 610, 640, 670
  };

  @Test
  void levelOneToTwentyMatchesD2mooColdCurveWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get("Cold Arrow");
    Missiles.Entry row = Riiablo.files.Missiles.get("coldarrow");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(11, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals(128, skill.SrcDam);
    assertEquals(7, skill.HitShift);
    assertEquals(10, skill.ToHit);
    assertEquals(9, skill.LevToHit);
    assertEquals("cold", skill.EType);
    assertEquals(6, skill.EMin);
    assertEquals(8, skill.EMax);
    assertArrayEquals(new int[] {4, 5, 8, 16, 42}, skill.EMinLev);
    assertArrayEquals(new int[] {4, 5, 9, 17, 44}, skill.EMaxLev);
    assertEquals("(skill('Ice Arrow'.blvl)) * par8", skill.EDmgSymPerCalc);
    assertEquals(100, skill.ELen);
    assertArrayEquals(new int[] {30, 30, 30}, skill.ELevLen);

    assertEquals(1, row.pSrvDmgFunc,
        "D2MOO MISSMODE_SrvDmg01 owns Cold Arrow's physical-to-cold conversion");
    assertEquals("cold", row.EType);
    assertEquals("dl12", row.DmgCalc1);
    assertArrayEquals(new int[] {3, 2}, row.dParam);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_COLD_MIN[level - 1];
      int expectedMax = D2MOO_COLD_MAX[level - 1];
      assertEquals(expectedMin,
          d2mooElementalDamageFixed(skill, level, true) >> 8,
          "D2MOO cold minimum level " + level);
      assertEquals(expectedMax,
          d2mooElementalDamageFixed(skill, level, false) >> 8,
          "D2MOO cold maximum level " + level);

      Missile projectile = new Missile().set(row, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, level));
      assertEquals(0, statInt(projectile, Stat.mindamage),
          "zero weapon source leaves no physical minimum at level " + level);
      assertEquals(0, statInt(projectile, Stat.maxdamage),
          "zero weapon source leaves no physical maximum at level " + level);
      assertEquals(expectedMin, statInt(projectile, Stat.coldmindam),
          "riiablo cold minimum level " + level);
      assertEquals(expectedMax, statInt(projectile, Stat.coldmaxdam),
          "riiablo cold maximum level " + level);
      assertEquals(D2MOO_COLD_LENGTH[level - 1], statInt(projectile, Stat.coldlength),
          "native chill length level " + level);
      assertTrue(!projectile.freezesTarget,
          "Cold Arrow chills and must not use the freeze path at level " + level);
    }
  }

  private static int d2mooElementalDamageFixed(
      Skills.Entry skill, int level, boolean minimum) {
    int base = minimum ? skill.EMin : skill.EMax;
    int[] perLevel = minimum ? skill.EMinLev : skill.EMaxLev;
    return (base + d2mooDamageBonusByLevel(level, perLevel)) << skill.HitShift;
  }

  private static int d2mooDamageBonusByLevel(int level, int[] values) {
    if (level <= 1) return 0;
    if (level > 28) {
      return 7 * values[0] + values[4] * (level - 28)
          + 6 * (values[2] + values[3]) + 8 * values[1];
    }
    if (level > 22) {
      return 7 * values[0] + values[3] * (level - 22)
          + 6 * values[2] + 8 * values[1];
    }
    if (level > 16) {
      return 7 * values[0] + values[2] * (level - 16) + 8 * values[1];
    }
    if (level > 8) return 7 * values[0] + values[1] * (level - 8);
    return values[0] * (level - 1);
  }

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
