package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Overlay;
import com.riiablo.graphics.BlendMode;

/** 1.10f D2Client overlay drawing parameters, not gameplay aura calculations. */
public final class AuraRenderPolicy {
  private AuraRenderPolicy() {}

  /** Verified 1.10f warcry cast/target overlays, including shared fear/stun effects. */
  public static boolean isWarCryOverlay(String name) {
    return "warcry".equalsIgnoreCase(name) || "curseterror".equalsIgnoreCase(name)
        || "curse_hit".equalsIgnoreCase(name) || "taunt".equalsIgnoreCase(name)
        || "battlecry".equalsIgnoreCase(name) || "stun".equalsIgnoreCase(name);
  }

  // D2Client 6FB5BE13: AnimRate << 4; frames have eight fractional bits.
  public static float frameDuration(int animRate) {
    return animRate <= 0 ? Float.POSITIVE_INFINITY
        : 16f / (Animation.FRAMES_PER_SECOND * animRate);
  }

  // Overlay.Trans is already a D2Gfx draw mode (unlike Missiles.Trans).
  public static int blendMode(int trans) {
    return trans == 3 ? BlendMode.ADDITIVE : BlendMode.ID;
  }

  public static float alpha(int trans) {
    switch (trans) {
      case 0: return .25f;
      case 1: return .5f;
      case 2: return .75f;
      default: return 1f;
    }
  }

  /** D2Common #10430 returns 1 for players, OverlayHeight - 1 for monsters. */
  public static int height(Overlay.Entry row, int heightClass) {
    switch (heightClass) {
      case -1: return 75;
      case 0: return row.Height1;
      case 1: return row.Height2;
      case 2: return row.Height3;
      case 3: return row.Height4;
      default: return 0;
    }
  }

  /** Convert native downward-positive screen offsets to the renderer's Y-up coordinates. */
  public static float offsetY(Overlay.Entry row, int heightClass) {
    return -row.Yoffset - height(row, heightClass);
  }
}
