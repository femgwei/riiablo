package com.riiablo.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
    SESSION_NOT_ACTIVE
  }

  public static final class Session {
    public final int connectionId;
    public final int actorId;
    public final String stableKey;
    public final State state;
    public final long connectedTick;
    public final long disconnectedTick;

    private Session(int connectionId, int actorId, String stableKey, State state,
        long connectedTick, long disconnectedTick) {
      this.connectionId = connectionId;
      this.actorId = actorId;
      this.stableKey = stableKey;
      this.state = state;
      this.connectedTick = connectedTick;
      this.disconnectedTick = disconnectedTick;
    }

    private Session active(int connectionId, int actorId, long tick) {
      return new Session(connectionId, actorId, stableKey, State.ACTIVE, tick, -1L);
    }

    private Session disconnected(long tick) {
      return new Session(connectionId, actorId, stableKey, State.DISCONNECTED,
          connectedTick, tick);
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
    Session session = new Session(connectionId, actorId, stableKey, State.ACTIVE, tick, -1L);
    byConnection.put(connectionId, session);
    byStableKey.put(stableKey, session);
    return Result.accepted(session);
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
}
