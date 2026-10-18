package com.riiablo.engine.server;

/** Pure 1.10f UNITS_UpdateRunWalkAnimRateAndVelocity player rules. */
public final class NativePlayerMovement {
  private NativePlayerMovement() {}

  public static long bonus(int equipmentFrw, int velocityBonus) {
    long frw = Math.max(0, equipmentFrw);
    return frw * 150L / (frw + 150L) + velocityBonus;
  }

  public static int percent(float walkSpeed, float runSpeed, boolean running, float bonus) {
    int base = running && walkSpeed > 0 ? (int) (100d * runSpeed / walkSpeed) : 100;
    return Math.max(25, base + (int) bonus);
  }

  public static int animationRate(float walkSpeed, float runSpeed, boolean running, float bonus) {
    return (int) ((long) (running ? 101 : 213)
        * percent(walkSpeed, runSpeed, running, bonus) / 100);
  }

  public static float movementMultiplier(float walkSpeed, float runSpeed, boolean running, float bonus) {
    float requested = running ? runSpeed : walkSpeed;
    return requested <= 0 ? 0 : walkSpeed * percent(walkSpeed, runSpeed, running, bonus)
        / 100f / requested;
  }
}
