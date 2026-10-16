package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.NativeUnitFlags;
import org.junit.jupiter.api.Test;

class TrapPoisonAiIntegrationTest extends RiiabloTest {
  private void configure(Fixture f, int casts) {
    f.ai.params[0] = 20;
    f.ai.params[1] = casts;
    f.ai.params[2] = 17;
  }

  private void assertExpired(Fixture f) {
    assertEquals("DEAD", f.ai.getState());
    assertNull(f.casting());
    assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
    assertEquals(Engine.Monster.MODE_DD, f.sequence().mode2);
    assertTrue(f.world.getMapper(NativeUnitFlags.class).get(f.actor)
        .has(NativeUnitFlags.NO_TREASURE_CLASS));
  }

  @Test void realPoisonRowResolvesAndUsesItsOwnSkill() {
    int count = 0;
    for (MonStats.Entry mon : Riiablo.files.monstats) {
      if (!"Trap-Poison".equals(mon.AI)) continue;
      assertEquals(TrapPoison.class, AI.findAI(12, mon.AI).getClass());
      Skills.Entry skill = Riiablo.files.skills.get(mon.Skill1);
      assertNotNull(skill);
      assertNotEquals(Riiablo.files.skills.get(
          Riiablo.files.monstats.get("trap-nova").Skill1).Id, skill.Id);
      System.out.println("[TRAP_POISON_DATA] row=" + mon.Id + " skill=" + skill.skill
          + " srvdofunc=" + skill.srvdofunc + " mode=" + mon.Sk1mode
          + " missile=" + skill.srvmissile + " missileA=" + skill.srvmissilea);
      count++;
    }
    assertEquals(1, count);
  }

  @Test void poisonCastsWaitsAndExpiresInEveryDifficulty() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("trap-poisoncloud", difficulty, 10)) {
        configure(f, 2);
        f.ai.update(Animation.FRAME_DURATION);
        f.assertSkill(0);
        f.sequence().started = true;
        f.ai.update(10);
        assertTrue(f.sequence().started);
        f.finishAction();
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals("IDLE", f.ai.getState());
        f.ai.update(16 * Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.ai.update(Animation.FRAME_DURATION + .00001f);
        f.assertSkill(0);
        f.finishAction();
        f.ai.update(Animation.FRAME_DURATION);
        assertExpired(f);
      }
    }
  }

  @Test void rangeBoundaryIsInclusiveAndOverflowExpires() {
    try (Fixture f = new Fixture("trap-poisoncloud", 0, 10)) {
      configure(f, 2);
      ((TrapPoison) f.ai).think(f.target, 20, false);
      f.assertSkill(0);
      f.finishAction();
      ((TrapPoison) f.ai).think(f.target, 20.01f, false);
      assertExpired(f);
    }
  }

  @Test void noTargetAndNoUsesExpire() {
    try (Fixture f = new Fixture("trap-poisoncloud", 0, 10)) {
      configure(f, 2);
      f.targetPosition().set(1000, 1000);
      f.ai.update(Animation.FRAME_DURATION);
      assertExpired(f);
    }
    try (Fixture f = new Fixture("trap-poisoncloud", 0, 10)) {
      configure(f, 0);
      f.ai.update(Animation.FRAME_DURATION);
      assertExpired(f);
    }
  }

  @Test void failedPoisonCastConsumesUseAndExpires() {
    try (Fixture f = new Fixture("trap-poisoncloud", 0, 10)) {
      configure(f, 1);
      f.probe.reject = true;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.probe.reject = false;
      f.ai.update(10 * Animation.FRAME_DURATION + .00001f);
      assertExpired(f);
    }
  }
}
