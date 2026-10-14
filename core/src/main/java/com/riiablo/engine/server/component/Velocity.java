package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;

@PooledWeaver
public class Velocity extends PooledComponent {
  /** Native 8.8 velocity * 16 / 65536 subtiles per tick, at 25 ticks/sec. */
  public static final float PLAYER_NATIVE_SPEED_SCALE = 25f / 16f;
  /** D2Game initializes monsters with STAT_VELOCITYPERCENT=75. */
  public static final float MONSTER_BASE_SPEED_MULTIPLIER = 0.75f;
  /** D2Common clamps the combined movement percentage to at least 25. */
  public static final float MIN_SPEED_MULTIPLIER = 0.25f;

  public final Vector2 velocity = new Vector2();
  public float walkSpeed;
  public float runSpeed;
  public boolean nativePlayerSpeed;
  public boolean movementRunning;
  public float nativePlayerVelocityBonus;

  /** Base percentage for the unit type: players use 100%, monsters use 75%. */
  public float baseSpeedMultiplier = 1f;
  /** Temporary AI velocity bonus installed for the current movement mode. */
  public float modeSpeedBonusMultiplier;

  /** Movement modifier calculated from active server states. */
  public float stateSpeedMultiplier = 1f;
  /** Whether active states completely prevent movement. */
  public boolean stateMovementLocked;

  @Override
  protected void reset() {
    velocity.setZero();
    walkSpeed = 0;
    runSpeed = 0;
    nativePlayerSpeed = false;
    movementRunning = false;
    nativePlayerVelocityBonus = 0f;
    baseSpeedMultiplier = 1f;
    modeSpeedBonusMultiplier = 0f;
    stateSpeedMultiplier = 1f;
    stateMovementLocked = false;
  }

  public Velocity set(float walkSpeed, float runSpeed) {
    nativePlayerSpeed = false;
    movementRunning = false;
    nativePlayerVelocityBonus = 0f;
    this.walkSpeed = walkSpeed;
    this.runSpeed = runSpeed;
    baseSpeedMultiplier = 1f;
    modeSpeedBonusMultiplier = 0f;
    return this;
  }

  /** Converts CharStats walk/run values to world subtiles per second. */
  public Velocity setPlayer(float nativeWalkSpeed, float nativeRunSpeed) {
    set(nativeWalkSpeed * PLAYER_NATIVE_SPEED_SCALE,
        nativeRunSpeed * PLAYER_NATIVE_SPEED_SCALE);
    nativePlayerSpeed = true;
    return this;
  }

  /**
   * Initializes native monster movement. MonStats.Run is an animation-rate
   * reference in D2Common; both walk and run displacement use Velocity.
   */
  public Velocity setMonster(float baseVelocity) {
    walkSpeed = baseVelocity;
    runSpeed = baseVelocity;
    baseSpeedMultiplier = MONSTER_BASE_SPEED_MULTIPLIER;
    modeSpeedBonusMultiplier = 0f;
    return this;
  }

  /** Mirrors the temporary STAT_VELOCITYPERCENT added by AITACTICS_SetVelocity. */
  public Velocity setModeSpeedBonusPercent(float bonusPercent) {
    modeSpeedBonusMultiplier = bonusPercent / 100f;
    return this;
  }

  public Velocity clearModeSpeedBonus() {
    modeSpeedBonusMultiplier = 0f;
    return this;
  }

  public float speed(boolean running) {
    float baseSpeed = running ? runSpeed : walkSpeed;
    float multiplier = Math.max(
        MIN_SPEED_MULTIPLIER,
        baseSpeedMultiplier + modeSpeedBonusMultiplier);
    return baseSpeed * multiplier;
  }

  /** Applied once by the position integrator, never baked into requested velocity. */
  public float movementMultiplier() {
    if (stateMovementLocked) return 0f;
    if (nativePlayerSpeed && movementRunning && runSpeed > 0f) {
      return Math.max(0.25f * walkSpeed / runSpeed,
          1f + nativePlayerVelocityBonus / 100f * walkSpeed / runSpeed);
    }
    return Math.max(0f, stateSpeedMultiplier);
  }
}
