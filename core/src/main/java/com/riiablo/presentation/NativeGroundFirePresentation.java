package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.codec.excel.Missiles;

/** CltDo5 presentation using the reference APK's Ws/Ts/vne animation model. */
public final class NativeGroundFirePresentation {
  private NativeGroundFirePresentation() {}

  /** Returns whether this missile owns the native CltDo5 ground-fire callback. */
  public static boolean handles(Missile missile) {
    return missile != null && missile.missile != null
        && missile.missile.pCltDoFunc == 5;
  }

  /** Growth starts at frame zero; the random phase applies only inside the loop. */
  static void initialize(Missile missile, Animation animation) {
    if (!missile.presentationSeedKnown) {
      missile.presentationSeed = missile.rngState;
      missile.presentationSeedKnown = true;
    }
    missile.clientFrame = Math.max(missile.clientFrame, missile.nativeFrame);
    animation.setFrameDelta(256); // APK ground-fire sheets run at 25 FPS.
    animation.setFrame(frameAt(missile, animation.getNumFramesPerDir(), missile.clientFrame));
    int remaining = missile.authoritative && !missile.presentationOnly && missile.persistent
        ? missile.remainingFrames : remainingFrames(missile);
    applyFade(missile, animation, remaining);
  }

  /** Keep playing forward while fading over the final 0.4 seconds (ten ticks). */
  public static void update(Missile missile, Animation animation, int remainingFrames,
      float delta) {
    if (!handles(missile) || animation == null) return;
    missile.clientFrame = Math.max(missile.nativeFrame,
        missile.clientFrame + Math.max(0, Math.round(delta * 25f)));
    animation.setFrame(frameAt(missile, animation.getNumFramesPerDir(), missile.clientFrame));
    // Replica snapshots may be sparse. Extrapolate only the presentation
    // clock; never advance the server's lifetime, collision or damage clock.
    applyFade(missile, animation, remainingFrames);
  }

  private static void applyFade(Missile missile, Animation animation, int remainingFrames) {
    int remaining = remainingFrames - Math.max(0, missile.clientFrame - missile.nativeFrame);
    Animation.Layer layer = animation.getLayer(0);
    if (layer != null) layer.setAlpha(fadeAlpha(remaining));
  }

  /**
   * Returns the native remaining clock for a replica.  Older snapshot packets
   * legitimately carry a zero TotalFrames value for persistent ground fire;
   * the client-created missile still has the row's Range and must not fade
   * immediately. Once a server lifetime is available,
   * it remains authoritative (some skills extend the sub-loop beyond Range).
   */
  static int remainingFrames(Missile missile) {
    if (missile == null || missile.missile == null) return 0;
    if (missile.nativeLifetimeFrames > 0) {
      return Math.max(0, missile.nativeLifetimeFrames - missile.nativeFrame);
    }
    int rowLifetime = Missile.nativeRangeFrames(missile.missile, missile.damageLevel);
    return Math.max(0, rowLifetime - missile.nativeFrame);
  }

  static int frameAt(Missile missile, int frames, int ageFrames) {
    if (frames <= 0) return 0;
    Missiles.Entry row = missile.missile;
    int subStart = row.SubStart;
    int subStop = Math.min(frames - 1, row.SubStop);
    int age = Math.max(0, ageFrames);
    // Ts includes loopEnd, unlike the old native exclusive-stop sub-loop.
    if (row.SubLoop > 0 && subStart >= 0 && subStart <= subStop && age >= subStart) {
      return subStart + (age - subStart + phase(missile)) % (subStop - subStart + 1);
    }
    return Math.min(frames - 1, age);
  }

  static int phase(Missile missile) {
    // Ws samples phase once in [0,24]. Derive it from the immutable birth
    // seed so reloads are stable and neither client nor damage RNG is consumed.
    int seed = missile.presentationSeedKnown ? missile.presentationSeed : missile.rngState;
    return com.d2moo.common.seed.Seed.rollLimitedRandomNumber(
        new com.d2moo.common.drlg.D2Seed(seed, 666), 25);
  }

  static float fadeAlpha(int remainingFrames) {
    return Math.max(0f, Math.min(1f, remainingFrames / 10f));
  }
}
