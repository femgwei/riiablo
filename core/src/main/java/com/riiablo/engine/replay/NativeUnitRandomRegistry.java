package com.riiablo.engine.replay;

import com.riiablo.engine.server.NativeUnitRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Checkpoint registry for D2Common's two-word native unit random streams.
 *
 * <p>Unlike {@code NativeRng}, a native unit stream cannot be reconstructed
 * without the owning ECS unit and its world. Callers therefore provide a
 * factory for missing streams when restoring in another process.</p>
 */
public final class NativeUnitRandomRegistry {
  public interface Factory {
    NativeUnitRandom create(SimulationCheckpoint.NativeUnitRngState state);
  }

  private final Factory factory;
  private final TreeMap<Key, NativeUnitRandom> streams = new TreeMap<>();

  public NativeUnitRandomRegistry() {
    this(null);
  }

  public NativeUnitRandomRegistry(Factory factory) {
    this.factory = factory;
  }

  public synchronized void register(String stream, long contextId, NativeUnitRandom rng) {
    Objects.requireNonNull(rng, "rng");
    Key key = key(stream, contextId);
    if (streams.putIfAbsent(key, rng) != null) {
      throw new IllegalArgumentException("native RNG stream already registered: " + key);
    }
  }

  public synchronized NativeUnitRandom get(String stream, long contextId) {
    return streams.get(key(stream, contextId));
  }

  public synchronized List<SimulationCheckpoint.NativeUnitRngState> snapshot() {
    List<SimulationCheckpoint.NativeUnitRngState> result = new ArrayList<>(streams.size());
    for (Map.Entry<Key, NativeUnitRandom> entry : streams.entrySet()) {
      NativeUnitRandom.Snapshot state = entry.getValue().snapshot();
      result.add(new SimulationCheckpoint.NativeUnitRngState(
          entry.getKey().stream, entry.getKey().contextId, state.lowSeed, state.highSeed));
    }
    return Collections.unmodifiableList(result);
  }

  /** Validates identities and requires a factory when a stream is absent. */
  public synchronized void validate(List<SimulationCheckpoint.NativeUnitRngState> states) {
    List<SimulationCheckpoint.NativeUnitRngState> normalized = normalized(states);
    if (factory == null) {
      for (SimulationCheckpoint.NativeUnitRngState state : normalized) {
        if (!streams.containsKey(key(state.stream, state.contextId))) {
          throw new IllegalStateException("native RNG factory required for missing stream: "
              + state.stream + "#" + state.contextId);
        }
      }
    }
  }

  /** Restores all streams and recreates missing units through the configured factory. */
  public synchronized void restore(List<SimulationCheckpoint.NativeUnitRngState> states) {
    List<SimulationCheckpoint.NativeUnitRngState> normalized = normalized(states);
    TreeMap<Key, NativeUnitRandom> next = new TreeMap<>();
    for (SimulationCheckpoint.NativeUnitRngState state : normalized) {
      Key key = key(state.stream, state.contextId);
      NativeUnitRandom rng = streams.get(key);
      if (rng == null) {
        if (factory == null) {
          throw new IllegalStateException("native RNG factory required for missing stream: " + key);
        }
        rng = Objects.requireNonNull(factory.create(state), "native RNG factory result");
      }
      next.put(key, rng);
    }
    for (SimulationCheckpoint.NativeUnitRngState state : normalized) {
      next.get(key(state.stream, state.contextId)).restore(
          new NativeUnitRandom.Snapshot(state.lowSeed, state.highSeed));
    }
    streams.clear();
    streams.putAll(next);
  }

  private static List<SimulationCheckpoint.NativeUnitRngState> normalized(
      List<SimulationCheckpoint.NativeUnitRngState> states) {
    Objects.requireNonNull(states, "states");
    TreeMap<Key, SimulationCheckpoint.NativeUnitRngState> unique = new TreeMap<>();
    for (SimulationCheckpoint.NativeUnitRngState state : states) {
      Objects.requireNonNull(state, "nativeUnitRngState");
      Key key = key(state.stream, state.contextId);
      if (unique.put(key, state) != null) {
        throw new IllegalArgumentException("duplicate native RNG stream: " + key);
      }
    }
    return Collections.unmodifiableList(new ArrayList<>(unique.values()));
  }

  private static Key key(String stream, long contextId) {
    if (stream == null || stream.trim().isEmpty()) throw new IllegalArgumentException("stream");
    return new Key(stream, contextId);
  }

  private static final class Key implements Comparable<Key> {
    final String stream;
    final long contextId;

    Key(String stream, long contextId) {
      this.stream = stream;
      this.contextId = contextId;
    }

    @Override public int compareTo(Key other) {
      int streamOrder = stream.compareTo(other.stream);
      return streamOrder != 0 ? streamOrder : Long.compare(contextId, other.contextId);
    }

    @Override public String toString() {
      return stream + "#" + contextId;
    }
  }
}
