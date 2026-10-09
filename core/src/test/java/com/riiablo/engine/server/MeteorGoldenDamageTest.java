package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import org.junit.jupiter.api.Test;

/** Fifty-fourth DMG-04 audit: Meteor's skill-owned impact fire packet. */
class MeteorGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_IMPACT_MIN = {
      80, 103, 126, 149, 172, 195, 218, 241, 280, 319,
      358, 397, 436, 475, 514, 553, 632, 711, 790, 869
  };
  private static final int[] D2MOO_IMPACT_MAX = {
      100, 125, 150, 175, 200, 225, 250, 275, 316, 357,
      398, 439, 480, 521, 562, 603, 684, 765, 846, 927
  };
  private static final int[] D2MOO_FIELD_MIN_FIXED = {
      120, 152, 184, 216, 248, 280, 312, 344, 384, 424,
      464, 504, 544, 584, 624, 664, 712, 760, 808, 856
  };
  private static final int[] D2MOO_FIELD_MAX_FIXED = {
      200, 232, 264, 296, 328, 360, 392, 424, 464, 504,
      544, 584, 624, 664, 704, 744, 792, 840, 888, 936
  };

  @Test
  void levelOneToTwentyMatchesD2mooImpactAndTableOwnedFieldCurves() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.METEOR);
    Missiles.Entry centerRow = Riiablo.files.Missiles.get("meteorcenter");
    Missiles.Entry fieldRow = Riiablo.files.Missiles.get("meteorfire");
    assertNotNull(skill);
    assertNotNull(centerRow);
    assertNotNull(fieldRow);
    assertEquals("Meteor", skill.skill);
    assertEquals(56, skill.Id);
    assertEquals(28, skill.srvdofunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("fire", skill.EType);
    assertEquals(80, skill.EMin);
    assertEquals(100, skill.EMax);
    assertArrayEquals(new int[] {23, 39, 79, 81, 83}, skill.EMinLev);
    assertArrayEquals(new int[] {25, 41, 81, 83, 85}, skill.EMaxLev);
    assertEquals("(skill('Fire Bolt'.blvl)+skill('Fire Ball'.blvl))*par8",
        skill.EDmgSymPerCalc);
    assertEquals(30, skill.Param[2]);
    assertEquals(15, skill.Param[3]);

    assertEquals(1, centerRow.pSrvDoFunc);
    assertEquals(14, centerRow.pSrvHitFunc);
    assertEquals("Meteor", centerRow.Skill);
    assertFalse(centerRow.MissileSkill);
    assertEquals("meteorfire", centerRow.HitSubMissile[0]);
    assertEquals(1, centerRow.sHitPar[1]);

    assertEquals(5, fieldRow.pSrvDoFunc);
    assertEquals(3, fieldRow.pSrvDmgFunc);
    assertTrue(fieldRow.Skill == null || fieldRow.Skill.isEmpty());
    assertFalse(fieldRow.MissileSkill);
    assertEquals(3, fieldRow.HitShift);
    assertEquals("fire", fieldRow.EType);
    assertEquals(15, fieldRow.EMin);
    assertEquals(25, fieldRow.Emax);
    assertArrayEquals(new int[] {4, 5, 6, 6, 6}, fieldRow.MinELev);
    assertArrayEquals(new int[] {4, 5, 6, 6, 6}, fieldRow.MaxELev);
    assertEquals(41, fieldRow.DamageRate);
    assertTrue(fieldRow.ApplyMastery);
    assertEquals(19, fieldRow.dParam[0]);

    Attributes owner = Attributes.obtainStandard();
    for (int level = 1; level <= 20; level++) {
      assertEquals(D2MOO_IMPACT_MIN[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, true, name -> 0),
          "impact min level " + level);
      assertEquals(D2MOO_IMPACT_MAX[level - 1],
          MissileDamageResolver.skillElementalDamage(skill, level, false, name -> 0),
          "impact max level " + level);

      Missile center = new Missile();
      center.missile = centerRow;
      assertTrue(MissileDamageResolver.initializeSorceressFireArea(
          center, skill, owner, true, level, name -> 0));
      assertEquals(D2MOO_IMPACT_MIN[level - 1] << 8, center.elementalMinRateFixed,
          "production impact fixed min level " + level);
      assertEquals(D2MOO_IMPACT_MAX[level - 1] << 8, center.elementalMaxRateFixed,
          "production impact fixed max level " + level);

      Missile field = new Missile();
      field.missile = fieldRow;
      assertTrue(MissileDamageResolver.initializeMeteorFireArea(
          field, owner, true, level, null));
      assertEquals(D2MOO_FIELD_MIN_FIXED[level - 1], field.elementalMinRateFixed,
          "field fixed min level " + level);
      assertEquals(D2MOO_FIELD_MAX_FIXED[level - 1], field.elementalMaxRateFixed,
          "field fixed max level " + level);
      assertEquals(41, field.elementalDamageRate);
      assertEquals(30 + (level - 1) * 15, ServerSkillSystem.meteorFireLifetime(skill, level),
          "field duration level " + level);
    }
  }

  @Test
  void tableOwnedFieldAppliesFireMasteryWhenRequestedByMissilesRow() {
    Missiles.Entry fieldRow = Riiablo.files.Missiles.get("meteorfire");
    Attributes owner = Attributes.obtainStandard();
    owner.base().put(Stat.passive_fire_mastery, 30);
    owner.reset();
    Missile field = new Missile();
    field.missile = fieldRow;

    assertTrue(MissileDamageResolver.initializeMeteorFireArea(field, owner, true, 1, null));
    assertEquals(156, field.elementalMinRateFixed);
    assertEquals(260, field.elementalMaxRateFixed);
  }
}
