package com.riiablo.engine.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable authoritative checkpoint used by replay diagnostics.
 *
 * <p>The checkpoint deliberately stores digests rather than mutable ECS
 * objects.  It is therefore safe to retain across reconnects and can report
 * a mismatch at the first tick/entity/field without becoming a save format.
 */
public final class SimulationCheckpoint {
  public static final class RngState {
    public final String stream;
    public final long contextId;
    public final int state;
    public final long drawCount;

    public RngState(String stream, long contextId, int state, long drawCount) {
      if (stream == null || stream.trim().isEmpty()) throw new IllegalArgumentException("stream");
      if (drawCount < 0L) throw new IllegalArgumentException("drawCount");
      this.stream = stream;
      this.contextId = contextId;
      this.state = state;
      this.drawCount = drawCount;
    }

    String canonical() {
      return encode(stream) + ":" + contextId + ":" + state + ":" + drawCount;
    }

    @Override public boolean equals(Object other) {
      if (this == other) return true;
      if (!(other instanceof RngState)) return false;
      RngState that = (RngState) other;
      return contextId == that.contextId && state == that.state
          && drawCount == that.drawCount && stream.equals(that.stream);
    }

    @Override public int hashCode() {
      return Objects.hash(stream, contextId, state, drawCount);
    }
  }

  public static final class Mismatch {
    public final long tick;
    public final int entityId;
    public final String field;
    public final String expected;
    public final String actual;

    private Mismatch(long tick, int entityId, String field, String expected, String actual) {
      this.tick = tick;
      this.entityId = entityId;
      this.field = field;
      this.expected = expected;
      this.actual = actual;
    }

    @Override public String toString() {
      return "tick=" + tick + " entity=" + entityId + " field=" + field
          + " expected=" + expected + " actual=" + actual;
    }
  }

  private final long tick;
  private final String worldDigest;
  private final Map<Integer, String> entityDigests;
  private final java.util.List<RngState> rngStates;

  private SimulationCheckpoint(long tick, String worldDigest,
      Map<Integer, String> entityDigests, java.util.List<RngState> rngStates) {
    if (tick < 0L) throw new IllegalArgumentException("tick");
    if (worldDigest == null || worldDigest.trim().isEmpty()) {
      throw new IllegalArgumentException("worldDigest");
    }
    this.tick = tick;
    this.worldDigest = worldDigest;
    TreeMap<Integer, String> entities = new TreeMap<>();
    if (entityDigests != null) {
      for (Map.Entry<Integer, String> entry : entityDigests.entrySet()) {
        if (entry.getKey() == null || entry.getKey() < 0) throw new IllegalArgumentException("entityId");
        if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
          throw new IllegalArgumentException("entityDigest");
        }
        entities.put(entry.getKey(), entry.getValue());
      }
    }
    this.entityDigests = Collections.unmodifiableMap(entities);
    ArrayList<RngState> streams = new ArrayList<>();
    if (rngStates != null) streams.addAll(rngStates);
    for (RngState state : streams) Objects.requireNonNull(state, "rngState");
    streams.sort((a, b) -> a.canonical().compareTo(b.canonical()));
    this.rngStates = Collections.unmodifiableList(streams);
  }

  public static SimulationCheckpoint of(long tick, String worldDigest,
      Map<Integer, String> entityDigests, java.util.List<RngState> rngStates) {
    return new SimulationCheckpoint(tick, worldDigest, entityDigests, rngStates);
  }

  public long tick() { return tick; }
  public String worldDigest() { return worldDigest; }
  public Map<Integer, String> entityDigests() { return entityDigests; }
  public java.util.List<RngState> rngStates() { return rngStates; }

  /** Stable field order for replay manifests and cross-process comparisons. */
  public String canonical() {
    StringBuilder result = new StringBuilder("checkpoint|" + tick + "|" + encode(worldDigest));
    for (Map.Entry<Integer, String> entity : entityDigests.entrySet()) {
      result.append("|entity:").append(entity.getKey()).append(":")
          .append(encode(entity.getValue()));
    }
    for (RngState state : rngStates) result.append("|rng:").append(state.canonical());
    return result.toString();
  }

  public String digest() { return sha256(canonical()); }

  /**
   * Compares two checkpoints without collapsing the reason into one hash.
   * Each result identifies the tick, entity and field that diverged.
   */
  public java.util.List<Mismatch> diff(SimulationCheckpoint actual) {
    Objects.requireNonNull(actual, "actual");
    ArrayList<Mismatch> mismatches = new ArrayList<>();
    if (tick != actual.tick) mismatches.add(new Mismatch(tick, -1, "tick",
        Long.toString(tick), Long.toString(actual.tick)));
    if (!worldDigest.equals(actual.worldDigest)) mismatches.add(new Mismatch(tick, -1,
        "worldDigest", worldDigest, actual.worldDigest));
    TreeMap<Integer, String> ids = new TreeMap<>(entityDigests);
    ids.putAll(actual.entityDigests);
    for (Integer id : ids.keySet()) {
      String expected = entityDigests.get(id), observed = actual.entityDigests.get(id);
      if (!Objects.equals(expected, observed)) mismatches.add(new Mismatch(tick, id,
          "digest", String.valueOf(expected), String.valueOf(observed)));
    }
    TreeMap<String, RngState> expectedRng = byRng(rngStates);
    TreeMap<String, RngState> actualRng = byRng(actual.rngStates);
    TreeMap<String, RngState> streams = new TreeMap<>(expectedRng);
    streams.putAll(actualRng);
    for (String key : streams.keySet()) {
      RngState expected = expectedRng.get(key), observed = actualRng.get(key);
      if (expected == null || observed == null || expected.state != observed.state
          || expected.drawCount != observed.drawCount) {
        mismatches.add(new Mismatch(tick, -1, "rng:" + key,
            expected == null ? "null" : expected.canonical(),
            observed == null ? "null" : observed.canonical()));
      }
    }
    return Collections.unmodifiableList(mismatches);
  }

  private static TreeMap<String, RngState> byRng(java.util.List<RngState> states) {
    TreeMap<String, RngState> result = new TreeMap<>();
    for (RngState state : states) result.put(state.stream + "#" + state.contextId, state);
    return result;
  }

  static String encode(String value) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte item : digest) hex.append(String.format("%02x", item & 0xff));
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("JRE must provide SHA-256", e);
    }
  }
}
