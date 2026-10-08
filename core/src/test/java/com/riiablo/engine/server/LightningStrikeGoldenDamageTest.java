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

/** DMG-04 audit: one skill-owned Lightning Strike packet on one target. */
class LightningStrikeGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_LIGHTNING_MIN = {
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
      1, 1, 1, 1, 1, 1, 1, 1, 1, 1
  };
  private static final int[] D2MOO_LIGHTNING_MAX = {
      25, 35, 45, 55, 65, 75, 85, 95, 110, 125,
      140, 155, 170, 185, 200, 215, 235, 255, 275, 295
  };

  @Test
  void levelOneToTwentyMatchD2mooLightningCurvePerMeleeOrChainTarget() {
    Skills.Entry skill = Riiablo.files.skills.get("Lightning Strike");
    Missiles.Entry row = Riiablo.files.Missiles.get("lightningstrike");
    assertNotNull(skill);
    assertNotNull(row);
    assertEquals(34, skill.Id);
    assertEquals(10, skill.srvstfunc);
    assertEquals(14, skill.srvdofunc);
    assertEquals("lightningstrike", skill.srvmissilea);
    assertEquals("lightningstrike", skill.srvmissileb);
    assertEquals("lightningstrike", skill.srvmissilec);
    assertEquals(12, row.pSrvHitFunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertEquals("ltng", skill.EType);
    assertEquals(1, skill.EMin);
    assertEquals(25, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {10, 15, 20, 25, 30}, skill.EMaxLev);
    assertEquals(
        "(skill('Charged Strike'.blvl)+skill('Lightning Bolt'.blvl)"
            + "+skill('Power Strike'.blvl)+skill('Lightning Fury'.blvl)) * par8",
        skill.EDmgSymPerCalc);
    assertEquals("20", skill.calc1);
    assertEquals("ln34", skill.calc2);
    assertEquals("par1", skill.aurarangecalc);
    assertArrayEquals(new int[] {20, 0, 2, 1, 0, 0, 0, 8}, skill.Param);

    Attributes owner = Attributes.obtainStandard();
    owner.base().clear();
    owner.reset();
    for (int level = 1; level <= 20; level++) {
      int expectedMin = D2MOO_LIGHTNING_MIN[level - 1];
      int expectedMax = D2MOO_LIGHTNING_MAX[level - 1];
      assertEquals(0, SkillFormula.evaluate(
          skill.EDmgSymPerCalc, skill, level, name -> 0),
          "zero hard-point synergy level " + level);
      assertEquals(20, ServerSkillSystem.lightningStrikeRange(skill, level),
          "native chain range level " + level);
      assertEquals(level + 1, ServerSkillSystem.lightningStrikeJumpCount(skill, level),
          "native Calc2 jump budget level " + level);
      assertEquals(expectedMin,
          MissileDamageResolver.skillElementalDamage(skill, level, true, name -> 0),
          "canonical getter minimum level " + level);
      assertEquals(expectedMax,
          MissileDamageResolver.skillElementalDamage(skill, level, false, name -> 0),
          "canonical getter maximum level " + level);

      Missile projectile = new Missile().set(row, new Vector2(), 40).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, level));
      assertEquals(0, statInt(projectile, Stat.mindamage),
          "chain segment has no weapon source level " + level);
      assertEquals(0, statInt(projectile, Stat.maxdamage),
          "chain segment has no weapon source maximum level " + level);
      assertEquals(expectedMin, statInt(projectile, Stat.lightmindam),
          "riiablo lightning minimum level " + level);
      assertEquals(expectedMax, statInt(projectile, Stat.lightmaxdam),
          "riiablo lightning maximum level " + level);
    }
  }

  private static int statInt(Missile projectile, short stat) {
    StatRef ref = projectile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
