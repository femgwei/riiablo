package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest.fixture;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Leap;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyRangedCheckpointIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @ValueSource(strings = {"CorruptArcher", "SkeletonBow", "SkeletonMage"})
  void missileBindingIsValidatedWithoutLoadingOrReplacingTheTargetRow(String type) throws Exception {
    AI source = AI.findAI(0, type), target = AI.findAI(0, type);
    Missiles.Entry original = row(7, "checkpoint-arrow", 31), bound = row(7, "checkpoint-arrow", 31);
    AiRuntimeStateTest.put(source, source.getClass(), "missile", original);
    AiRuntimeStateTest.put(target, target.getClass(), "missile", bound);
    AiRuntimeStateTest.put(source, source.getClass(), "time", -.125f);
    AiRuntimeState image = AiRuntimeState.fromBytes(AiRuntimeState.capture(source, 0).bytes());
    image.validateRestore(target, 0); image.restore(target, 0);
    assertSame(bound, AiRuntimeStateTest.get(target, target.getClass(), "missile"));
    assertArrayEquals(image.bytes(), AiRuntimeState.capture(target, 0).bytes());
    for (Missiles.Entry mismatch : new Missiles.Entry[] {null, row(8, "checkpoint-arrow", 31),
        row(7, "another-arrow", 31), row(7, "checkpoint-arrow", 30)}) {
      AiRuntimeStateTest.put(target, target.getClass(), "missile", mismatch);
      AiRuntimeStateTest.put(target, target.getClass(), "time", 17f);
      byte[] before = AiRuntimeState.capture(target, 0).bytes();
      assertThrows(IllegalStateException.class, () -> image.restore(target, 0));
      assertArrayEquals(before, AiRuntimeState.capture(target, 0).bytes());
    }
    AiRuntimeState absent = AiRuntimeState.capture(AI.findAI(0, type), 0);
    AiRuntimeStateTest.put(target, target.getClass(), "missile", bound);
    assertThrows(IllegalStateException.class, () -> absent.restore(target, 0));
    byte[] bytes = image.bytes();
    for (int i = 0; i < bytes.length; i++) {
      byte[] cut = java.util.Arrays.copyOf(bytes, i);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(cut));
    }
    byte[] badId = bytes.clone();
    int binding = bytes.length - (1 + 4 + 1 + 4 + "checkpoint-arrow".length() + 4);
    ByteBuffer.wrap(badId).putInt(binding + 1, -1);
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(badId));
    byte[] badBool = absent.bytes(); badBool[badBool.length - 1] = 2;
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(badBool));
  }

  @Test void sandRaiderSubscriptionsAreIsolatedAcrossTwoLiveWorldsAndChargeResumesAtTheBoundary() throws Exception {
    java.util.Random previous = MathUtils.random;
    try (Fixture source = fixture("SandRaider", 1); Fixture target = fixture("SandRaider", 1)) {
      MathUtils.random = mock(RandomXS128.class);
      SandRaider a = (SandRaider) source.ai, b = (SandRaider) target.ai;
      assertSame(source.world, a.world); assertSame(target.world, b.world);
      assertNotSame(AiRuntimeStateTest.get(a, SandRaider.class, "enemyEntities"),
          AiRuntimeStateTest.get(b, SandRaider.class, "enemyEntities"));
      a.params = new int[] {0, 0, 100, 0, 5, 0, 0, 0};
      a.aiParam0 = 4; a.aiParam2 = 6; a.time = .2f;
      AiRuntimeState.capture(a, 0).restore(b, 0);
      b.update(.1f); assertEquals(4, b.aiParam0);
      b.update(.15f); assertEquals(5, b.aiParam0); assertEquals("CHARGE", b.getState());
      assertEquals(6, b.aiParam2); assertNull(target.casting());
      b.update(b.time + .01f); assertEquals("ATTACK", b.getState()); assertNotNull(target.casting());
      source.world.getMapper(com.riiablo.engine.server.component.Class.class).remove(source.target);
      source.world.process(); a.time = 0f; a.nextAction = 10f;
      a.update(.04f); assertNull(source.casting());
    } finally { MathUtils.random = previous; }
  }

  @Test void vampireCooldownContinuesByDecisionsRatherThanBeingResetOrConsumedDuringRestore() throws Exception {
    AiRuntimeState image;
    try (Fixture f = fixture("Vampire", 6)) {
      Vampire ai = (Vampire) f.ai; ai.aiParam0 = 1; ai.aiParam1 = 19; ai.aiParam2 = 2; ai.time = .2f;
      ai.params = new int[] {0, 100, 20, 100, 2, 0, 0, 0}; image = AiRuntimeState.capture(ai, 0);
    }
    java.util.Random previous = MathUtils.random;
    try (Fixture f = fixture("Vampire", 6)) {
      MathUtils.random = mock(RandomXS128.class); image.restore(f.ai, 0); Vampire ai = (Vampire) f.ai;
      ai.update(.1f); assertEquals(2, ai.aiParam2); assertNull(f.casting());
      ai.update(.15f); assertEquals(1, ai.aiParam2); assertNull(f.casting());
      ai.update(ai.time + .01f);
      assertEquals(11, ai.aiParam2); assertEquals(1, ai.aiParam0); assertEquals(19, ai.aiParam1);
      assertEquals("CAST", ai.getState()); f.assertSkill(1);
    } finally { MathUtils.random = previous; }
  }

  @ParameterizedTest
  @ValueSource(strings = {"FallenShaman", "FetishShaman"})
  void shamanRetainsDestinationCorpseSubscriptionAndOnlyRequestsResurrectionAfterTheDelay(String type) throws Exception {
    AiRuntimeState image; Object oldSubscription;
    try (Fixture f = fixture(type, 10)) {
      AiRuntimeStateTest.put(f.ai, f.ai.getClass(), "time", .2f);
      f.ai.params[0] = 100; f.ai.params[2] = type.equals("FallenShaman") ? 0 : 10;
      f.ai.params[3] = type.equals("FallenShaman") ? 15 : 0;
      f.ai.params[4] = type.equals("FallenShaman") ? 0 : 15;
      image = AiRuntimeState.capture(f.ai, 0);
      oldSubscription = AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "corpseEntities");
    }
    java.util.Random previous = MathUtils.random;
    try (Fixture f = fixture(type, 10)) {
      MathUtils.random = mock(RandomXS128.class);
      int corpse = f.ally(type.equals("FallenShaman") ? "fallen1" : "zombie1", 0f, 12f, 10f);
      f.world.getMapper(Corpse.class).create(corpse).usable = true;
      f.world.getMapper(CofReference.class).create(corpse).mode = Engine.Monster.MODE_DD;
      f.world.process();
      Object subscription = AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "corpseEntities");
      assertNotSame(oldSubscription, subscription); image.restore(f.ai, 0);
      assertSame(subscription, AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "corpseEntities"));
      f.ai.update(.1f); assertNull(f.casting()); assertEquals(0, f.probe.starts);
      f.ai.update(.15f); assertNotNull(f.casting()); assertEquals(corpse, f.casting().targetId);
      assertEquals(Riiablo.files.skills.get(type.equals("FallenShaman")
          ? f.ai.monster.monstats.Skill1 : f.ai.monster.monstats.Skill3).Id, f.casting().skillId);
      assertEquals(1, f.probe.starts);
      assertTrue(f.world.getMapper(Corpse.class).get(corpse).usable, "resurrection is still deferred to its keyframe");
    } finally { MathUtils.random = previous; }
  }

  @Test void leaperDoesNotReissueALeapWhileItsLiveAirborneComponentStillExists() throws Exception {
    AiRuntimeState image;
    try (Fixture f = fixture("SandLeaper", 1)) {
      SandLeaper ai = (SandLeaper) f.ai; ai.stateMachine.changeState(SandLeaper.State.LEAP);
      ai.time = .2f; ai.params[0] = 0; ai.params[1] = 100; image = AiRuntimeState.capture(ai, 0);
    }
    try (Fixture f = fixture("SandLeaper", 1)) {
      Leap live = f.world.getMapper(Leap.class).create(0); image.restore(f.ai, 0);
      f.ai.update(10f); assertEquals("LEAP", f.ai.getState()); assertNull(f.casting());
      assertSame(live, f.world.getMapper(Leap.class).get(0)); assertEquals(0, f.probe.starts);
      f.world.getMapper(Leap.class).remove(0); f.world.process();
      f.ai.update(.04f); assertNotEquals("LEAP", f.ai.getState());
    }
  }

  private static Missiles.Entry row(int id, String key, int range) {
    Missiles.Entry row = new Missiles.Entry(); row.Id = id; row.Missile = key; row.Range = range; return row;
  }
}
