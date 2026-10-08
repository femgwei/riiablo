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

/** Thirty-seventh DMG-04 audit: one Charged Strike skill-owned lightning bolt. */
class ChargedStrikeGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_MIN_LIGHTNING = {
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1
  };
  private static final int[] D2MOO_MAX_LIGHTNING = {
      30, 42, 54, 66, 78, 90, 102, 114, 130, 146,
      162, 178, 194, 210, 226, 242, 262, 282, 302, 322
  };
  private static final int[] D2MOO_BOLT_COUNT = {
      3, 3, 3, 3, 4, 4, 4, 4, 4, 5,
      5, 5, 5, 5, 6, 6, 6, 6, 6, 7
  };

  @Test
  void levelOneToTwentyMatchD2mooLightningCurvePerReleasedBoltWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get("Charged Strike");
    Missiles.Entry row = Riiablo.files.Missiles.get("chargedstrikebolt");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(24, skill.Id);
    assertEquals(6, skill.srvstfunc);
    assertEquals(11, skill.srvdofunc);
    assertEquals("par1+lvl/par2", skill.calc1);
    assertArrayEquals(new int[] {3, 5, 0, 0, 0, 0, 0, 10}, skill.Param);
    assertEquals("chargedstrikebolt", skill.srvmissilea);
    assertEquals("chargedstrikebolt", skill.srvmissileb);
    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(30, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {12, 16, 20, 24, 28}, skill.EMaxLev);
    assertEquals(1, row.pSrvDoFunc);
    assertEquals(0, row.pSrvDmgFunc);
    assertEquals(4, row.ResultFlags);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_MIN_LIGHTNING[level - 1];
      int expectedMax = D2MOO_MAX_LIGHTNING[level - 1];
      assertEquals(D2MOO_BOLT_COUNT[level - 1],
          ServerSkillSystem.chargedStrikeBoltCount(skill, level),
          "native Calc1 bolt count level " + level);
      assertEquals(0, SkillFormula.evaluate(
          skill.EDmgSymPerCalc, skill, level, name -> 0),
          "zero hard-point synergy level " + level);

      Missile projectile = new Missile().set(row, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, level));
      assertEquals(0, statInt(projectile, Stat.mindamage),
          "charged-strike bolt has no physical source level " + level);
      assertEquals(0, statInt(projectile, Stat.maxdamage),
          "charged-strike bolt has no physical source maximum level " + level);
      assertEquals(expectedMin, statInt(projectile, Stat.lightmindam),
          "riiablo charged-strike lightning minimum level " + level);
      assertEquals(expectedMax, statInt(projectile, Stat.lightmaxdam),
          "riiablo charged-strike lightning maximum level " + level);
    }
  }

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
