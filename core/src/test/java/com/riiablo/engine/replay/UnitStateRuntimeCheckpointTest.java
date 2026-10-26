package com.riiablo.engine.replay;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.riiablo.attributes.*;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class UnitStateRuntimeCheckpointTest {
  @Test void legacyV1EmptyHolderAndAbsentHolderStillRestore() {
    try (TestWorld source = new TestWorld(); TestWorld target = new TestWorld()) {
      source.create(); source.create(); target.create(); target.create();
      source.getMapper(UnitStates.class).create(0).init(source, 0);
      Map<Integer, UnitStateRuntimeCheckpoint> current = UnitStateRuntimeCheckpoint.capture(source, new int[] {0, 1});
      byte[] bytes = current.get(0).bytes();
      int actionTail = 4 + current.get(0).actionRuntime().bytes().length;
      int pathTail = 4 + current.get(0).pathRuntime().bytes().length;
      byte[] legacy = new byte[bytes.length - 12 - actionTail - pathTail];
      System.arraycopy(bytes, 0, legacy, 0, 5);
      System.arraycopy(bytes, 6, legacy, 5, bytes.length - 17 - actionTail - pathTail);
      legacy[3] = 1; // v1 has neither requiresTargets nor Monster overlay presence.
      Map<Integer, UnitStateRuntimeCheckpoint> records = new TreeMap<>();
      records.put(0, UnitStateRuntimeCheckpoint.fromBytes(legacy));
      records.put(1, UnitStateRuntimeCheckpoint.fromBytes(new byte[] {0, 0, 0, 1, 0}));
      UnitStateRuntimeCheckpoint.restore(target, records);
      assertEquals(0, target.getMapper(UnitStates.class).get(0).stateList.size());
      assertFalse(target.getMapper(UnitStates.class).has(1));
    }
  }

  @Test void specialStatesWithoutHooksDoNotAcquireAnInventedLifecycle() {
    try (TestWorld source = new TestWorld(); TestWorld target = new TestWorld()) {
      source.create(); target.create();
      StateList states = source.getMapper(UnitStates.class).create(0).init(source, 0).stateList;
      states.addState(StateId.CONVERSION, 5); states.addState(StateId.PREGNANT, 4);
      states.detachNativeTargetNode();
      UnitStateRuntimeCheckpoint.restore(target, UnitStateRuntimeCheckpoint.capture(source, new int[] {0}));
      StateList restored = target.getMapper(UnitStates.class).get(0).stateList;
      assertNull(restored.getState(StateId.CONVERSION).removalCallback);
      assertNull(restored.getState(StateId.PREGNANT).removalCallback);
    }
  }
  @Test void schemaAndEveryScalarIncludePrivateCompatibilityProjectionBaselines() throws Exception {
    Set<String> expected = new HashSet<>(Arrays.asList(
      "stateId", "level", "duration", "initialDuration",
      "sourceEntityId", "skillId", "curseGroup", "curseStrength",
      "damageModifier", "defenseModifier", "attackModifier", "velocityModifier",
      "runtimeValue", "animationRateModifier", "lifeLeechModifier", "stunLength",
      "fireResistModifier", "coldResistModifier", "lightResistModifier", "poisonResistModifier",
      "magicResistModifier", "skillModifier", "experienceModifier", "manaRecoveryModifier",
      "maxStaminaModifier", "maxLifeModifier", "maxManaModifier", "staminaRecoveryModifier",
      "masteryAttackRatingModifier", "masteryDamageModifier", "masteryCriticalChance", "damagePerFrame",
      "damageType", "poisonMinDamage", "poisonMaxDamage", "poisonLengthOverride",
      "periodicDelayFrames", "periodicCountdownFrames", "thunderStormTargetId", "conversionOriginalLevel",
      "conversionOriginalMaxHpEncoded", "projectedDamageModifier", "projectedDefenseModifier", "projectedAttackModifier",
      "projectedVelocityModifier", "projectedAnimationRateModifier", "projectedFireResistModifier", "projectedColdResistModifier",
      "projectedLightResistModifier", "projectedPoisonResistModifier", "projectedMagicResistModifier", "basicStatList",
      "trailPositionSet", "throwingMastery", "isNew", "needsSync",
      "expired", "trailX", "trailY", "exactDamagePerFrame",
      "masteryItemType", "statContributions", "removalCallback"));
    Set<String> actual = new HashSet<>();
    try (TestWorld source = new TestWorld(); TestWorld target = new TestWorld()) {
      int id = source.create(); assertEquals(id, target.create());
      UnitStates holder = source.getMapper(UnitStates.class).create(id).init(source, id);
      UnitState state = holder.stateList.addState(StateId.POISON, 17, 2, -1);
      int value = 100;
      for (Field field : UnitState.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        actual.add(field.getName()); field.setAccessible(true);
        if (field.getType() == int.class) field.setInt(state, value++);
        else if (field.getType() == float.class) field.setFloat(state, value++ + 0.00390625f);
        else if (field.getType() == boolean.class) field.setBoolean(state, true);
        else if (field.getType() == String.class) field.set(state, "runtime-中文");
      }
      assertEquals(expected, actual, "new fields require an explicit checkpoint decision");
      state.stateId = StateId.POISON;
      state.setStatContribution(Stat.firemindam, 4, NativeStatResolver.Operation.ADD, 321);
      state.setStatContribution(Stat.firemaxdam, 3, NativeStatResolver.Operation.PERCENT, 654);
      holder.stateList.getFlags().toggleGfx(StateId.POISON, true);
      holder.stateList.getFlags().set(StateId.COLD); // Flags need not be reconstructed from layers.
      for (Field field : UnitStates.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        if (field.getType() == int.class) field.setInt(holder, value++);
        else if (field.getType() == long.class) field.setLong(holder, 10000000001L);
        else if (field.getType() == boolean.class) field.setBoolean(holder, true);
      }
      Map<Integer, UnitStateRuntimeCheckpoint> checkpoint = UnitStateRuntimeCheckpoint.capture(source, new int[] {id});
      byte[] bytes = checkpoint.get(id).bytes();
      assertArrayEquals(bytes, UnitStateRuntimeCheckpoint.fromBytes(bytes).bytes());
      UnitStateRuntimeCheckpoint.restore(target, checkpoint);
      UnitStates restoredHolder = target.getMapper(UnitStates.class).get(id);
      UnitState restored = restoredHolder.stateList.getStates().first();
      for (Field field : UnitState.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
        field.setAccessible(true);
        if (field.getType().isPrimitive() || field.getType() == String.class) {
          assertEquals(field.get(state), field.get(restored), field.getName());
        }
      }
      assertEquals(2, restored.getStatContributions().size());
      assertEquals(NativeStatResolver.Operation.PERCENT, restored.getStatContributions().get(1).operation);
      assertTrue(restoredHolder.stateList.getFlags().checkGfx(StateId.POISON));
      assertTrue(restoredHolder.stateList.hasState(StateId.COLD));
      assertEquals(10000000001L, restoredHolder.observedAggregateRevision);
      for (Field field : UnitStates.class.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic() && field.getType().isPrimitive()) {
          assertEquals(field.get(holder), field.get(restoredHolder), field.getName());
        }
      }
      assertArrayEquals(bytes, UnitStateRuntimeCheckpoint.capture(target, new int[] {id}).get(id).bytes());
      bytes[0] ^= 1; assertNotEquals(bytes[0], checkpoint.get(id).bytes()[0]);
    }
  }

  @Test void anonymousDuplicatesOrderedLayersAbsenceAndNullListsSurviveWithoutRemovalCallbacks() {
    try (TestWorld source = new TestWorld(); TestWorld target = new TestWorld()) {
      for (int i = 0; i < 3; i++) { source.create(); target.create(); }
      StateList list = source.getMapper(UnitStates.class).create(0).init(source, 0).stateList;
      list.addAnonymousStatList(0).runtimeValue = 1;
      list.addStateLayer(StateId.COLD, 8, 1, 0, 9).runtimeValue = 2;
      list.addAnonymousStatList(0).runtimeValue = 3;
      list.addStateLayer(StateId.COLD, 3, 2, 7, 10).runtimeValue = 4;
      source.getMapper(UnitStates.class).create(2); // Explicit null list.
      UnitState stale = target.getMapper(UnitStates.class).create(0).init(target, 0)
          .stateList.addState(StateId.COLD, 7);
      int[] calls = {0}; stale.removalCallback = (states, removed) -> calls[0]++;
      target.getMapper(UnitStates.class).create(1).init(target, 1);
      target.getMapper(UnitStates.class).create(2).init(target, 2);
      Map<Integer, UnitStateRuntimeCheckpoint> saved = UnitStateRuntimeCheckpoint.capture(source, new int[] {0, 1, 2});
      UnitStateRuntimeCheckpoint.restore(target, saved);
      assertEquals(0, calls[0]); assertFalse(target.getMapper(UnitStates.class).has(1));
      assertNull(target.getMapper(UnitStates.class).get(2).stateList);
      StateList restored = target.getMapper(UnitStates.class).get(0).stateList;
      assertEquals(4, restored.size());
      for (int i = 0; i < 4; i++) assertEquals(i + 1, restored.getStates().get(i).runtimeValue);
      for (int i = 0; i < 3; i++) { list.update(); restored.update(); }
      assertEquals(3, restored.size()); assertEquals(list.size(), restored.size());
    }
  }

  @Test void rejectsUnknownHooksTargetMembershipAndMalformedBytesBeforeMutation() {
    try (TestWorld world = new TestWorld()) {
      int id = world.create();
      UnitStates h = world.getMapper(UnitStates.class).create(id).init(world, id);
      UnitState state = h.stateList.addState(StateId.COLD, 3);
      state.removalCallback = (states, removed) -> {};
      assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.capture(world, new int[] {id}));
      state.removalCallback = null;
      world.getMapper(NativeTargetNode.class).get(id).insert(NativeTargetNode.CURSE, -1);
      assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.capture(world, new int[] {id}));
      world.getMapper(NativeTargetNode.class).get(id).detach();
      byte[] bytes = UnitStateRuntimeCheckpoint.capture(world, new int[] {id}).get(id).bytes();
      for (int i = 0; i < bytes.length; i++) {
        final byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> UnitStateRuntimeCheckpoint.fromBytes(truncated));
      }
      byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);
      assertThrows(IllegalArgumentException.class, () -> UnitStateRuntimeCheckpoint.fromBytes(trailing));
      bytes[4] = 2;
      assertThrows(IllegalArgumentException.class, () -> UnitStateRuntimeCheckpoint.fromBytes(bytes));
      state.exactDamagePerFrame = Float.NaN;
      assertThrows(IllegalArgumentException.class, () -> UnitStateRuntimeCheckpoint.capture(world, new int[] {id}));
    }
  }

  private static final class TestWorld extends World implements AutoCloseable {
    @Override public void close() { dispose(); }
  }

  @Test void missingLaterHostIsValidatedBeforeEarlierStateIsChanged() {
    try (TestWorld source = new TestWorld(); TestWorld target = new TestWorld()) {
      source.create(); source.create(); target.create();
      source.getMapper(UnitStates.class).create(0).init(source, 0).stateList.addState(StateId.COLD, 4);
      source.getMapper(UnitStates.class).create(1).init(source, 1).stateList.addState(StateId.POISON, 7);
      UnitState original = target.getMapper(UnitStates.class).create(0).init(target, 0)
          .stateList.addState(StateId.COLD, 99);
      Map<Integer, UnitStateRuntimeCheckpoint> records = UnitStateRuntimeCheckpoint.capture(source, new int[] {0, 1});
      assertThrows(RuntimeException.class, () -> UnitStateRuntimeCheckpoint.restore(target, records));
      assertSame(original, target.getMapper(UnitStates.class).get(0).stateList.getState(StateId.COLD));
      assertEquals(99, original.duration);
    }
  }
}
