package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

/** Forty-fourth DMG-04 audit: one Freezing Arrow explosion cold packet. */
class FreezingArrowGoldenDamageTest extends RiiabloTest {
  private static final int[] D2MOO_COLD_MIN = {
      40, 50, 60, 70, 80, 90, 100, 110, 125, 140,
      155, 170, 185, 200, 215, 230, 250, 270, 290, 310
  };
  private static final int[] D2MOO_COLD_MAX = {
      50, 60, 70, 80, 90, 100, 110, 120, 135, 150,
      165, 180, 195, 210, 225, 240, 260, 280, 300, 320
  };

  @Test
  void levelOneToTwentyMatchD2mooExplosionColdCurveAndFreezeLength() {
    Skills.Entry skill = Riiablo.files.skills.get("Freezing Arrow");
    Missiles.Entry root = Riiablo.files.Missiles.get("freezingarrow");
    Missiles.Entry explosion = Riiablo.files.Missiles.get("freezingarrowexp3");
    assertNotNull(skill);
    assertNotNull(root);
    assertNotNull(explosion);
    assertEquals(31, skill.Id);
    assertEquals(4, skill.srvstfunc);
    assertEquals(0, skill.srvdofunc);
    assertEquals("freezingarrow", skill.srvmissile);
    assertEquals(128, skill.SrcDam);
    assertEquals(8, skill.HitShift);
    assertEquals("cold", skill.EType);
    assertEquals(40, skill.EMin);
    assertEquals(50, skill.EMax);
    assertEquals("(skill('Cold Arrow'.blvl))*par8", skill.EDmgSymPerCalc);
    assertEquals(4, root.pSrvHitFunc);
    assertEquals("freezingarrowexp3", root.HitSubMissile[0]);
    assertEquals(1, explosion.pSrvHitFunc);

    Attributes owner = Attributes.obtainStandard();
    Missile parent = new Missile();
    parent.missile = root;
    Attributes weaponOwner = Attributes.obtainStandard();
    weaponOwner.base().put(Stat.mindamage, 10);
    weaponOwner.base().put(Stat.maxdamage, 10);
    weaponOwner.reset();
    assertTrue(MissileDamageResolver.initializeSkill(parent, skill, weaponOwner, 1, name -> 0));
    assertNull(parent.damage.get(Stat.coldmaxdam),
        "the travelling arrow owns the weapon hit; its child owns the cold packet");

    for (int level = 1; level <= 20; level++) {
      Missile child = new Missile().set(explosion, new Vector2(), 1).setOwner(1);
      assertTrue(MissileDamageResolver.initializeSkillArea(child, skill, owner, level));
      assertEquals(D2MOO_COLD_MIN[level - 1], statInt(child, Stat.coldmindam),
          "cold min level " + level);
      assertEquals(D2MOO_COLD_MAX[level - 1], statInt(child, Stat.coldmaxdam),
          "cold max level " + level);
      assertEquals(50, statInt(child, Stat.coldlength), "freeze length level " + level);
      assertTrue(child.freezesTarget, "explosion child must use native freeze semantics");
      assertEquals(0, statInt(child, Stat.maxdamage),
          "explosion child has no independent physical packet level " + level);
    }
  }

  private static int statInt(Missile missile, short stat) {
    StatRef ref = missile.damage.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
