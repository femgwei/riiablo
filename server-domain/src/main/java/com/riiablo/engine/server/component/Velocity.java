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
  public boolean nativeMonsterSpeed;
  /** Explicit MonStats integer source; -1 is the legacy world-speed contract. */
  public int nativeMonsterBaseVelocity = -1;
  /** This tick's fixed-point integrator owns position; physics must only follow. */
  public boolean nativeMovementOwned;
  public float nativeMonsterVelocityBonus;

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
    nativeMonsterSpeed = false;
    nativeMonsterBaseVelocity = -1;
    nativeMovementOwned = false;
    nativeMonsterVelocityBonus = 0f;
    baseSpeedMultiplier = 1f;
    modeSpeedBonusMultiplier = 0f;
    stateSpeedMultiplier = 1f;
    stateMovementLocked = false;
  }

  public Velocity set(float walkSpeed, float runSpeed) {
    nativeMonsterBaseVelocity = -1;
    nativeMovementOwned = false;
    nativeMonsterSpeed = false;
    nativeMonsterVelocityBonus = 0f;
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
    set(baseVelocity, baseVelocity);
    nativeMonsterSpeed = true;
    baseSpeedMultiplier = MONSTER_BASE_SPEED_MULTIPLIER;
    modeSpeedBonusMultiplier = 0f;
    return this;
  }

  /** Native table units: (Velocity << 8) * 4096 >> 8 per 25 Hz frame. */
  public Velocity setMonsterFromNativeTable(int baseVelocity) {
    if (baseVelocity < 0 || baseVelocity > 32767)
      throw new IllegalArgumentException("native nonnegative int16 monster velocity required");
    setMonster(baseVelocity * PLAYER_NATIVE_SPEED_SCALE);
    nativeMonsterBaseVelocity = baseVelocity;
    return this;
  }

  /** UNITS_UpdateRunWalkAnimRateAndVelocity: integer percentage then /100. */
  public int nativeMonsterVelocity() {
    if (nativeMonsterBaseVelocity < 0) throw new IllegalStateException("native velocity source unknown");
    if (stateMovementLocked) return 0;
    int percent = Math.max(25, Math.round((baseSpeedMultiplier + modeSpeedBonusMultiplier) * 100f
        + nativeMonsterVelocityBonus));
    long velocity = (long) percent * (nativeMonsterBaseVelocity << 8) / 100;
    if (velocity > Integer.MAX_VALUE / 4096)
      throw new IllegalArgumentException("native direction-vector multiply would overflow");
    return (int) velocity;
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
    if (nativeMonsterSpeed) {
      float requestedPercent = Math.max(MIN_SPEED_MULTIPLIER,
          baseSpeedMultiplier + modeSpeedBonusMultiplier);
      float effectivePercent = Math.max(MIN_SPEED_MULTIPLIER,
          baseSpeedMultiplier + modeSpeedBonusMultiplier + nativeMonsterVelocityBonus / 100f);
      return effectivePercent / requestedPercent * Math.max(0f, stateSpeedMultiplier);
    }
    if (nativePlayerSpeed && movementRunning && runSpeed > 0f) {
      return Math.max(0.25f * walkSpeed / runSpeed,
          1f + nativePlayerVelocityBonus / 100f * walkSpeed / runSpeed);
    }
    return Math.max(0f, stateSpeedMultiplier);
  }
}
