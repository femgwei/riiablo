package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.Vector2;

/** PathMisc.cpp sub_6FD5CEB0: signed 8.8 velocity increments every fifth step. */
public final class NativeMissileAcceleration {
  private int counter;
  private int acceleration;
  private int maximum;

  public void initialize(int acceleration, int maximumVelocity) {
    this.acceleration = acceleration;
    maximum = maximumVelocity;
    counter = 0;
  }

  public void advance(Vector2 velocity) {
    if (acceleration == 0 || ++counter < 5) return;
    counter = 0;
    float speed = velocity.len() + acceleration / 256f;
    if (speed > maximum) {
      speed = maximum;
      acceleration = 0;
    }
    velocity.setLength(Math.max(0f, speed));
  }
}
