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
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

/** Thirty-fourth DMG-04 audit: one skill-linked Ice Arrow missile. */
class IceArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_COLD_MIN = {
      6, 12, 18, 24, 30, 36, 42, 48, 60, 72,
      84, 96, 108, 120, 132, 144, 162, 180, 198, 216
  };
  private static final int[] D2MOO_COLD_MAX = {
      10, 16, 22, 28, 34, 40, 46, 52, 65, 78,
      91, 104, 117, 130, 143, 156, 175, 194, 213, 232
  };
  private static final int[] D2MOO_FREEZE_LENGTH = {
      50, 55, 60, 65, 70, 75, 80, 85, 90, 95,
      100, 105, 110, 115, 120, 125, 130, 135, 140, 145
  };

  @Test
  void levelOneToTwentyMatchesD2mooColdCurveWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get("Ice Arrow");
    Missiles.Entry row = Riiablo.files.Missiles.get("icearrow");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(21, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("icearrow", skill.srvmissile);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(20, skill.ToHit);
    assertEquals(9, skill.LevToHit);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals("cold", skill.EType);
    assertEquals(6, skill.EMin);
    assertEquals(10, skill.EMax);
    assertArrayEquals(new int[] {6, 12, 18, 26, 36}, skill.EMinLev);
    assertArrayEquals(new int[] {6, 13, 19, 27, 38}, skill.EMaxLev);
    assertEquals("(skill('Cold Arrow'.blvl))*par8", skill.EDmgSymPerCalc);
    assertEquals(50, skill.ELen);
    assertArrayEquals(new int[] {5, 5, 5}, skill.ELevLen);

    assertEquals(2, row.pSrvDmgFunc,
        "D2MOO MISSMODE_SrvDmg02 owns Ice Arrow's cold-to-freeze length conversion");
    assertTrue(row.EType == null || row.EType.isEmpty(),
        "Ice Arrow's element comes from Skills.txt rather than Missiles.txt");
    assertTrue(row.dParam != null && row.dParam.length > 0);
    assertEquals(100, row.dParam[0],
        "SrvDmg02 must preserve 100% of the skill cold length as freeze length");

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_COLD_MIN[level - 1];
      int expectedMax = D2MOO_COLD_MAX[level - 1];
      assertEquals(0, SkillFormula.evaluate(
          skill.EDmgSymPerCalc, skill, level, name -> 0),
          "zero Cold Arrow hard-point synergy level " + level);
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
      assertEquals(expectedMin, statInt(projectile, Stat.coldmindam),
          "riiablo cold minimum level " + level);
      assertEquals(expectedMax, statInt(projectile, Stat.coldmaxdam),
          "riiablo cold maximum level " + level);
      assertEquals(D2MOO_FREEZE_LENGTH[level - 1], statInt(projectile, Stat.coldlength),
          "pre-conversion cold length level " + level);
      assertTrue(projectile.freezesTarget,
          "Ice Arrow must convert its cold length to freeze at level " + level);
      assertEquals(StateId.FREEZE, projectile.onHitStateId,
          "freeze state metadata level " + level);
      assertEquals(D2MOO_FREEZE_LENGTH[level - 1], projectile.onHitStateDuration,
          "dParam1=100 preserves the freeze length at level " + level);
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
