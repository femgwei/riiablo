package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrogDemonAiIntegrationTest extends RiiabloTest {
  @Test void ordinaryActionStillBlocksDecisionsAndZeroPercentRollConsumesNativeSeed() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1)) {
      f.seedFirstRoll(50, true);
      com.d2moo.common.drlg.D2Seed expected = new com.d2moo.common.drlg.D2Seed(
          Riiablo.gameSeed ^ (f.actor * 0x45D9F3B), 666);
      com.d2moo.common.seed.Seed.rollPercentage(expected); // zero-percent shoot roll
      boolean attack = com.d2moo.common.seed.Seed.rollPercentage(expected) < 50;
      f.ai.params[1] = 0;
      f.ai.params[0] = 50;
      f.ai.params[2] = 0;
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(1);
      assertEquals(attack, f.casting() != null);
      if (attack) {
        f.sequence().started = true;
        f.ai.update(10);
        assertTrue(f.sequence().started, "ordinary attacks must not be replaced by a new decision");
      }
    }
  }
  @Test void nearSpawnEntersCombatWithoutCastingAndNeverResetsAfterAttacks() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1)) {
      f.ai.params[1] = 0;
      f.ai.params[0] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting(), "already-small presence does not cast Emerge");
      f.ai.update(11 * Animation.FRAME_DURATION);
      assertNull(f.casting());
      f.ai.update(2 * Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.casting().skillId);
      assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
      f.finishAction();
      f.targetPosition().set(28, 10);
      f.ai.params[3] = 0;
      f.ai.update(1);
      assertNull(f.casting(), "surfaced state must not cast Submerge again");
      assertEquals("APPROACH", f.ai.getState());
    }
  }

  @Test void meleeShootTakesPriorityOverMeleeAttackAndCircle() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1)) {
      f.ai.params[1] = f.ai.params[0] = f.ai.params[2] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(1);
      assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
    }
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(1)) {
      f.ai.params[1] = f.ai.params[0] = 0;
      f.ai.params[2] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(1);
      assertEquals("CIRCLE", f.ai.getState());
      assertNull(f.casting());
    }
  }

  @Test void rangedShootCircleAndIdleHaveNativePriority() {
    for (int branch = 0; branch < 3; branch++) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(7)) {
        f.ai.params[5] = 12;
        f.ai.params[4] = branch == 0 ? 100 : 0;
        f.ai.params[3] = branch == 1 ? 100 : 0;
        f.ai.update(Animation.FRAME_DURATION);
        f.ai.update(1);
        if (branch == 0) assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
        else {
          assertNull(f.casting());
          assertEquals(branch == 1 ? "CIRCLE" : "IDLE", f.ai.getState());
        }
      }
    }
  }

  @Test void hiddenDecisionsUseTwelveThenTwentyFourFramesAndStrictTimeout() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(18)) {
      // Only the collision boundary is mocked here; real factory coverage is separate.
      EntityFactory factory = mock(EntityFactory.class);
      f.ai.factory = factory;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(Riiablo.files.skills.get("Submerge").Id, f.casting().skillId);
      assertTrue(f.sequence().nativeFrogHidden);
      assertEquals(Engine.Monster.MODE_S1, f.sequence().mode1);
      f.sequence().nativeSubmergeHeld = true;
      f.ai.update(11 * Animation.FRAME_DURATION);
      verify(factory, times(1)).enterFrogHidden(f.actor);
      f.ai.update(2 * Animation.FRAME_DURATION); // first hidden decision
      verify(factory, times(2)).enterFrogHidden(f.actor);
      for (int i = 1; i < 65; i++) f.ai.update(25 * Animation.FRAME_DURATION);
      verify(factory, never()).restoreFrogPresence(f.actor);
      when(factory.restoreFrogPresence(f.actor)).thenReturn(true);
      f.ai.update(25 * Animation.FRAME_DURATION);
      verify(factory).restoreFrogPresence(f.actor);
      assertEquals(Riiablo.files.skills.get("Emerge").Id, f.casting().skillId);
      assertEquals(f.actor, f.casting().targetId);
      assertFalse(f.sequence().nativeFrogHidden);
    }
  }

  @Test void hiddenNeedsSuccessfulPresenceRestoreAndUsesStrictDistanceBoundary() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(18)) {
      EntityFactory factory = mock(EntityFactory.class);
      f.ai.factory = factory;
      f.ai.update(Animation.FRAME_DURATION);
      f.sequence().nativeSubmergeHeld = true;
      f.ai.params[7] = 9;
      f.targetPosition().set(19, 10);
      f.ai.update(1);
      verify(factory, never()).restoreFrogPresence(f.actor);
      f.targetPosition().set(18, 10);
      f.ai.update(1);
      verify(factory).restoreFrogPresence(f.actor);
      assertTrue(f.sequence().nativeFrogHidden, "a failed landing stays hidden");
      when(factory.restoreFrogPresence(f.actor)).thenReturn(true);
      f.ai.update(1);
      assertEquals(Riiablo.files.skills.get("Emerge").Id, f.casting().skillId);
    }
  }

  private Act1NativeAiIntegrationTest.Fixture fixture(float distance) {
    Act1NativeAiIntegrationTest.Fixture f = new Act1NativeAiIntegrationTest.Fixture("frogdemon1", 0, distance);
    f.world.getMapper(CofReference.class).create(f.actor).set("FD", Engine.Monster.MODE_NU);
    com.riiablo.map.Map map = new com.riiablo.map.Map(1, 0);
    com.riiablo.map.Map.Zone zone = map.createSyntheticArena(2, 2, 2);
    f.world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).get(f.actor).set(map, zone);
    return f;
  }
}
