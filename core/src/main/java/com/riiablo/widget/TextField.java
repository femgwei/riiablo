package com.riiablo.widget;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.riiablo.Riiablo;
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
   * Applies the same measured Chinese-vs-English baseline correction used by
   * Riiablo Label. This is needed because libGDX TextField draws its font
   * directly and otherwise leaves native CJK text above the background box.
   */
  public TextField setBaselineAligned(boolean baselineAligned) {
    this.baselineAligned = baselineAligned;
    return this;
  }

  public boolean isBaselineAligned() {
    return baselineAligned;
  }

  @Override
  protected float getTextY(BitmapFont font, Drawable background) {
    float y = super.getTextY(font, background);
    if (baselineAligned && Riiablo.fonts != null) {
      y += Riiablo.fonts.baselineCorrection(font);
    }
    return y;
  }

  @Override
  public void dispose() {

  }

  public static class TextFieldStyle extends com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle {
    public TextFieldStyle() {

    }
  }

}
