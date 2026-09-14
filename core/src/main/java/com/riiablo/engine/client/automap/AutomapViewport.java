package com.riiablo.engine.client.automap;

import com.badlogic.gdx.math.Rectangle;
import com.riiablo.map.RenderSystem;

/** Calculates the common viewport used by native and fallback Automap renderers. */
public final class AutomapViewport {
  public static final float DEFAULT_MARGIN = 12f;
  public static final float MINI_MAP_RATIO = 0.5f;

  private AutomapViewport() {}

  public static Rectangle calculate(int mode, float screenWidth, float screenHeight,
                                    Rectangle out) {
    return calculate(mode, screenWidth, screenHeight, DEFAULT_MARGIN, out);
  }

  static Rectangle calculate(int mode, float screenWidth, float screenHeight,
                             float requestedMargin, Rectangle out) {
    if (out == null) out = new Rectangle();
    screenWidth = Math.max(0f, screenWidth);
    screenHeight = Math.max(0f, screenHeight);
    float margin = Math.max(0f, Math.min(requestedMargin,
        Math.min(screenWidth, screenHeight) * 0.25f));

    if (AutomapOptions.normalizeMode(mode) == RenderSystem.AUTOMAP_MODE_CENTER) {
      return out.set(margin, margin,
          Math.max(0f, screenWidth - margin * 2f),
          Math.max(0f, screenHeight - margin * 2f));
    }

    float width = screenWidth * MINI_MAP_RATIO;
    float height = screenHeight * MINI_MAP_RATIO;
    float x = mode == RenderSystem.AUTOMAP_MODE_TOP_RIGHT
        ? screenWidth - margin - width : margin;
    float y = screenHeight - margin - height;
    return out.set(x, y, width, height);
  }
}
