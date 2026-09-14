package com.riiablo.engine.client.automap;

import com.riiablo.map.RenderSystem;

/** Shared Automap option semantics used by the menu and renderer. */
public final class AutomapOptions {
  public static final float FADED_OPACITY = 0.65f;
  public static final float SOLID_OPACITY = 1.0f;

  private AutomapOptions() {}

  public static int normalizeMode(int mode) {
    switch (mode) {
      case RenderSystem.AUTOMAP_MODE_TOP_LEFT:
      case RenderSystem.AUTOMAP_MODE_TOP_RIGHT:
      case RenderSystem.AUTOMAP_MODE_CENTER:
        return mode;
      default:
        return RenderSystem.AUTOMAP_MODE_CENTER;
    }
  }

  public static int nextMode(int mode) {
    switch (normalizeMode(mode)) {
      case RenderSystem.AUTOMAP_MODE_CENTER:
        return RenderSystem.AUTOMAP_MODE_TOP_LEFT;
      case RenderSystem.AUTOMAP_MODE_TOP_LEFT:
        return RenderSystem.AUTOMAP_MODE_TOP_RIGHT;
      case RenderSystem.AUTOMAP_MODE_TOP_RIGHT:
      default:
        return RenderSystem.AUTOMAP_MODE_CENTER;
    }
  }

  public static String modeLabel(int mode) {
    switch (normalizeMode(mode)) {
      case RenderSystem.AUTOMAP_MODE_TOP_LEFT:
        return "MINI MAP (Left-Top)";
      case RenderSystem.AUTOMAP_MODE_TOP_RIGHT:
        return "MINI MAP (Right-Top)";
      case RenderSystem.AUTOMAP_MODE_CENTER:
      default:
        return "FULL SCREEN";
    }
  }

  public static float opacity(boolean fade) {
    return fade ? FADED_OPACITY : SOLID_OPACITY;
  }
}
