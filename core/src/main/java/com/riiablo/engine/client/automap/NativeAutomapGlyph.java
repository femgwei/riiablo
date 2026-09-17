package com.riiablo.engine.client.automap;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Pixel-accurate fallback silhouette used by native player/NPC Automap markers. */
final class NativeAutomapGlyph {
  private NativeAutomapGlyph() {}

  static final String[] ROWS = {
      ".....###......###...",
      "....##..###..##..###",
      "####.....####...####",
      "###...........##....",
      "...###........###...",
      "...###........###...",
      "....##...........###",
      "####...####.....###.",
      "###.###..###..##....",
      "...###......###....."
  };

  static final int WIDTH = ROWS[0].length();
  static final int HEIGHT = ROWS.length;

  static boolean isSet(int x, int y) {
    return y >= 0 && y < HEIGHT && x >= 0 && x < WIDTH
        && ROWS[y].charAt(x) == '#';
  }

  static void draw(ShapeRenderer shapes, float centerX, float centerY) {
    float left = centerX - WIDTH * 0.5f;
    float bottom = centerY - HEIGHT * 0.5f;
    for (int row = 0; row < HEIGHT; row++) {
      float y = bottom + HEIGHT - row - 1;
      for (int x = 0; x < WIDTH; x++) {
        if (isSet(x, row)) shapes.rect(left + x, y, 1f, 1f);
      }
    }
  }
}
