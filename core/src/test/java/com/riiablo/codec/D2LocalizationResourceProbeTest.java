package com.riiablo.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.riiablo.D2Language;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Opt-in verification against an installed Diablo II Chinese resource set. */
class D2LocalizationResourceProbeTest {
  @Test
  void loadsNativeChineseStringsAndUnicodeFontMetadata() {
    String home = System.getProperty("d2.localization.home");
    if (home == null || home.isEmpty()) home = System.getenv("D2_LOCALIZATION_HOME");
    Assumptions.assumeTrue(home != null && new File(home, "d2data.mpq").isFile());

    Gdx.app = new HeadlessApplication(new ApplicationAdapter() {});
    MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(home));
    assertTrue(resolver.contains("data\\local\\lng\\chi\\string.tbl"));
    assertTrue(resolver.contains("data\\local\\lng\\chi\\expansionstring.tbl"));
    assertTrue(resolver.contains("data\\local\\lng\\chi\\patchstring.tbl"));
    assertTrue(resolver.contains("data\\local\\font\\chi\\font16.tbl"));
    assertTrue(resolver.contains("data\\local\\font\\chi\\font16.dc6"));
    assertTrue(resolver.contains("data\\local\\ui\\chi\\options.dc6"));
    assertTrue(resolver.contains("data\\local\\ui\\chi\\exit.dc6"));
    assertTrue(resolver.contains("data\\local\\ui\\chi\\returntogame.dc6"));

    StringTBLs strings = new StringTBLs(resolver, D2Language.CHINESE);
    assertEquals("\u82b1\u8cbb\uff1a", strings.lookup("cost"));
    assertEquals("\u51fa\u552e\u50f9\u683c\uff1a ", strings.lookup("Sell"));

    FontTBL font = FontTBL.loadFromFile(
        resolver.resolve("data\\local\\font\\chi\\font16.tbl"));
    assertEquals(13_806, font.cData.length);
    assertEquals('\u0020', font.cData[0].wChar);
    assertEquals('\ufffd', font.cData[font.cData.length - 1].wChar);

    DC6 dc6 = DC6.loadFromFile(
        resolver.resolve("data\\local\\font\\chi\\font16.dc6"));
    FontTBL.BitmapFontData fontData = font.data(dc6);
    try {
      assertTrue(fontData.fontSheets.size > 0);
      assertTrue(fontData.getGlyph('\u82b1') != null);
      assertTrue(fontData.getGlyph('\u8cbb') != null);
    } finally {
      for (Pixmap sheet : fontData.fontSheets) sheet.dispose();
      dc6.dispose();
    }
  }
}
