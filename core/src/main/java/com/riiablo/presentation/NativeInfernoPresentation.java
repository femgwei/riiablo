package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.Missile;

final class NativeInfernoPresentation {
  private NativeInfernoPresentation() {}
  static boolean handles(Missile missile) {
    if (missile == null || missile.missile == null) return false;
    String name = missile.missile.Missile;
    return missile.skillId == 41 && ("infernoflame1".equalsIgnoreCase(name)
        || "infernoflame2".equalsIgnoreCase(name))
        || missile.skillId == 230 && ("arcticblast1".equalsIgnoreCase(name)
        || "arcticblast2".equalsIgnoreCase(name));
  }
  static void initialize(Missile missile, Animation animation) {
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    int lifetime = Math.max(1, missile.nativeLifetimeFrames);
    int delta = (missile.missile.AnimLen << 8) / lifetime;
    int age = missile.authoritative || missile.presentationOnly ? missile.nativeFrame
        : Math.max(missile.nativeFrame, missile.clientFrame);
    animation.setFrameDelta(Math.max(1, delta));
    animation.setFrame((int) Math.min(frames - 1, (long) Math.max(0, age) * delta >> 8));
  }
}
