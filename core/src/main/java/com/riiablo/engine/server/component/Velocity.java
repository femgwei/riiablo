package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;

@PooledWeaver
public class Velocity extends PooledComponent {
  /** D2Game initializes monsters with STAT_VELOCITYPERCENT=75. */
  public static final float MONSTER_BASE_SPEED_MULTIPLIER = 0.75f;
  /** D2Common clamps the combined movement percentage to at least 25. */
  public static final float MIN_SPEED_MULTIPLIER = 0.25f;

  public final Vector2 velocity = new Vector2();
  public float walkSpeed;
  public float runSpeed;

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
    baseSpeedMultiplier = 1f;
    modeSpeedBonusMultiplier = 0f;
    stateSpeedMultiplier = 1f;
    stateMovementLocked = false;
  }

  public Velocity set(float walkSpeed, float runSpeed) {
    this.walkSpeed = walkSpeed;
    this.runSpeed = runSpeed;
    baseSpeedMultiplier = 1f;
    modeSpeedBonusMultiplier = 0f;
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
}
