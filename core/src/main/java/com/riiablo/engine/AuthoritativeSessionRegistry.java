package com.riiablo.engine;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Owns the authenticated connection-to-actor identity boundary for a D2GS
 * session. Socket threads may only establish or close this identity; command
 * admission remains on the fixed-tick owner.
 */
public final class AuthoritativeSessionRegistry {
  public enum State { ACTIVE, DISCONNECTED }
  public enum Rejection {
    NONE,
    INVALID_CONNECTION,
    INVALID_ACTOR,
    INVALID_STABLE_KEY,
    CONNECTION_IN_USE,
    STABLE_KEY_ACTIVE,
    UNKNOWN_CONNECTION,
    SESSION_NOT_ACTIVE,
    GENERATION_MISMATCH,
    INVALID_SNAPSHOT
  }

  public static final class Session {
    public final int connectionId;
    public final int actorId;
    public final String stableKey;
    public final State state;
    public final long connectedTick;
    public final long disconnectedTick;
    /** Monotonic authority generation for this stable identity. */
    public final long generation;

    private Session(int connectionId, int actorId, String stableKey, State state,
        long connectedTick, long disconnectedTick, long generation) {
      this.connectionId = connectionId;
      this.actorId = actorId;
      this.stableKey = stableKey;
      this.state = state;
      this.connectedTick = connectedTick;
      this.disconnectedTick = disconnectedTick;
      this.generation = generation;
    }

    /** Rehydrates one validated session record from a process-independent checkpoint. */
    public static Session of(int connectionId, int actorId, String stableKey, State state,
        long connectedTick, long disconnectedTick, long generation) {
      return new Session(connectionId, actorId, stableKey, state, connectedTick,
          disconnectedTick, generation);
    }

    private Session disconnected(long tick) {
      return new Session(connectionId, actorId, stableKey, State.DISCONNECTED,
          connectedTick, tick, generation);
    }
  }

  /** Immutable cross-process authority payload. */
  public static final class Snapshot {
    private final List<Session> sessions;

    private Snapshot(List<Session> sessions) {
      this.sessions = Collections.unmodifiableList(new ArrayList<>(sessions));
    }

    /** Rehydrates and validates an immutable authority snapshot from a checkpoint. */
    public static Snapshot of(List<Session> sessions) {
      Snapshot snapshot = new Snapshot(sessions == null
          ? Collections.emptyList() : sessions);
      AuthoritativeSessionRegistry validator = new AuthoritativeSessionRegistry();
      validator.restore(snapshot);
      return snapshot;
    }

    public List<Session> sessions() { return sessions; }

    public Session stableKey(String stableKey) {
      if (stableKey == null) return null;
      Session latest = null;
      for (Session session : sessions) {
        if (stableKey.equals(session.stableKey)
            && (latest == null || session.generation > latest.generation)) latest = session;
      }
      return latest;
    }

    public String canonical() {
      StringBuilder result = new StringBuilder("session-authority-v1");
      for (Session session : sessions) {
        result.append('|').append(session.connectionId).append(':')
            .append(session.actorId).append(':').append(session.stableKey).append(':')
            .append(session.state).append(':').append(session.connectedTick).append(':')
            .append(session.disconnectedTick).append(':').append(session.generation);
      }
      return result.toString();
    }
  }

  public static final class Result {
    public final boolean accepted;
    public final Rejection rejection;
    public final Session session;

    private Result(boolean accepted, Rejection rejection, Session session) {
      this.accepted = accepted;
      this.rejection = rejection;
      this.session = session;
    }

    static Result accepted(Session session) {
      return new Result(true, Rejection.NONE, session);
    }

    static Result rejected(Rejection rejection) {
      return new Result(false, rejection, null);
    }
  }

  private final Map<Integer, Session> byConnection = new HashMap<>();
  private final Map<String, Session> byStableKey = new HashMap<>();

  public synchronized Result connect(int connectionId, String stableKey, int actorId, long tick) {
    if (connectionId < 0) return Result.rejected(Rejection.INVALID_CONNECTION);
    if (actorId < 0) return Result.rejected(Rejection.INVALID_ACTOR);
    if (stableKey == null || stableKey.trim().isEmpty()) {
      return Result.rejected(Rejection.INVALID_STABLE_KEY);
    }
    if (tick < 0L) throw new IllegalArgumentException("tick");
    Session currentConnection = byConnection.get(connectionId);
    if (currentConnection != null && currentConnection.state == State.ACTIVE) {
      return Result.rejected(Rejection.CONNECTION_IN_USE);
    }
    Session currentKey = byStableKey.get(stableKey);
    if (currentKey != null && currentKey.state == State.ACTIVE) {
      return Result.rejected(Rejection.STABLE_KEY_ACTIVE);
    }
    if (currentConnection != null) byConnection.remove(connectionId);
    long generation = nextGeneration(currentKey);
    Session session = new Session(connectionId, actorId, stableKey, State.ACTIVE, tick, -1L,
        generation);
    byConnection.put(connectionId, session);
    byStableKey.put(stableKey, session);
    return Result.accepted(session);
  }

  /**
   * Takes over an existing stable identity after a process restart.  The old
   * active connection is retained as disconnected, so packets arriving from
   * that connection cannot be admitted after the handoff.
   */
  public synchronized Result rebind(int connectionId, String stableKey, int actorId, long tick) {
    if (connectionId < 0) return Result.rejected(Rejection.INVALID_CONNECTION);
    if (actorId < 0) return Result.rejected(Rejection.INVALID_ACTOR);
    if (stableKey == null || stableKey.trim().isEmpty()) {
      return Result.rejected(Rejection.INVALID_STABLE_KEY);
    }
    if (tick < 0L) throw new IllegalArgumentException("tick");
    Session currentConnection = byConnection.get(connectionId);
    if (currentConnection != null && currentConnection.state == State.ACTIVE
        && !stableKey.equals(currentConnection.stableKey)) {
      return Result.rejected(Rejection.CONNECTION_IN_USE);
    }
    Session currentKey = byStableKey.get(stableKey);
    if (currentKey != null && currentKey.state == State.ACTIVE
        && currentKey.connectionId != connectionId) {
      byConnection.put(currentKey.connectionId, currentKey.disconnected(tick));
    }
    if (currentConnection != null) byConnection.remove(connectionId);
    long generation = nextGeneration(currentKey);
    Session session = new Session(connectionId, actorId, stableKey, State.ACTIVE, tick, -1L,
        generation);
    byConnection.put(connectionId, session);
    byStableKey.put(stableKey, session);
    return Result.accepted(session);
  }

  /** Rebinds only when the caller still owns the expected checkpoint generation. */
  public synchronized Result rebind(int connectionId, String stableKey, int actorId, long tick,
      long expectedGeneration) {
    Session current = stableKey == null ? null : byStableKey.get(stableKey);
    if (current == null || current.generation != expectedGeneration) {
      return Result.rejected(Rejection.GENERATION_MISMATCH);
    }
    return rebind(connectionId, stableKey, actorId, tick);
  }

  public synchronized Result disconnect(int connectionId, long tick) {
    if (connectionId < 0) return Result.rejected(Rejection.INVALID_CONNECTION);
    if (tick < 0L) throw new IllegalArgumentException("tick");
    Session current = byConnection.get(connectionId);
    if (current == null) return Result.rejected(Rejection.UNKNOWN_CONNECTION);
    if (current.state != State.ACTIVE) return Result.rejected(Rejection.SESSION_NOT_ACTIVE);
    Session disconnected = current.disconnected(tick);
    byConnection.put(connectionId, disconnected);
    byStableKey.put(disconnected.stableKey, disconnected);
    return Result.accepted(disconnected);
  }

  public synchronized Session connection(int connectionId) {
    return byConnection.get(connectionId);
  }

  public synchronized Session stableKey(String stableKey) {
    return stableKey == null ? null : byStableKey.get(stableKey);
  }

  public synchronized Session activeConnection(int connectionId) {
    Session session = byConnection.get(connectionId);
    return session != null && session.state == State.ACTIVE ? session : null;
  }

  public synchronized int activeCount() {
    int count = 0;
    for (Session session : byConnection.values()) {
      if (session.state == State.ACTIVE) count++;
    }
    return count;
  }

  /** Captures all connection and stable-key entries in deterministic order. */
  public synchronized Snapshot snapshot() {
    List<Session> sessions = new ArrayList<>(byConnection.values());
    sessions.sort(Comparator.comparingInt(session -> session.connectionId));
    return new Snapshot(sessions);
  }

  /** Restores authority atomically; invalid snapshots leave this registry untouched. */
  public synchronized void restore(Snapshot snapshot) {
    if (snapshot == null) throw new NullPointerException("snapshot");
    Map<Integer, Session> restoredConnections = new HashMap<>();
    Map<String, Session> restoredKeys = new HashMap<>();
    Map<String, java.util.Set<Long>> generations = new HashMap<>();
    try {
      for (Session session : snapshot.sessions()) {
        validateSnapshotSession(session);
        if (restoredConnections.put(session.connectionId, session) != null) {
          throw new IllegalArgumentException("duplicate connection");
        }
        if (!generations.computeIfAbsent(session.stableKey, key -> new java.util.HashSet<>())
            .add(session.generation)) {
          throw new IllegalArgumentException("duplicate stable-key generation");
        }
        Session latest = restoredKeys.get(session.stableKey);
        if (latest == null || latest.generation < session.generation) {
          restoredKeys.put(session.stableKey, session);
        }
      }
      for (Session session : restoredConnections.values()) {
        if (session.state == State.ACTIVE && restoredKeys.get(session.stableKey) != session) {
          throw new IllegalArgumentException("active session is not latest generation");
        }
      }
    } catch (RuntimeException e) {
      throw new IllegalArgumentException(Rejection.INVALID_SNAPSHOT.name(), e);
    }
    byConnection.clear();
    byConnection.putAll(restoredConnections);
    byStableKey.clear();
    byStableKey.putAll(restoredKeys);
  }

  private static long nextGeneration(Session current) {
    if (current == null) return 1L;
    if (current.generation == Long.MAX_VALUE) throw new IllegalStateException("generation overflow");
    return current.generation + 1L;
  }

  private static void validateSnapshotSession(Session session) {
    if (session == null || session.state == null || session.connectionId < 0 || session.actorId < 0
        || session.stableKey == null || session.stableKey.trim().isEmpty()
        || session.connectedTick < 0L || session.generation <= 0L) {
      throw new IllegalArgumentException("invalid session");
    }
    if (session.state == State.ACTIVE && session.disconnectedTick != -1L) {
      throw new IllegalArgumentException("active disconnected tick");
    }
    if (session.state == State.DISCONNECTED
        && (session.disconnectedTick < session.connectedTick)) {
      throw new IllegalArgumentException("disconnected tick");
    }
  }
}
