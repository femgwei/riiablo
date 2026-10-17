package com.riiablo.engine.server;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/** D2MOO SkillDruid.cpp SrvDo136 and Units.cpp UNITS_GetDistanceToCoordinates. */
public final class NativeDeathMaul {
  private NativeDeathMaul() {}

  public static int distance(Vector2 source, Vector2 target) {
    int dx = Math.abs(MathUtils.floor(source.x) - MathUtils.floor(target.x));
    int dy = Math.abs(MathUtils.floor(source.y) - MathUtils.floor(target.y));
    return Math.max(dx, dy) + Math.min(dx, dy) / 2;
  }

  public static int lifetime(int total, int distance) {
    if (distance < 1) return total / 2 + total / 24;
    if (distance >= 10) return total * distance / 12;
    return total / 2 + total * distance / 24;
  }
}
