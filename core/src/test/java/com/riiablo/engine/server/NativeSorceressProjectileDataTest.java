package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import org.junit.jupiter.api.Test;

/** Stable executable contract for the 1.10f Sorceress starter projectiles. */
class NativeSorceressProjectileDataTest extends RiiabloTest {
  @Test
  void fireAndIceBoltUseTheGenericServerMissilePipeline() {
    assertGenericBolt(SkillId.FIRE_BOLT, "Fire Bolt", "firebolt", "fire");
    assertGenericBolt(SkillId.ICE_BOLT, "Ice Bolt", "icebolt", "cold");

    Skills.Entry ice = Riiablo.files.skills.get(SkillId.ICE_BOLT);
    assertTrue(ice.ELen > 0, "Ice Bolt must carry a native cold duration");
  }

  @Test
  void chargedBoltUsesSrvDo17AndCalc1Burst() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.CHARGED_BOLT);
    assertNotNull(skill);
    assertEquals("Charged Bolt", skill.skill);
    assertEquals(17, skill.srvdofunc);
    assertFalse(skill.calc1 == null || skill.calc1.isEmpty());

    String missileName = firstNonEmpty(skill.srvmissilea, skill.srvmissile);
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    assertNotNull(missile);
    assertEquals("chargedbolt", missile.Missile);
    assertEquals("ltng", skill.EType);

    int levelOne = ServerSkillSystem.chargedBoltCount(skill, 1);
    int levelTwenty = ServerSkillSystem.chargedBoltCount(skill, 20);
    assertTrue(levelOne > 1, "Charged Bolt must create a burst at level one");
    assertTrue(levelTwenty >= levelOne, "calc1 burst count must not shrink with level");
  }

  @Test
  void chargedBoltInitialDirectionRemainsTargetAligned() {
    Vector2 target = new Vector2(3, 4).nor();
    for (int i = 0; i < 8; i++) {
      Vector2 actual = ServerSkillSystem.chargedBoltDirection(target, i, 8, new Vector2());
      assertEquals(target.x, actual.x, 0.0001f);
      assertEquals(target.y, actual.y, 0.0001f);
      assertEquals(1f, actual.len(), 0.0001f);
    }
  }

  @Test
  void elementalSnapshotEvaluatesNativeSynergyAndMasteryGate() {
    Skills.Entry skill = new Skills.Entry();
    skill.skill = "Synthetic Fire Bolt";
    skill.EMin = 10;
    skill.EMax = 20;
    skill.EType = "fire";
    skill.EDmgSymPerCalc = "skill('Warmth'.blvl)*par1";
    skill.Param = new int[] {5};
    skill.HitShift = 8;

    Missiles.Entry row = new Missiles.Entry();
    row.Missile = "synthetic";
    row.ApplyMastery = true;
    row.ToHit = false;
    Missile projectile = new Missile();
    projectile.missile = row;

    com.riiablo.attributes.Attributes attrs =
        com.riiablo.attributes.Attributes.obtainStandard();
    attrs.aggregate().put(Stat.passive_fire_mastery, 20);
    boolean initialized = com.riiablo.engine.server.missile.MissileDamageResolver
        .initializeSkill(projectile, skill, attrs, 1, name ->
            "Warmth".equalsIgnoreCase(name) ? 2 : 0);
    assertTrue(initialized);
    StatRef fireMin = projectile.damage.get(Stat.firemindam, StatRef.obtain());
    StatRef fireMax = projectile.damage.get(Stat.firemaxdam, StatRef.obtain());
    assertNotNull(fireMin);
    assertNotNull(fireMax);
    // 10..20 * (1 + 2*5% synergy) * (1 + 20% mastery).
    assertEquals(13, fireMin.asInt());
    assertEquals(26, fireMax.asInt());
  }

  @Test
  void staticFieldNativeRowsAreAvailable() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.STATIC_FIELD);
    assertNotNull(skill);
    assertEquals("Static Field", skill.skill);
    assertEquals(20, skill.srvdofunc);
    assertEquals("par4", skill.calc1);
    assertEquals("par3", skill.calc2);
    assertEquals("ln12", skill.aurarangecalc);
    assertEquals(0x8783, skill.aurafilter);
    assertEquals("ltng", skill.EType);
    assertEquals(25, SkillFormula.evaluate(skill.calc1, skill, 1));
    assertEquals(0, SkillFormula.evaluate(skill.calc2, skill, 1));
    assertEquals(5, SorceressSkills.getStaticFieldRadius(skill, 1));
    assertEquals(14, SorceressSkills.getStaticFieldRadius(skill, 10));
    assertEquals(0, Riiablo.files.DifficultyLevels.get(0).StaticFieldMin);
    assertEquals(33, Riiablo.files.DifficultyLevels.get(1).StaticFieldMin);
    assertEquals(50, Riiablo.files.DifficultyLevels.get(2).StaticFieldMin);
  }

  private static void assertGenericBolt(
      int skillId, String name, String expectedMissile, String expectedElement) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    assertNotNull(skill);
    assertEquals(name, skill.skill);
    assertEquals(0, skill.srvdofunc);
    assertFalse(skill.srvmissile == null || skill.srvmissile.isEmpty());
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissile);
    assertNotNull(missile);
    assertEquals(expectedMissile, missile.Missile);
    assertEquals(expectedElement, skill.EType);
    assertTrue(skill.EMax >= skill.EMin);
  }

  private static String firstNonEmpty(String first, String second) {
    return first != null && !first.isEmpty() ? first : second;
  }
}
