package com.riiablo.widget;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import com.riiablo.Riiablo;
import com.riiablo.Fonts;
import com.riiablo.codec.FontTBL;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.graphics.PaletteIndexedColorDrawable;

public class Label extends com.badlogic.gdx.scenes.scene2d.ui.Label {
  public static final Drawable MODAL = new PaletteIndexedColorDrawable(Riiablo.colors.modal75) {{
    final float padding = 2;
    setLeftWidth(padding);
    setTopHeight(padding);
    setRightWidth(padding);
    setBottomHeight(padding);
  }};

  boolean updateSize = true; // FIXME: find a less hacky solution
  private Fonts.Role fontRole;
  private int fontGeneration = -1;
  private boolean baselineAligned = true;

  /**
   * Controls whether changing the text also replaces the actor's explicit
   * layout size with the font's preferred size. Fixed native UI panels need
   * this disabled so dynamic values cannot move their baseline or alignment.
   */
  public Label setAutoSize(boolean autoSize) {
    updateSize = autoSize;
    return this;
  }

  public boolean isAutoSize() {
    return updateSize;
  }

  public Label(int id, BitmapFont font) {
    this(id == -1 ? "" : Riiablo.string.lookup(id), font);
  }

  public Label(int id, BitmapFont font, Color color) {
    this(id, font);
    setColor(color);
  }

  public Label(String text, BitmapFont font, int align) {
    super(text, new LabelStyle(font, null));
    bindFont(font);
    setAlignment(align);
  }

  public Label(String text, BitmapFont font) {
    super(text, new LabelStyle(font, null));
    bindFont(font);
  }

  public Label(String text, BitmapFont font, Color color) {
    this(text, font);
    setColor(color);
  }

  public Label(BitmapFont font) {
    this(null, font);
  }

  public Label(LabelStyle style) {
    super(null, style);
    bindFont(style.font);
  }

  public Label(Label src) {
    super(src.getText(), src.getStyle());
    fontRole = src.fontRole;
    fontGeneration = src.fontGeneration;
    setColor(src.getColor());
  }

  /**
   * Binds this label to a logical Riiablo font. If staged loading replaces
   * that font object, the label follows the role instead of retaining the
   * temporary startup font forever. External fonts remain fixed.
   */
  public void bindFont(BitmapFont font) {
    Fonts fonts = Riiablo.fonts;
    fontRole = fonts == null ? null : fonts.roleOf(font);
    fontGeneration = fonts == null ? -1 : fonts.generation();
  }

  /** Replaces the font and updates its logical role in one operation. */
  public void setFont(BitmapFont font) {
    LabelStyle style = getStyle();
    style.font = font;
    bindFont(font);
    setStyle(style);
    invalidateHierarchy();
  }

  /**
   * Controls whether the measured Chinese-vs-English baseline correction is
   * applied while drawing. Centered controls should disable this; their
   * layout already centers the complete label inside its bounds.
   */
  public Label setBaselineAligned(boolean baselineAligned) {
    this.baselineAligned = baselineAligned;
    return this;
  }

  public boolean isBaselineAligned() {
    return baselineAligned;
  }

  private void refreshBoundFont() {
    Fonts fonts = Riiablo.fonts;
    if (fontRole == null || fonts == null || fontGeneration == fonts.generation()) return;
    fontGeneration = fonts.generation();
    BitmapFont font = fonts.get(fontRole);
    LabelStyle style = getStyle();
    if (font == null || style.font == font) return;
    style.font = font;
    setStyle(style);
    invalidateHierarchy();
  }

  @Override
  public void act(float delta) {
    refreshBoundFont();
    super.act(delta);
  }

  public static Label i18n(String id, BitmapFont font) {
    return new Label(Riiablo.string.lookup(id), font);
  }

  public static Label i18n(String id, BitmapFont font, Color color) {
    return new Label(Riiablo.string.lookup(id), font, color);
  }

  @Override
  public void draw(Batch batch, float a) {
    if (batch instanceof PaletteIndexedBatch) {
      draw((PaletteIndexedBatch) batch, a);
    } else {
      throw new GdxRuntimeException("Not supported");
    }
  }

  public void draw(PaletteIndexedBatch batch, float a) {
    refreshBoundFont();
    validate();

    LabelStyle style = getStyle();
    if (style != null) {
      Drawable background = style.background;
      if (background != null) {
        background.draw(batch,
            getX() - background.getLeftWidth(), getY() - background.getBottomHeight(),
            getWidth() + background.getMinWidth(), getHeight() + background.getMinHeight());
      }
    }

    batch.setBlendMode(((FontTBL.BitmapFont) getStyle().font).getBlendMode());
    BitmapFontCache cache = getBitmapFontCache();
    float baselineCorrection = baselineAligned && Riiablo.fonts != null
        ? Riiablo.fonts.baselineCorrection(getStyle().font)
        : 0;
    cache.setPosition(getX(), getY() + baselineCorrection);
    cache.tint(getColor());
    cache.draw(batch);
    batch.resetBlendMode();
  }

  @Override
  public boolean setText(int id) {
    setText(id == -1 ? "" : Riiablo.string.lookup(id));
    return true;
  }

  @Override
  public void setText(CharSequence newText) {
    super.setText(newText);
    if (updateSize) setSize(getPrefWidth(), getPrefHeight());
  }
}
