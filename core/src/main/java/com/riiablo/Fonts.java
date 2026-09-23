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
  public final FontTBL.BitmapFont font6;
  public final FontTBL.BitmapFont font8;
  public final FontTBL.BitmapFont font16;
  public final FontTBL.BitmapFont font24;
  public final FontTBL.BitmapFont font30;
  public final FontTBL.BitmapFont font42;
  public final FontTBL.BitmapFont fontformal10;
  public final FontTBL.BitmapFont fontformal11;
  public final FontTBL.BitmapFont fontformal12;
  public final FontTBL.BitmapFont fontexocet10;
  public final FontTBL.BitmapFont fontridiculous;
  public final FontTBL.BitmapFont ReallyTheLastSucker;

  private final String fontDirectory;

  public Fonts(AssetManager assets) {
    this(assets, D2Language.ENGLISH);
  }

  public Fonts(AssetManager assets, D2Language language) {
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
    }

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
