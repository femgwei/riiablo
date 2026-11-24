package com.riiablo.presentation;

import com.riiablo.presentation.component.Overlay;

/** CltSt15's ONCE ColdWindOverlay clock is independent of the channel and asset loading. */
final class NativeArcticCastOverlay {
  private NativeArcticCastOverlay() {}
  static boolean handles(Overlay overlay) {
    return !overlay.persistent && overlay.entry != null && "arctic_blast_cast".equalsIgnoreCase(overlay.entry.overlay);
  }
  static boolean advance(Overlay overlay, float delta) {
    if (overlay.nativeClockStarted) overlay.nativeClockTime += Math.max(0, delta);
    else overlay.nativeClockStarted = true; // Publish the birth cel, including a warm cache.
    overlay.nativeFrame = (int) Math.floor(overlay.nativeClockTime * 25 + .00001);
    return (long) overlay.nativeFrame * Math.max(0, overlay.entry.AnimRate) * 16
        < (long) Math.max(1, overlay.entry.Frames) * 256;
  }
  static void apply(Overlay overlay) {
    int rate = Math.max(0, overlay.entry.AnimRate) << 4;
    overlay.animation.setFrameDelta(rate);
    int frames = overlay.animation.getNumFramesPerDir();
    if (frames > 0) overlay.animation.setFrame((int) Math.min(frames - 1,
        (long) overlay.nativeFrame * rate >> 8));
  }
}
