package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.path.D2PathStep;

@PooledWeaver
public class Position extends PooledComponent {
  public final Vector2 position = new Vector2();

  private boolean nativePrecisionKnown;
  private int nativePrecisionX, nativePrecisionY;
  private float nativeProjectionX, nativeProjectionY;

  /** Explicit native allocation input, never inferred from the legacy vector. */
  public Position initializeNativeCell(int x, int y) {
    if (x < 0 || x > 0xFFFF || y < 0 || y > 0xFFFF) {
      throw new IllegalArgumentException("Native subtile coordinates must be unsigned 16-bit");
    }
    return setNativePrecision(D2PathStep.toFp16Center(x), D2PathStep.toFp16Center(y));
  }

  /**
   * Accepts authoritative raw uint32 bit patterns, including their low words.
   * Host integer coordinates denote cell centres (Map.round), so the display
   * projection subtracts half a cell. This is an adapter convention, not a
   * claim that legacy movement uses native coordinates.
   */
  public Position setNativePrecision(int x, int y) {
    nativePrecisionX = x;
    nativePrecisionY = y;
    nativeProjectionX = projectNativePrecision(x);
    nativeProjectionY = projectNativePrecision(y);
    position.set(nativeProjectionX, nativeProjectionY);
    nativePrecisionKnown = true;
    return this;
  }

  private static float projectNativePrecision(int raw) {
    return (float) (Integer.toUnsignedLong(raw) / 65536.0 - 0.5);
  }

  /** Advance only from explicitly supplied native vectors and waypoint precision. */
  public D2PathStep.StepResult stepNativeUnit(int velocityX, int velocityY,
      int targetX, int targetY, D2PathStep.UnitStepCollision collision) {
    requireNativePrecision();
    D2PathStep.StepResult result = D2PathStep.stepUnit(nativePrecisionX, nativePrecisionY,
        velocityX, velocityY, targetX, targetY, collision);
    setNativePrecision(result.x, result.y);
    return result;
  }

  /** Legacy writes cannot establish precision; detected divergence is latched. */
  public boolean hasNativePrecision() {
    if (nativePrecisionKnown
        && (Float.floatToIntBits(position.x) != Float.floatToIntBits(nativeProjectionX)
            || Float.floatToIntBits(position.y) != Float.floatToIntBits(nativeProjectionY))) {
      invalidateNativePrecision();
    }
    return nativePrecisionKnown;
  }

  public int nativePrecisionX() {
    requireNativePrecision();
    return nativePrecisionX;
  }

  public int nativePrecisionY() {
    requireNativePrecision();
    return nativePrecisionY;
  }

  private void requireNativePrecision() {
    if (!hasNativePrecision()) throw new IllegalStateException("Native position precision is unknown");
  }

  public void invalidateNativePrecision() {
    nativePrecisionKnown = false;
  }

  @Override
  protected void reset() {
    position.setZero();
    nativePrecisionKnown = false;
    nativePrecisionX = nativePrecisionY = 0;
    nativeProjectionX = nativeProjectionY = 0;
  }
}
