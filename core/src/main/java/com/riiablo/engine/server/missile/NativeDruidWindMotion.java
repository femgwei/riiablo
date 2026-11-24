package com.riiablo.engine.server.missile;

import com.riiablo.engine.server.component.Missile;

/** Twister/Tornado share native charged-bolt waypoints, not distance-spaced float turns. */
public final class NativeDruidWindMotion {
  private NativeDruidWindMotion() {}

  public static boolean handles(Missile m) {
    if (m == null || m.missile == null || !m.chargedBoltPath || m.chargedBoltSeedHigh != 666) return false;
    long lane = (long)m.chargedBoltSeedLow-m.chargedBoltTargetX;
    // New flights retain the exact initializer (targetX+lane,666). Older
    // checkpoints contain an already-rolled mutable seed; those keep the
    // previous incremental path instead of being silently reinterpreted.
    return lane>=0 && (m.skillId == 240 && lane<3 && "twister".equalsIgnoreCase(m.missile.Missile)
        || m.skillId == 245 && lane==0 && "tornado".equalsIgnoreCase(m.missile.Missile));
  }
}
