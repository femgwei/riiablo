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
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** DMG-04 audit: one Lightning Fury skill-lightning packet on one target. */
class LightningFuryGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_LIGHTNING_MIN = {
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1
  };
  private static final int[] D2MOO_LIGHTNING_MAX = {
      40, 60, 80, 100, 120, 140, 160, 180, 210, 240,
      270, 300, 330, 360, 390, 420, 460, 500, 540, 580
  };

  @Test
  void levelOneToTwentyMatchD2mooLightningCurvePerRootOrSplitTarget() {
    Skills.Entry skill = Riiablo.files.skills.get("Lightning Fury");
    Missiles.Entry root = Riiablo.files.Missiles.get("lightningfury");
    Missiles.Entry child = Riiablo.files.Missiles.get("furylightning");
    assertNotNull(skill);
    assertNotNull(root);
    assertNotNull(child);
    assertEquals(35, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("lightningfury", skill.srvmissile);
    assertEquals(20, root.pSrvHitFunc);
    assertEquals("furylightning", root.HitSubMissile[0]);
    assertEquals("Lightning Fury", root.Skill);
    assertEquals("Lightning Fury", child.Skill);
    assertEquals(3, child.CollideType);
    assertFalse(child.Collision);
    assertTrue(child.LastCollide);

    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(40, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {20, 30, 40, 50, 50}, skill.EMaxLev);
    assertEquals(
        "(skill('Charged Strike'.blvl)+skill('Lightning Bolt'.blvl)"
            + "+skill('Power Strike'.blvl)+skill('Lightning Strike'.blvl)) * par8",
        skill.EDmgSymPerCalc);
    assertEquals("ln12", skill.calc1);
    assertEquals("par3", skill.aurarangecalc);
    assertEquals(0xA583, skill.aurafilter,
        "the native row is used; D2MOO only falls back to 0xA783 when this field is zero");
    assertArrayEquals(new int[] {2, 1, 15, 0, 0, 0, 0, 1}, skill.Param);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_LIGHTNING_MIN[level - 1];
      int expectedMax = D2MOO_LIGHTNING_MAX[level - 1];
      assertEquals(0, SkillFormula.evaluate(
          skill.EDmgSymPerCalc, skill, level, name -> 0),
          "zero hard-point synergy level " + level);
      assertEquals(15, MissileCollisionSystem.lightningFuryRange(root, skill, level),
          "native aura range level " + level);
      assertEquals(level + 1,
          MissileCollisionSystem.lightningFuryBoltCount(root, skill, level),
          "native Calc1 split budget level " + level);

      Missile rootProjectile = new Missile().set(root, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkill(rootProjectile, skill, owner, level));
      assertEquals(0, statInt(rootProjectile, Stat.mindamage),
          "zero weapon source leaves no physical minimum level " + level);
      assertEquals(0, statInt(rootProjectile, Stat.maxdamage),
          "zero weapon source leaves no physical maximum level " + level);
      assertEquals(expectedMin, statInt(rootProjectile, Stat.lightmindam),
          "root lightning minimum level " + level);
      assertEquals(expectedMax, statInt(rootProjectile, Stat.lightmaxdam),
          "root lightning maximum level " + level);

      Missile childProjectile = new Missile().set(child, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkillArea(
          childProjectile, skill, owner, level));
      assertEquals(0, statInt(childProjectile, Stat.mindamage));
      assertEquals(0, statInt(childProjectile, Stat.maxdamage));
      assertEquals(expectedMin, statInt(childProjectile, Stat.lightmindam),
          "split lightning minimum level " + level);
      assertEquals(expectedMax, statInt(childProjectile, Stat.lightmaxdam),
          "split lightning maximum level " + level);
    }
  }

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
