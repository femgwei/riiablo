package com.riiablo;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.files.FileHandle;
import java.util.IdentityHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.riiablo.graphics.BlendMode;
import com.riiablo.codec.FontBaselineCalibration;
import com.riiablo.codec.FontTBL;
import com.riiablo.loader.BitmapFontLoader;

public class Fonts {
  public enum Role {
    FONT6,
    FONT8,
    FONT16,
    FONT24,
    FONT30,
    FONT42,
    FONTFORMAL10,
    FONTFORMAL11,
    FONTFORMAL12,
    FONTEXOCET10,
    FONTRIDICULOUS,
    REALLY_THE_LAST_SUCKER
  }

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
  private final boolean chinese;
  private final boolean sharedChinese;
  private final AssetManager assets;
  /**
   * Includes both the current fonts and fonts replaced after staged loading.
   * Identity semantics are intentional: several staged fonts share textures,
   * but each object represents a different UI font role.
   */
  private final IdentityHashMap<BitmapFont, Role> fontRoles = new IdentityHashMap<>();
  private int generation;
  private boolean gameplayFontsLoaded;
  private boolean gameplayFontsQueued;
  private Future<FontProbe[]> gameplayFontProbe;

  public Fonts(AssetManager assets) {
    this(assets, D2Language.ENGLISH);
  }

  public Fonts(AssetManager assets, D2Language language) {
    this.assets = assets;
    fontDirectory = language.fontDirectory;
    chinese = language == D2Language.CHINESE;
    sharedChinese = chinese && Boolean.getBoolean("riiablo.chinese.sharedFont");
    consolas12   = loadEx(assets, "consolas12.fnt");
    consolas16   = loadEx(assets, "consolas16.fnt");
    if (sharedChinese) {
      // Native CJK fonts contain 13,806 glyphs each. Build the complete
      // font16 atlas once and share it across the UI variants; each copy has
      // isolated metrics and blend mode but does not duplicate textures.
      FontTBL.BitmapFont base = load(assets, "font16", BlendMode.LUMINOSITY_TINT);
      // This compatibility mode is opt-in. Native Chinese fonts have distinct
      // glyph bearings and must be loaded independently for accurate layout.
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
    registerCurrentFonts();
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
    fontridiculous = load(assets, "fontridiculous", BlendMode.TINT_BLACKS);
    ReallyTheLastSucker = load(assets, "ReallyTheLastSucker", BlendMode.ID);
    gameplayFontsLoaded = true;
    applyMetrics();
    registerCurrentFonts();
    generation++;
  }

  private void registerCurrentFonts() {
    register(font6, Role.FONT6);
    register(font8, Role.FONT8);
    register(font16, Role.FONT16);
    register(font24, Role.FONT24);
    register(font30, Role.FONT30);
    register(font42, Role.FONT42);
    register(fontformal10, Role.FONTFORMAL10);
    register(fontformal11, Role.FONTFORMAL11);
    register(fontformal12, Role.FONTFORMAL12);
    register(fontexocet10, Role.FONTEXOCET10);
    register(fontridiculous, Role.FONTRIDICULOUS);
    register(ReallyTheLastSucker, Role.REALLY_THE_LAST_SUCKER);
  }

  private void register(BitmapFont font, Role role) {
    if (font != null) fontRoles.put(font, role);
  }

  /** Returns the stable UI role represented by this font object. */
  public Role roleOf(BitmapFont font) {
    return fontRoles.get(font);
  }

  /** Returns the latest font object for a stable UI role. */
  public BitmapFont get(Role role) {
    if (role == null) return null;
    switch (role) {
      case FONT6: return font6;
      case FONT8: return font8;
      case FONT16: return font16;
      case FONT24: return font24;
      case FONT30: return font30;
      case FONT42: return font42;
      case FONTFORMAL10: return fontformal10;
      case FONTFORMAL11: return fontformal11;
      case FONTFORMAL12: return fontformal12;
      case FONTEXOCET10: return fontexocet10;
      case FONTRIDICULOUS: return fontridiculous;
      case REALLY_THE_LAST_SUCKER: return ReallyTheLastSucker;
      default: throw new AssertionError(role);
    }
  }

  /** Changes only when staged font objects have been replaced. */
  public int generation() {
    return generation;
  }

  /**
   * Returns the measured correction for baseline-anchored labels. The
   * correction is intentionally not written into the shared glyph atlas:
   * centered controls (for example TextButton) must keep their visual center.
   */
  public int baselineCorrection(BitmapFont font) {
    if (!chinese || sharedChinese
        || Boolean.getBoolean("riiablo.font-baseline-raw")) return 0;
    Role role = roleOf(font);
    if (role == null) return 0;
    switch (role) {
      case FONT6: return FontBaselineCalibration.chineseCorrection("font6");
      case FONT8: return FontBaselineCalibration.chineseCorrection("font8");
      case FONT16: return FontBaselineCalibration.chineseCorrection("font16");
      case FONT24: return FontBaselineCalibration.chineseCorrection("font24");
      case FONT30: return FontBaselineCalibration.chineseCorrection("font30");
      case FONT42: return FontBaselineCalibration.chineseCorrection("font42");
      case FONTFORMAL10: return FontBaselineCalibration.chineseCorrection("fontformal10");
      case FONTFORMAL11: return FontBaselineCalibration.chineseCorrection("fontformal11");
      case FONTFORMAL12: return FontBaselineCalibration.chineseCorrection("fontformal12");
      case FONTEXOCET10: return FontBaselineCalibration.chineseCorrection("fontexocet10");
      case FONTRIDICULOUS: return FontBaselineCalibration.chineseCorrection("fontridiculous");
      case REALLY_THE_LAST_SUCKER:
        return FontBaselineCalibration.chineseCorrection("ReallyTheLastSucker");
      default: throw new AssertionError(role);
    }
  }

  /**
   * Computes the actor-Y adjustment needed to align the visible bottoms of a
   * candidate label and a reference label. This includes the per-font
   * baseline corrections used by Label, so callers do not stack a measured
   * value on top of an old hand-tuned offset.
   */
  public int visualBottomAlignmentOffset(BitmapFont reference, CharSequence referenceText,
      BitmapFont candidate, CharSequence candidateText) {
    int referenceBottom = FontBaselineCalibration.textBottom(reference, referenceText)
        + baselineCorrection(reference);
    int candidateBottom = FontBaselineCalibration.textBottom(candidate, candidateText)
        + baselineCorrection(candidate);
    return referenceBottom - candidateBottom;
  }

  /** Starts probing staged font caches without blocking the render thread. */
  public void queueGameplayFonts() {
    if (gameplayFontsLoaded || gameplayFontProbe != null || gameplayFontsQueued) return;
    final String[] names = {"font6", "font8", "font24", "font30", "font42",
        "fontformal10", "fontformal11", "fontridiculous", "ReallyTheLastSucker"};
    final int[] modes = {BlendMode.LUMINOSITY_TINT, BlendMode.LUMINOSITY_TINT,
        BlendMode.ID, BlendMode.ID, BlendMode.ID, BlendMode.LUMINOSITY_TINT,
        BlendMode.LUMINOSITY_TINT, BlendMode.TINT_BLACKS, BlendMode.ID};
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "riiablo-font-cache-probe");
      thread.setDaemon(true);
      return thread;
    });
    gameplayFontProbe = executor.submit(() -> {
      try {
        FontProbe[] result = new FontProbe[names.length];
        for (int i = 0; i < names.length; i++) {
          FileHandle tbl = Riiablo.mpqs.resolve("data\\local\\font\\" + fontDirectory + "\\" + names[i] + ".TBL");
          FileHandle dc6 = Riiablo.mpqs.resolve("data\\local\\font\\" + fontDirectory + "\\" + names[i] + ".DC6");
          FileHandle index = com.riiablo.codec.FontAtlasCache.indexFor(tbl, dc6);
          FileHandle cache = com.riiablo.codec.FontAtlasCache.indexed(index);
          if (cache == null) {
            // One-time migration for caches written by the MD5-only version.
            cache = com.riiablo.codec.FontAtlasCache.fileFor(tbl, dc6);
            if (cache != null && cache.exists()) {
              com.riiablo.codec.FontAtlasCache.remember(index, cache);
            }
          }
          result[i] = new FontProbe(names[i], modes[i], cache != null && cache.exists());
        }
        return result;
      } finally {
        executor.shutdown();
      }
    });
  }

  /** Queues fonts after the background cache probe has completed. */
  public boolean finishQueueGameplayFonts() {
    if (gameplayFontsLoaded || gameplayFontsQueued) return true;
    if (gameplayFontProbe == null || !gameplayFontProbe.isDone()) return false;
    try {
      for (FontProbe probe : gameplayFontProbe.get()) {
        assets.load(getDescriptor(probe.name, probe.blendMode, probe.cached));
      }
      gameplayFontsQueued = true;
      return true;
    } catch (Exception e) {
      throw new RuntimeException("Unable to prepare gameplay fonts", e);
    }
  }

  public boolean isGameplayFontsLoaded() {
    return gameplayFontsLoaded;
  }

  /**
   * Fast check used by the entry screen to avoid showing a second loading
   * animation when all staged fonts already have indexed caches. It never
   * hashes TBL/DC6 contents; a legacy cache is migrated by the normal load
   * path instead.
   */
  public boolean hasIndexedGameplayFontCache() {
    if (gameplayFontsLoaded) return true;
    final String[] names = {"font6", "font8", "font24", "font30", "font42",
        "fontformal10", "fontformal11", "fontridiculous", "ReallyTheLastSucker"};
    for (String name : names) {
      String path = "data\\local\\font\\" + fontDirectory + "\\" + name;
      FileHandle tbl = Riiablo.mpqs.resolve(path + ".TBL");
      FileHandle dc6 = Riiablo.mpqs.resolve(path + ".DC6");
      if (tbl == null || dc6 == null) return false;
      if (com.riiablo.codec.FontAtlasCache.indexed(
          com.riiablo.codec.FontAtlasCache.indexFor(tbl, dc6)) == null) return false;
    }
    return true;
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
    AssetDescriptor<FontTBL.BitmapFont> descriptor = getDescriptor(fontName, blendMode, hasCache(fontName));
    assets.load(descriptor);
    assets.finishLoadingAsset(descriptor);
    return assets.get(descriptor);
  }

  private AssetDescriptor<FontTBL.BitmapFont> getDescriptor(String fontName, int blendMode) {
    return getDescriptor(fontName, blendMode, false);
  }

  private boolean hasCache(String fontName) {
    String path = "data\\local\\font\\" + fontDirectory + "\\" + fontName;
    FileHandle tbl = Riiablo.mpqs.resolve(path + ".TBL");
    FileHandle dc6 = Riiablo.mpqs.resolve(path + ".DC6");
    FileHandle cache = com.riiablo.codec.FontAtlasCache.indexed(
        com.riiablo.codec.FontAtlasCache.indexFor(tbl, dc6));
    if (cache == null) {
      // One-time migration for caches created before the lightweight index.
      // The result is immediately indexed so subsequent launches never hash
      // the large CJK DC6 again.
      cache = com.riiablo.codec.FontAtlasCache.fileFor(tbl, dc6);
      if (cache != null && cache.exists()) {
        com.riiablo.codec.FontAtlasCache.remember(
            com.riiablo.codec.FontAtlasCache.indexFor(tbl, dc6), cache);
      }
    }
    return cache != null && cache.exists();
  }

  private AssetDescriptor<FontTBL.BitmapFont> getDescriptor(String fontName, int blendMode, boolean cached) {
    BitmapFontLoader.Params params = BitmapFontLoader.Params.of(blendMode);
    params.cached = cached;
    return new AssetDescriptor<>("data\\local\\font\\" + fontDirectory + "\\" + fontName + ".TBL", FontTBL.BitmapFont.class, params);
  }

  private static final class FontProbe {
    final String name;
    final int blendMode;
    final boolean cached;
    FontProbe(String name, int blendMode, boolean cached) {
      this.name = name;
      this.blendMode = blendMode;
      this.cached = cached;
    }
  }
}
