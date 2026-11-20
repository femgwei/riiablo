package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Missile;

/** 1.10f PoisonSparks intro, bounded middle loop and exit, independent of DCC loading. */
public final class NativePoisonJavelinPresentation {
  private NativePoisonJavelinPresentation() {}

  /** Damage-bearing copies stay on the server clock; CltDo3 owns their visuals. */
  public static boolean serverTrailCloud(Missile missile) {
    return missile != null && !missile.presentationOnly && missile.skillId == 15
        && missile.missile != null && "poisonjavcloud".equalsIgnoreCase(missile.missile.Missile);
  }

  static boolean stationaryCloud(Missile missile) {
    return missile != null && missile.missile != null && !missile.poisonCloudTraveling
        && ("poisonjavcloud".equalsIgnoreCase(missile.missile.Missile)
            // Plague's stationary trails are local visuals. Replicated burst
            // clouds lack the transient traveling flag in MissileP, but still
            // carry their Angle; do not mistake them for stationary trails.
            || missile.presentationOnly && "plaguejavcloud".equalsIgnoreCase(missile.missile.Missile));
  }

  static boolean ownsClock(Missile missile) {
    if (missile == null || missile.missile == null) return false;
    String name = missile.missile.Missile;
    return "poisonjav".equalsIgnoreCase(name) || "poisonjavcloud".equalsIgnoreCase(name)
        || "poisonpuff".equalsIgnoreCase(name) || "plaguejavelin".equalsIgnoreCase(name)
        || "plaguejavcloud".equalsIgnoreCase(name);
  }

  static int direction(Missile missile, com.badlogic.gdx.math.Vector2 facing, int directions) {
    if (stationaryCloud(missile) && directions == 8) return 4;
    if (missile != null && missile.missile != null && directions == 8
        && "plaguejavcloud".equalsIgnoreCase(missile.missile.Missile)) {
      // PoisonSparks cells use the native path order, not the COF direction order.
      return CLOUD_DIRECTIONS[((nativeDirection(facing) + 4) & 63) >> 3];
    }
    if (missile == null || !MissileLoader.isPoisonSmokePuff(missile.missile) || directions != 4) return -1;
    // PoisonSmokePuff's four cells are in native path-quadrant order, unlike
    // COF's four cardinal directions. Apply the native 64-direction reduction.
    return ((nativeDirection(facing) + 8) & 63) >> 4;
  }

  private static final int[] CLOUD_DIRECTIONS = {4, 0, 5, 1, 6, 2, 7, 3};

  private static int nativeDirection(com.badlogic.gdx.math.Vector2 facing) {
    int origin = 0x40000000;
    return com.d2moo.common.path.D2PathStep.directionVector(origin, origin,
        origin + Math.round(facing.x * 65536f), origin + Math.round(facing.y * 65536f)).direction;
  }

  static void initializeCloud(Missile missile, Animation animation) {
    Missiles.Entry row = missile.missile;
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    int age = missile.authoritative || missile.presentationOnly ? missile.nativeFrame
        : Math.max(missile.nativeFrame, missile.clientFrame);
    int lifetime = missile.nativeLifetimeFrames > 0 ? missile.nativeLifetimeFrames : row.Range;
    long cursor = (long) row.RandStart << 8;
    int speed = Math.max(1, animation.getFrameDelta());
    // D2Client+9DB1E compares CurrentFrame before decrement against
    // AnimLen - SubStop + 1. D2Common#10375 wraps at SubStop immediately,
    // preserving the fractional cursor, rather than playing the whole DCC first.
    int threshold = row.AnimLen - row.SubStop + 1;
    for (int tick = 1; tick <= Math.min(Math.max(0, age), Math.max(0, lifetime)); tick++) {
      cursor += speed;
      if (row.SubLoop > 0 && row.SubStop > row.SubStart
          && lifetime - tick + 1 > threshold) {
        if (cursor >= (long) row.SubStop << 8) cursor -= (long) (row.SubStop - row.SubStart) << 8;
      } else if (cursor >= (long) frames << 8) {
        cursor = row.LoopAnim > 0 ? cursor % ((long) frames << 8) : ((long) frames << 8) - 1;
      }
    }
    animation.setFrame((int) Math.min(frames - 1, Math.max(0, cursor >> 8)));
  }
}
