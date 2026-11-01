package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.codec.excel.Missiles;

/**
 * Small client-side part of D2Client's CltDo5 (0x6FB306F0) callback.
 *
 * <p>The callback is intentionally kept outside {@link Animation}: ground
 * fire uses the missile's remaining native clock to rewind the resident DCC
 * around its SubStart frame, while ordinary animation playback continues in
 * {@link Animation#update(float)} afterwards.</p>
 */
public final class NativeGroundFirePresentation {
  private NativeGroundFirePresentation() {}

  /** Returns whether this missile owns the native CltDo5 ground-fire callback. */
  public static boolean handles(Missile missile) {
    return missile != null && missile.missile != null
        && missile.missile.pCltDoFunc == 5;
  }

  /**
   * Applies one 1.10f CltDo5 frame correction before normal animation update.
   * The original callback uses the native missile frame for the first branch,
   * then the remaining lifetime for the two rewind branches.
   */
  public static void adjustFrame(Missile missile, Animation animation, int remainingFrames) {
    if (!handles(missile) || animation == null) return;
    Missiles.Entry row = missile.missile;
    int subStart = row.SubStart;
    int subStop = row.SubStop;
    if (subStart <= 0 || animation.getNumFramesPerDir() <= 0) return;

    int frame = animation.getFrame();
    if (frame == subStart - 1) {
      // D2Client's CltDo5 uses ITEMS_RollLimitedRandomNumber with the
      // SubStop-SubStart bound, then adds it to the pre-subloop frame.
      int span = subStop - subStart;
      if (span <= 0) return;
      int selected = frame + MissileImpactPresentationSystem.nextClientRandom(missile, span);
      setFrameClamped(animation, selected);
    } else if (remainingFrames == subStart) {
      setFrameClamped(animation, Math.max(subStart - 3, 0));
    } else if (remainingFrames < subStart) {
      setFrameClamped(animation, Math.max(frame - 2, 0));
    }
  }

  private static void setFrameClamped(Animation animation, int frame) {
    int max = animation.getNumFramesPerDir() - 1;
    animation.setFrame(Math.max(0, Math.min(max, frame)));
  }
}
