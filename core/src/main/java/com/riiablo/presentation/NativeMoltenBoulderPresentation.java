package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.NativeMoltenBoulder;

/** Emerge and roll use one integer animation frame per native 25 Hz tick. */
final class NativeMoltenBoulderPresentation {
  private static final int[] DIRECTIONS = {4, 0, 5, 1, 6, 2, 7, 3};
  private NativeMoltenBoulderPresentation() {}

  static boolean handles(Missile m) {
    return m != null && NativeMoltenBoulder.moves(m.missile);
  }

  static void initialize(Missile m, Animation animation) {
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    int age = m.authoritative ? m.nativeFrame : Math.max(m.nativeFrame, m.clientFrame);
    animation.setFrameDelta(256);
    animation.setFrame(Math.floorMod(Math.max(0, age), frames));
  }

  static int rockFrame(int frames, int lifetime, int age) {
    // CltDo1 holds the first cel until the flight enters its final AnimLen
    // ticks. A flight shorter than AnimLen starts at cel zero too.
    return Math.max(0, Math.min(frames - 1, age - Math.max(0, lifetime - frames)));
  }

  static int direction(com.badlogic.gdx.math.Vector2 facing, int directions) {
    if (directions != 8) return 0;
    int origin = 0x40000000;
    int path = com.d2moo.common.path.D2PathStep.directionVector(origin, origin,
        origin + Math.round(facing.x * 65536f), origin + Math.round(facing.y * 65536f)).direction;
    return pathDirection(path, directions);
  }

  static int pathDirection(int path, int directions) {
    return directions == 8 ? DIRECTIONS[((path + 4) & 63) >> 3] : 0;
  }
}
