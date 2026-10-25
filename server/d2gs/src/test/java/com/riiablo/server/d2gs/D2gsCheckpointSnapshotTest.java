package com.riiablo.server.d2gs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.AuthoritativeSimulation;
import com.riiablo.engine.AuthoritativeSessionRegistry;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.EntitySync;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** Verifies the D2GS checkpoint adapter without loading native maps or MPQs. */
public class D2gsCheckpointSnapshotTest {
  @Test public void v6EnvelopeRetainsNonemptyV1UnitStateRecordsWithoutInventingTargets() {
    World source = worldWithEntities(1);
    try {
      D2gsCheckpointSnapshot original = D2gsCheckpointSnapshot.capture(source, new FakeSerializer(), 9);
      D2gsCheckpointSnapshot subset = D2gsCheckpointSnapshot.fromPayloads(9, original.payloads())
          .withUnitStateRuntime(java.util.Collections.singletonMap(0,
              com.riiablo.engine.replay.UnitStateRuntimeCheckpoint.fromBytes(new byte[] {0, 0, 0, 1, 0})));
      byte[] current = subset.toBytes();
      int targetPresence = current.length - 1 - 4 - 4
          - subset.summary().canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      assertEquals(0, current[targetPresence]);
      byte[] legacy = new byte[current.length - 1];
      System.arraycopy(current, 0, legacy, 0, targetPresence);
      System.arraycopy(current, targetPresence + 1, legacy, targetPresence, current.length - targetPresence - 1);
      java.nio.ByteBuffer.wrap(legacy).putInt(4, 6);
      D2gsCheckpointSnapshot decoded = D2gsCheckpointSnapshot.fromBytes(legacy);
      assertEquals(subset.summary().canonical(), decoded.summary().canonical());
      org.junit.Assert.assertNull(decoded.targetRuntime());
      org.junit.Assert.assertArrayEquals(new byte[] {0, 0, 0, 1, 0}, decoded.unitStateRuntime().get(0).bytes());
    } finally { source.dispose(); }
  }

  @Test public void allocatorOrderChangesDigestAndTamperedWitnessOrEcsDowngradeIsRejected() {
    D2gsCheckpointSnapshot original = D2gsCheckpointSnapshot.fromPayloads(9, java.util.Collections.emptyMap())
        .withAllocatorState(com.artemis.EntityAllocatorCheckpoint.of(3, new int[] {2, 0, 1}));
    D2gsCheckpointSnapshot changed = original.withAllocatorState(
        com.artemis.EntityAllocatorCheckpoint.of(3, new int[] {0, 2, 1}));
    assertFalse(original.summary().canonical().equals(changed.summary().canonical()));
    assertEquals(original.payloads().keySet(), changed.payloads().keySet());
    try { original.toEcsBytes(); org.junit.Assert.fail("allocator metadata discarded"); }
    catch (IllegalStateException expected) {}
    byte[] encoded = original.toBytes(), state = original.allocatorState().bytes();
    int offset = -1;
    for (int i = 0; i <= encoded.length - state.length; i++) {
      if (java.util.Arrays.equals(state, java.util.Arrays.copyOfRange(encoded, i, i + state.length))) {
        offset = i; break;
      }
    }
    assertTrue(offset >= 0);
    System.arraycopy(changed.allocatorState().bytes(), 0, encoded, offset, state.length);
    assertIllegalArgument(() -> D2gsCheckpointSnapshot.fromBytes(encoded));
    World live = worldWithEntities(1);
    try {
      D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(live, new FakeSerializer(), 1);
      assertIllegalArgument(() -> checkpoint.withAllocatorState(com.artemis.EntityAllocatorCheckpoint.of(1, new int[] {0})));
    } finally { live.dispose(); }
  }

  @Test public void allocatorTransactionRollsBackDeletionFailureAndNextAllocationWithoutTicking() {
    World source = worldWithEntities(2), target = worldWithEntities(3);
    try {
      FakeSerializer serializer = new FakeSerializer();
      for (int id = 0; id < 3; id++) serializer.states.put(id, 100 + id);
      D2gsCheckpointSnapshot archive = D2gsCheckpointSnapshot.capture(source, serializer, 7)
          .withAllocatorState(com.artemis.EntityAllocatorCheckpoint.capture(source));
      byte[] before = com.artemis.EntityAllocatorCheckpoint.capture(target).bytes();
      boolean[] fail = {true};
      D2gsCheckpointSnapshot.EntityLifecycle lifecycle = new D2gsCheckpointSnapshot.EntityLifecycle() {
        @Override public int createEntity(World world, int expectedId, java.nio.ByteBuffer bytes) {
          int id = world.create(); world.getMapper(Networked.class).create(id); return id;
        }
        @Override public boolean deleteEntity(World world, int id) {
          world.delete(id);
          if (fail[0]) { fail[0] = false; return false; }
          return true;
        }
      };
      D2gsCheckpointSnapshot.RestoreResult result = archive.restoreExisting(target, serializer, lifecycle);
      assertFalse(result.success()); assertTrue(result.rolledBack);
      org.junit.Assert.assertArrayEquals(before, com.artemis.EntityAllocatorCheckpoint.capture(target).bytes());
      org.junit.Assert.assertArrayEquals(new int[] {0, 1, 2}, networkedIds(target));
      assertEquals(3, target.create());
    } finally { source.dispose(); target.dispose(); }
  }

  @Test public void occupiedLocalIdAndPendingDeletionFailBeforeAnyDeserialization() {
    World source = worldWithEntities(2);
    World target = worldWithEntities(0);
    try {
      FakeSerializer serializer = new FakeSerializer();
      serializer.states.put(0, 10);
      serializer.states.put(1, 20);
      D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(source, serializer, 42L);
      assertEquals(0, target.create()); // unrelated non-Networked entity
      target.process();
      D2gsCheckpointSnapshot.RestoreResult result = checkpoint.restoreExisting(target, serializer,
          new FakeLifecycle());
      assertFalse(result.success());
      assertEquals(java.util.Arrays.asList(0), result.failedEntities);
      assertTrue(result.createdEntities.isEmpty());
      assertFalse(target.getMapper(Networked.class).has(0));
      assertFalse(D2gsCheckpointSnapshot.isActive(target, 1));

      target.getMapper(Networked.class).create(0);
      target.process();
      target.delete(0);
      result = checkpoint.restoreExisting(target, serializer, new FakeLifecycle());
      assertFalse(result.success());
      assertEquals(java.util.Arrays.asList(0), result.failedEntities);
      assertTrue(result.createdEntities.isEmpty());
      assertFalse(D2gsCheckpointSnapshot.isActive(target, 1));
    } finally { source.dispose(); target.dispose(); }
  }

  @Test public void lifecycleAllocatesNeverBeforeAssignedIdsInAnEmptyWorld() {
    World source = worldWithEntities(2);
    World target = worldWithEntities(0);
    try {
      FakeSerializer serializer = new FakeSerializer();
      serializer.states.put(0, 10);
      serializer.states.put(1, 20);
      D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(source, serializer, 42L);
      assertFalse(D2gsCheckpointSnapshot.isActive(target, 0));
      assertFalse(D2gsCheckpointSnapshot.isActive(target, Integer.MAX_VALUE));
      D2gsCheckpointSnapshot.RestoreResult result = checkpoint.restoreExisting(
          target, serializer, new FakeLifecycle());
      target.process();
      assertTrue(result.success());
      assertEquals(java.util.Arrays.asList(0, 1), result.createdEntities);
      assertEquals(checkpoint.summary().canonical(),
          D2gsCheckpointSnapshot.capture(target, serializer, 42L).summary().canonical());
    } finally {
      source.dispose();
      target.dispose();
    }
  }

  @Test public void captureRestoreCaptureKeepsSummaryStable() {
    World world = worldWithEntities(2);
    FakeSerializer serializer = new FakeSerializer();
    for (int id : networkedIds(world)) serializer.states.put(id, id + 10);

    D2gsCheckpointSnapshot before = D2gsCheckpointSnapshot.capture(world, serializer, 42L);
    for (int id : networkedIds(world)) serializer.states.put(id, -id);

    D2gsCheckpointSnapshot.RestoreResult result = before.restoreExisting(world, serializer);
    D2gsCheckpointSnapshot after = D2gsCheckpointSnapshot.capture(world, serializer, 42L);

    assertTrue(result.success());
    assertEquals(2, result.appliedEntities);
    assertFalse(result.rolledBack);
    assertEquals(before.summary().canonical(), after.summary().canonical());
  }

  @Test public void portableEcsEnvelopeRestoresIntoAnotherWorldInstance() {
    World source = worldWithEntities(2);
    FakeSerializer sourceSerializer = new FakeSerializer();
    int[] ids = networkedIds(source);
    for (int id : ids) sourceSerializer.states.put(id, id + 40);
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(
        source, sourceSerializer, 55L).withUnitStateRuntime(null);

    byte[] encoded = checkpoint.toEcsBytes();
    D2gsCheckpointSnapshot transferred = D2gsCheckpointSnapshot.fromEcsBytes(encoded);
    World resumed = worldWithEntities(2);
    FakeSerializer resumedSerializer = new FakeSerializer();
    for (int id : networkedIds(resumed)) resumedSerializer.states.put(id, 900);

    D2gsCheckpointSnapshot.RestoreResult result = transferred.restoreExisting(
        resumed, resumedSerializer);
    D2gsCheckpointSnapshot restored = D2gsCheckpointSnapshot.capture(
        resumed, resumedSerializer, 55L).withUnitStateRuntime(null);
    assertTrue(result.success());
    assertEquals(ids.length, result.appliedEntities);
    assertEquals(checkpoint.summary().canonical(), transferred.summary().canonical());
    assertEquals(checkpoint.summary().canonical(), restored.summary().canonical());
  }

  @Test public void portableEcsEnvelopeRejectsTamperingAndTrailingBytes() {
    World world = worldWithEntities(1);
    FakeSerializer serializer = new FakeSerializer();
    int id = networkedIds(world)[0];
    serializer.states.put(id, 77);
    byte[] encoded = D2gsCheckpointSnapshot.capture(world, serializer, 8L)
        .withUnitStateRuntime(null).toEcsBytes();

    byte[] tampered = java.util.Arrays.copyOf(encoded, encoded.length);
    tampered[tampered.length - 1] ^= 1;
    assertIllegalArgument(() -> D2gsCheckpointSnapshot.fromEcsBytes(tampered));

    byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
    assertIllegalArgument(() -> D2gsCheckpointSnapshot.fromEcsBytes(trailing));
  }

  @Test public void completeEnvelopePreservesRngAndSessionAuthorityAcrossProcesses() {
    World world = worldWithEntities(1);
    FakeSerializer serializer = new FakeSerializer();
    int id = networkedIds(world)[0];
    serializer.states.put(id, 12);
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    assertTrue(sessions.connect(3, "hero", id, 10L).accepted);
    java.util.List<SimulationCheckpoint.RngState> rng = java.util.Collections.singletonList(
        new SimulationCheckpoint.RngState("combat.hit", id, 123, 9L));
    java.util.List<SimulationCheckpoint.NativeUnitRngState> nativeRng =
        java.util.Collections.singletonList(
            new SimulationCheckpoint.NativeUnitRngState("unit.hit", id, 7, 11));
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 10L,
        rng, nativeRng, null, sessions.snapshot());

    D2gsCheckpointSnapshot transferred = D2gsCheckpointSnapshot.fromBytes(checkpoint.toBytes());
    assertEquals(checkpoint.summary().canonical(), transferred.summary().canonical());
    assertEquals(checkpoint.sessionSnapshot().canonical(), transferred.sessionSnapshot().canonical());
    assertEquals(checkpoint.summary().rngStates(), transferred.summary().rngStates());
    assertEquals(checkpoint.summary().nativeUnitRngStates(),
        transferred.summary().nativeUnitRngStates());
  }

  private static void assertIllegalArgument(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new AssertionError("expected IllegalArgumentException");
  }

  @Test public void completeEnvelopeDistinguishesAbsentAndEmptyRegistries() {
    D2gsCheckpointSnapshot empty = D2gsCheckpointSnapshot.fromPayloads(0L,
        java.util.Collections.emptyMap(), null, null,
        com.riiablo.save.D2SCheckpoint.of(java.util.Collections.emptyMap()),
        new AuthoritativeSessionRegistry().snapshot());
    D2gsCheckpointSnapshot decoded = D2gsCheckpointSnapshot.fromBytes(empty.toBytes());
    assertEquals(empty.summary().canonical(), decoded.summary().canonical());
    assertEquals(empty.d2sCheckpoint().canonical(), decoded.d2sCheckpoint().canonical());
    assertEquals(empty.sessionSnapshot().canonical(), decoded.sessionSnapshot().canonical());

    D2gsCheckpointSnapshot absent = D2gsCheckpointSnapshot.fromBytes(
        D2gsCheckpointSnapshot.fromPayloads(0L, java.util.Collections.emptyMap()).toBytes());
    org.junit.Assert.assertNull(absent.d2sCheckpoint());
    org.junit.Assert.assertNull(absent.sessionSnapshot());
  }

  @Test public void completeEnvelopeStillReadsVersionOneWithoutOptionalMetadata() {
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.fromPayloads(5L,
        java.util.Collections.emptyMap());
    byte[] current = checkpoint.toBytes();
    // v3 world presence, v4 missile count, v5 allocator, v6 state count, v7 targets.
    byte[] legacy = new byte[current.length - 11];
    System.arraycopy(current, 0, legacy, 0, 36);
    System.arraycopy(current, 47, legacy, 36, current.length - 47);
    java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(legacy);
    bytes.putInt(4, 1); // original complete envelope version
    bytes.putInt(28, 0); // v1 encoded absent D2S as zero entries
    bytes.putInt(32, 0); // v1 encoded absent sessions as zero entries
    assertEquals(checkpoint.summary().canonical(),
        D2gsCheckpointSnapshot.fromBytes(legacy).summary().canonical());
  }

  @Test public void worldIdentityRoundTripRejectsTamperingAndEcsOnlyExport() {
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.fromPayloads(5L,
        java.util.Collections.emptyMap()).withWorldIdentity(12345, 2);
    D2gsCheckpointSnapshot restored = D2gsCheckpointSnapshot.fromBytes(checkpoint.toBytes());
    assertEquals(12345, restored.worldIdentity().seed);
    assertEquals(2, restored.worldIdentity().difficulty);
    assertEquals(checkpoint.summary().canonical(), restored.summary().canonical());
    byte[] tampered = checkpoint.toBytes();
    tampered[40] ^= 1; // last seed byte in the empty fixture
    assertIllegalArgument(() -> D2gsCheckpointSnapshot.fromBytes(tampered));
    byte[] badPresence = checkpoint.toBytes();
    badPresence[36] = 2;
    assertIllegalArgument(() -> D2gsCheckpointSnapshot.fromBytes(badPresence));
    try {
      checkpoint.toEcsBytes();
      throw new AssertionError("ECS-only transport must not discard world identity");
    } catch (IllegalStateException expected) {}
  }

  @Test public void completeEnvelopeStillReadsVersionTwoWithoutWorldIdentity() {
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.fromPayloads(5L,
        java.util.Collections.emptyMap(), null, null,
        com.riiablo.save.D2SCheckpoint.of(java.util.Collections.emptyMap()),
        new AuthoritativeSessionRegistry().snapshot());
    byte[] current = checkpoint.toBytes();
    byte[] legacy = new byte[current.length - 11];
    System.arraycopy(current, 0, legacy, 0, 36);
    System.arraycopy(current, 47, legacy, 36, current.length - 47);
    java.nio.ByteBuffer.wrap(legacy).putInt(4, 2);
    D2gsCheckpointSnapshot restored = D2gsCheckpointSnapshot.fromBytes(legacy);
    assertEquals(checkpoint.summary().canonical(), restored.summary().canonical());
    assertEquals(checkpoint.d2sCheckpoint().canonical(), restored.d2sCheckpoint().canonical());
    org.junit.Assert.assertNull(restored.worldIdentity());
  }

  @Test public void checkpointCarriesRngStateAndReportsContinuationDrift() {
    World world = worldWithEntities(1);
    FakeSerializer serializer = new FakeSerializer();
    int id = networkedIds(world)[0];
    serializer.states.put(id, 10);
    java.util.List<SimulationCheckpoint.RngState> rng = java.util.Collections.singletonList(
        new SimulationCheckpoint.RngState("combat.hit", id, 1234, 8));
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 7L, rng);

    assertEquals(rng, checkpoint.summary().rngStates());
    D2gsCheckpointSnapshot drifted = D2gsCheckpointSnapshot.fromPayloads(
        7L, checkpoint.payloads(), java.util.Collections.singletonList(
            new SimulationCheckpoint.RngState("combat.hit", id, 1235, 9)))
        .withUnitStateRuntime(checkpoint.unitStateRuntime());
    assertEquals(1, checkpoint.summary().diff(drifted.summary()).size());
    assertEquals("rng:combat.hit#" + id,
        checkpoint.summary().diff(drifted.summary()).get(0).field);

    java.util.List<SimulationCheckpoint.NativeUnitRngState> nativeRng =
        java.util.Collections.singletonList(
            new SimulationCheckpoint.NativeUnitRngState("unit.hit", id, 1, 2));
    D2gsCheckpointSnapshot nativeCheckpoint = D2gsCheckpointSnapshot.fromPayloads(
        7L, checkpoint.payloads(), null, nativeRng);
    D2gsCheckpointSnapshot nativeDrifted = D2gsCheckpointSnapshot.fromPayloads(
        7L, checkpoint.payloads(), null, java.util.Collections.singletonList(
            new SimulationCheckpoint.NativeUnitRngState("unit.hit", id, 3, 4)));
    assertEquals("native-rng:unit.hit#" + id,
        nativeCheckpoint.summary().diff(nativeDrifted.summary()).get(0).field);
  }

  @Test public void checkpointCarriesSessionAuthorityForReconnectTakeover() {
    World world = worldWithEntities(1);
    FakeSerializer serializer = new FakeSerializer();
    int id = networkedIds(world)[0];
    serializer.states.put(id, 10);
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    assertTrue(sessions.connect(4, "hero", id, 7L).accepted);

    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 7L,
        null, null, null, sessions.snapshot());
    assertEquals(sessions.snapshot().canonical(), checkpoint.sessionSnapshot().canonical());

    AuthoritativeSessionRegistry resumed = new AuthoritativeSessionRegistry();
    resumed.restore(checkpoint.sessionSnapshot());
    long generation = resumed.stableKey("hero").generation;
    assertTrue(resumed.rebind(9, "hero", 99, 8L, generation).accepted);
    assertEquals(AuthoritativeSessionRegistry.State.DISCONNECTED,
        resumed.connection(4).state);
    assertEquals(99, resumed.activeConnection(9).actorId);
  }

  @Test public void restoredEcsStateContinuesOnTheNextFixedTick() {
    World world = worldWithEntities(1);
    FakeSerializer serializer = new FakeSerializer();
    int id = networkedIds(world)[0];
    serializer.states.put(id, 10);
    AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);

    simulation.tick(null, null);
    simulation.tick(null, null);
    D2gsCheckpointSnapshot checkpoint =
        D2gsCheckpointSnapshot.capture(world, serializer, simulation.tickNumber());

    simulation.tick(() -> serializer.states.put(id, 99), null);
    simulation.tick(null, null);
    assertEquals(4L, simulation.tickNumber());

    D2gsCheckpointSnapshot.RestoreResult result =
        checkpoint.restoreExisting(world, serializer);
    assertTrue(result.success());
    simulation.restoreFromCheckpoint(checkpoint.tick(), true);

    simulation.tick(() -> serializer.states.put(id, serializer.states.get(id) + 1), null);
    assertEquals(3L, simulation.tickNumber());
    assertEquals(11, (int) serializer.states.get(id));

    D2gsCheckpointSnapshot continued =
        D2gsCheckpointSnapshot.capture(world, serializer, simulation.tickNumber());
    assertEquals(3L, continued.tick());
    assertTrue(continued.summary().canonical().startsWith("checkpoint|3|"));
  }

  @Test public void rejectsMissingAndUnexpectedEntitiesBeforeApplying() {
    World world = worldWithEntities(1);
    FakeSerializer serializer = new FakeSerializer();
    int original = networkedIds(world)[0];
    serializer.states.put(original, 7);
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 3L);

    world.delete(original);
    world.process();
    D2gsCheckpointSnapshot.RestoreResult missing = checkpoint.restoreExisting(world, serializer);
    assertFalse(missing.success());
    assertEquals(java.util.Arrays.asList(original), missing.missingEntities);
    assertTrue(missing.unexpectedEntities.isEmpty());

    World extraWorld = worldWithEntities(2);
    FakeSerializer extraSerializer = new FakeSerializer();
    int[] ids = networkedIds(extraWorld);
    for (int id : ids) extraSerializer.states.put(id, 1);
    D2gsCheckpointSnapshot extraCheckpoint =
        D2gsCheckpointSnapshot.capture(extraWorld, extraSerializer, 3L);
    int extra = extraWorld.create();
    extraWorld.getEntity(extra).edit().add(new Networked());
    extraWorld.process();
    D2gsCheckpointSnapshot.RestoreResult unexpected =
        extraCheckpoint.restoreExisting(extraWorld, extraSerializer);
    assertFalse(unexpected.success());
    assertEquals(java.util.Arrays.asList(extra), unexpected.unexpectedEntities);
  }

  @Test public void componentFailureRollsBackAllAppliedEntities() {
    World world = worldWithEntities(2);
    FakeSerializer serializer = new FakeSerializer();
    int[] ids = networkedIds(world);
    for (int id : ids) serializer.states.put(id, id + 100);
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 9L);

    for (int id : ids) serializer.states.put(id, id + 200);
    serializer.failOnce = ids[1];
    D2gsCheckpointSnapshot.RestoreResult result = checkpoint.restoreExisting(world, serializer);

    assertFalse(result.success());
    assertEquals(java.util.Arrays.asList(ids[1]), result.failedEntities);
    assertTrue(result.rolledBack);
    assertEquals(ids[0] + 200, (int) serializer.states.get(ids[0]));
    assertEquals(ids[1] + 200, (int) serializer.states.get(ids[1]));
  }

  @Test public void lifecycleBridgeReconcilesMissingAndUnexpectedEntities() {
    World world = worldWithEntities(2);
    FakeSerializer serializer = new FakeSerializer();
    int[] ids = networkedIds(world);
    for (int id : ids) serializer.states.put(id, id + 10);
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 12L);

    int extra = world.create();
    world.getEntity(extra).edit().add(new Networked());
    world.process();
    world.delete(ids[1]);
    world.process();
    D2gsCheckpointSnapshot.RestoreResult result = checkpoint.restoreExisting(
        world, serializer, new FakeLifecycle());
    world.process();
    D2gsCheckpointSnapshot after = D2gsCheckpointSnapshot.capture(world, serializer, 12L);

    assertTrue(result.success());
    assertEquals(java.util.Arrays.asList(ids[1]), result.createdEntities);
    assertEquals(java.util.Arrays.asList(extra), result.deletedEntities);
    assertEquals(java.util.Arrays.toString(ids), java.util.Arrays.toString(networkedIds(world)));
    assertEquals(checkpoint.summary().canonical(), after.summary().canonical());
  }

  @Test public void lifecycleFailureRollsBackEntitySetAndComponents() {
    World world = worldWithEntities(2);
    FakeSerializer serializer = new FakeSerializer();
    int[] ids = networkedIds(world);
    for (int id : ids) serializer.states.put(id, id + 10);
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 15L);

    int extra = world.create();
    world.getEntity(extra).edit().add(new Networked());
    world.process();
    world.delete(ids[1]);
    world.process();
    serializer.failOnce = ids[1];
    D2gsCheckpointSnapshot.RestoreResult result = checkpoint.restoreExisting(
        world, serializer, new FakeLifecycle());
    world.process();

    assertFalse(result.success());
    assertTrue(result.rolledBack);
    assertTrue(result.createdEntities.isEmpty());
    assertTrue(result.deletedEntities.isEmpty());
    assertEquals(java.util.Arrays.toString(new int[] {0, extra}),
        java.util.Arrays.toString(networkedIds(world)));
  }

  private static World worldWithEntities(int count) {
    World world = new World(new WorldConfigurationBuilder().build());
    for (int i = 0; i < count; i++) {
      int id = world.create();
      world.getEntity(id).edit().add(new Networked());
    }
    world.process();
    return world;
  }

  private static int[] networkedIds(World world) {
    com.artemis.utils.IntBag entities = world.getAspectSubscriptionManager()
        .get(com.artemis.Aspect.all(Networked.class)).getEntities();
    int[] ids = new int[entities.size()];
    System.arraycopy(entities.getData(), 0, ids, 0, ids.length);
    java.util.Arrays.sort(ids);
    return ids;
  }

  private static final class FakeSerializer extends SerializationManager {
    final Map<Integer, Integer> states = new HashMap<>();
    int failOnce = -1;

    @Override public int serialize(FlatBufferBuilder builder, int entityId, long tick,
        long serverTimeMillis) {
      int types = EntitySync.createComponentTypeVector(builder, new byte[0]);
      int components = EntitySync.createComponentVector(builder, new int[0]);
      int sync = EntitySync.createEntitySync(builder, entityId,
          states.getOrDefault(entityId, 0), 0, types, components, tick, serverTimeMillis,
          0L, 0L, 0L, -1);
      return sync;
    }

    @Override public void deserialize(int entityId, D2GS packet) {
      EntitySync sync = (EntitySync) packet.data(new EntitySync());
      states.put(entityId, sync.type());
      if (entityId == failOnce) {
        failOnce = -1;
        throw new IllegalStateException("injected component failure");
      }
    }
  }

  private static final class FakeLifecycle implements D2gsCheckpointSnapshot.EntityLifecycle {
    @Override public int createEntity(World world, int expectedEntityId,
        java.nio.ByteBuffer payload) {
      int id = world.create();
      world.getEntity(id).edit().add(new Networked());
      return id;
    }

    @Override public boolean deleteEntity(World world, int entityId) {
      if (!world.getEntityManager().isActive(entityId)) return false;
      world.delete(entityId);
      return true;
    }
  }
}
