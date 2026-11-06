package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

/** Multiplayer presentation contract for server-owned area skills. */
class AuthoritativeAreaSkillPresentationTest extends RiiabloTest {
  @Test
  void complexAreaSkillsNeverCreateASecondClientMissileSet() {
    // Hydra is intentionally absent: native SrvDo144 creates three monster
    // units, whose visuals follow MonsterP rather than MissileP.
    Skills.Entry hydra = Riiablo.files.skills.get(SkillId.HYDRA);
    assertNotNull(hydra);
    assertFalse(SkillCastHandler.hasServerMissile(hydra));
    int[] skills = {
        SkillId.FIRESTORM, SkillId.FISSURE, SkillId.VOLCANO,
        SkillId.ARMAGEDDON, SkillId.HURRICANE
    };
    for (int id : skills) {
      Skills.Entry row = Riiablo.files.skills.get(id);
      assertNotNull(row, Integer.toString(id));
      assertTrue(SkillCastHandler.shouldReuseServerMissile(row,
          true, false, false, false, false, false, false), row.skill);
    }
  }

  @Test
  void multipleShotReusesLocalAuthoritativeMissiles() {
    Skills.Entry skill = Riiablo.files.skills.get("Multiple Shot");
    assertNotNull(skill);
    assertEquals(8, skill.srvdofunc);
    assertTrue(SkillCastHandler.shouldReuseServerMissile(skill,
        false, false, false, false, false, false, false, true));
  }

  @Test
  void arcticBlastUsesTheLocalAuthoritativeStreamMissile() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.ARCTIC_BLAST);
    assertNotNull(skill);
    assertEquals(19, skill.srvdofunc);
    assertTrue(SkillCastHandler.shouldReuseServerMissile(skill,
        false, false, false, false, false, false, false, false, false, true));
  }

  @Test
  void lightningStrikeDoesNotUseSharedNovaClientFan() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.LIGHTNING_STRIKE);
    assertNotNull(skill);
    assertEquals(14, skill.srvdofunc);
    assertTrue(SkillCastHandler.isLocalAuthoritativeLightningStrike(skill, true, 14));
    assertFalse(SkillCastHandler.isLocalAuthoritativeLightningStrike(skill, false, 14));
  }

  @Test
  void blessedHammerAlwaysReusesTheAuthoritativeSpiralMissile() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLESSED_HAMMER);
    assertNotNull(skill);
    assertTrue(PaladinSkills.isBlessedHammer(skill));
    assertTrue(SkillCastHandler.shouldReuseServerMissile(skill,
        true, false, false, false, false, false, false));
    assertTrue(SkillCastHandler.shouldReuseServerMissile(skill,
        false, false, true, false, false, false, false));
  }

  @Test
  void meteorVisualStartsAboveTheImpactInIsometricProjection() {
    Vector2 target = new Vector2(10f, 10f);
    Vector2 start = MissileImpactPresentationSystem.meteorVisualStart(target, 59, 0, 0,
        new Vector2());
    // RenderSystem/IsometricCamera use screenY = -(x + y) * tileHeight.
    assertTrue(start.x + start.y < target.x + target.y);
    Vector2 after = new Vector2(start).lerp(target, .5f);
    assertTrue(after.x + after.y > start.x + start.y);
  }
}
