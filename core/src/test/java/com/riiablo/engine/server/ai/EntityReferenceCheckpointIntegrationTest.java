package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.replay.*;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.NativePathIdentity;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real decisions at explicit action boundaries; paths, collision and corpse mode are prebuilt dependencies. */
class EntityReferenceCheckpointIntegrationTest extends RiiabloTest {
  @ParameterizedTest @ValueSource(ints = {0, 1, 2})
  void foodReferencePreservesNullCurrentAndRetiredSemanticsAcrossWorlds(int lifetime) throws Exception {
    Map<Integer, UnitStateRuntimeCheckpoint> image;
    NativePathIdentity.Token sourceToken;
    try (Fixture f = foodFixture(lifetime)) {
      sourceToken = (NativePathIdentity.Token) AiRuntimeStateTest.get(f.ai, Regurgitator.class, "foodIdentity");
      image = UnitStateRuntimeCheckpoint.capture(f.world, new int[] {f.actor, f.target, 2});
      if (lifetime != 0) assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(f.ai, f.actor));
    }
    try (Fixture f = foodFixture(-1)) {
      Object liveAi = f.ai;
      image.get(f.actor).aiDecisionRuntime().validateRestore(f.world, f.actor);
      assertEquals(-1, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "food"));
      UnitStateRuntimeCheckpoint.restore(f.world, image);
      assertSame(liveAi, f.world.getMapper(AIWrapper.class).get(f.actor).ai);
      NativePathIdentity.Token restored = (NativePathIdentity.Token) AiRuntimeStateTest.get(f.ai, Regurgitator.class, "foodIdentity");
      if (lifetime == 0) assertNull(restored);
      else {
        assertNotSame(sourceToken, restored);
        assertEquals(lifetime == 1, restored.isCurrent(f.world, 2));
        if (lifetime == 1) assertSame(f.world.getMapper(NativePathIdentity.class).get(2).token(), restored);
      }
      assertEquals(3, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "phase"));
      Map<Integer, UnitStateRuntimeCheckpoint> after = UnitStateRuntimeCheckpoint.capture(f.world, new int[] {0, 1, 2});
      for (int id : image.keySet()) assertArrayEquals(image.get(id).bytes(), after.get(id).bytes());
      ((Regurgitator) f.ai).think(f.target, 10, false);
      if (lifetime == 2) {
        assertNull(f.casting()); assertEquals(-1, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "food"));
      } else {
        assertNotNull(f.casting()); assertEquals(2, f.casting().targetId);
        assertEquals(Riiablo.files.skills.get("RegurgitatorEat").Id, f.casting().skillId);
      }
    }
  }

  @Test void recycledCorpseIdentityAfterRestoreCannotBeConsumed() throws Exception {
    Map<Integer, UnitStateRuntimeCheckpoint> image;
    try (Fixture f = foodFixture(1)) {
      image = UnitStateRuntimeCheckpoint.capture(f.world, new int[] {0, 1, 2});
    }
    try (Fixture f = foodFixture(-1)) {
      UnitStateRuntimeCheckpoint.restore(f.world, image);
      f.world.getMapper(NativePathIdentity.class).get(2).allocated(f.world, 2);
      ((Regurgitator) f.ai).think(f.target, 10, false);
      assertNull(f.casting()); assertEquals(0, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "phase"));
    }
  }

  @Test void coverageAndMissingCurrentLifetimeFailBeforeAnyHostMutation() throws Exception {
    for (int lifetime : new int[] {0, 1, 2}) {
      Map<Integer, UnitStateRuntimeCheckpoint> image;
      try (Fixture f = foodFixture(lifetime)) {
        image = UnitStateRuntimeCheckpoint.capture(f.world, new int[] {0, 1, 2});
        AiDecisionRuntimeCheckpoint ai = image.get(0).aiDecisionRuntime();
        if (lifetime == 2) ai.validateCoverage(Set.of(0, 1));
        else assertThrows(IllegalArgumentException.class, () -> ai.validateCoverage(Set.of(0, 1)));
      }
      try (Fixture f = foodFixture(-1)) {
        AiRuntimeStateTest.put(f.ai, Regurgitator.class, "phase", 17);
        if (lifetime != 2) {
          Map<Integer, UnitStateRuntimeCheckpoint> incomplete = new TreeMap<>(image); incomplete.remove(2);
          assertThrows(IllegalArgumentException.class, () -> UnitStateRuntimeCheckpoint.restore(f.world, incomplete));
          assertEquals(17, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "phase"));
        }
        if (lifetime == 1) {
          f.world.getMapper(NativePathIdentity.class).remove(2); f.world.process();
          assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.restore(f.world, image));
          assertEquals(17, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "phase"));
          assertEquals(-1, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "food"));
          assertNull(AiRuntimeStateTest.get(f.ai, AI.class, "aiSeed"));
        }
      }
    }
  }

  @Test void referenceWireRejectsUnknownKindsInvalidIdsAndTruncation() throws Exception {
    byte[] bytes;
    try (Fixture f = foodFixture(1)) { bytes = AiRuntimeState.capture(f.world, f.ai, f.actor).bytes(); }
    for (int kind : new int[] {3, 255}) {
      byte[] bad = bytes.clone(); bad[bad.length - 1] = (byte) kind;
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(bad));
    }
    for (int id : new int[] {-1, -2}) {
      byte[] bad = bytes.clone(); java.nio.ByteBuffer.wrap(bad).putInt(bad.length - 5, id);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(bad));
    }
    for (int end = 0; end < bytes.length; end++) {
      byte[] bad = Arrays.copyOf(bytes, end);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(bad));
    }
  }

  @Test void retiredReferenceSurvivesActualDeletionAndIdReuseWithoutCreatingIdentityDuringPreflight() throws Exception {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      int id = world.create();
      NativePathIdentity.Token old = world.getMapper(NativePathIdentity.class).create(id).allocated(world, id).token();
      world.process(); world.delete(id); world.process();
      assertEquals(id, world.create());
      world.process();
      NativeEntityReferenceCheckpoint retired = NativeEntityReferenceCheckpoint.capture(world, id, old);
      retired.validateCoverage(Collections.emptySet()); retired.validateRestore(world);
      assertNull(world.getMapper(NativePathIdentity.class).get(id));
      NativePathIdentity.Token replacement = world.getMapper(NativePathIdentity.class).create(id).allocated(world, id).token();
      NativePathIdentity.Token restored = retired.resolve(world);
      assertNotSame(replacement, restored); assertFalse(restored.isCurrent(world, id));
    } finally { world.dispose(); }
  }

  @Test void currentReferenceRejectsAbsentWorldAndUnallocatedOrDeletedIdsWithoutWritingAi() throws Exception {
    byte[] bytes;
    try (Fixture f = foodFixture(1)) { bytes = AiRuntimeState.capture(f.world, f.ai, f.actor).bytes(); }
    try (Fixture f = foodFixture(-1)) {
      AiRuntimeStateTest.put(f.ai, Regurgitator.class, "phase", 17);
      AiRuntimeState image = AiRuntimeState.fromBytes(bytes);
      assertThrows(IllegalStateException.class, () -> image.restore(f.ai, f.actor));
      for (int id : new int[] {99, Integer.MAX_VALUE}) {
        byte[] bad = bytes.clone(); java.nio.ByteBuffer.wrap(bad).putInt(bad.length - 5, id);
        AiRuntimeState missing = AiRuntimeState.fromBytes(bad);
        assertThrows(IllegalStateException.class, () -> missing.restore(f.world, f.ai, f.actor));
      }
      f.world.delete(2); f.world.process();
      assertThrows(IllegalStateException.class, () -> image.restore(f.world, f.ai, f.actor));
      assertEquals(17, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "phase"));
      assertEquals(-1, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "food"));
      assertNull(AiRuntimeStateTest.get(f.ai, AI.class, "aiSeed"));
    }
  }

  @Test void retiredOutOfCoverageAndUnallocatedIdsRemainInertWithoutQueryingComponents() throws Exception {
    byte[] bytes;
    try (Fixture f = foodFixture(2)) { bytes = AiRuntimeState.capture(f.world, f.ai, f.actor).bytes(); }
    for (int id : new int[] {99, Integer.MAX_VALUE}) {
      try (Fixture f = foodFixture(-1)) {
        byte[] saved = bytes.clone(); java.nio.ByteBuffer.wrap(saved).putInt(saved.length - 5, id);
        AiRuntimeState image = AiRuntimeState.fromBytes(saved);
        image.validateCoverage(Collections.emptySet()); image.restore(f.world, f.ai, f.actor);
        NativePathIdentity.Token token = (NativePathIdentity.Token) AiRuntimeStateTest.get(f.ai, Regurgitator.class, "foodIdentity");
        assertFalse(token.isCurrent(f.world, id));
        ((Regurgitator) f.ai).think(f.target, 10, false);
        assertNull(f.casting()); assertEquals(-1, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "food"));
      }
    }
  }

  private static Fixture foodFixture(int lifetime) throws Exception {
    Fixture f = new Fixture("regurgitator1", 0, 10);
    int corpse = f.ally("zombie1", 0, 11, 10); assertEquals(2, corpse);
    f.world.getMapper(CofReference.class).create(corpse).mode = Engine.Monster.MODE_DD;
    if (lifetime != 0) f.world.getMapper(NativePathIdentity.class).create(corpse).allocated(f.world, corpse);
    f.world.getMapper(AIWrapper.class).create(f.actor).ai = f.ai; f.world.process();
    if (lifetime >= 0) {
      f.ai.params[1] = f.ai.params[4] = 100; f.ai.params[5] = 30;
      ((Regurgitator) f.ai).think(f.target, 10, false); // Select corpse.
      ((Regurgitator) f.ai).think(f.target, 10, false); // Existing corpse -> eating delay.
      assertEquals(3, AiRuntimeStateTest.get(f.ai, Regurgitator.class, "phase"));
      if (lifetime == 2) f.world.getMapper(NativePathIdentity.class).get(corpse).allocated(f.world, corpse);
    }
    return f;
  }

  @Test void bloodRavenRetainsHistoricalAnchorObjectAndReturnsToItInsteadOfNewSpawn() throws Exception {
    AiRuntimeState image; Object sourceAnchor;
    try (Fixture f = new Fixture("bloodraven", 0, 10)) {
      sourceAnchor = AiRuntimeStateTest.get(f.ai, BloodRaven.class, "anchor");
      ((Vector2) sourceAnchor).set(1.25f, -2.5f);
      AiRuntimeStateTest.put(f.ai, BloodRaven.class, "returningToAnchor", true);
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture("bloodraven", 0, 10)) {
      Object anchor = AiRuntimeStateTest.get(f.ai, BloodRaven.class, "anchor");
      image.validateRestore(f.ai, f.actor);
      assertEquals(new Vector2(10, 10), anchor);
      image.restore(f.ai, f.actor);
      assertSame(anchor, AiRuntimeStateTest.get(f.ai, BloodRaven.class, "anchor"));
      assertNotSame(sourceAnchor, anchor); f.ai.update(.04f);
      assertEquals("RETURN", f.ai.getState()); assertNull(f.casting());
      verify(f.paths).findPath(eq(f.actor), eq(new Vector2(1.25f, -2.5f)), eq(true), eq(Engine.INVALID_ENTITY));
      ((Vector2) anchor).x = Float.NaN;
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(f.ai, f.actor));
    }
  }

  @Test void bloodRavenDoesNotRegainExhaustedSpecialBudget() throws Exception {
    AiRuntimeState image;
    try (Fixture f = new Fixture("bloodraven", 0, 10)) {
      AiRuntimeStateTest.put(f.ai, BloodRaven.class, "quickStrikeRollChance", 100);
      AiRuntimeStateTest.put(f.ai, BloodRaven.class, "specialUses", 8);
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture("bloodraven", 0, 10)) {
      image.restore(f.ai, f.actor); f.ai.update(.04f);
      assertEquals("ATTACK", f.ai.getState());
      assertEquals(com.riiablo.skill.SkillCodes.attack, f.casting().skillId);
      assertEquals(8, AiRuntimeStateTest.get(f.ai, BloodRaven.class, "specialUses"));
    }
  }

  @Test void hiddenFrogDecisionBudgetResumesWithPrebuiltCollisionAndActionBoundary() throws Exception {
    AiRuntimeState image;
    try (Fixture f = new Fixture("frogdemon1", 0, 18)) {
      f.world.getMapper(CofReference.class).create(f.actor).set("FD", Engine.Monster.MODE_NU);
      // Held S1/Sequence is outside this batch. Save at an explicit completed-action boundary.
      f.ai.factory = mock(EntityFactory.class); f.ai.update(.04f); f.finishAction();
      AiRuntimeStateTest.put(f.ai, FrogDemon.class, "hiddenDecisions", 65);
      image = AiRuntimeState.capture(f.ai, f.actor);
    }
    try (Fixture f = new Fixture("frogdemon1", 0, 18)) {
      f.world.getMapper(CofReference.class).create(f.actor).set("FD", Engine.Monster.MODE_NU);
      EntityFactory factory = mock(EntityFactory.class);
      when(factory.restoreFrogPresence(f.actor)).thenReturn(true); f.ai.factory = factory;
      image.restore(f.ai, f.actor);
      f.ai.update(13 * com.riiablo.codec.Animation.FRAME_DURATION);
      verify(factory).restoreFrogPresence(f.actor);
      assertNotNull(f.casting());
      assertEquals(Riiablo.files.skills.get("Emerge").Id, f.casting().skillId);
    }
  }
}
