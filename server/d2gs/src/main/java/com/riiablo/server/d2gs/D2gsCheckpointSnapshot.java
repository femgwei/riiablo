package com.riiablo.server.d2gs;

import java.nio.ByteBuffer;
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
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;

/**
 * D2GS-owned checkpoint payload containing the authoritative network state for
 * every currently networked ECS entity.  The summary is immutable and can be
 * retained by the replay recorder; payload bytes are used only by the
 * same-world restore adapter.
 */
public final class D2gsCheckpointSnapshot {
  public static final class RestoreResult {
    public final int appliedEntities;
    public final List<Integer> missingEntities;
    public final List<Integer> failedEntities;
    public final List<Integer> unexpectedEntities;

    private RestoreResult(int appliedEntities, List<Integer> missingEntities,
        List<Integer> failedEntities, List<Integer> unexpectedEntities) {
      this.appliedEntities = appliedEntities;
      this.missingEntities = Collections.unmodifiableList(new ArrayList<>(missingEntities));
      this.failedEntities = Collections.unmodifiableList(new ArrayList<>(failedEntities));
      this.unexpectedEntities = Collections.unmodifiableList(new ArrayList<>(unexpectedEntities));
    }

    public boolean success() {
      return missingEntities.isEmpty() && failedEntities.isEmpty() && unexpectedEntities.isEmpty();
    }
  }

  private final long tick;
  private final SimulationCheckpoint summary;
  private final Map<Integer, byte[]> payloads;

  private D2gsCheckpointSnapshot(long tick, SimulationCheckpoint summary,
      Map<Integer, byte[]> payloads) {
    this.tick = tick;
    this.summary = summary;
    TreeMap<Integer, byte[]> copy = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
    }
    this.payloads = Collections.unmodifiableMap(copy);
  }

  /** Captures the same EntitySync state used for an authoritative baseline. */
  public static D2gsCheckpointSnapshot capture(World world, SerializationManager serializer,
      long tick) {
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
    return fromPayloads(tick, payloads);
  }

  /** Builds a snapshot from serialized EntitySync envelopes for deterministic tests/tools. */
  public static D2gsCheckpointSnapshot fromPayloads(long tick, Map<Integer, byte[]> payloads) {
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
    SimulationCheckpoint summary = SimulationCheckpoint.of(tick, digest(canonical.toString()),
        entityDigests, null);
    return new D2gsCheckpointSnapshot(tick, summary, copy);
  }

  public long tick() { return tick; }
  public SimulationCheckpoint summary() { return summary; }
  public Map<Integer, byte[]> payloads() {
    Map<Integer, byte[]> copy = new TreeMap<>();
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
    }
    return Collections.unmodifiableMap(copy);
  }

  /**
   * Applies serialized component state to existing ECS entities only. Entity
   * creation/deletion is deliberately rejected until a native factory restore
   * contract exists, preventing an accidental partial world resurrection.
   */
  public RestoreResult restoreExisting(World world, SerializationManager serializer) {
    if (world == null) throw new NullPointerException("world");
    if (serializer == null) throw new NullPointerException("serializer");
    List<Integer> missing = new ArrayList<>();
    List<Integer> failed = new ArrayList<>();
    List<Integer> unexpected = new ArrayList<>();
    IntBag active = world.getAspectSubscriptionManager()
        .get(Aspect.all(Networked.class)).getEntities();
    for (int i = 0; i < active.size(); i++) {
      int entityId = active.get(i);
      if (!payloads.containsKey(entityId)) unexpected.add(entityId);
    }
    int applied = 0;
    for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
      int entityId = entry.getKey();
      if (!world.getEntityManager().isActive(entityId)) {
        missing.add(entityId);
        continue;
      }
      try {
        Packet packet = Packet.obtain(1, ByteBuffer.wrap(entry.getValue()).duplicate());
        serializer.deserialize(entityId, packet.data);
        applied++;
      } catch (Throwable ignored) {
        failed.add(entityId);
      }
    }
    Collections.sort(missing);
    Collections.sort(failed);
    Collections.sort(unexpected);
    return new RestoreResult(applied, missing, failed, unexpected);
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
