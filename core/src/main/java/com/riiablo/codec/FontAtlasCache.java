package com.riiablo.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.riiablo.graphics.PaletteIndexedPixmap;

/**
 * Persistent cache for the expensive, decoded CJK font atlas.
 *
 * <p>The source TBL/DC6 pair is identified by MD5.  The cache stores the
 * palette-indexed atlas pages and the glyph records, so a cache hit does not
 * need to parse the TBL or decompress thousands of DC6 frames.</p>
 */
public final class FontAtlasCache {
  private static final int MAGIC = 0x52464E54; // RFNT
  // Glyph bearings are part of the serialized data. Bump this whenever the
  // bearing calculation changes so stale atlases cannot restore old offsets.
  private static final int VERSION = 3;
  private FontAtlasCache() {}

  public static FileHandle fileFor(FileHandle tbl, FileHandle dc6) {
    try {
      String configured = System.getProperty("riiablo.cache.dir", "").trim();
      File root;
      if (!configured.isEmpty()) {
        root = new File(configured);
      } else {
        String localAppData = System.getenv("LOCALAPPDATA");
        root = localAppData == null || localAppData.trim().isEmpty()
            ? new File(System.getProperty("user.home"), ".riiablo/cache")
            : new File(localAppData, "Riiablo/cache");
      }
      return new FileHandle(new File(root, "fonts/font-" + sourceKey(tbl, dc6) + ".bin"));
    } catch (Throwable ignored) {
      return null;
    }
  }

  /** Path-based lookup for the persistent content-addressed cache index. */
  public static FileHandle indexFor(FileHandle tbl, FileHandle dc6) {
    try {
      String configured = System.getProperty("riiablo.cache.dir", "").trim();
      File root;
      if (!configured.isEmpty()) {
        root = new File(configured);
      } else {
        String localAppData = System.getenv("LOCALAPPDATA");
        root = localAppData == null || localAppData.trim().isEmpty()
            ? new File(System.getProperty("user.home"), ".riiablo/cache")
            : new File(localAppData, "Riiablo/cache");
      }
      // MPQFileHandle.path() only returns the parent directory. Include the
      // file names and cheap source sizes so each font gets its own index and
      // a changed resource naturally selects a new cache entry. This avoids
      // reading the complete TBL/DC6 just to calculate an MD5 on startup.
      String identity = tbl.toString() + "|" + tbl.length() + "|"
          + dc6.toString() + "|" + dc6.length();
      StringBuilder key = new StringBuilder(identity.length());
      for (int i = 0; i < identity.length(); i++) {
        char c = identity.charAt(i);
        key.append(Character.isLetterOrDigit(c) || c == '.' || c == '-'
            ? c : '_');
      }
      return new FileHandle(new File(root, "fonts/index-" + key + ".idx"));
    } catch (Throwable ignored) {
      return null;
    }
  }

  /** Returns the cache path recorded in an index, or {@code null}. */
  public static FileHandle indexed(FileHandle index) {
    if (index == null || !index.exists()) return null;
    try {
      String path = index.readString("UTF-8").trim();
      if (path.isEmpty()) return null;
      FileHandle cache = new FileHandle(path);
      return cache.exists() ? cache : null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  /** Atomically records a content-addressed cache path for future startups. */
  public static void remember(FileHandle index, FileHandle cache) {
    if (index == null || cache == null) return;
    try {
      FileHandle parent = index.parent();
      if (parent != null) parent.mkdirs();
      FileHandle temporary = new FileHandle(index.path() + ".tmp");
      temporary.writeString(cache.path(), false, "UTF-8");
      if (index.exists()) index.delete();
      temporary.moveTo(index);
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.debug("FontAtlasCache", "Unable to write " + index, t);
    }
  }

  public static CachedData read(FileHandle file) {
    if (file == null || !file.exists()) return null;
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file.readBytes()))) {
      if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;

      CachedData data = new CachedData();
      int pages = in.readInt();
      if (pages <= 0 || pages > 128) return null;
      data.pages = new Array<>(true, pages, Pixmap.class);
      for (int i = 0; i < pages; i++) {
        int width = in.readInt();
        int height = in.readInt();
        int length = in.readInt();
        if (width <= 0 || height <= 0 || length != width * height || length > 64 * 1024 * 1024) {
          dispose(data.pages);
          return null;
        }
        byte[] pixels = new byte[length];
        in.readFully(pixels);
        data.pages.add(new PaletteIndexedPixmap(width, height, pixels));
      }

      data.padTop = in.readFloat();
      data.padRight = in.readFloat();
      data.padBottom = in.readFloat();
      data.padLeft = in.readFloat();
      data.lineHeight = in.readFloat();
      data.xHeight = in.readFloat();
      data.capHeight = in.readFloat();
      data.descent = in.readFloat();
      data.ascent = in.readFloat();
      data.down = in.readFloat();
      data.spaceXadvance = in.readFloat();
      data.scaleX = in.readFloat();
      data.scaleY = in.readFloat();
      data.markupEnabled = in.readBoolean();
      data.blankLineScale = in.readFloat();
      data.missingGlyphId = in.readInt();

      int glyphCount = in.readInt();
      if (glyphCount <= 0 || glyphCount > 100_000) {
        dispose(data.pages);
        return null;
      }
      data.glyphs = new GlyphData[glyphCount];
      for (int i = 0; i < glyphCount; i++) {
        GlyphData glyph = data.glyphs[i] = new GlyphData();
        glyph.id = in.readInt();
        glyph.page = in.readInt();
        glyph.srcX = in.readInt();
        glyph.srcY = in.readInt();
        glyph.width = in.readInt();
        glyph.height = in.readInt();
        glyph.xoffset = in.readInt();
        glyph.yoffset = in.readInt();
        glyph.xadvance = in.readInt();
        glyph.fixedWidth = in.readBoolean();
      }
      if (in.available() != 0) {
        dispose(data.pages);
        return null;
      }
      return data;
    } catch (Throwable ignored) {
      return null;
    }
  }

  public static void write(FileHandle file, FontTBL.BitmapFontData source) {
    if (file == null) return;
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(bytes)) {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(source.fontSheets.size);
        for (Pixmap page : source.fontSheets) {
          int width = page.getWidth();
          int height = page.getHeight();
          byte[] pixels = pixels(page, width * height);
          out.writeInt(width);
          out.writeInt(height);
          out.writeInt(pixels.length);
          out.write(pixels);
        }

        out.writeFloat(source.padTop);
        out.writeFloat(source.padRight);
        out.writeFloat(source.padBottom);
        out.writeFloat(source.padLeft);
        out.writeFloat(source.lineHeight);
        out.writeFloat(source.xHeight);
        out.writeFloat(source.capHeight);
        out.writeFloat(source.descent);
        out.writeFloat(source.ascent);
        out.writeFloat(source.down);
        out.writeFloat(source.spaceXadvance);
        out.writeFloat(source.scaleX);
        out.writeFloat(source.scaleY);
        out.writeBoolean(source.markupEnabled);
        out.writeFloat(source.blankLineScale);
        out.writeInt(source.missingGlyphId());

        GlyphData[] glyphs = source.glyphsSnapshot();
        out.writeInt(glyphs.length);
        for (GlyphData glyph : glyphs) {
          out.writeInt(glyph.id);
          out.writeInt(glyph.page);
          out.writeInt(glyph.srcX);
          out.writeInt(glyph.srcY);
          out.writeInt(glyph.width);
          out.writeInt(glyph.height);
          out.writeInt(glyph.xoffset);
          out.writeInt(glyph.yoffset);
          out.writeInt(glyph.xadvance);
          out.writeBoolean(glyph.fixedWidth);
        }
      }

      FileHandle parent = file.parent();
      if (parent != null) parent.mkdirs();
      FileHandle temporary = new FileHandle(file.path() + ".tmp");
      temporary.writeBytes(bytes.toByteArray(), false);
      if (file.exists()) file.delete();
      temporary.moveTo(file);
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.debug("FontAtlasCache", "Unable to write " + file, t);
    }
  }

  public static void dispose(CachedData data) {
    if (data != null) dispose(data.pages);
  }

  private static void dispose(Array<Pixmap> pages) {
    if (pages == null) return;
    for (Pixmap page : pages) page.dispose();
  }

  private static byte[] pixels(Pixmap page, int length) {
    byte[] result = new byte[length];
    java.nio.ByteBuffer buffer = page.getPixels().duplicate();
    buffer.clear();
    buffer.get(result);
    return result;
  }

  private static String sourceKey(FileHandle tbl, FileHandle dc6) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      digest.update(tbl.readBytes());
      digest.update((byte) 0);
      digest.update(dc6.readBytes());
      byte[] result = digest.digest();
      StringBuilder key = new StringBuilder(result.length * 2);
      for (byte b : result) key.append(String.format("%02x", b & 0xff));
      return key.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("MD5 is unavailable", e);
    }
  }

  public static final class CachedData {
    Array<Pixmap> pages;
    float padTop, padRight, padBottom, padLeft;
    float lineHeight, xHeight, capHeight, descent, ascent, down;
    float spaceXadvance, scaleX, scaleY, blankLineScale;
    boolean markupEnabled;
    int missingGlyphId;
    GlyphData[] glyphs;
  }

  static final class GlyphData {
    int id, page, srcX, srcY, width, height, xoffset, yoffset, xadvance;
    boolean fixedWidth;
  }
}
