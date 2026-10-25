package com.riiablo.engine.replay;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetRuntimeCheckpointTest {
  @Test void smallAiComponentSchemasRequireAnExplicitCheckpointDecisionForNewFields() {
    assertFields(NativeAiState.class, "previousMode");
    assertFields(NativeAiTargetOverride.class, "mode", "targetId", "sourceEntityId",
        "sourceSkillId", "remainingFrames", "rngState");
  }

  private static void assertFields(java.lang.Class<?> type, String... names) {
    Set<String> actual = new HashSet<>();
    for (Field field : type.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) actual.add(field.getName());
    }
    assertEquals(new HashSet<>(Arrays.asList(names)), actual);
  }

  @Test void worldLocalClockRawMembershipAndSmallAiOverlaysRoundTrip() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture(); Fixture unrelated = new Fixture()) {
      for (Fixture f : new Fixture[] {source, target, unrelated}) {
        f.player(); f.world.create(); f.world.process();
      }
      NativeTargetNode node = source.node(1);
      assertTrue(node.insert(NativeTargetNode.SUMMON, 0));
      source.world.getMapper(NativeAiState.class).create(1).previousMode = 13;
      NativeAiTargetOverride ai = source.world.getMapper(NativeAiTargetOverride.class).create(1);
      ai.mode = NativeAiTargetOverride.ATTRACT; ai.targetId = 0; ai.sourceEntityId = 1;
      ai.sourceSkillId = 86; ai.remainingFrames = 17; ai.rngState = -87432;
      target.node(0).insert(NativeTargetNode.CURSE, -1);
      target.world.getMapper(NativeAiState.class).create(0);
      target.world.getMapper(NativeAiTargetOverride.class).create(0).setConfuse(0, 1, 2, 3);
      target.world.process();
      for (int i = 0; i < 20; i++) target.slots.nextOrder();
      source.world.process();
      TargetRuntimeCheckpoint saved = TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1});
      saved.restore(target.world);
      assertFalse(target.world.getMapper(NativeTargetNode.class).has(0));
      assertFalse(target.world.getMapper(NativeAiState.class).has(0));
      assertFalse(target.world.getMapper(NativeAiTargetOverride.class).has(0));
      assertArrayEquals(saved.bytes(), TargetRuntimeCheckpoint.capture(target.world, new int[] {0, 1}).bytes());
      assertEquals(13, target.world.getMapper(NativeAiState.class).get(1).previousMode);
      assertEquals(-87432, target.world.getMapper(NativeAiTargetOverride.class).get(1).rngState);
      assertEquals(source.slots.nextOrder(), target.slots.nextOrder());
      assertEquals(2, unrelated.slots.nextOrder(), "restoring another World must not rewind this clock");
      byte[] owned = saved.bytes(); owned[0] ^= 1;
      assertNotEquals(owned[0], saved.bytes()[0]);
    }
  }

  @Test void attemptsIncludeFullAndDeadAllocationsAndNeverBecomeAWaitingQueue() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      int[] ids = new int[10];
      for (Fixture f : new Fixture[] {source, target}) {
        for (int i = 0; i < ids.length; i++) ids[i] = f.player();
        f.world.getMapper(CofReference.class).create(9).mode = Engine.Player.MODE_DD;
      }
      for (int i = 7; i >= 0; i--) source.slots.registerPlayer(i);
      source.slots.registerPlayer(8); source.slots.registerPlayer(9);
      source.world.process(); target.world.process();
      TargetRuntimeCheckpoint saved = TargetRuntimeCheckpoint.capture(source.world, ids);
      saved.restore(target.world);
      assertEquals(7, target.slots.playerAtSlot(0));
      assertEquals(10, target.slots.captureCheckpoint().attempts().length);
      for (Fixture f : new Fixture[] {source, target}) {
        f.world.getMapper(Player.class).remove(7); f.world.process();
        f.world.getMapper(CofReference.class).get(9).mode = Engine.Player.MODE_NU;
        f.world.process();
        assertEquals(11, f.slots.slotOf(8)); assertEquals(11, f.slots.slotOf(9));
        assertEquals(-1, f.slots.playerAtSlot(0));
      }
      assertArrayEquals(TargetRuntimeCheckpoint.capture(source.world, ids).bytes(),
          TargetRuntimeCheckpoint.capture(target.world, ids).bytes());
    }
  }

  @Test void captureDoesNotObserveOrRepairStaleRawNodesAndRecycledHeadDoesNotRejoin() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      for (Fixture f : new Fixture[] {source, target}) { f.player(); f.world.create(); f.world.process(); }
      NativeTargetNode node = source.node(1);
      long staleOrder = source.slots.nextOrder();
      source.world.getMapper(Player.class).remove(0); source.world.process();
      source.world.getMapper(Player.class).create(0); source.world.process();
      node.restoreCheckpoint(NativeTargetNode.SUMMON, 0, staleOrder, source.slots);
      source.world.process();
      TargetRuntimeCheckpoint saved = TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1});
      assertEquals(staleOrder, node.checkpoint()[2]);
      saved.restore(target.world);
      NativeTargetNode restored = target.world.getMapper(NativeTargetNode.class).get(1);
      assertEquals(staleOrder, restored.checkpoint()[2]);
      assertEquals(0, restored.order()); assertEquals(0, node.order());
      assertTrue(restored.insert(NativeTargetNode.SUMMON, 0));
      assertTrue(node.insert(NativeTargetNode.SUMMON, 0));
      assertEquals(node.order(), restored.order());
    }
  }

  @Test void stateAndPetShareTheRestoredNodeAndRefreshDoesNotReinsert() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      for (Fixture f : new Fixture[] {source, target}) {
        f.player(); f.world.create(); f.world.process();
        f.world.getMapper(UnitStates.class).create(1).init(f.world, 1);
        f.world.getMapper(SummonedPet.class).create(1).bindNativeTargetNode(f.node(1))
            .set(0, "test", 1, 1, false, 0);
      }
      StateList states = source.world.getMapper(UnitStates.class).get(1).stateList;
      states.addState(StateId.ATTRACT, 20);
      for (Fixture f : new Fixture[] {source, target}) f.world.process();
      TargetRuntimeCheckpoint saved = TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1});
      UnitStateRuntimeCheckpoint.restore(target.world,
          UnitStateRuntimeCheckpoint.capture(source.world, new int[] {0, 1}, true));
      saved.restore(target.world);
      StateList restored = target.world.getMapper(UnitStates.class).get(1).stateList;
      NativeTargetNode shared = target.world.getMapper(NativeTargetNode.class).get(1);
      assertSame(shared, restored.checkpointTargetNode());
      assertSame(shared, target.world.getMapper(SummonedPet.class).get(1).checkpointTargetNode());
      assertEquals(NativeTargetNode.SUMMON, shared.checkpoint()[0]);
      restored.detachNativeTargetNode();
      restored.addState(StateId.ATTRACT, 25);
      assertEquals(0, shared.order(), "refreshing a retained layer is not a TARGETS insertion");
      shared.insert(NativeTargetNode.SUMMON, 0);
      restored.removeState(StateId.ATTRACT);
      assertEquals(0, target.world.getMapper(SummonedPet.class).get(1).nativeTargetOrder());
    }
  }

  @Test void malformedImagesAndMissingLaterHostsFailBeforeMutation() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.player(); source.world.create(); source.world.process();
      source.node(1).insert(NativeTargetNode.CURSE, -1); source.world.process();
      target.player(); target.world.process();
      TargetRuntimeCheckpoint saved = TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1});
      long before = target.slots.captureCheckpoint().sequence;
      assertThrows(IllegalStateException.class, () -> saved.restore(target.world));
      assertEquals(before, target.slots.captureCheckpoint().sequence);
      byte[] bytes = saved.bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> TargetRuntimeCheckpoint.fromBytes(truncated));
      }
      assertThrows(IllegalArgumentException.class, () -> TargetRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      byte[] bad = bytes.clone(); bad[3] = 2; final byte[] versionImage = bad;
      assertThrows(IllegalArgumentException.class, () -> TargetRuntimeCheckpoint.fromBytes(versionImage));
      // version + sequence + eight slots + attempts count/one ID + entity count/ID.
      int firstBoolean = 4 + 8 + 8 * 12 + 4 + 4 + 4 + 4;
      bad = bytes.clone(); bad[firstBoolean] = 2; final byte[] booleanImage = bad;
      assertThrows(IllegalArgumentException.class, () -> TargetRuntimeCheckpoint.fromBytes(booleanImage));
      bad = bytes.clone(); ByteBuffer.wrap(bad).putInt(firstBoolean + 3, 0); final byte[] duplicate = bad;
      assertThrows(IllegalArgumentException.class, () -> TargetRuntimeCheckpoint.fromBytes(duplicate));
      source.node(1).restoreCheckpoint(NativeTargetNode.CURSE, -1,
          source.slots.registrationOrder(0), source.slots);
      assertThrows(IllegalArgumentException.class, () -> TargetRuntimeCheckpoint.capture(source.world, new int[] {0, 1}));
    }
  }

  @Test void detachedAndUncoveredOwnersAreNeverSilentlyDowngraded() {
    try (Fixture f = new Fixture()) {
      f.player(); int id = f.world.create(); f.world.process();
      f.world.getMapper(SummonedPet.class).create(id).set(0, "test", 1, 1, false, 0);
      assertThrows(IllegalStateException.class, () -> TargetRuntimeCheckpoint.capture(f.world, new int[] {0, id}));
      assertThrows(IllegalStateException.class, () -> TargetRuntimeCheckpoint.capture(f.world, new int[] {id}));
      f.world.getMapper(SummonedPet.class).get(id).bindNativeTargetNode(f.node(id));
      f.node(id).usePlayerSlots(null);
      assertThrows(IllegalStateException.class, () -> TargetRuntimeCheckpoint.capture(f.world, new int[] {0, id}));
      f.node(id);
      f.world.getMapper(UnitStates.class).create(id).init(id).stateList.addState(StateId.ATTRACT, 3);
      assertThrows(IllegalStateException.class, () -> TargetRuntimeCheckpoint.capture(f.world, new int[] {0, id}));
    }
  }

  @Test void exhaustedClockNeverPublishesAHalfInsertedHeadOrNode() {
    try (Fixture f = new Fixture()) {
      f.player(); f.world.create(); f.world.process();
      int[] heads = new int[8]; Arrays.fill(heads, -1);
      f.slots.restoreCheckpoint(new NativePlayerTargetSlots.Checkpoint(Long.MAX_VALUE, heads, new long[8], new int[] {0}));
      NativeTargetNode node = f.node(1);
      assertThrows(IllegalStateException.class, () -> node.insert(NativeTargetNode.CURSE, -1));
      assertArrayEquals(new long[] {NativeTargetNode.NONE, -1, 0}, node.checkpoint());
      assertThrows(IllegalStateException.class, () -> f.slots.registerPlayer(0));
      assertEquals(-1, f.slots.playerAtSlot(0));
      assertEquals(Long.MAX_VALUE, f.slots.captureCheckpoint().sequence);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final NativePlayerTargetSlots slots = new NativePlayerTargetSlots();
    final World world = new World(new WorldConfigurationBuilder().with(slots).build());
    int player() { int id = world.create(); world.getMapper(Player.class).create(id); return id; }
    NativeTargetNode node(int id) {
      NativeTargetNode node = world.getMapper(NativeTargetNode.class).create(id); node.usePlayerSlots(slots); return node;
    }
    @Override public void close() { world.dispose(); }
  }
}
