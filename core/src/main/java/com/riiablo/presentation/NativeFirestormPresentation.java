package com.riiablo.presentation;

import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.missile.NativeFirestormMotion;

/** Captured 1.10f Firestorm clocks; other CltDo5 families retain their own policy. */
public final class NativeFirestormPresentation {
  private NativeFirestormPresentation() {}

  static boolean handles(Missile m) {
    return m != null && (NativeFirestormMotion.maker(m.missile) || NativeFirestormMotion.fire(m.missile));
  }

  public static boolean serverGround(Missile m) {
    return m != null && !m.presentationOnly && NativeFirestormMotion.fire(m.missile);
  }

  static void initialize(Missile m, Animation animation) {
    int age = m.authoritative || m.presentationOnly ? m.nativeFrame : Math.max(m.nativeFrame, m.clientFrame);
    animation.setFrameDelta(NativeFirestormMotion.maker(m.missile) ? 512 : 256);
    animation.setFrame(frameAt(m, animation.getNumFramesPerDir(), age));
  }

  static int frameAt(Missile m, int frames, int age) {
    if (frames <= 0) return 0;
    if (NativeFirestormMotion.maker(m.missile)) return Math.floorMod(Math.max(0, age) * 2, frames);
    int lifetime = m.nativeLifetimeFrames > 0 ? m.nativeLifetimeFrames : Math.max(1, m.missile.Range);
    return groundFrame(m.missile, lifetime,
        m.presentationSeedKnown ? m.presentationSeed : m.rngState, frames, age);
  }

  static int groundFrame(com.riiablo.codec.excel.Missiles.Entry row, int lifetime,
      int seedLow, int frames, int age) {
    return groundFrame(row, lifetime, seedLow, frames, age, false);
  }

  static int groundFrame(com.riiablo.codec.excel.Missiles.Entry row, int lifetime,
      int seedLow, int frames, int age, boolean subLoop) {
    return groundFrame(row, lifetime, seedLow, 666, frames, age, subLoop);
  }

  static int groundFrame(com.riiablo.codec.excel.Missiles.Entry row, int lifetime,
      int seedLow, int seedHigh, int frames, int age, boolean subLoop) {
    if (frames <= 0) return 0;
    int start = row.SubStart, stop = row.SubStop, cursor = 0;
    D2Seed seed = new D2Seed(seedLow, seedHigh);
    for (int tick = 1; tick <= Math.min(Math.max(0, age), lifetime); tick++) {
      int remaining = lifetime - tick + 1;
      if (cursor == start - 1) {
        if (stop > start) cursor += Seed.rollLimitedRandomNumber(seed, stop - start);
      } else if (remaining == start) cursor = Math.max(0, start - 3);
      else if (remaining < start) cursor = Math.max(0, cursor - 2);
      cursor = (cursor + 1) % frames;
      if (subLoop && remaining > start && stop > start && cursor >= stop) cursor = start;
    }
    return cursor;
  }
}
