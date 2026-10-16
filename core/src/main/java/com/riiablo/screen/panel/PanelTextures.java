package com.riiablo.screen.panel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.riiablo.codec.DC6;

/** Unmanaged panel textures own their GL data, not the temporary upload pixels. */
final class PanelTextures {
  private PanelTextures() {}

  static Texture solid(Color color) {
    Pixmap pixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
    try {
      pixels.setColor(color);
      pixels.fill();
      return new Texture(pixels);
    } finally {
      pixels.dispose();
    }
  }

  static Texture controlBackground(DC6 panel) {
    int end = panel.getNumFramesPerDir() - 2;
    int width = 0;
    int height = Integer.MIN_VALUE;
    for (int i = 1; i < end; i++) {
      Pixmap frame = panel.getPixmap(0, i);
      width += frame.getWidth();
      height = Math.max(height, frame.getHeight());
    }
    Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
    try {
      pixels.setBlending(Pixmap.Blending.None);
      int x = 0;
      for (int i = 1; i < end; i++) {
        Pixmap frame = panel.getPixmap(0, i);
        pixels.drawPixmap(frame, x, pixels.getHeight() - frame.getHeight());
        x += frame.getWidth();
      }
      return new Texture(new PixmapTextureData(pixels, null, false, false, false));
    } finally {
      // Source frames belong to DC6; only the composed upload buffer is ours.
      pixels.dispose();
    }
  }
}
