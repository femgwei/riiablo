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
import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.utils.IntBag;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.AuthoritativeSessionRegistry;
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.engine.replay.MissileRuntimeCheckpoint;
import com.riiablo.engine.replay.UnitStateRuntimeCheckpoint;
import com.riiablo.engine.replay.TargetRuntimeCheckpoint;
import com.riiablo.save.D2SCheckpoint;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;

/**
 * D2GS-owned checkpoint payload containing the authoritative network state for
 * every currently networked ECS entity. The summary is immutable and can be
 * retained by the replay recorder; payloads may be restored into another world
 * when the native lifecycle and authoritative runtime bindings are installed.
 * Network component coverage alone is not a complete simulation snapshot.
 */
public final class D2gsCheckpointSnapshot {
  private static final int ECS_WIRE_MAGIC = 0x44324745; // "D2GE"
  private static final int ECS_WIRE_VERSION = 1;
  private static final int CHECKPOINT_WIRE_MAGIC = 0x44324743; // "D2GC"
  private static final int CHECKPOINT_WIRE_VERSION = 7;
  private static final int MAX_ECS_ENTITIES = 1_000_000;
  private static final int MAX_ECS_PAYLOAD = 16 * 1024 * 1024;
  private static final int MAX_CHECKPOINT_STRING = 64 * 1024 * 1024;

  /**
   * Native ECS lifecycle bridge used when a checkpoint crosses an entity-set
   * boundary. Implementations must attach all components required by the
   * serializer before returning from createEntity, and must preserve the
   * requested entity id; a newly allocated id cannot be remapped safely inside
   * an authoritative world.
   */
  public interface EntityLifecycle {
    /** Brackets creation, application, deletion and any internal rollback; must not tick the world. */
    default void beginRestore(World world) {}
    default void endRestore(World world) {}
    int createEntity(World world, int expectedEntityId, ByteBuffer payload);
    default int createEntity(World world, int expectedEntityId, ByteBuffer payload, boolean restoreRuntime) {
      return createEntity(world, expectedEntityId, payload);
    }
    boolean deleteEntity(World world, int entityId);
  }

  /** Minimal world compatibility lock; not a content digest or a map runtime snapshot. */
  public static final class WorldIdentity {
    public final int seed;
    public final int difficulty;

    public WorldIdentity(int seed, int difficulty) {
      if (difficulty < 0 || difficulty > 2) throw new IllegalArgumentException("difficulty");
      this.seed = seed;
      this.difficulty = difficulty;
    }

    public String canonical() { return "world-v1:" + seed + ":" + difficulty; }
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
  private final WorldIdentity worldIdentity;
  private final Map<Integer, MissileRuntimeCheckpoint> missileRuntime;
  private final EntityAllocatorCheckpoint allocatorState;
  private final Map<Integer, UnitStateRuntimeCheckpoint> unitStateRuntime;
  private final TargetRuntimeCheckpoint targetRuntime;

  private D2gsCheckpointSnapshot(long tick, SimulationCheckpoint summary,
      Map<Integer, byte[]> payloads, D2SCheckpoint d2sCheckpoint,
      AuthoritativeSessionRegistry.Snapshot sessionSnapshot, WorldIdentity worldIdentity,
      Map<Integer, MissileRuntimeCheckpoint> missileRuntime, EntityAllocatorCheckpoint allocatorState,
      Map<Integer, UnitStateRuntimeCheckpoint> unitStateRuntime, TargetRuntimeCheckpoint targetRuntime) {
    this.tick = tick;
    this.summary = summary;
    TreeMap<Integer, byte[]> copy = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
    }
    this.payloads = Collections.unmodifiableMap(copy);
    this.d2sCheckpoint = d2sCheckpoint;
    this.sessionSnapshot = sessionSnapshot;
    this.worldIdentity = worldIdentity;
    this.missileRuntime = Collections.unmodifiableMap(new TreeMap<>(missileRuntime));
    this.allocatorState = allocatorState;
    this.unitStateRuntime = unitStateRuntime == null ? null
        : Collections.unmodifiableMap(new TreeMap<>(unitStateRuntime));
    this.targetRuntime = targetRuntime;
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
    TargetRuntimeCheckpoint targets = TargetRuntimeCheckpoint.capture(world, ids);
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint,
        sessionSnapshot).withMissileRuntime(MissileRuntimeCheckpoint.capture(world, ids))
        .withTargetRuntime(targets)
        .withUnitStateRuntime(UnitStateRuntimeCheckpoint.capture(world, ids, targets != null));
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
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint,
        sessionSnapshot, null);
  }

  public static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint, AuthoritativeSessionRegistry.Snapshot sessionSnapshot,
      WorldIdentity worldIdentity) {
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint,
        sessionSnapshot, worldIdentity, Collections.emptyMap());
  }

  private static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint, AuthoritativeSessionRegistry.Snapshot sessionSnapshot,
      WorldIdentity worldIdentity, Map<Integer, MissileRuntimeCheckpoint> missileRuntime) {
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint,
        sessionSnapshot, worldIdentity, missileRuntime, null);
  }

  private static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint, AuthoritativeSessionRegistry.Snapshot sessionSnapshot,
      WorldIdentity worldIdentity, Map<Integer, MissileRuntimeCheckpoint> missileRuntime,
      EntityAllocatorCheckpoint allocatorState) {
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint,
        sessionSnapshot, worldIdentity, missileRuntime, allocatorState, null);
  }

  private static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint, AuthoritativeSessionRegistry.Snapshot sessionSnapshot,
      WorldIdentity worldIdentity, Map<Integer, MissileRuntimeCheckpoint> missileRuntime,
      EntityAllocatorCheckpoint allocatorState,
      Map<Integer, UnitStateRuntimeCheckpoint> unitStateRuntime) {
    return fromPayloads(tick, payloads, rngStates, nativeUnitRngStates, d2sCheckpoint,
        sessionSnapshot, worldIdentity, missileRuntime, allocatorState, unitStateRuntime, null);
  }

  private static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads,
      List<SimulationCheckpoint.RngState> rngStates,
      List<SimulationCheckpoint.NativeUnitRngState> nativeUnitRngStates,
      D2SCheckpoint d2sCheckpoint, AuthoritativeSessionRegistry.Snapshot sessionSnapshot,
      WorldIdentity worldIdentity, Map<Integer, MissileRuntimeCheckpoint> missileRuntime,
      EntityAllocatorCheckpoint allocatorState,
      Map<Integer, UnitStateRuntimeCheckpoint> unitStateRuntime, TargetRuntimeCheckpoint targetRuntime) {
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
    if (worldIdentity != null) canonical.append("|world:").append(worldIdentity.canonical());
    if (allocatorState != null) {
      for (int id : copy.keySet()) if (!allocatorState.active(id)) {
        throw new IllegalArgumentException("checkpoint payload uses free/unallocated allocator id");
      }
      canonical.append("|allocator:").append(digest(allocatorState.bytes()));
    }
    MissileRuntimeCheckpoint.validate(missileRuntime);
    for (Map.Entry<Integer, MissileRuntimeCheckpoint> entry : new TreeMap<>(missileRuntime).entrySet()) {
      byte[] payload = copy.get(entry.getKey());
      if (payload == null || entry.getValue() == null) throw new IllegalArgumentException("missile payload missing");
      D2GS packet = D2GS.getRootAsD2GS(com.google.flatbuffers.ByteBufferUtil.removeSizePrefix(
          ByteBuffer.wrap(payload)));
      if (packet.dataType() != D2GSData.EntitySync) throw new IllegalArgumentException("missile envelope");
      com.riiablo.net.packet.d2gs.EntitySync sync = (com.riiablo.net.packet.d2gs.EntitySync)
          packet.data(new com.riiablo.net.packet.d2gs.EntitySync());
      boolean matched = false;
      for (int i = 0; i < sync.componentTypeLength(); i++) {
        if (sync.componentType(i) == com.riiablo.net.packet.d2gs.ComponentP.MissileP) {
          com.riiablo.net.packet.d2gs.MissileP missile = (com.riiablo.net.packet.d2gs.MissileP)
              sync.component(new com.riiablo.net.packet.d2gs.MissileP(), i);
          matched = missile.missileId() == entry.getValue().missileId()
              && missile.ownerId() == entry.getValue().ownerId();
        }
      }
      if (sync.entityId() != entry.getKey()
          || sync.type() != com.riiablo.engine.server.component.Class.Type.MIS.ordinal() || !matched) {
        throw new IllegalArgumentException("missile ECS/runtime identity mismatch");
      }
      canonical.append("|missile-runtime:").append(entry.getKey()).append(':')
          .append(digest(entry.getValue().bytes()));
    }
    if (d2sCheckpoint != null) {
      for (D2SCheckpoint.Entry entry : d2sCheckpoint.entries().values()) {
        if (entry.potionCheckpoint() != null) canonical.append("|player-runtime:")
            .append(entry.entityId()).append(':').append(digest(entry.potionCheckpoint().canonical()));
      }
    }
    if (unitStateRuntime != null) {
      if (!unitStateRuntime.keySet().equals(copy.keySet())) {
        throw new IllegalArgumentException("unit-state runtime coverage mismatch");
      }
      canonical.append("|unit-state-runtime-v1");
      for (Map.Entry<Integer, UnitStateRuntimeCheckpoint> entry : new TreeMap<>(unitStateRuntime).entrySet()) {
        if (entry.getValue() == null) throw new IllegalArgumentException("null unit-state runtime");
        entry.getValue().validateMonsterCoverage(copy.keySet());
        entry.getValue().validateAiHost(entry.getKey());
        entry.getValue().validateAiCoverage(copy.keySet());
        if (entry.getValue().requiresTargetRuntime() && targetRuntime == null) {
          throw new IllegalArgumentException("state target runtime missing");
        }
        canonical.append('|').append(entry.getKey()).append(':').append(digest(entry.getValue().bytes()));
      }
    }
    if (targetRuntime != null) {
      if (!targetRuntime.entityIds().equals(copy.keySet())) {
        throw new IllegalArgumentException("target runtime coverage mismatch");
      }
      canonical.append("|target-runtime:").append(digest(targetRuntime.bytes()));
    }
    SimulationCheckpoint summary = SimulationCheckpoint.of(tick, digest(canonical.toString()),
        entityDigests, rngStates, nativeUnitRngStates);
    return new D2gsCheckpointSnapshot(tick, summary, copy, d2sCheckpoint, sessionSnapshot, worldIdentity,
        missileRuntime, allocatorState, unitStateRuntime, targetRuntime);
  }

  public long tick() { return tick; }
  public SimulationCheckpoint summary() { return summary; }
  public D2SCheckpoint d2sCheckpoint() { return d2sCheckpoint; }
  public AuthoritativeSessionRegistry.Snapshot sessionSnapshot() { return sessionSnapshot; }
  public WorldIdentity worldIdentity() { return worldIdentity; }
  public Map<Integer, MissileRuntimeCheckpoint> missileRuntime() { return missileRuntime; }
  public EntityAllocatorCheckpoint allocatorState() { return allocatorState; }
  public Map<Integer, UnitStateRuntimeCheckpoint> unitStateRuntime() { return unitStateRuntime; }
  public TargetRuntimeCheckpoint targetRuntime() { return targetRuntime; }
  public D2gsCheckpointSnapshot withTargetRuntime(TargetRuntimeCheckpoint runtime) {
    return fromPayloads(tick, payloads, summary.rngStates(), summary.nativeUnitRngStates(),
        d2sCheckpoint, sessionSnapshot, worldIdentity, missileRuntime, allocatorState, unitStateRuntime, runtime);
  }

  /** Null is an explicit legacy subset without authoritative state-list coverage. */
  public D2gsCheckpointSnapshot withUnitStateRuntime(Map<Integer, UnitStateRuntimeCheckpoint> runtime) {
    return fromPayloads(tick, payloads, summary.rngStates(), summary.nativeUnitRngStates(),
        d2sCheckpoint, sessionSnapshot, worldIdentity, missileRuntime, allocatorState, runtime, targetRuntime);
  }

  /** Reapply after D2S replaces player attribute pointers, without re-running gameplay. */
  public void restoreUnitStateRuntime(World world) {
    UnitStateRuntimeCheckpoint.restore(world, unitStateRuntime);
  }

  public D2gsCheckpointSnapshot withAllocatorFrom(World world) {
    EntityAllocatorCheckpoint allocator = EntityAllocatorCheckpoint.capture(world);
    if (!managedIds(world, allocator).equals(payloads.keySet())) {
      throw new IllegalStateException("allocator checkpoint requires published network entities");
    }
    return withAllocatorState(allocator);
  }

  /** Null explicitly selects legacy subset restore without allocator guarantees. */
  public D2gsCheckpointSnapshot withAllocatorState(EntityAllocatorCheckpoint allocator) {
    return fromPayloads(tick, payloads, summary.rngStates(), summary.nativeUnitRngStates(),
        d2sCheckpoint, sessionSnapshot, worldIdentity, missileRuntime, allocator, unitStateRuntime, targetRuntime);
  }

  public D2gsCheckpointSnapshot withMissileRuntime(Map<Integer, MissileRuntimeCheckpoint> runtime) {
    return fromPayloads(tick, payloads, summary.rngStates(), summary.nativeUnitRngStates(),
        d2sCheckpoint, sessionSnapshot, worldIdentity, runtime, allocatorState, unitStateRuntime, targetRuntime);
  }

  public D2gsCheckpointSnapshot withWorldIdentity(int seed, int difficulty) {
    return fromPayloads(tick, payloads, summary.rngStates(), summary.nativeUnitRngStates(),
        d2sCheckpoint, sessionSnapshot, new WorldIdentity(seed, difficulty), missileRuntime, allocatorState, unitStateRuntime, targetRuntime);
  }
  public Map<Integer, byte[]> payloads() {
    Map<Integer, byte[]> copy = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
    }
    return Collections.unmodifiableMap(copy);
  }

  /** Artemis 2.3 isActive only checks recycling, not whether an id was ever allocated. */
  static boolean isActive(World world, int entityId) {
    if (entityId < 0) return false;
    try {
      return world.getEntity(entityId) != null && world.getEntityManager().isActive(entityId);
    } catch (IndexOutOfBoundsException unallocated) {
      return false;
    }
  }

  /**
   * Encodes the ECS-only portion as a deterministic, process-independent
   * envelope. Optional RNG/D2S/session metadata is deliberately rejected here
   * until the combined checkpoint codec is introduced; callers must not lose
   * those fields by accidentally using this narrow transport.
   */
  public byte[] toEcsBytes() {
    if (!summary.rngStates().isEmpty() || !summary.nativeUnitRngStates().isEmpty()
        || d2sCheckpoint != null || sessionSnapshot != null || worldIdentity != null
        || !missileRuntime.isEmpty() || allocatorState != null || unitStateRuntime != null || targetRuntime != null) {
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
   * Encodes the complete checkpoint, including ECS, both RNG registries, D2S
   * role bytes, and session authority. The canonical summaries are included as
   * integrity witnesses so a receiving process can reject partial metadata.
   */
  public byte[] toBytes() {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream output = new DataOutputStream(bytes);
      output.writeInt(CHECKPOINT_WIRE_MAGIC);
      output.writeInt(CHECKPOINT_WIRE_VERSION);
      output.writeLong(tick);
      writePayloads(output, payloads);

      output.writeInt(summary.rngStates().size());
      for (SimulationCheckpoint.RngState state : summary.rngStates()) {
        writeString(output, state.stream);
        output.writeLong(state.contextId);
        output.writeInt(state.state);
        output.writeLong(state.drawCount);
      }
      output.writeInt(summary.nativeUnitRngStates().size());
      for (SimulationCheckpoint.NativeUnitRngState state : summary.nativeUnitRngStates()) {
        writeString(output, state.stream);
        output.writeLong(state.contextId);
        output.writeInt(state.lowSeed);
        output.writeInt(state.highSeed);
      }

      if (d2sCheckpoint == null) {
        output.writeInt(-1);
      } else {
        output.writeInt(d2sCheckpoint.entries().size());
        for (D2SCheckpoint.Entry entry : d2sCheckpoint.entries().values()) {
          output.writeInt(entry.entityId());
          output.writeInt(entry.difficulty());
          byte[] save = entry.bytes();
          output.writeInt(save.length);
          output.write(save);
          CharData.PotionCheckpoint potion = entry.potionCheckpoint();
          output.writeBoolean(potion != null);
          if (potion != null) {
            output.writeInt(potion.seed);
            output.writeInt(potion.healthRateEncoded);
            output.writeInt(potion.healthFrames);
            output.writeInt(potion.manaRateEncoded);
            output.writeInt(potion.manaFrames);
          }
        }
      }

      if (sessionSnapshot == null) {
        output.writeInt(-1);
      } else {
        output.writeInt(sessionSnapshot.sessions().size());
        for (AuthoritativeSessionRegistry.Session session : sessionSnapshot.sessions()) {
          output.writeInt(session.connectionId);
          output.writeInt(session.actorId);
          writeString(output, session.stableKey);
          output.writeByte(session.state.ordinal());
          output.writeLong(session.connectedTick);
          output.writeLong(session.disconnectedTick);
          output.writeLong(session.generation);
        }
      }
      output.writeBoolean(worldIdentity != null);
      if (worldIdentity != null) {
        output.writeInt(worldIdentity.seed);
        output.writeInt(worldIdentity.difficulty);
      }
      output.writeInt(missileRuntime.size());
      for (Map.Entry<Integer, MissileRuntimeCheckpoint> entry : missileRuntime.entrySet()) {
        output.writeInt(entry.getKey());
        byte[] runtime = entry.getValue().bytes();
        output.writeInt(runtime.length);
        output.write(runtime);
      }
      output.writeBoolean(allocatorState != null);
      if (allocatorState != null) {
        byte[] allocator = allocatorState.bytes();
        output.writeInt(allocator.length); output.write(allocator);
      }
      output.writeInt(unitStateRuntime == null ? -1 : unitStateRuntime.size());
      if (unitStateRuntime != null) {
        for (Map.Entry<Integer, UnitStateRuntimeCheckpoint> entry : unitStateRuntime.entrySet()) {
          output.writeInt(entry.getKey());
          byte[] runtime = entry.getValue().bytes();
          output.writeInt(runtime.length); output.write(runtime);
        }
      }
      output.writeBoolean(targetRuntime != null);
      if (targetRuntime != null) {
        byte[] targets = targetRuntime.bytes();
        output.writeInt(targets.length); output.write(targets);
      }
      writeString(output, summary.canonical());
      writeString(output, d2sCheckpoint == null ? "" : d2sCheckpoint.canonical());
      output.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("unable to encode checkpoint", e);
    }
  }

  /** Decodes and validates a complete checkpoint produced by {@link #toBytes()}. */
  public static D2gsCheckpointSnapshot fromBytes(byte[] encoded) {
    if (encoded == null) throw new NullPointerException("encoded");
    try {
      DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
      if (input.readInt() != CHECKPOINT_WIRE_MAGIC) {
        throw new IllegalArgumentException("checkpoint magic");
      }
      int version = input.readInt();
      if (version < 1 || version > CHECKPOINT_WIRE_VERSION) {
        throw new IllegalArgumentException("checkpoint version");
      }
      long tick = input.readLong();
      if (tick < 0L) throw new IllegalArgumentException("tick");
      Map<Integer, byte[]> payloads = readPayloads(input);

      int rngCount = readCount(input, "rng count");
      List<SimulationCheckpoint.RngState> rngStates = new ArrayList<>();
      for (int i = 0; i < rngCount; i++) {
        rngStates.add(new SimulationCheckpoint.RngState(readString(input, "rng stream"),
            input.readLong(), input.readInt(), input.readLong()));
      }
      int nativeCount = readCount(input, "native rng count");
      List<SimulationCheckpoint.NativeUnitRngState> nativeStates = new ArrayList<>();
      for (int i = 0; i < nativeCount; i++) {
        nativeStates.add(new SimulationCheckpoint.NativeUnitRngState(
            readString(input, "native rng stream"), input.readLong(), input.readInt(),
            input.readInt()));
      }

      int d2sCount = readOptionalCount(input, "D2S count", version);
      D2SCheckpoint d2s = null;
      if (d2sCount >= 0) {
        Map<Integer, D2SCheckpoint.Entry> entries = new TreeMap<>();
        for (int i = 0; i < d2sCount; i++) {
          int entityId = input.readInt();
          int difficulty = input.readInt();
          int length = input.readInt();
          if (entityId < 0 || length <= 0 || length > MAX_ECS_PAYLOAD) {
            throw new IllegalArgumentException("D2S entry");
          }
          byte[] save = new byte[length];
          input.readFully(save);
          CharData.PotionCheckpoint potion = null;
          if (version >= 3 && readPresence(input, "potion metadata")) {
            potion = new CharData.PotionCheckpoint(input.readInt(), input.readInt(),
                input.readInt(), input.readInt(), input.readInt());
          }
          if (entries.put(entityId, D2SCheckpoint.entry(entityId, difficulty, save, potion)) != null) {
            throw new IllegalArgumentException("duplicate D2S entity");
          }
        }
        d2s = D2SCheckpoint.of(entries);
      }

      int sessionCount = readOptionalCount(input, "session count", version);
      AuthoritativeSessionRegistry.Snapshot sessions = null;
      if (sessionCount >= 0) {
        List<AuthoritativeSessionRegistry.Session> records = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
          int connectionId = input.readInt();
          int actorId = input.readInt();
          String stableKey = readString(input, "stable key");
          int stateOrdinal = input.readUnsignedByte();
          if (stateOrdinal >= AuthoritativeSessionRegistry.State.values().length) {
            throw new IllegalArgumentException("session state");
          }
          records.add(AuthoritativeSessionRegistry.Session.of(connectionId, actorId, stableKey,
              AuthoritativeSessionRegistry.State.values()[stateOrdinal], input.readLong(),
              input.readLong(), input.readLong()));
        }
        sessions = AuthoritativeSessionRegistry.Snapshot.of(records);
      }

      WorldIdentity identity = null;
      if (version >= 3 && readPresence(input, "world identity")) {
        identity = new WorldIdentity(input.readInt(), input.readInt());
      }
      Map<Integer, MissileRuntimeCheckpoint> missiles = new TreeMap<>();
      if (version >= 4) {
        int count = readCount(input, "missile runtime count");
        for (int i = 0; i < count; i++) {
          int id = input.readInt(), length = input.readInt();
          if (id < 0 || length <= 0 || length > MissileRuntimeCheckpoint.MAX_BYTES) {
            throw new IllegalArgumentException("missile runtime entry");
          }
          byte[] runtime = new byte[length]; input.readFully(runtime);
          if (missiles.put(id, MissileRuntimeCheckpoint.fromBytes(runtime)) != null) {
            throw new IllegalArgumentException("duplicate missile runtime");
          }
        }
      }
      EntityAllocatorCheckpoint allocator = null;
      if (version >= 5 && readPresence(input, "allocator state")) {
        int length = input.readInt();
        if (length < 12 || length > EntityAllocatorCheckpoint.MAX_BYTES) {
          throw new IllegalArgumentException("allocator state length");
        }
        byte[] bytes = new byte[length]; input.readFully(bytes);
        allocator = EntityAllocatorCheckpoint.fromBytes(bytes);
      }
      Map<Integer, UnitStateRuntimeCheckpoint> unitStates = null;
      if (version >= 6) {
        int count = readOptionalCount(input, "unit-state runtime count", version);
        if (count >= 0) {
          unitStates = new TreeMap<>();
          for (int i = 0; i < count; i++) {
            int id = input.readInt(), length = input.readInt();
            if (id < 0 || length <= 0 || length > UnitStateRuntimeCheckpoint.MAX_BYTES) {
              throw new IllegalArgumentException("unit-state runtime entry");
            }
            byte[] runtime = new byte[length]; input.readFully(runtime);
            if (unitStates.put(id, UnitStateRuntimeCheckpoint.fromBytes(runtime)) != null) {
              throw new IllegalArgumentException("duplicate unit-state runtime");
            }
          }
        }
      }
      TargetRuntimeCheckpoint targets = null;
      if (version >= 7 && readPresence(input, "target runtime")) {
        int length = input.readInt();
        if (length <= 0 || length > TargetRuntimeCheckpoint.MAX_BYTES) {
          throw new IllegalArgumentException("target runtime length");
        }
        byte[] runtime = new byte[length]; input.readFully(runtime);
        targets = TargetRuntimeCheckpoint.fromBytes(runtime);
      }
      String expectedSummary = readString(input, "summary canonical");
      String expectedD2s = readString(input, "D2S canonical");
      if (input.read() != -1) throw new IllegalArgumentException("trailing checkpoint bytes");
      D2gsCheckpointSnapshot result = fromPayloads(tick, payloads, rngStates, nativeStates,
          d2s, sessions, identity, missiles, allocator, unitStates, targets);
      if (!result.summary.canonical().equals(expectedSummary)) {
        throw new IllegalArgumentException("checkpoint summary mismatch");
      }
      String actualD2s = d2s == null ? "" : d2s.canonical();
      if (!actualD2s.equals(expectedD2s)) throw new IllegalArgumentException("D2S mismatch");
      return result;
    } catch (EOFException e) {
      throw new IllegalArgumentException("truncated checkpoint", e);
    } catch (IOException e) {
      throw new IllegalArgumentException("invalid checkpoint", e);
    }
  }

  private static boolean readPresence(DataInputStream input, String field) throws IOException {
    int present = input.readUnsignedByte();
    if (present > 1) throw new IllegalArgumentException(field);
    return present == 1;
  }

  private static int readOptionalCount(DataInputStream input, String field, int version)
      throws IOException {
    int count = input.readInt();
    if (count < (version == 1 ? 0 : -1) || count > MAX_ECS_ENTITIES) {
      throw new IllegalArgumentException(field);
    }
    // Version 1 used zero for absent metadata and could not represent empty registries.
    return version == 1 && count == 0 ? -1 : count;
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
    if ((allocatorState != null || targetRuntime != null) && captureRollback) {
      return restoreAllocatorTransaction(world, serializer, lifecycle);
    }
    if (lifecycle != null) lifecycle.beginRestore(world);
    try {
      return restoreEntities(world, serializer, lifecycle, captureRollback);
    } finally {
      if (lifecycle != null) lifecycle.endRestore(world);
    }
  }

  private RestoreResult restoreAllocatorTransaction(World world, SerializationManager serializer,
      EntityLifecycle lifecycle) {
    EntityAllocatorCheckpoint live = EntityAllocatorCheckpoint.capture(world);
    if (allocatorState != null) allocatorState.validateRestore(world, payloads.keySet(), managedIds(world, live));
    D2gsCheckpointSnapshot before = capture(world, serializer, tick).withAllocatorFrom(world);
    RestoreResult result;
    try {
      // The outer transaction owns rollback, including failed native creations
      // not yet published to subscriptions and the complete allocator image.
      result = restoreExisting(world, serializer, lifecycle, false);
      EntityAllocatorCheckpoint.flushStructure(world);
      if (result.success()) {
        if (targetRuntime != null) targetRuntime.restore(world);
        if (allocatorState != null) allocatorState.restore(world);
        return result;
      }
    } catch (Throwable failure) {
      try { rollbackAllocator(world, serializer, lifecycle, before); }
      catch (Throwable rollback) {
        failure.addSuppressed(rollback);
        throw new IllegalStateException("allocator checkpoint rollback failed", failure);
      }
      throw new IllegalStateException("allocator checkpoint transaction rolled back", failure);
    }
    rollbackAllocator(world, serializer, lifecycle, before);
    return new RestoreResult(0, result.missingEntities, result.failedEntities, result.unexpectedEntities,
        Collections.emptyList(), Collections.emptyList(), true);
  }

  private static java.util.Set<Integer> managedIds(World world, EntityAllocatorCheckpoint allocator) {
    java.util.Set<Integer> ids = new java.util.TreeSet<>();
    for (int id : allocator.activeIds()) if (world.getMapper(Networked.class).has(id)) ids.add(id);
    return ids;
  }

  private static void rollbackAllocator(World world, SerializationManager serializer,
      EntityLifecycle lifecycle, D2gsCheckpointSnapshot before) {
    EntityAllocatorCheckpoint.flushStructure(world);
    for (int id : managedIds(world, EntityAllocatorCheckpoint.capture(world))) {
      if (!before.payloads.containsKey(id)) {
        if (lifecycle == null || !lifecycle.deleteEntity(world, id)) {
          throw new IllegalStateException("allocator rollback cannot remove creation: " + id);
        }
      }
    }
    EntityAllocatorCheckpoint.flushStructure(world);
    RestoreResult result = before.restoreExisting(world, serializer, lifecycle, false);
    if (!result.success()) throw new IllegalStateException("allocator rollback cannot rebuild entity set");
    EntityAllocatorCheckpoint.flushStructure(world);
    if (before.targetRuntime != null) before.targetRuntime.restore(world);
    before.allocatorState.restore(world);
  }

  private RestoreResult restoreEntities(World world, SerializationManager serializer,
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
      if (!isActive(world, entityId)) {
        missing.add(entityId);
      } else if (!world.getMapper(Networked.class).has(entityId)
          || com.artemis.PendingEntityDeletion.isDeleted(world, entityId)) {
        // Never deserialize a network checkpoint over an unrelated local
        // entity, or resurrect a deletion that the batch processor will purge.
        failed.add(entityId);
      }
    }
    Collections.sort(missing);
    Collections.sort(failed);
    Collections.sort(unexpected);
    if (!failed.isEmpty()) {
      return result(0, missing, failed, unexpected, created, deleted, false);
    }
    if (lifecycle == null && (!missing.isEmpty() || !unexpected.isEmpty())) {
      return result(0, missing, failed, unexpected, created, deleted, false);
    }
    D2gsCheckpointSnapshot before = captureRollback
        ? capture(world, serializer, tick) : null;

    if (lifecycle != null) {
      for (Integer entityId : missing) {
        try {
          int createdId = lifecycle.createEntity(world, entityId,
              ByteBuffer.wrap(payloads.get(entityId)).duplicate(), missileRuntime.containsKey(entityId));
          if (createdId != entityId || !isActive(world, entityId)) {
            if (isActive(world, createdId)) {
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
        if (!isActive(world, entityId)) {
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
    try {
      MissileRuntimeCheckpoint.restore(world, missileRuntime);
      restoreUnitStateRuntime(world);
    } catch (RuntimeException invalidRuntime) {
      failed.addAll(missileRuntime.keySet());
      if (unitStateRuntime != null) failed.addAll(unitStateRuntime.keySet());
      return failure(world, serializer, lifecycle, before, missing, failed, unexpected, created, deleted);
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
          if (!isActive(world, entry.getKey())) return false;
          Packet packet = Packet.obtain(1, ByteBuffer.wrap(entry.getValue()).duplicate());
          serializer.deserialize(entry.getKey(), packet.data);
        }
        MissileRuntimeCheckpoint.restore(world, before.missileRuntime);
        before.restoreUnitStateRuntime(world);
        return true;
      }
      for (Integer entityId : created) {
        if (isActive(world, entityId)
            && !lifecycle.deleteEntity(world, entityId)) return false;
      }
      IntBag active = world.getAspectSubscriptionManager()
          .get(Aspect.all(Networked.class)).getEntities();
      for (int i = 0; i < active.size(); i++) {
        int entityId = active.get(i);
        if (isActive(world, entityId) && !before.payloads.containsKey(entityId)
            && !lifecycle.deleteEntity(world, entityId)) return false;
      }
      for (Integer entityId : before.payloads.keySet()) {
        if (!isActive(world, entityId)) {
          int createdId = lifecycle.createEntity(world, entityId,
              ByteBuffer.wrap(before.payloads.get(entityId)).duplicate(), before.missileRuntime.containsKey(entityId));
          if (createdId != entityId || !isActive(world, entityId)) return false;
        }
      }
      for (Map.Entry<Integer, byte[]> entry : before.payloads.entrySet()) {
        if (!isActive(world, entry.getKey())) return false;
        Packet packet = Packet.obtain(1, ByteBuffer.wrap(entry.getValue()).duplicate());
        serializer.deserialize(entry.getKey(), packet.data);
      }
      MissileRuntimeCheckpoint.restore(world, before.missileRuntime);
      before.restoreUnitStateRuntime(world);
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

  private static void writePayloads(DataOutputStream output, Map<Integer, byte[]> payloads)
      throws IOException {
    if (payloads.size() > MAX_ECS_ENTITIES) throw new IllegalArgumentException("entity count");
    output.writeInt(payloads.size());
    int previousId = -1;
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      if (entry.getKey() == null || entry.getKey() <= previousId) {
        throw new IllegalArgumentException("entity ordering");
      }
      byte[] payload = entry.getValue();
      if (payload == null || payload.length <= 0 || payload.length > MAX_ECS_PAYLOAD) {
        throw new IllegalArgumentException("entity payload length");
      }
      previousId = entry.getKey();
      output.writeInt(entry.getKey());
      output.writeInt(payload.length);
      output.write(payload);
    }
  }

  private static Map<Integer, byte[]> readPayloads(DataInputStream input) throws IOException {
    int count = readCount(input, "entity count");
    Map<Integer, byte[]> payloads = new LinkedHashMap<>();
    int previousId = -1;
    for (int i = 0; i < count; i++) {
      int entityId = input.readInt();
      if (entityId < 0 || entityId <= previousId) throw new IllegalArgumentException("entity ordering");
      previousId = entityId;
      int length = input.readInt();
      if (length <= 0 || length > MAX_ECS_PAYLOAD) {
        throw new IllegalArgumentException("entity payload length");
      }
      byte[] payload = new byte[length];
      input.readFully(payload);
      payloads.put(entityId, payload);
    }
    return payloads;
  }

  private static int readCount(DataInputStream input, String name) throws IOException {
    int count = input.readInt();
    if (count < 0 || count > MAX_ECS_ENTITIES) throw new IllegalArgumentException(name);
    return count;
  }

  private static void writeString(DataOutputStream output, String value) throws IOException {
    if (value == null) throw new NullPointerException("value");
    byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length > MAX_CHECKPOINT_STRING) throw new IllegalArgumentException("string length");
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  private static String readString(DataInputStream input, String name) throws IOException {
    int length = input.readInt();
    if (length < 0 || length > MAX_CHECKPOINT_STRING) {
      throw new IllegalArgumentException(name);
    }
    byte[] bytes = new byte[length];
    input.readFully(bytes);
    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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
