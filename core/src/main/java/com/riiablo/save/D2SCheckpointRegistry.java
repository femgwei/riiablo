package com.riiablo.save;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Transactional D2S state registry for authoritative player bindings.
 *
 * <p>Restore decodes and validates every save before replacing any binding.
 * If a binding callback fails, previously installed character objects are put
 * back and the registry remains unchanged.</p>
 */
public final class D2SCheckpointRegistry {
  public interface Binding {
    CharData get();
    void set(CharData data);
    int difficulty();
  }

  public interface Factory {
    Binding create(int entityId, D2SCheckpoint.Entry entry, CharData decoded);
  }

  public static final class RestoreResult {
    public final int applied;
    public final List<Integer> failedEntities;
    public final boolean rolledBack;

    private RestoreResult(int applied, List<Integer> failedEntities, boolean rolledBack) {
      this.applied = applied;
      this.failedEntities = Collections.unmodifiableList(new ArrayList<>(failedEntities));
      this.rolledBack = rolledBack;
    }

    public boolean success() { return failedEntities.isEmpty(); }
  }

  private final Factory factory;
  private final TreeMap<Integer, Binding> bindings = new TreeMap<>();

  public D2SCheckpointRegistry() { this(null); }
  public D2SCheckpointRegistry(Factory factory) { this.factory = factory; }

  public synchronized void register(int entityId, Binding binding) {
    if (entityId < 0) throw new IllegalArgumentException("entityId");
    if (bindings.putIfAbsent(entityId, Objects.requireNonNull(binding, "binding")) != null) {
      throw new IllegalArgumentException("D2S binding already registered: " + entityId);
    }
  }

  public synchronized Binding get(int entityId) { return bindings.get(entityId); }

  /** Removes a binding after its authoritative ECS player has left the world. */
  public synchronized void unregister(int entityId) { bindings.remove(entityId); }

  public synchronized D2SCheckpoint capture() {
    TreeMap<Integer, D2SCheckpoint.Entry> entries = new TreeMap<>();
    for (Map.Entry<Integer, Binding> entry : bindings.entrySet()) {
      Binding binding = entry.getValue();
      CharData data = Objects.requireNonNull(binding.get(), "character data");
      byte[] bytes = new D2SWriter96().writeD2S(D2SWriter96.createD2S(data));
      entries.put(entry.getKey(), D2SCheckpoint.entry(
          entry.getKey(), binding.difficulty(), bytes));
    }
    return D2SCheckpoint.of(entries);
  }

  /** Validates every D2S and confirms factories exist for missing bindings. */
  public synchronized void validate(D2SCheckpoint checkpoint) {
    if (checkpoint == null) throw new NullPointerException("checkpoint");
    for (D2SCheckpoint.Entry entry : checkpoint.entries().values()) {
      if (!bindings.containsKey(entry.entityId()) && factory == null) {
        throw new IllegalStateException("D2S factory required for missing entity: "
            + entry.entityId());
      }
      CharData.loadFromBuffer(entry.difficulty(), ByteBuffer.wrap(entry.bytes()));
    }
  }

  /** Restores all characters as one binding transaction. */
  public synchronized RestoreResult restore(D2SCheckpoint checkpoint) {
    validate(checkpoint);
    TreeMap<Integer, Binding> next = new TreeMap<>();
    TreeMap<Integer, CharData> decoded = new TreeMap<>();
    for (D2SCheckpoint.Entry entry : checkpoint.entries().values()) {
      CharData data = CharData.loadFromBuffer(entry.difficulty(), ByteBuffer.wrap(entry.bytes()));
      Binding binding = bindings.get(entry.entityId());
      if (binding == null) binding = Objects.requireNonNull(
          factory.create(entry.entityId(), entry, data), "D2S factory result");
      next.put(entry.entityId(), binding);
      decoded.put(entry.entityId(), data);
    }

    TreeMap<Integer, CharData> before = new TreeMap<>();
    List<Integer> applied = new ArrayList<>();
    int currentEntity = -1;
    try {
      for (Map.Entry<Integer, Binding> entry : next.entrySet()) {
        currentEntity = entry.getKey();
        before.put(entry.getKey(), entry.getValue().get());
        entry.getValue().set(decoded.get(entry.getKey()));
        applied.add(entry.getKey());
      }
      bindings.clear();
      bindings.putAll(next);
      return new RestoreResult(applied.size(), Collections.emptyList(), false);
    } catch (Throwable failure) {
      List<Integer> failed = new ArrayList<>();
      if (currentEntity >= 0 && !applied.contains(currentEntity)) failed.add(currentEntity);
      boolean rollbackOk = true;
      for (Integer entityId : applied) {
        try {
          next.get(entityId).set(before.get(entityId));
        } catch (Throwable ignored) {
          rollbackOk = false;
          if (!failed.contains(entityId)) failed.add(entityId);
        }
      }
      return new RestoreResult(0, failed, rollbackOk);
    }
  }
}
