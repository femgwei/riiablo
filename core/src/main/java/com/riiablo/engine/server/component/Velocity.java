package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;

@PooledWeaver
public class Velocity extends PooledComponent {
  public final Vector2 velocity = new Vector2();
  public float walkSpeed;
  public float runSpeed;

  /** Movement modifier calculated from active server states. */
  public float stateSpeedMultiplier = 1f;
  /** Whether active states completely prevent movement. */
  public boolean stateMovementLocked;

  @Override
  protected void reset() {
    velocity.setZero();
    walkSpeed = 0;
    runSpeed = 0;
    stateSpeedMultiplier = 1f;
    stateMovementLocked = false;
  }

  public Velocity set(float walkSpeed, float runSpeed) {
    this.walkSpeed = walkSpeed;
    this.runSpeed = runSpeed;
    return this;
  }
}
