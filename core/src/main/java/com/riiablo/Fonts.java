package com.riiablo;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.BitmapFont;

import com.riiablo.graphics.BlendMode;
import com.riiablo.codec.FontTBL;
import com.riiablo.loader.BitmapFontLoader;

public class Fonts {
  /** Native CJK glyphs sit one pixel high when the font16 atlas is shared. */
  private static final int CHINESE_SHARED_BASELINE_ADJUST = -1;
  public final BitmapFont         consolas12;
  public final BitmapFont         consolas16;
  public FontTBL.BitmapFont font6;
  public FontTBL.BitmapFont font8;
  public FontTBL.BitmapFont font16;
  public FontTBL.BitmapFont font24;
  public FontTBL.BitmapFont font30;
  public FontTBL.BitmapFont font42;
  public FontTBL.BitmapFont fontformal10;
  public FontTBL.BitmapFont fontformal11;
  public FontTBL.BitmapFont fontformal12;
  public FontTBL.BitmapFont fontexocet10;
  public FontTBL.BitmapFont fontridiculous;
  public FontTBL.BitmapFont ReallyTheLastSucker;

  private final String fontDirectory;
  private final AssetManager assets;
  private boolean gameplayFontsLoaded;

  public Fonts(AssetManager assets) {
    this(assets, D2Language.ENGLISH);
  }

  public Fonts(AssetManager assets, D2Language language) {
    this.assets = assets;
    fontDirectory = language.fontDirectory;
    consolas12   = loadEx(assets, "consolas12.fnt");
    consolas16   = loadEx(assets, "consolas16.fnt");
    if (language == D2Language.CHINESE
        && Boolean.getBoolean("riiablo.chinese.sharedFont")) {
      // Native CJK fonts contain 13,806 glyphs each. Build the complete
      // font16 atlas once and share it across the UI variants; each copy has
      // isolated metrics and blend mode but does not duplicate textures.
      FontTBL.BitmapFont base = load(assets, "font16", BlendMode.LUMINOSITY_TINT);
      // This compatibility mode is opt-in. Native Chinese fonts have distinct
      // glyph bearings and must be loaded independently for accurate layout.
      ((FontTBL.BitmapFontData) base.getData())
          .shiftGlyphsY(CHINESE_SHARED_BASELINE_ADJUST);
      font16 = base;
      font6 = base.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      font8 = base.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      font24 = base.sharedAtlasCopy(BlendMode.ID);
      font30 = base.sharedAtlasCopy(BlendMode.ID);
      font42 = base.sharedAtlasCopy(BlendMode.ID);
      fontformal10 = base.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      fontformal11 = base.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      fontformal12 = base.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      fontexocet10 = base.sharedAtlasCopy(BlendMode.TINT_BLACKS);
      fontridiculous = base.sharedAtlasCopy(BlendMode.TINT_BLACKS);
      ReallyTheLastSucker = base.sharedAtlasCopy(BlendMode.ID);
      gameplayFontsLoaded = true;
    } else if (language == D2Language.CHINESE) {
      // The splash/menu only need these three fonts.  The remaining native
      // CJK atlases are staged until the first GameScreen is constructed.
      font16       = load(assets, "font16", BlendMode.LUMINOSITY_TINT);
      fontformal12 = load(assets, "fontformal12", BlendMode.LUMINOSITY_TINT);
      fontexocet10 = load(assets, "fontexocet10", BlendMode.TINT_BLACKS);

      // Keep all public fields usable by pre-game screens. These lightweight
      // views share font16's atlas and are replaced with native fonts when
      // initializeGameplayFonts() runs.
      font6 = font16.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      font8 = font16.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      font24 = font16.sharedAtlasCopy(BlendMode.ID);
      font30 = font16.sharedAtlasCopy(BlendMode.ID);
      font42 = font16.sharedAtlasCopy(BlendMode.ID);
      fontformal10 = font16.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      fontformal11 = font16.sharedAtlasCopy(BlendMode.LUMINOSITY_TINT);
      fontridiculous = font16.sharedAtlasCopy(BlendMode.TINT_BLACKS);
      ReallyTheLastSucker = font16.sharedAtlasCopy(BlendMode.ID);
      gameplayFontsLoaded = false;
    } else {
      font6        = load(assets, "font6",  BlendMode.LUMINOSITY_TINT);
      font8        = load(assets, "font8",  BlendMode.LUMINOSITY_TINT);
      font16       = load(assets, "font16", BlendMode.LUMINOSITY_TINT);
      font24       = load(assets, "font24", BlendMode.ID);
      font30       = load(assets, "font30", BlendMode.ID);
      font42       = load(assets, "font42", BlendMode.ID);
      fontformal10 = load(assets, "fontformal10", BlendMode.LUMINOSITY_TINT);
      fontformal11 = load(assets, "fontformal11", BlendMode.LUMINOSITY_TINT);
      fontformal12 = load(assets, "fontformal12", BlendMode.LUMINOSITY_TINT);
      fontexocet10 = load(assets, "fontexocet10", BlendMode.TINT_BLACKS);
      fontridiculous = load(assets, "fontridiculous", BlendMode.TINT_BLACKS);
      ReallyTheLastSucker = load(assets, "ReallyTheLastSucker", BlendMode.ID);
      gameplayFontsLoaded = true;
    }

    applyMetrics();
  }

  /** Loads the remaining native fonts at the first transition into gameplay. */
  public void initializeGameplayFonts() {
    if (gameplayFontsLoaded) return;
    font6        = load(assets, "font6",  BlendMode.LUMINOSITY_TINT);
    font8        = load(assets, "font8",  BlendMode.LUMINOSITY_TINT);
    font24       = load(assets, "font24", BlendMode.ID);
    font30       = load(assets, "font30", BlendMode.ID);
    font42       = load(assets, "font42", BlendMode.ID);
    fontformal10 = load(assets, "fontformal10", BlendMode.LUMINOSITY_TINT);
    fontformal11 = load(assets, "fontformal11", BlendMode.LUMINOSITY_TINT);
    // The native Chinese scrolling-dialog font uses a different bearing from
    // the menu/button fonts. Normalize only this font; applying the correction
    // globally moves Exocet menu text outside its button bounds.
    ((FontTBL.BitmapFontData) fontformal11.getData()).alignGlyphsToBaseline();
    fontridiculous = load(assets, "fontridiculous", BlendMode.TINT_BLACKS);
    ReallyTheLastSucker = load(assets, "ReallyTheLastSucker", BlendMode.ID);
    gameplayFontsLoaded = true;
    applyMetrics();
  }

  /** Queues staged fonts without blocking the render thread. */
  public void queueGameplayFonts() {
    if (gameplayFontsLoaded) return;
    assets.load(getDescriptor("font6", BlendMode.LUMINOSITY_TINT));
    assets.load(getDescriptor("font8", BlendMode.LUMINOSITY_TINT));
    assets.load(getDescriptor("font24", BlendMode.ID));
    assets.load(getDescriptor("font30", BlendMode.ID));
    assets.load(getDescriptor("font42", BlendMode.ID));
    assets.load(getDescriptor("fontformal10", BlendMode.LUMINOSITY_TINT));
    assets.load(getDescriptor("fontformal11", BlendMode.LUMINOSITY_TINT));
    assets.load(getDescriptor("fontridiculous", BlendMode.TINT_BLACKS));
    assets.load(getDescriptor("ReallyTheLastSucker", BlendMode.ID));
  }

  public boolean isGameplayFontsLoaded() {
    return gameplayFontsLoaded;
  }

  private void applyMetrics() {
    BitmapFont.BitmapFontData data;
    data = font8.getData();
    data.lineHeight = data.xHeight = data.capHeight = 12;
    data.ascent = 16;
    data.down = -12;

    data = font16.getData();
    data.lineHeight = data.xHeight = data.capHeight = 14;
    data.ascent = 17;
    data.down = -16;

    data = font42.getData();
    data.lineHeight = data.xHeight = data.capHeight = 31;
    data.ascent = 48;
    data.down = -31;

    data = fontformal10.getData();
    data.lineHeight = data.xHeight = data.capHeight = 14;
    data.ascent = 17;
    data.down = -14;

    data = fontformal11.getData();
    data.lineHeight = data.xHeight = data.capHeight = 18;
    data.ascent = 18;
    data.down = -18;

    data = fontformal12.getData();
    data.lineHeight = data.xHeight = data.capHeight = 16;
    data.ascent = 42;
    data.down = -20;

    data = ReallyTheLastSucker.getData();
    data.lineHeight = data.xHeight = data.capHeight = 8;
    data.ascent = 11;
    data.down = -8;
  }

  private BitmapFont loadEx(AssetManager assets, String fontName) {
    assets.load(fontName, BitmapFont.class);
    assets.finishLoadingAsset(fontName);
    return assets.get(fontName);
  }

  private FontTBL.BitmapFont load(AssetManager assets, String fontName, int blendMode) {
    AssetDescriptor<FontTBL.BitmapFont> descriptor = getDescriptor(fontName, blendMode);
    assets.load(descriptor);
    assets.finishLoadingAsset(descriptor);
    return assets.get(descriptor);
  }

  private AssetDescriptor<FontTBL.BitmapFont> getDescriptor(String fontName, int blendMode) {
    return new AssetDescriptor<>("data\\local\\font\\" + fontDirectory + "\\" + fontName + ".TBL", FontTBL.BitmapFont.class, BitmapFontLoader.Params.of(blendMode));
  }
}
