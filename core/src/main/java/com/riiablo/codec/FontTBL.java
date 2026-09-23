package com.riiablo.codec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.text.StringEscapeUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.StreamUtils;

import com.riiablo.codec.util.BBox;
import com.riiablo.graphics.PaletteIndexedPixmap;
import com.riiablo.util.BufferUtils;

public class FontTBL {
  private static final String TAG = "FontTBL";
  private static final boolean DEBUG       = true;
  private static final boolean DEBUG_CHARS = DEBUG && false;

  /** Number of entries in the Latin font tables; CJK tables contain 13,806. */
  public static final int CHARS = 256;
  private static final int CHAR_SHEET_PADDING = 2;
  private static final int MAX_SHEET_SIZE = 2048;

  final Header   header;
  final CharData cData[];

  private FontTBL(Header header, CharData[] cData) {
    this.header = header;
    this.cData  = cData;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("header", header)
        .append("cData", cData)
        .toString();
  }

  public BitmapFontData data(com.riiablo.codec.DC6 dc6) {
    return new BitmapFontData(dc6);
  }

  public class BitmapFontData extends com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData {
    final  com.riiablo.codec.DC6 dc6;
    final  Array<Pixmap>        fontSheets;
    public int                  blendMode;
    BitmapFontData(DC6 dc6) {
      this.dc6 = dc6;
      BBox box = dc6.directions[0].box;
      final int charWidth = box.width;
      final int charHeight = box.height;
      final int columnWidth = charWidth + CHAR_SHEET_PADDING;
      final int columnHeight = charHeight + CHAR_SHEET_PADDING;
      final int columns = Math.max(1, MAX_SHEET_SIZE / columnWidth);
      final int rows = Math.max(1, MAX_SHEET_SIZE / columnHeight);
      final int glyphsPerPage = columns * rows;
      fontSheets = createFontSheets(columns, rows, glyphsPerPage, columnWidth, columnHeight);
      padLeft = padTop = padRight = padBottom = 0;
      lineHeight = xHeight = capHeight = charHeight;
      descent = 0;
      for (int i = 0; i < cData.length; i++) {
        CharData cData = FontTBL.this.cData[i];
        BitmapFont.Glyph glyph = new BitmapFont.Glyph();
        setGlyph(cData.wChar, glyph);
        if (missingGlyph == null || cData.wChar == '\ufffd') missingGlyph = glyph;

        glyph.id = cData.wChar;
        glyph.page = i / glyphsPerPage;
        int pageIndex = i % glyphsPerPage;

        glyph.srcX = (pageIndex % columns) * columnWidth;
        glyph.srcY = (pageIndex / columns) * columnHeight;

        glyph.width = Math.min(cData.width + 1, charWidth);
        glyph.height = charHeight; // this was  {@code charHeight - 1} before, maybe because of no glyph padding in the backing texture
        glyph.yoffset = -(2 * glyph.height);
        glyph.xadvance = cData.width;

        // This was messing with
        //if (glyph.width > 0 && glyph.height > 0 && glyph.yoffset < descent) descent = glyph.yoffset;
      }
      descent += padBottom;
      ascent = capHeight;
      down = -lineHeight;
    }

    private BitmapFontData(DC6 dc6, Array<Pixmap> fontSheets) {
      this.dc6 = dc6;
      this.fontSheets = fontSheets;
    }

    /** Creates a metrics-isolated view that reuses this font's glyph atlas. */
    BitmapFontData copyForSharedSheets() {
      BitmapFontData copy = new BitmapFontData(dc6, fontSheets);
      copy.blendMode = blendMode;
      copy.padTop = padTop;
      copy.padRight = padRight;
      copy.padBottom = padBottom;
      copy.padLeft = padLeft;
      copy.lineHeight = lineHeight;
      copy.xHeight = xHeight;
      copy.capHeight = capHeight;
      copy.descent = descent;
      copy.ascent = ascent;
      copy.down = down;
      copy.spaceXadvance = spaceXadvance;
      copy.scaleX = scaleX;
      copy.scaleY = scaleY;
      copy.markupEnabled = markupEnabled;
      copy.blankLineScale = blankLineScale;
      // Glyph metadata is immutable after FontTBL construction. Share the
      // page arrays instead of cloning every CJK glyph for every visual font
      // variant; the old deep copy allocated ~150k Glyph objects at startup.
      copy.missingGlyph = missingGlyph;
      for (int page = 0; page < glyphs.length; page++) {
        copy.glyphs[page] = glyphs[page];
      }
      return copy;
    }

    /**
     * Applies a small baseline correction to every glyph in this atlas.
     *
     * <p>The native Chinese font16 atlas is reused by the compact/formal
     * font variants.  Those variants have different line metrics, while the
     * shared glyph records keep the same bitmap baseline.  Adjusting the
     * glyph offset once on the base atlas keeps all shared views aligned.</p>
     */
    public void shiftGlyphsY(int delta) {
      if (delta == 0) return;
      for (int page = 0; page < glyphs.length; page++) {
        BitmapFont.Glyph[] pageGlyphs = glyphs[page];
        if (pageGlyphs == null) continue;
        for (BitmapFont.Glyph glyph : pageGlyphs) {
          if (glyph != null) glyph.yoffset += delta;
        }
      }
    }

    private BitmapFont.Glyph copyGlyph(BitmapFont.Glyph source) {
      if (source == null) return null;
      BitmapFont.Glyph target = new BitmapFont.Glyph();
      target.id = source.id;
      target.srcX = source.srcX;
      target.srcY = source.srcY;
      target.width = source.width;
      target.height = source.height;
      target.u = source.u;
      target.v = source.v;
      target.u2 = source.u2;
      target.v2 = source.v2;
      target.xoffset = source.xoffset;
      target.yoffset = source.yoffset;
      target.xadvance = source.xadvance;
      target.fixedWidth = source.fixedWidth;
      target.page = source.page;
      if (source.kerning != null) target.kerning = source.kerning.clone();
      return target;
    }

    Array<Pixmap> createFontSheets(
        int columns, int rows, int glyphsPerPage, int columnWidth, int columnHeight) {
      int pages = (cData.length + glyphsPerPage - 1) / glyphsPerPage;
      Array<Pixmap> sheets = new Array<>(true, pages, Pixmap.class);
      for (int page = 0; page < pages; page++) {
        int first = page * glyphsPerPage;
        int count = Math.min(glyphsPerPage, cData.length - first);
        int usedRows = (count + columns - 1) / columns;
        int usedColumns = Math.min(columns, count);
        Pixmap sheet = new PaletteIndexedPixmap(
            usedColumns * columnWidth, usedRows * columnHeight);
        sheets.add(sheet);
        for (int local = 0; local < count; local++) {
          CharData charData = cData[first + local];
          int frameIndex = charData.imageIndex | charData.nChar << 8;
          if (frameIndex < 0 || frameIndex >= dc6.getNumFramesPerDir()) {
            throw new GdxRuntimeException("Font glyph frame is out of range: " + frameIndex);
          }
          Pixmap frame = dc6.getPixmap(0, frameIndex);
          sheet.drawPixmap(frame,
              local % columns * columnWidth,
              local / columns * columnHeight);
        }
      }
      return sheets;
    }
  }

  public static class BitmapFont extends com.badlogic.gdx.graphics.g2d.BitmapFont {
    private int blendMode;

    public BitmapFont(FontTBL.BitmapFontData data) {
      super(data, createRegions(data), true);
      blendMode = data.blendMode;
      setOwnsTexture(true);
    }

    private BitmapFont(FontTBL.BitmapFontData data, Array<TextureRegion> regions, int blendMode) {
      super(data, regions, false);
      this.blendMode = blendMode;
      data.blendMode = blendMode;
    }

    /**
     * Returns a font with independent metrics and blend mode backed by the
     * same texture atlas. This avoids rebuilding the full CJK DC6 atlas for
     * every visual font size.
     */
    public BitmapFont sharedAtlasCopy(int blendMode) {
      FontTBL.BitmapFontData copy = ((FontTBL.BitmapFontData) getData()).copyForSharedSheets();
      return new BitmapFont(copy, getRegions(), blendMode);
    }

    private static Array<TextureRegion> createRegions(FontTBL.BitmapFontData data) {
      Array<TextureRegion> regions = new Array<>(true, data.fontSheets.size, TextureRegion.class);
      for (Pixmap sheet : data.fontSheets) {
        Texture texture = new Texture(new PixmapTextureData(sheet, null, false, true, false));
        regions.add(new TextureRegion(texture));
      }
      return regions;
    }

    public int getBlendMode() {
      return blendMode;
    }

    public void setBlendMode(int blendMode) {
      this.blendMode = blendMode;
    }
  }

  public static FontTBL loadFromFile(FileHandle file) {
    return loadFromArray(file.readBytes());
  }

  public static FontTBL loadFromArray(byte[] data) {
    return loadFromStream(new ByteArrayInputStream(data));
  }

  public static FontTBL loadFromStream(InputStream in) {
    try {
      Header header = new Header(in);
      if (DEBUG) Gdx.app.debug(TAG, header.toString());

      if (!header.id.equals("Woo!"))
        throw new GdxRuntimeException("Not a valid TBL file header: " + StringEscapeUtils.escapeJava(header.id));
      if (header.one != 0x01)
        throw new GdxRuntimeException("Not a valid TBL file version: " + header.one);

      int remaining = in.available();
      if (remaining == 0 || remaining % CharData.SIZE != 0) {
        throw new GdxRuntimeException("Invalid font TBL glyph data length: " + remaining);
      }
      int chars = remaining / CharData.SIZE;
      CharData[] cData = new CharData[chars];
      for (int i = 0; i < chars; i++) {
        CharData c = cData[i] = new CharData(in);
        if (DEBUG_CHARS) Gdx.app.debug(TAG, c.toString());
        assert c.bTrue == 1;
      }

      assert in.available() == 0;
      return new FontTBL(header, cData);
    } catch (Throwable t) {
      throw new GdxRuntimeException("Couldn't load font TBL from stream.", t);
    } finally {
      StreamUtils.closeQuietly(in);
    }
  }

  static class Header {
    static final int SIZE = 12;

    String id;     // 4
    short  one;    // 2
    int    locale; // 4
    int    height; // 1
    int    width;  // 1

    Header(InputStream in) throws IOException {
      ByteBuffer buffer = ByteBuffer.wrap(IOUtils.readFully(in, SIZE)).order(ByteOrder.LITTLE_ENDIAN);
      id     = BufferUtils.readString(buffer, 4);
      one    = buffer.getShort();
      locale = buffer.get() << 24 | buffer.get() << 16 | buffer.get() << 8 | buffer.get();
      height = BufferUtils.readUnsignedByte(buffer);
      width  = BufferUtils.readUnsignedByte(buffer);
      assert !buffer.hasRemaining();
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("id", StringEscapeUtils.escapeJava(id))
          .append("one", Integer.toHexString(one))
          .append("locale", Integer.toHexString(locale))
          .append("height", height)
          .append("width", width)
          .toString();
    }
  }
  static class CharData {
    static final int SIZE = 14;

    char  wChar;      // 2
    byte  nUnk;       // 1
    int   width;      // 1
    int   height;     // 1
    byte  bTrue;      // 1
    short wUnkEx;     // 2
    int   imageIndex; // 1
    int   nChar;      // 1
    int   dwUnk;      // 4

    CharData(InputStream in) throws IOException {
      ByteBuffer buffer = ByteBuffer.wrap(IOUtils.readFully(in, SIZE)).order(ByteOrder.LITTLE_ENDIAN);
      wChar      = (char) BufferUtils.readUnsignedShort(buffer);
      nUnk       = buffer.get();
      width      = BufferUtils.readUnsignedByte(buffer);
      height     = BufferUtils.readUnsignedByte(buffer);
      bTrue      = buffer.get();
      wUnkEx     = buffer.getShort();
      imageIndex = BufferUtils.readUnsignedByte(buffer);
      nChar      = BufferUtils.readUnsignedByte(buffer);
      dwUnk      = buffer.getInt();
      assert !buffer.hasRemaining();
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("wChar", StringEscapeUtils.escapeJava(Character.toString(wChar)))
          //.append("nUnk", Integer.toHexString(nUnk))
          .append("width", width)
          .append("height", height)
          //.append("bTrue", bTrue)
          //.append("wUnkEx", Integer.toHexString(wUnkEx))
          .append("imageIndex", imageIndex)
          .append("nChar", nChar)
          //.append("dwUnk", Integer.toHexString(dwUnk))
          .toString();
    }
  }
}
