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
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Thirty-third DMG-04 audit: one skill-linked Lightning Bolt javelin. */
class LightningBoltGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_LIGHTNING_MIN = {
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1
  };
  private static final int[] D2MOO_LIGHTNING_MAX = {
      40, 52, 64, 76, 88, 100, 112, 124, 142, 160,
      178, 196, 214, 232, 250, 268, 296, 324, 352, 380
  };

  @Test
  void levelOneToTwentyMatchesD2mooLightningCurveWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get("Lightning Bolt");
    Missiles.Entry row = Riiablo.files.Missiles.get("lightningjavelin");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(20, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("lightningjavelin", skill.srvmissile);
    assertEquals(96, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(0, skill.ToHit);
    assertEquals(0, skill.LevToHit);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(40, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {12, 18, 28, 48, 88}, skill.EMaxLev);
    assertEquals(
        "(skill('Lightning Strike'.blvl)+skill('Power Strike'.blvl)"
            + "+skill('Charged Strike'.blvl)+skill('Lightning Fury'.blvl)) * par8",
        skill.EDmgSymPerCalc);
    assertEquals(12, row.pSrvDmgFunc,
        "D2MOO MISSMODE_SrvDmg12 owns the physical-to-lightning conversion");
    assertEquals("ltng", row.EType);
    assertEquals("dl12", row.DmgCalc1);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_LIGHTNING_MIN[level - 1];
      int expectedMax = D2MOO_LIGHTNING_MAX[level - 1];
      assertEquals(0, SkillFormula.evaluate(
          skill.EDmgSymPerCalc, skill, level, name -> 0),
          "zero hard-point synergy level " + level);
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
      assertEquals(expectedMin, statInt(projectile, Stat.lightmindam),
          "riiablo lightning minimum level " + level);
      assertEquals(expectedMax, statInt(projectile, Stat.lightmaxdam),
          "riiablo lightning maximum level " + level);
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
