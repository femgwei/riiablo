package com.riiablo.engine.replay;

import com.riiablo.engine.server.NativeRng;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Authoritative registry for checkpointable {@link NativeRng} streams.
 *
 * <p>Stream identity is the pair {@code (stream, contextId)}. Checkpoint
 * restore validates every state before mutating the registry, and missing
 * streams are recreated directly from their captured state so a new process
 * does not need the original seed derivation.</p>
 *
 * <p>This registry covers the compatibility one-word {@link NativeRng}
 * algorithm only. D2Common's two-word {@code pSeed} streams remain owned by
 * {@code NativeUnitRandom} and require a separate native-unit adapter.</p>
 */
public final class CheckpointRngRegistry {
  private final TreeMap<Key, NativeRng> streams = new TreeMap<>();

  /** Registers a stream under the identity carried by its snapshot. */
  public synchronized void register(NativeRng rng) {
    Objects.requireNonNull(rng, "rng");
    register(rng.streamName(), rng.contextId(), rng);
  }

  /** Registers a stream with an explicit identity check. */
  public synchronized void register(String stream, long contextId, NativeRng rng) {
    Objects.requireNonNull(rng, "rng");
    Key key = key(stream, contextId);
    if (!rng.streamName().equals(stream) || rng.contextId() != contextId) {
      throw new IllegalArgumentException("RNG stream identity mismatch");
    }
    if (streams.putIfAbsent(key, rng) != null) {
      throw new IllegalArgumentException("RNG stream already registered: " + key);
    }
  }

  /** Returns the registered stream, or {@code null} when it is not present. */
  public synchronized NativeRng get(String stream, long contextId) {
    return streams.get(key(stream, contextId));
  }

  /** Captures all registered streams in stable identity order. */
  public synchronized List<SimulationCheckpoint.RngState> snapshot() {
    List<SimulationCheckpoint.RngState> result = new ArrayList<>(streams.size());
    for (Map.Entry<Key, NativeRng> entry : streams.entrySet()) {
      NativeRng.Snapshot state = entry.getValue().snapshot();
      result.add(new SimulationCheckpoint.RngState(
          state.stream, state.contextId, state.state, state.drawCount));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Validates a checkpoint payload without mutating registered streams.
   * Duplicate identities and malformed states are rejected before ECS restore
   * begins.
   */
  public synchronized void validate(List<SimulationCheckpoint.RngState> states) {
    normalized(states);
  }

  /**
   * Restores the complete registry atomically from checkpoint states.
   * Existing streams are restored in place; missing streams are recreated from
   * their captured state. Streams absent from the checkpoint are removed from
   * the registry because the checkpoint is authoritative for this registry.
   */
  public synchronized void restore(List<SimulationCheckpoint.RngState> states) {
    List<SimulationCheckpoint.RngState> normalized = normalized(states);
    TreeMap<Key, NativeRng> next = new TreeMap<>();
    for (SimulationCheckpoint.RngState state : normalized) {
      Key key = key(state.stream, state.contextId);
      NativeRng rng = streams.get(key);
      if (rng == null) rng = fromState(state);
      next.put(key, rng);
    }

    // NativeRng.restore validates before changing state. All factories above
    // have already completed, so no externally visible partial registry is
    // published if payload validation or reconstruction fails.
    for (SimulationCheckpoint.RngState state : normalized) {
      next.get(key(state.stream, state.contextId)).restore(
          new NativeRng.Snapshot(state.stream, state.contextId,
              state.state, state.drawCount));
    }
    streams.clear();
    streams.putAll(next);
  }

  private static List<SimulationCheckpoint.RngState> normalized(
      List<SimulationCheckpoint.RngState> states) {
    Objects.requireNonNull(states, "states");
    TreeMap<Key, SimulationCheckpoint.RngState> unique = new TreeMap<>();
    for (SimulationCheckpoint.RngState state : states) {
      Objects.requireNonNull(state, "rngState");
      Key key = key(state.stream, state.contextId);
      if (unique.put(key, state) != null) {
        throw new IllegalArgumentException("duplicate RNG stream: " + key);
      }
    }
    return Collections.unmodifiableList(new ArrayList<>(unique.values()));
  }

  private static NativeRng fromState(SimulationCheckpoint.RngState state) {
    return NativeRng.fromSnapshot(new NativeRng.Snapshot(
        state.stream, state.contextId, state.state, state.drawCount));
  }

  private static Key key(String stream, long contextId) {
    if (stream == null || stream.trim().isEmpty()) {
      throw new IllegalArgumentException("stream");
    }
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
