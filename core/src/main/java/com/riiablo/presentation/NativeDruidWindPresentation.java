package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;

/** 1.10f skill CltDo68/69 rates and missile CltDo1 countdown-controlled loops. */
final class NativeDruidWindPresentation {
  private NativeDruidWindPresentation() {}

  static boolean handles(Missile m) {
    return m != null && m.missile != null
        && (m.skillId == 240 && "twister".equalsIgnoreCase(m.missile.Missile)
            || m.skillId == 245 && "tornado".equalsIgnoreCase(m.missile.Missile));
  }

  static int rate(Missile m) {
    // CltDo68/69 pass an explicit 8.8 animation rate to the shared spread
    // constructor. Tornado's 0x200 overrides the row's ordinary 0x100.
    return m.skillId == 245 ? 512 : 256;
  }

  static int direction(int nativeDirection, int directions) {
    // Hash-pinned D2CMP direction table (4 directions, row3 at RVA0x1A3D0).
    // The wind's path-facing byte is a fixed variant; waypoint velocity does
    // not replace it. Tornado has a single direction.
    return directions == 4 ? ((nativeDirection + 8) & 63) >> 4 : 0;
  }

  static void initialize(Missile m, Animation animation) {
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    int age = m.authoritative || m.presentationOnly ? m.nativeFrame : Math.max(m.nativeFrame, m.clientFrame);
    int life = m.nativeLifetimeFrames > 0 ? m.nativeLifetimeFrames : Missile.nativeRangeFrames(m.missile, m.damageLevel);
    animation.setMode(Animation.Mode.CLAMP);
    animation.setFrameDelta(rate(m));
    animation.setFrame(frame(m.missile, frames, life, age, rate(m)));
  }

  static int frame(Missiles.Entry row, int frames, int life, int age, int rate) {
    if (frames <= 0) return 0;
    int length = Math.max(1, row.AnimLen), stop = row.SubStop, start = row.SubStart;
    int ticks = Math.min(Math.max(0, age), Math.max(0, life));
    int loopTicks = row.SubLoop > 0 && stop > start
        ? Math.min(ticks, Math.max(0, life - (length - stop + 1))) : 0;
    long cursor = (long) loopTicks * rate;
    int stopFixed = stop << 8, startFixed = start << 8;
    if (loopTicks > 0 && cursor >= stopFixed)
      cursor = startFixed + (cursor - stopFixed) % (stopFixed - startFixed);
    cursor += (long) (ticks - loopTicks) * rate;
    cursor %= (long) length << 8;
    return Math.min(frames - 1, (int) (cursor >> 8));
  }
}
