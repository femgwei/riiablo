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
        source, sourceSerializer, 55L);

    byte[] encoded = checkpoint.toEcsBytes();
    D2gsCheckpointSnapshot transferred = D2gsCheckpointSnapshot.fromEcsBytes(encoded);
    World resumed = worldWithEntities(2);
    FakeSerializer resumedSerializer = new FakeSerializer();
    for (int id : networkedIds(resumed)) resumedSerializer.states.put(id, 900);

    D2gsCheckpointSnapshot.RestoreResult result = transferred.restoreExisting(
        resumed, resumedSerializer);
    D2gsCheckpointSnapshot restored = D2gsCheckpointSnapshot.capture(
        resumed, resumedSerializer, 55L);
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
    byte[] encoded = D2gsCheckpointSnapshot.capture(world, serializer, 8L).toEcsBytes();

    byte[] tampered = java.util.Arrays.copyOf(encoded, encoded.length);
    tampered[tampered.length - 1] ^= 1;
    assertIllegalArgument(() -> D2gsCheckpointSnapshot.fromEcsBytes(tampered));

    byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
    assertIllegalArgument(() -> D2gsCheckpointSnapshot.fromEcsBytes(trailing));
  }

  private static void assertIllegalArgument(Runnable action) {
    try {
      action.run();
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new AssertionError("expected IllegalArgumentException");
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
            new SimulationCheckpoint.RngState("combat.hit", id, 1235, 9)));
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
