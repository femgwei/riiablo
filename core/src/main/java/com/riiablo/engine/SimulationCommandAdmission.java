package com.riiablo.engine;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Validates command metadata before a command enters the fixed-tick queue.
 *
 * <p>The default policy preserves local UI compatibility. Network-adapted
 * commands must use {@link #network(BiPredicate)} so actor and request
 * identity are mandatory and target ownership can be checked by the caller.
 */
public final class SimulationCommandAdmission {
  public enum Rejection {
    NONE,
    STALE_TICK,
    MISSING_ACTOR,
    MISSING_REQUEST,
    INVALID_TARGET,
    UNAUTHORIZED_TARGET,
    DUPLICATE_REQUEST
  }

  private final boolean requireNetworkIdentity;
  private final BiPredicate<SimulationCommandEnvelope, Integer> targetAuthorizer;

  private SimulationCommandAdmission(boolean requireNetworkIdentity,
      BiPredicate<SimulationCommandEnvelope, Integer> targetAuthorizer) {
    this.requireNetworkIdentity = requireNetworkIdentity;
    this.targetAuthorizer = targetAuthorizer;
  }

  public static SimulationCommandAdmission local() {
    return new SimulationCommandAdmission(false, (command, ignored) -> true);
  }

  public static SimulationCommandAdmission network(
      BiPredicate<SimulationCommandEnvelope, Integer> targetAuthorizer) {
    return new SimulationCommandAdmission(true,
        Objects.requireNonNull(targetAuthorizer, "targetAuthorizer"));
  }

  Rejection validate(SimulationCommandEnvelope command, long currentTick) {
    if (command.tick <= currentTick) return Rejection.STALE_TICK;
    if (command.targetEntityId < -1) return Rejection.INVALID_TARGET;
    if (!requireNetworkIdentity) return Rejection.NONE;
    if (command.authority != SimulationCommandEnvelope.Authority.NETWORK_INPUT
        || command.actorId < 0) return Rejection.MISSING_ACTOR;
    if (command.requestId <= 0L) return Rejection.MISSING_REQUEST;
    if (command.targetEntityId >= 0 && !targetAuthorizer.test(command, command.targetEntityId)) {
      return Rejection.UNAUTHORIZED_TARGET;
    }
    return Rejection.NONE;
  }
}
