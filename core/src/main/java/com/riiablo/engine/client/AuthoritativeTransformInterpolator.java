package com.riiablo.engine.client;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.SimulationClock;

/**
 * Two-snapshot presentation buffer for one authoritative entity transform.
 * It never mutates simulation state; callers sample it only while rendering.
 */
public final class AuthoritativeTransformInterpolator {
  static final float TELEPORT_DISTANCE = 8f;
  static final int MAX_INTERPOLATION_TICKS = 3;

  private final Vector2 fromPosition = new Vector2();
  private final Vector2 toPosition = new Vector2();
  private final Vector2 fromAngle = new Vector2(1f, 0f);
  private final Vector2 toAngle = new Vector2(1f, 0f);
  private long tick;
  private long serverTimeMillis;
  private float elapsed;
  private float duration;
  private boolean hasPosition;
  private boolean hasAngle;

  /**
   * Records a complete or partial transform snapshot. Legacy zero-tick frames
   * and large position discontinuities snap immediately.
   */
  public boolean record(long nextTick, long nextServerTimeMillis,
                        boolean positionPresent, float x, float y,
                        boolean anglePresent, float angleX, float angleY) {
    return record(nextTick, nextServerTimeMillis, positionPresent, x, y,
        anglePresent, angleX, angleY, false);
  }

  public boolean record(long nextTick, long nextServerTimeMillis,
                        boolean positionPresent, float x, float y,
                        boolean anglePresent, float angleX, float angleY,
                        boolean forceSnap) {
    if (nextTick != 0L
        && (nextTick < tick || nextServerTimeMillis < serverTimeMillis)) {
      return false;
    }

    boolean legacy = nextTick == 0L;
    boolean first = tick == 0L && serverTimeMillis == 0L;
    boolean newFrame = !legacy && (nextTick > tick
        || nextServerTimeMillis > serverTimeMillis);
    float nextDuration = newFrame
        ? interpolationDuration(nextTick, nextServerTimeMillis) : duration;

    if (positionPresent) {
      Vector2 current = samplePosition(new Vector2());
      boolean teleport = forceSnap || hasPosition
          && current.dst2(x, y) > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
      if (!hasPosition || first || legacy || teleport) {
        fromPosition.set(x, y);
        toPosition.set(x, y);
      } else if (newFrame) {
        fromPosition.set(current);
        toPosition.set(x, y);
      } else {
        toPosition.set(x, y);
      }
      hasPosition = true;
      if (teleport) nextDuration = 0f;
    }

    if (anglePresent) {
      Vector2 current = sampleAngle(new Vector2());
      if (!hasAngle || first || legacy || forceSnap || nextDuration == 0f) {
        fromAngle.set(angleX, angleY).nor();
        toAngle.set(fromAngle);
      } else if (newFrame) {
        fromAngle.set(current);
        toAngle.set(angleX, angleY).nor();
      } else {
        toAngle.set(angleX, angleY).nor();
      }
      hasAngle = true;
    }

    if (newFrame || first || legacy) {
      elapsed = nextDuration == 0f ? nextDuration : 0f;
      duration = nextDuration;
    }
    if (!legacy) {
      tick = nextTick;
      serverTimeMillis = nextServerTimeMillis;
    }
    return true;
  }

  private float interpolationDuration(long nextTick, long nextServerTimeMillis) {
    long millis = nextServerTimeMillis - serverTimeMillis;
    if (serverTimeMillis != 0L && millis > 0L) {
      return MathUtils.clamp(millis / 1000f, SimulationClock.STEP_SECONDS,
          SimulationClock.STEP_SECONDS * MAX_INTERPOLATION_TICKS);
    }
    long tickGap = tick == 0L ? 1L : Math.max(1L, nextTick - tick);
    return Math.min(tickGap, MAX_INTERPOLATION_TICKS) * SimulationClock.STEP_SECONDS;
  }

  public void advance(float renderDelta) {
    if (!Float.isFinite(renderDelta) || renderDelta <= 0f || duration <= 0f) return;
    elapsed = Math.min(duration, elapsed + renderDelta);
  }

  public Vector2 samplePosition(Vector2 out) {
    if (!hasPosition) return out.setZero();
    return out.set(fromPosition).lerp(toPosition, alpha());
  }

  public Vector2 sampleAngle(Vector2 out) {
    if (!hasAngle) return out.setZero();
    out.set(fromAngle).lerp(toAngle, alpha());
    return out.isZero() ? out.set(toAngle) : out.nor();
  }

  private float alpha() {
    return duration <= 0f ? 1f : MathUtils.clamp(elapsed / duration, 0f, 1f);
  }

  public boolean hasPosition() {
    return hasPosition;
  }

  public boolean hasAngle() {
    return hasAngle;
  }

  public long tick() {
    return tick;
  }
}
