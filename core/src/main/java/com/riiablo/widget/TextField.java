package com.riiablo.widget;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Disposable;

public class TextField extends com.badlogic.gdx.scenes.scene2d.ui.TextField implements Disposable {
  private boolean baselineAligned = true;

  public TextField(TextFieldStyle style) {
    this("", style);
  }

  public TextField(String text, TextFieldStyle style) {
    super(text, style);
  }

  /**
   * Centers the actual native glyph rectangle in the input background. This
   * is separate from Label's baseline alignment because TextField's cursor
   * must remain on the stock libGDX text-field baseline.
   */
  public TextField setBaselineAligned(boolean baselineAligned) {
    this.baselineAligned = baselineAligned;
    return this;
  }

  public boolean isBaselineAligned() {
    return baselineAligned;
  }

  @Override
  protected void drawText(com.badlogic.gdx.graphics.g2d.Batch batch, BitmapFont font,
      float x, float y) {
    if (baselineAligned) y += centeredGlyphOffset(font, y);
    super.drawText(batch, font, x, y);
  }

  /**
   * TextField's stock metrics use capHeight/descent, while the native TBL
   * glyphs have a deliberately large negative yoffset. Center the actual
   * glyph rectangle in the background content area, leaving the cursor on
   * libGDX's normal text-field baseline.
   */
  private float centeredGlyphOffset(BitmapFont font, float absoluteBaseline) {
    if (displayText == null || displayText.length() == 0) return 0;
    BitmapFont.BitmapFontData data = font.getData();
    float min = Float.POSITIVE_INFINITY;
    float max = Float.NEGATIVE_INFINITY;
    for (int i = 0; i < displayText.length(); i++) {
      BitmapFont.Glyph glyph = data.getGlyph(displayText.charAt(i));
      if (glyph == null) continue;
      float top = data.ascent + glyph.yoffset * data.scaleY;
      float bottom = top + glyph.height * data.scaleY;
      min = Math.min(min, top);
      max = Math.max(max, bottom);
    }
    if (min == Float.POSITIVE_INFINITY) return 0;

    Drawable background = getBackgroundDrawable();
    float contentBottom = background == null ? 0 : background.getBottomHeight();
    float contentTop = getHeight() - (background == null ? 0 : background.getTopHeight());
    float targetCenter = (contentBottom + contentTop) * 0.5f;
    float currentCenter = (absoluteBaseline - getY()) + (min + max) * 0.5f;
    return targetCenter - currentCenter;
  }

  @Override
  public void dispose() {

  }

  public static class TextFieldStyle extends com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle {
    public TextFieldStyle() {

    }
  }

}
