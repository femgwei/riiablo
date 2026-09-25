package com.riiablo.codec;

import com.badlogic.gdx.graphics.g2d.BitmapFont;

/** Computes a per-font vertical correction from common glyph pixels. */
public final class FontBaselineCalibration {
  private static final String COMMON_GLYPHS =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private FontBaselineCalibration() {}

  /** Calibration profile generated against the matching English 1.10F MPQ fonts. */
  public static int chineseCorrection(String fontName) {
    switch (fontName) {
      case "font6": return 0;
      case "font8": return -3;
      case "font16": return -3;
      case "font24": return -7;
      case "font30": return -6;
      case "font42": return -4;
      case "fontformal10": return -3;
      case "fontformal11": return -3;
      case "fontformal12": return -17;
      case "fontexocet10": return 4;
      case "fontridiculous": return 5;
      case "ReallyTheLastSucker": return 2;
      default: return 0;
    }
  }

  /**
   * Returns the integer shift required for {@code candidate} to match the
   * visual glyph bottom of {@code reference}. A positive value moves the
   * candidate glyphs upward in the font's coordinate system.
   */
  public static int correction(BitmapFont reference, BitmapFont candidate) {
    int[] deltas = new int[COMMON_GLYPHS.length()];
    int count = 0;
    BitmapFont.BitmapFontData refData = reference.getData();
    BitmapFont.BitmapFontData candidateData = candidate.getData();
    for (int i = 0; i < COMMON_GLYPHS.length(); i++) {
      char c = COMMON_GLYPHS.charAt(i);
      BitmapFont.Glyph ref = refData.getGlyph(c);
      BitmapFont.Glyph actual = candidateData.getGlyph(c);
      if (ref == null || actual == null || ref.height <= 0 || actual.height <= 0) continue;
      int refBottom = ref.yoffset + ref.height;
      int actualBottom = actual.yoffset + actual.height;
      deltas[count++] = refBottom - actualBottom;
    }
    if (count == 0) return 0;
    java.util.Arrays.sort(deltas, 0, count);
    return deltas[count / 2];
  }

  /**
   * Returns the median visible glyph bottom relative to a text baseline.
   * Using the median makes this stable for labels containing a mixture of
   * glyphs with slightly different descenders.
   */
  public static int textBottom(BitmapFont font, CharSequence text) {
    if (font == null || text == null || text.length() == 0) return 0;
    BitmapFont.BitmapFontData data = font.getData();
    int[] bottoms = new int[text.length()];
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      BitmapFont.Glyph glyph = data.getGlyph(text.charAt(i));
      if (glyph == null || glyph.height <= 0) continue;
      bottoms[count++] = glyph.yoffset + glyph.height;
    }
    if (count == 0) return 0;
    java.util.Arrays.sort(bottoms, 0, count);
    return bottoms[count / 2];
  }

  /**
   * Text baseline relative to the Actor origin for a non-wrapped Label using
   * Align.bottom. This mirrors libGDX Label.layout(): the cache baseline is
   * capHeight + ascent - descent above the actor origin.
   */
  public static int labelBaseline(BitmapFont font) {
    if (font == null) return 0;
    BitmapFont.BitmapFontData data = font.getData();
    return Math.round(data.capHeight + data.ascent - data.descent);
  }
}
