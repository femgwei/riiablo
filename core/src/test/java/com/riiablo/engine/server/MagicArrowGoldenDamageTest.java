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

/** Nineteenth DMG-04 audit: one skill-linked Magic Arrow missile. */
class MagicArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_TOTAL_DAMAGE = {
      1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
      11, 12, 13, 14, 15, 16, 17, 18, 19, 20
  };
  private static final int[] RIIABLO_PHYSICAL_CHANNEL = {
      1, 2, 3, 4, 5, 6, 7, 8, 9, 9,
      10, 11, 12, 13, 13, 14, 15, 15, 16, 16
  };
  private static final int[] RIIABLO_MAGIC_CHANNEL = {
      0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
      1, 1, 1, 1, 2, 2, 2, 3, 3, 4
  };

  @Test
  void levelOneToTwentyPreservesD2mooTotalAcrossMagicConversion() {
    Skills.Entry skill = Riiablo.files.skills.get("Magic Arrow");
    Missiles.Entry row = Riiablo.files.Missiles.get("magicarrow");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(6, skill.Id);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(1, skill.MinDam);
    assertEquals(1, skill.MaxDam);
    assertArrayEquals(new int[] {1, 1, 1, 1, 1}, skill.MinLevDam);
    assertArrayEquals(new int[] {1, 1, 1, 1, 1}, skill.MaxLevDam);
    assertEquals(1, row.pSrvDmgFunc,
        "D2MOO MISSMODE_SrvDmg01 owns Magic Arrow's physical-to-magic conversion");
    assertEquals("mag", row.EType);
    assertEquals("dl12", row.DmgCalc1);
    assertArrayEquals(new int[] {1, 1}, row.dParam);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int sourceFixed = d2mooPhysicalDamageFixed(skill, level);
      int conversion = row.dParam[0] + (level - 1) * row.dParam[1];
      int convertedFixed = sourceFixed * conversion / 100;
      int remainingFixed = sourceFixed - convertedFixed;
      assertEquals(D2MOO_TOTAL_DAMAGE[level - 1], sourceFixed >> 8,
          "D2MOO total level " + level);
      assertEquals(sourceFixed, remainingFixed + convertedFixed,
          "SrvDmg01 must only redistribute damage at level " + level);

      Missile projectile = new Missile().set(row, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, level));
      int physical = statInt(projectile, Stat.mindamage);
      int magic = statInt(projectile, Stat.magicmindam);
      assertEquals(RIIABLO_PHYSICAL_CHANNEL[level - 1], physical,
          "riiablo physical channel level " + level);
      assertEquals(RIIABLO_MAGIC_CHANNEL[level - 1], magic,
          "riiablo magic channel level " + level);
      assertEquals(D2MOO_TOTAL_DAMAGE[level - 1], physical + magic,
          "riiablo total level " + level);
      assertEquals(physical, statInt(projectile, Stat.maxdamage));
      assertEquals(magic, statInt(projectile, Stat.magicmaxdam));
    }
  }

  private static int d2mooPhysicalDamageFixed(Skills.Entry skill, int level) {
    return (skill.MinDam + d2mooDamageBonusByLevel(level, skill.MinLevDam))
        << skill.HitShift;
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
