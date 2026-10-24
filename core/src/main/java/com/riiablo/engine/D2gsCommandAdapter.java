package com.riiablo.engine;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Protocol-neutral D2GS ingress adapter. It binds socket connection identity
 * to an admitted command and exposes only fixed-tick queue operations to the
 * authoritative simulation.
 */
public final class D2gsCommandAdapter {
  public enum Rejection {
    NONE, SESSION_NOT_ACTIVE, STALE_SESSION, INVALID_REQUEST
  }

  public static final class Result {
    public final boolean accepted;
    public final Rejection rejection;
    public final SimulationCommandAdmission.Rejection admissionRejection;
    public final SimulationCommandEnvelope command;

    private Result(boolean accepted, Rejection rejection,
        SimulationCommandAdmission.Rejection admissionRejection,
        SimulationCommandEnvelope command) {
      this.accepted = accepted;
      this.rejection = rejection;
      this.admissionRejection = admissionRejection;
      this.command = command;
    }

    private static Result rejected(Rejection rejection) {
      return new Result(false, rejection, SimulationCommandAdmission.Rejection.NONE, null);
    }

    private static Result rejected(SimulationCommandAdmission.Rejection rejection) {
      return new Result(false, Rejection.NONE, rejection, null);
    }

    private static Result accepted(SimulationCommandEnvelope command) {
      return new Result(true, Rejection.NONE, SimulationCommandAdmission.Rejection.NONE, command);
    }
  }

  private final AuthoritativeSessionRegistry sessions;
  private final SimulationCommandQueue commands;
  private final String source;

  public D2gsCommandAdapter(AuthoritativeSessionRegistry sessions, String source,
      BiPredicate<SimulationCommandEnvelope, Integer> targetAuthorizer) {
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.source = requireText(source, "source");
    this.commands = new SimulationCommandQueue(SimulationCommandAdmission.network(
        Objects.requireNonNull(targetAuthorizer, "targetAuthorizer")));
  }

  public AuthoritativeSessionRegistry.Result connect(int connectionId, String stableKey,
      int actorId, long tick) {
    return sessions.connect(connectionId, stableKey, actorId, tick);
  }

  public AuthoritativeSessionRegistry.Result disconnect(int connectionId, long tick) {
    AuthoritativeSessionRegistry.Result result = sessions.disconnect(connectionId, tick);
    if (result.accepted) commands.cancelActor(result.session.actorId);
    return result;
  }

  /** Takes over a stable identity retained by a restored checkpoint. */
  public AuthoritativeSessionRegistry.Result rebind(int connectionId, String stableKey,
      int actorId, long tick) {
    return sessions.rebind(connectionId, stableKey, actorId, tick);
  }

  /** Takes over a stable identity only if the checkpoint generation still matches. */
  public AuthoritativeSessionRegistry.Result rebind(int connectionId, String stableKey,
      int actorId, long tick, long expectedGeneration) {
    return sessions.rebind(connectionId, stableKey, actorId, tick, expectedGeneration);
  }

  public Result submit(int connectionId, String kind, Object payload, long tick,
      long requestId, int targetEntityId) {
    return submit(connectionId, 0L, kind, payload, tick, requestId, targetEntityId);
  }

  /**
   * Admits a command with an optional connection generation.  A non-zero
   * generation is required by reconnecting transports so a delayed packet
   * from the previous owner cannot enter the new session.
   */
  public Result submit(int connectionId, long generation, String kind, Object payload, long tick,
      long requestId, int targetEntityId) {
    if (requestId <= 0L) return Result.rejected(Rejection.INVALID_REQUEST);
    AuthoritativeSessionRegistry.Session session = sessions.activeConnection(connectionId);
    if (session == null) return Result.rejected(Rejection.SESSION_NOT_ACTIVE);
    if (generation > 0L && session.generation != generation) {
      return Result.rejected(Rejection.STALE_SESSION);
    }
    SimulationCommandEnvelope.Builder builder = SimulationCommandEnvelope.builder(
        source, requireText(kind, "kind"), Objects.requireNonNull(payload, "payload"))
        .tick(tick).actor(session.actorId).request(requestId)
        .authority(SimulationCommandEnvelope.Authority.NETWORK_INPUT);
    if (targetEntityId >= 0) builder.target(targetEntityId);
    SimulationCommandEnvelope command = commands.submit(builder);
    return command == null ? Result.rejected(commands.lastRejection()) : Result.accepted(command);
  }

  public int drain(long tick, Consumer<SimulationCommandEnvelope> consumer) {
    return commands.drain(tick, consumer);
  }

  public int pendingCount() { return commands.pendingCount(); }
  public long lastDrainedTick() { return commands.lastDrainedTick(); }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
    return value;
  }
}
