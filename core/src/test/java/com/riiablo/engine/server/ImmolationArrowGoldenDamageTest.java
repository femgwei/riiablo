package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Fortieth DMG-04 level-by-level audit: Immolation Arrow's immediate fire packet. */
class ImmolationArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_FIRE_MIN = {
      10, 20, 30, 40, 50, 60, 70, 80, 100, 120,
      140, 160, 180, 200, 220, 240, 270, 300, 330, 360
  };
  private static final int[] D2MOO_FIRE_MAX = {
      20, 30, 40, 50, 60, 70, 80, 90, 110, 130,
      150, 170, 190, 210, 230, 250, 280, 310, 340, 370
  };
  private static final int[] D2MOO_FIELD_MIN_FIXED = {
      28, 48, 68, 88, 108, 128, 148, 168, 188, 208,
      228, 248, 268, 288, 308, 328, 348, 368, 388, 408
  };
  private static final int[] D2MOO_FIELD_MAX_FIXED = {
      36, 56, 76, 96, 116, 136, 156, 176, 196, 216,
      236, 256, 276, 296, 316, 336, 356, 376, 396, 416
  };

  @Test
  void levelOneToTwentyMatchesD2mooImmediateFireCurveWithoutWeaponOrSynergy() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.IMMOLATION_ARROW);
    Missiles.Entry root = Riiablo.files.Missiles.get("immolationarrow");
    Missiles.Entry field = Riiablo.files.Missiles.get("immolationfire");
    assertNotNull(skill);
    assertNotNull(root);
    assertNotNull(field);
    assertEquals("Immolation Arrow", skill.skill);
    assertEquals(27, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("immolationarrow", skill.srvmissile);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals(30, skill.ToHit);
    assertEquals(9, skill.LevToHit);
    assertEquals("fire", skill.EType);
    assertEquals(10, skill.EMin);
    assertEquals(20, skill.EMax);
    assertArrayEquals(new int[] {10, 20, 30, 32, 34}, skill.EMinLev);
    assertArrayEquals(new int[] {10, 20, 30, 32, 34}, skill.EMaxLev);
    assertEquals("(skill('Exploding Arrow'.blvl)) * par8", skill.EDmgSymPerCalc);
    assertEquals(9, root.pSrvHitFunc);
    assertEquals("immolationfire", root.HitSubMissile[0]);
    assertEquals(5, field.pSrvDoFunc);
    assertEquals(3, field.pSrvDmgFunc);
    assertEquals(75, field.Range);
    assertEquals(2, field.HitShift);
    assertEquals(7, field.EMin);
    assertEquals(9, field.Emax);
    assertArrayEquals(new int[] {5, 5, 5, 5, 5}, field.MinELev);
    assertArrayEquals(new int[] {5, 5, 5, 5, 5}, field.MaxELev);
    assertEquals(41, field.DamageRate);
    assertEquals("skill('Fire Arrow'.blvl) * 5", field.EDmgSymPerCalc);

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      Missile area = new Missile();
      area.missile = root;
      assertTrue(MissileDamageResolver.initializeSkillArea(area, skill, owner, level));
      assertEquals(D2MOO_FIRE_MIN[level - 1], statInt(area, Stat.firemindam),
          "immediate fire min level " + level);
      assertEquals(D2MOO_FIRE_MAX[level - 1], statInt(area, Stat.firemaxdam),
          "immediate fire max level " + level);
      assertEquals(D2MOO_FIRE_MIN[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, true, name -> 0),
          "displayed min level " + level);
      assertEquals(D2MOO_FIRE_MAX[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, false, name -> 0),
          "displayed max level " + level);

      Missile fire = new Missile();
      fire.missile = field;
      assertTrue(MissileDamageResolver.initializeImmolationFireArea(fire, owner, true, level));
      assertTrue(fire.fixedElementalRate);
      assertEquals(D2MOO_FIELD_MIN_FIXED[level - 1], fire.elementalMinRateFixed,
          "field fixed min level " + level);
      assertEquals(D2MOO_FIELD_MAX_FIXED[level - 1], fire.elementalMaxRateFixed,
          "field fixed max level " + level);
      assertEquals(41, fire.elementalDamageRate);
      assertEquals(75, fire.missile.Range);
      assertEquals(D2MOO_FIELD_MIN_FIXED[level - 1] * 75 / 256,
          fire.elementalMinRateFixed * fire.missile.Range / 256,
          "field full-duration min level " + level);
      assertEquals(D2MOO_FIELD_MAX_FIXED[level - 1] * 75 / 256,
          fire.elementalMaxRateFixed * fire.missile.Range / 256,
          "field full-duration max level " + level);
    }
  }

  private static int statInt(Missile missile, short stat) {
    StatRef ref = missile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
