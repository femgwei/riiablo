package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

/** Native-data regression coverage for Amazon specialist skill handlers. */
class AmazonSkillSpecializationTest extends RiiabloTest {
  @Test
  void auditNativeAmazonSpecialRows() {
    String[] names = {"Multiple Shot", "Guided Arrow", "Charged Strike",
        "Dopplezon", "Valkyrie", "Lightning Strike", "Lightning Fury"};
    for (String name : names) {
      Skills.Entry skill = Riiablo.files.skills.get(name);
      assertNotNull(skill, name);
      System.out.println("[AMAZON_SKILL] name=" + name + " id=" + skill.Id
          + " srvSt=" + skill.srvstfunc + " srvDo=" + skill.srvdofunc + " calc1=" + skill.calc1
          + " calc2=" + skill.calc2 + " srv=" + skill.srvmissile
          + " srvA=" + skill.srvmissilea + " srvB=" + skill.srvmissileb
          + " auraRange=" + skill.aurarangecalc + " summon=" + skill.summon
          + " pettype=" + skill.pettype + " petmax=" + skill.petmax
          + " params=" + java.util.Arrays.toString(skill.Param));
      String[] missiles = {skill.srvmissile, skill.srvmissilea, skill.srvmissileb};
      for (String missileName : missiles) {
        if (missileName == null || missileName.isEmpty()) continue;
        Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
        assertNotNull(missile, name + ":" + missileName);
        System.out.println("[AMAZON_MISSILE] skill=" + name + " missile=" + missileName
            + " srvDo=" + missile.pSrvDoFunc + " srvHit=" + missile.pSrvHitFunc
            + " sHitPar=" + java.util.Arrays.toString(missile.sHitPar)
            + " sub=" + java.util.Arrays.toString(missile.SubMissile)
            + " hitSub=" + java.util.Arrays.toString(missile.HitSubMissile));
      }
    }
  }

  @Test
  void chargedStrikeUsesNativeBoltCountAndNormalizedSpread() {
    Skills.Entry skill = Riiablo.files.skills.get("Charged Strike");
    assertNotNull(skill);
    assertEquals(3, ServerSkillSystem.chargedStrikeBoltCount(skill, 1));
    assertEquals(4, ServerSkillSystem.chargedStrikeBoltCount(skill, 5));
    assertEquals(5, ServerSkillSystem.chargedStrikeBoltCount(skill, 10));

    Vector2 base = new Vector2(1, 0);
    Vector2 first = ServerSkillSystem.chargedStrikeDirection(base, 0, 3, new Vector2());
    Vector2 centre = ServerSkillSystem.chargedStrikeDirection(base, 1, 3, new Vector2());
    Vector2 last = ServerSkillSystem.chargedStrikeDirection(base, 2, 3, new Vector2());
    assertEquals(1f, first.len(), 0.0001f);
    assertEquals(1f, centre.len(), 0.0001f);
    assertEquals(1f, last.len(), 0.0001f);
    assertEquals(first.y, -last.y, 0.0001f);
  }

  @Test
  void lightningStrikeUsesNativeRangeAndJumpCalc() {
    Skills.Entry skill = Riiablo.files.skills.get("Lightning Strike");
    assertNotNull(skill);
    assertEquals(20f, ServerSkillSystem.lightningStrikeRange(skill, 1), 0.0001f);
    assertEquals(3, ServerSkillSystem.lightningStrikeJumpCount(skill, 1));
    assertEquals(12, ServerSkillSystem.lightningStrikeJumpCount(skill, 10));
  }

  @Test
  void lightningFuryUsesNativeHitFunctionRangeAndBoltCount() {
    Skills.Entry skill = Riiablo.files.skills.get("Lightning Fury");
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissile);
    assertNotNull(missile);
    assertEquals(20, missile.pSrvHitFunc);
    assertEquals("furylightning", missile.HitSubMissile[0]);
    assertEquals(15, MissileCollisionSystem.lightningFuryRange(missile, skill, 1));
    assertEquals(3, MissileCollisionSystem.lightningFuryBoltCount(missile, skill, 1));
    assertEquals(12, MissileCollisionSystem.lightningFuryBoltCount(missile, skill, 10));
  }
}
