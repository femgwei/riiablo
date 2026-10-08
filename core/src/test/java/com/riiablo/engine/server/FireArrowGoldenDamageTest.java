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

/** Twentieth DMG-04 audit: one skill-linked Fire Arrow missile. */
class FireArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIRE_MIN = {
      1, 3, 5, 7, 9, 11, 13, 15, 18, 21,
      24, 27, 30, 33, 36, 39, 45, 51, 57, 63
  };
  private static final int[] D2MOO_FIRE_MAX = {
      4, 6, 8, 10, 12, 14, 16, 18, 21, 24,
      27, 30, 33, 36, 39, 42, 49, 56, 63, 70
  };

  @Test
  void levelOneToTwentyMatchesD2mooFireCurveWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get("Fire Arrow");
    Missiles.Entry row = Riiablo.files.Missiles.get("firearrow");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(7, skill.Id);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("fire", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(4, skill.EMax);
    assertArrayEquals(new int[] {2, 3, 6, 12, 24}, skill.EMinLev);
    assertArrayEquals(new int[] {2, 3, 7, 14, 27}, skill.EMaxLev);
    assertEquals("(skill('Exploding Arrow'.blvl)) * par8", skill.EDmgSymPerCalc);
    assertEquals(1, row.pSrvDmgFunc,
        "D2MOO MISSMODE_SrvDmg01 owns Fire Arrow's physical-to-fire conversion");
    assertEquals("fire", row.EType);
    assertEquals("dl12", row.DmgCalc1);
    assertArrayEquals(new int[] {3, 2}, row.dParam);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_FIRE_MIN[level - 1];
      int expectedMax = D2MOO_FIRE_MAX[level - 1];
      assertEquals(expectedMin << 8,
          d2mooElementalDamageFixed(skill, level, true),
          "D2MOO fixed minimum level " + level);
      assertEquals(expectedMax << 8,
          d2mooElementalDamageFixed(skill, level, false),
          "D2MOO fixed maximum level " + level);

      Missile projectile = new Missile().set(row, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, level));
      assertEquals(0, statInt(projectile, Stat.mindamage),
          "zero weapon source leaves no physical minimum at level " + level);
      assertEquals(0, statInt(projectile, Stat.maxdamage),
          "zero weapon source leaves no physical maximum at level " + level);
      assertEquals(expectedMin, statInt(projectile, Stat.firemindam),
          "riiablo fire minimum level " + level);
      assertEquals(expectedMax, statInt(projectile, Stat.firemaxdam),
          "riiablo fire maximum level " + level);
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
