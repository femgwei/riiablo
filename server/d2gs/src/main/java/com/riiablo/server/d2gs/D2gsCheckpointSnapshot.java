package com.riiablo.server.d2gs;

import java.nio.ByteBuffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.AuthoritativeSessionRegistry;
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.save.D2SCheckpoint;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;

/**
 * D2GS-owned checkpoint payload containing the authoritative network state for
 * every currently networked ECS entity.  The summary is immutable and can be
 * retained by the replay recorder; payload bytes are used only by the
 * same-world restore adapter.
 */
public final class D2gsCheckpointSnapshot {
  private static final int ECS_WIRE_MAGIC = 0x44324745; // "D2GE"
  private static final int ECS_WIRE_VERSION = 1;
  private static final int MAX_ECS_ENTITIES = 1_000_000;
  private static final int MAX_ECS_PAYLOAD = 16 * 1024 * 1024;

  /**
   * Native ECS lifecycle bridge used when a checkpoint crosses an entity-set
   * boundary. Implementations must attach all components required by the
   * serializer before returning from createEntity, and must preserve the
   * requested entity id; a newly allocated id cannot be remapped safely inside
   * an authoritative world.
   */
  public interface EntityLifecycle {
    int createEntity(World world, int expectedEntityId, ByteBuffer payload);
    boolean deleteEntity(World world, int entityId);
  }

  public static final class RestoreResult {
    public final int appliedEntities;
    public final List<Integer> missingEntities;
    public final List<Integer> failedEntities;
    public final List<Integer> unexpectedEntities;
    public final List<Integer> createdEntities;
    public final List<Integer> deletedEntities;
    public final boolean rolledBack;

    private RestoreResult(int appliedEntities, List<Integer> missingEntities,
        List<Integer> failedEntities, List<Integer> unexpectedEntities,
        List<Integer> createdEntities, List<Integer> deletedEntities, boolean rolledBack) {
      this.appliedEntities = appliedEntities;
      this.missingEntities = Collections.unmodifiableList(new ArrayList<>(missingEntities));
      this.failedEntities = Collections.unmodifiableList(new ArrayList<>(failedEntities));
      this.unexpectedEntities = Collections.unmodifiableList(new ArrayList<>(unexpectedEntities));
      this.createdEntities = Collections.unmodifiableList(new ArrayList<>(createdEntities));
      this.deletedEntities = Collections.unmodifiableList(new ArrayList<>(deletedEntities));
      this.rolledBack = rolledBack;
    }

    public boolean success() {
      return missingEntities.isEmpty() && failedEntities.isEmpty() && unexpectedEntities.isEmpty();
    }
  }

  private final long tick;
  private final SimulationCheckpoint summary;
  private final Map<Integer, byte[]> payloads;
  private final D2SCheckpoint d2sCheckpoint;
  private final AuthoritativeSessionRegistry.Snapshot sessionSnapshot;

  private D2gsCheckpointSnapshot(long tick, SimulationCheckpoint summary,
      Map<Integer, byte[]> payloads, D2SCheckpoint d2sCheckpoint,
      AuthoritativeSessionRegistry.Snapshot sessionSnapshot) {
    this.tick = tick;
    this.summary = summary;
    TreeMap<Integer, byte[]> copy = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
    }
    this.payloads = Collections.unmodifiableMap(copy);
    this.d2sCheckpoint = d2sCheckpoint;
    this.sessionSnapshot = sessionSnapshot;
  }

  /** Captures the same EntitySync state used for an authoritative baseline. */
  public static D2gsCheckpointSnapshot capture(World world, SerializationManager serializer,
      long tick) {
    return capture(world, serializer, tick, null, null);
  }

  /** Captures ECS state and the named compatibility RNG streams at the same tick. */
  public static D2gsCheckpointSnapshot capture(World world, SerializationManager serializer,
      long tick, List<SimulationCheckpoint.RngState> rngStates) {
    return capture(world, serializer, tick, rngStates, null);
  }

  /** Captures ECS state and both compatibility/native-unit RNG registries. */
  public static D2gsCheckpointSnapshot capture(World world, SerializationManager serializer,
      long tick, List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates) {
    return capture(world, serializer, tick, rngStates, nativeUnitRngStates, null);
  }

  /** Captures ECS/RNG state and an optional authoritative D2S role snapshot. */
  public static D2gsCheckpointSnapshot capture(World world, SerializationManager serializer,
      long tick, List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint) {
    return capture(world, serializer, tick, rngStates, nativeUnitRngStates,
        d2sCheckpoint, null);
  }

  /** Captures ECS/RNG/D2S state and the authenticated session authority. */
  public static D2gsCheckpointSnapshot capture(World world, SerializationManager serializer,
      long tick, List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint,
      AuthoritativeSessionRegistry.Snapshot sessionSnapshot) {
    if (world == null) throw new NullPointerException("world");
    if (serializer == null) throw new NullPointerException("serializer");
    if (tick < 0L) throw new IllegalArgumentException("tick");
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Networked.class)).getEntities();
    int[] ids = Arrays.copyOf(entities.getData(), entities.size());
    Arrays.sort(ids);
    Map<Integer, byte[]> payloads = new LinkedHashMap<>();
    // Clock fields are recorded in the checkpoint envelope itself. Keep them
    // out of per-entity bytes so the same ECS state has the same digest after
    // a process restart with a different wall-clock epoch.
    for (int entityId : ids) payloads.put(entityId,
        serialize(serializer, entityId, 0L, 0L));
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint,
        sessionSnapshot);
  }

  /** Builds a snapshot from serialized EntitySync envelopes for deterministic tests/tools. */
  public static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads) {
    return fromPayloads(tick, payloads, null, null);
  }

  /** Builds a snapshot from payloads and explicit RNG checkpoint state. */
  public static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates) {
    return fromPayloads(tick, payloads, rngStates, null);
  }

  /** Builds a snapshot from payloads and both RNG checkpoint state lists. */
  public static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates) {
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, null);
  }

  /** Builds a snapshot with ECS/RNG payloads and an optional D2S role snapshot. */
  public static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint) {
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint, null);
  }

  /** Builds a snapshot with ECS/RNG/D2S payloads and session authority. */
  public static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint,
      AuthoritativeSessionRegistry.Snapshot sessionSnapshot) {
    if (tick < 0L) throw new IllegalArgumentException("tick");
    if (payloads == null) throw new NullPointerException("payloads");
    TreeMap<Integer, String> entityDigests = new TreeMap<>();
    TreeMap<Integer, byte[]> copy = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      if (entry.getKey() == null || entry.getKey() < 0) {
        throw new IllegalArgumentException("entityId");
      }
      if (entry.getValue() == null) throw new NullPointerException("payload");
      byte[] bytes = Arrays.copyOf(entry.getValue(), entry.getValue().length);
      copy.put(entry.getKey(), bytes);
      entityDigests.put(entry.getKey(), digest(bytes));
    }
    StringBuilder canonical = new StringBuilder("d2gs-ecs-v1|").append(tick);
    for (Map.Entry<Integer, String> entry : entityDigests.entrySet()) {
      canonical.append("|entity:").append(entry.getKey()).append(":")
          .append(entry.getValue());
    }
    if (sessionSnapshot != null) canonical.append("|sessions:")
        .append(digest(sessionSnapshot.canonical()));
    SimulationCheckpoint summary = SimulationCheckpoint.of(tick, digest(canonical.toString()),
        entityDigests, rngStates, nativeUnitRngStates);
    return new D2gsCheckpointSnapshot(tick, summary, copy, d2sCheckpoint, sessionSnapshot);
  }

  public long tick() { return tick; }
  public SimulationCheckpoint summary() { return summary; }
  public D2SCheckpoint d2sCheckpoint() { return d2sCheckpoint; }
  public AuthoritativeSessionRegistry.Snapshot sessionSnapshot() { return sessionSnapshot; }
  public Map<Integer, byte[]> payloads() {
    Map<Integer, byte[]> copy = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
    }
    return Collections.unmodifiableMap(copy);
  }

  /**
   * Encodes the ECS-only portion as a deterministic, process-independent
   * envelope. Optional RNG/D2S/session metadata is deliberately rejected here
   * until the combined checkpoint codec is introduced; callers must not lose
   * those fields by accidentally using this narrow transport.
   */
  public byte[] toEcsBytes() {
    if (!summary.rngStates().isEmpty() || !summary.nativeUnitRngStates().isEmpty()
        || d2sCheckpoint != null || sessionSnapshot != null) {
      throw new IllegalStateException("ECS envelope cannot discard checkpoint metadata");
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream output = new DataOutputStream(bytes);
      output.writeInt(ECS_WIRE_MAGIC);
      output.writeInt(ECS_WIRE_VERSION);
      output.writeLong(tick);
      output.writeInt(payloads.size());
      for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
        output.writeInt(entry.getKey());
        output.writeInt(entry.getValue().length);
        output.write(entry.getValue());
      }
      byte[] worldDigest = summary.worldDigest().getBytes(
          java.nio.charset.StandardCharsets.US_ASCII);
      output.writeInt(worldDigest.length);
      output.write(worldDigest);
      output.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("unable to encode ECS checkpoint", e);
    }
  }

  /** Decodes and integrity-checks an ECS-only checkpoint produced by {@link #toEcsBytes()}. */
  public static D2gsCheckpointSnapshot fromEcsBytes(byte[] encoded) {
    if (encoded == null) throw new NullPointerException("encoded");
    try {
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
      if (input.readInt() != ECS_WIRE_MAGIC) throw new IllegalArgumentException("ECS checkpoint magic");
      if (input.readInt() != ECS_WIRE_VERSION) throw new IllegalArgumentException("ECS checkpoint version");
      long tick = input.readLong();
      if (tick < 0L) throw new IllegalArgumentException("tick");
      int count = input.readInt();
      if (count < 0 || count > MAX_ECS_ENTITIES) throw new IllegalArgumentException("entity count");
      Map<Integer, byte[]> payloads = new LinkedHashMap<>();
      int previousId = -1;
      for (int i = 0; i < count; i++) {
        int entityId = input.readInt();
        if (entityId < 0 || entityId <= previousId) {
          throw new IllegalArgumentException("entity ordering");
        }
        previousId = entityId;
        int length = input.readInt();
        if (length <= 0 || length > MAX_ECS_PAYLOAD) {
          throw new IllegalArgumentException("entity payload length");
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        payloads.put(entityId, payload);
      }
      int digestLength = input.readInt();
      if (digestLength <= 0 || digestLength > 128) {
        throw new IllegalArgumentException("world digest length");
      }
      byte[] digestBytes = new byte[digestLength];
      input.readFully(digestBytes);
      if (input.read() != -1) throw new IllegalArgumentException("trailing ECS checkpoint bytes");
      String expectedDigest = new String(digestBytes, java.nio.charset.StandardCharsets.US_ASCII);
      D2gsCheckpointSnapshot result = fromPayloads(tick, payloads);
      if (!result.summary.worldDigest().equals(expectedDigest)) {
        throw new IllegalArgumentException("ECS checkpoint digest mismatch");
      }
      return result;
    } catch (EOFException e) {
      throw new IllegalArgumentException("truncated ECS checkpoint", e);
    } catch (IOException e) {
      throw new IllegalArgumentException("invalid ECS checkpoint", e);
    }
  }

  /**
   * Applies serialized component state to existing ECS entities only. Entity
   * creation/deletion remains rejected unless an explicit native lifecycle
   * bridge is supplied.
   */
  public RestoreResult restoreExisting(World world, SerializationManager serializer) {
    if (world == null) throw new NullPointerException("world");
    if (serializer == null) throw new NullPointerException("serializer");
    return restoreExisting(world, serializer, null, true);
  }

  /** Restores component state and reconciles the entity set through a bridge. */
  public RestoreResult restoreExisting(World world, SerializationManager serializer,
      EntityLifecycle lifecycle) {
    if (world == null) throw new NullPointerException("world");
    if (serializer == null) throw new NullPointerException("serializer");
    if (lifecycle == null) throw new NullPointerException("lifecycle");
    return restoreExisting(world, serializer, lifecycle, true);
  }

  private RestoreResult restoreExisting(World world, SerializationManager serializer,
      EntityLifecycle lifecycle, boolean captureRollback) {
    List<Integer> missing = new ArrayList<>();
    List<Integer> failed = new ArrayList<>();
    List<Integer> unexpected = new ArrayList<>();
    List<Integer> created = new ArrayList<>();
    List<Integer> deleted = new ArrayList<>();
    IntBag active = world.getAspectSubscriptionManager()
        .get(Aspect.all(Networked.class)).getEntities();
    for (int i = 0; i < active.size(); i++) {
      int entityId = active.get(i);
      if (!payloads.containsKey(entityId)) unexpected.add(entityId);
    }
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      int entityId = entry.getKey();
      if (!world.getEntityManager().isActive(entityId)) {
        missing.add(entityId);
      }
    }
    Collections.sort(missing);
    Collections.sort(failed);
    Collections.sort(unexpected);
    if (lifecycle == null && (!missing.isEmpty() || !unexpected.isEmpty())) {
      return result(0, missing, failed, unexpected, created, deleted, false);
    }
    D2gsCheckpointSnapshot before = captureRollback
        ? capture(world, serializer, tick) : null;

    if (lifecycle != null) {
      for (Integer entityId : missing) {
        try {
          int createdId = lifecycle.createEntity(world, entityId,
              ByteBuffer.wrap(payloads.get(entityId)).duplicate());
          if (createdId != entityId || !world.getEntityManager().isActive(entityId)) {
            if (createdId >= 0 && world.getEntityManager().isActive(createdId)) {
              lifecycle.deleteEntity(world, createdId);
            }
            failed.add(entityId);
            break;
          }
          created.add(entityId);
        } catch (Throwable ignored) {
          failed.add(entityId);
          break;
        }
      }
      if (!failed.isEmpty()) {
        return failure(world, serializer, lifecycle, before, missing, failed, unexpected,
            created, deleted);
      }
      missing.clear();
    }

    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      int entityId = entry.getKey();
      try {
        if (!world.getEntityManager().isActive(entityId)) {
          missing.add(entityId);
          throw new IllegalStateException("entity was not active after lifecycle restore");
        }
        Packet packet = Packet.obtain(1, ByteBuffer.wrap(entry.getValue()).duplicate());
        serializer.deserialize(entityId, packet.data);
      } catch (Throwable ignored) {
        failed.add(entityId);
        return failure(world, serializer, lifecycle, before, missing, failed, unexpected,
            created, deleted);
      }
    }
    if (lifecycle != null) {
      for (Integer entityId : unexpected) {
        try {
          if (!lifecycle.deleteEntity(world, entityId)) {
            failed.add(entityId);
            break;
          }
          deleted.add(entityId);
        } catch (Throwable ignored) {
          failed.add(entityId);
          break;
        }
      }
      if (!failed.isEmpty()) {
        return failure(world, serializer, lifecycle, before, missing, failed, unexpected,
            created, deleted);
      }
      unexpected.clear();
    }
    return result(payloads.size(), missing, failed, unexpected, created, deleted, false);
  }

  private RestoreResult failure(World world, SerializationManager serializer,
      EntityLifecycle lifecycle, D2gsCheckpointSnapshot before, List<Integer> missing,
      List<Integer> failed, List<Integer> unexpected, List<Integer> created,
      List<Integer> deleted) {
    boolean rolledBack = before != null
        && rollback(world, serializer, lifecycle, before, created);
    if (rolledBack) {
      created.clear();
      deleted.clear();
    }
    return result(0, missing, failed, unexpected, created, deleted, rolledBack);
  }

  private static boolean rollback(World world, SerializationManager serializer,
      EntityLifecycle lifecycle, D2gsCheckpointSnapshot before, List<Integer> created) {
    try {
      if (lifecycle == null) {
        for (Map.Entry<Integer, byte[]> entry : before.payloads.entrySet()) {
          if (!world.getEntityManager().isActive(entry.getKey())) return false;
          Packet packet = Packet.obtain(1, ByteBuffer.wrap(entry.getValue()).duplicate());
          serializer.deserialize(entry.getKey(), packet.data);
        }
        return true;
      }
      for (Integer entityId : created) {
        if (world.getEntityManager().isActive(entityId)
            && !lifecycle.deleteEntity(world, entityId)) return false;
      }
      IntBag active = world.getAspectSubscriptionManager()
          .get(Aspect.all(Networked.class)).getEntities();
      for (int i = 0; i < active.size(); i++) {
        int entityId = active.get(i);
        if (world.getEntityManager().isActive(entityId) && !before.payloads.containsKey(entityId)
            && !lifecycle.deleteEntity(world, entityId)) return false;
      }
      for (Integer entityId : before.payloads.keySet()) {
        if (!world.getEntityManager().isActive(entityId)) {
          int createdId = lifecycle.createEntity(world, entityId,
              ByteBuffer.wrap(before.payloads.get(entityId)).duplicate());
          if (createdId != entityId || !world.getEntityManager().isActive(entityId)) return false;
        }
      }
      for (Map.Entry<Integer, byte[]> entry : before.payloads.entrySet()) {
        if (!world.getEntityManager().isActive(entry.getKey())) return false;
        Packet packet = Packet.obtain(1, ByteBuffer.wrap(entry.getValue()).duplicate());
        serializer.deserialize(entry.getKey(), packet.data);
      }
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static RestoreResult result(int applied, List<Integer> missing,
      List<Integer> failed, List<Integer> unexpected, List<Integer> created,
      List<Integer> deleted, boolean rolledBack) {
    Collections.sort(missing);
    Collections.sort(failed);
    Collections.sort(unexpected);
    Collections.sort(created);
    Collections.sort(deleted);
    return new RestoreResult(applied, missing, failed, unexpected, created, deleted, rolledBack);
  }

  private static byte[] serialize(SerializationManager serializer, int entityId,
      long tick, long serverTimeMillis) {
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int sync = serializer.serialize(builder, entityId, tick, serverTimeMillis);
    int root = D2GS.createD2GS(builder, D2GSData.EntitySync, sync);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer buffer = builder.dataBuffer().duplicate();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }

  private static String digest(byte[] bytes) {
    try {
      return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("JRE must provide SHA-256", e);
    }
  }

  private static String digest(String value) {
    return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
    return result.toString();
  }
}
