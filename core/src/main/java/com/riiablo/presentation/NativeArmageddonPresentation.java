package com.riiablo.presentation;

import com.badlogic.gdx.math.Vector2;

/** Pinned 1.10f CltPrg92: one-second fp11 graphics descent, independent of combat. */
final class NativeArmageddonPresentation {
  static final int FRAMES = 25;
  static final int SLIDE = 15;
  static final int FALL = 25;
  private NativeArmageddonPresentation() {}

  static Vector2 offset(int age, Vector2 out) {
    int remaining = Math.max(0, FRAMES - Math.max(0, age));
    return out.set(remaining * SLIDE, remaining * FALL);
  }
}
