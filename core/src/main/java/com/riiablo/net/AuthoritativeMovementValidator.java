package com.riiablo.net;

import com.riiablo.engine.SimulationClock;

/** Stateless validation for untrusted client movement samples. */
public final class AuthoritativeMovementValidator {
  private static final float POSITION_TOLERANCE = 0.35f;
  private static final float SPEED_TOLERANCE = 0.5f;
  private static final long MAX_ACCUMULATED_TICKS = 64L;

  private AuthoritativeMovementValidator() {}

  public interface CollisionQuery {
    boolean blocked(float fromX, float fromY, float toX, float toY);
  }

  public enum Result {
    ACCEPTED,
    INVALID_NUMBER,
    EXCESSIVE_SPEED,
    EXCESSIVE_DISPLACEMENT,
    COLLISION
  }

  public static Result validate(float fromX, float fromY, float requestedX, float requestedY,
      float velocityX, float velocityY, float maximumSpeed, long sequenceAdvance,
      CollisionQuery collision) {
    if (!finite(fromX) || !finite(fromY) || !finite(requestedX) || !finite(requestedY)
        || !finite(velocityX) || !finite(velocityY) || !finite(maximumSpeed)) {
      return Result.INVALID_NUMBER;
    }
    float trustedMaximumSpeed = Math.max(0f, maximumSpeed);
    float velocity2 = velocityX * velocityX + velocityY * velocityY;
    float allowedVelocity = trustedMaximumSpeed + SPEED_TOLERANCE;
    if (velocity2 > allowedVelocity * allowedVelocity) return Result.EXCESSIVE_SPEED;

    long ticks = Math.max(1L, Math.min(MAX_ACCUMULATED_TICKS, sequenceAdvance));
    // One additional tick tolerates the server advancing the previous velocity
    // between receipt of adjacent client samples.
    float allowedDistance = trustedMaximumSpeed * SimulationClock.STEP_SECONDS
        * (ticks + 1L) + POSITION_TOLERANCE;
    float dx = requestedX - fromX;
    float dy = requestedY - fromY;
    if (dx * dx + dy * dy > allowedDistance * allowedDistance) {
      return Result.EXCESSIVE_DISPLACEMENT;
    }
    if ((dx != 0f || dy != 0f) && collision != null
        && collision.blocked(fromX, fromY, requestedX, requestedY)) {
      return Result.COLLISION;
    }
    return Result.ACCEPTED;
  }

  private static boolean finite(float value) {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }
}
