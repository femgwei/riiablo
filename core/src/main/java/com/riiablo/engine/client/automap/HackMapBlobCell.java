package com.riiablo.engine.client.automap;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Loads the 8-bit indexed BMP format used by jieaido/d2hackmap and converts it
 * to the same one-frame, in-memory DC6 CellFile layout built by
 * {@code LoadBmpCellFile} in the native plugin.
 */
public final class HackMapBlobCell {
  private static final int BMP_FILE_HEADER = 14;
  private static final int BITMAP_INFO_HEADER = 40;
  private static final int DC6_HEADER_SIZE = 24;
  private static final int DC6_FRAME_OFFSET_SIZE = 4;
  private static final int DC6_FRAME_HEADER_SIZE = 32;

  public final int width;
  public final int height;
  /** Top-to-bottom rows of D2 palette indices; index zero is transparent. */
  private final byte[] pixels;

  private HackMapBlobCell(int width, int height, byte[] pixels) {
    this.width = width;
    this.height = height;
    this.pixels = pixels;
  }

  public static HackMapBlobCell load(FileHandle file) {
    if (file == null || !file.exists()) return null;
    byte[] data = file.readBytes();
    try {
      if (data.length < BMP_FILE_HEADER + BITMAP_INFO_HEADER
          || data[0] != 'B' || data[1] != 'M') {
        throw new IllegalArgumentException("not a Windows BMP");
      }
      ByteBuffer in = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
      int pixelOffset = in.getInt(10);
      int dibSize = in.getInt(14);
      int width = in.getInt(18);
      int signedHeight = in.getInt(22);
      int planes = in.getShort(26) & 0xFFFF;
      int bitsPerPixel = in.getShort(28) & 0xFFFF;
      int compression = in.getInt(30);
      if (dibSize < BITMAP_INFO_HEADER || width <= 0 || signedHeight == 0
          || planes != 1 || bitsPerPixel != 8 || compression != 0) {
        throw new IllegalArgumentException(
            "requires uncompressed 8-bit indexed BMP");
      }

      int height = Math.abs(signedHeight);
      int stride = (width + 3) & ~3;
      long required = (long) pixelOffset + (long) stride * height;
      if (pixelOffset < BMP_FILE_HEADER + dibSize || required > data.length) {
        throw new IllegalArgumentException("truncated BMP pixel data");
      }

      boolean bottomUp = signedHeight > 0;
      byte[] pixels = new byte[width * height];
      for (int y = 0; y < height; y++) {
        int sourceY = bottomUp ? height - 1 - y : y;
        System.arraycopy(data, pixelOffset + sourceY * stride,
            pixels, y * width, width);
      }
      return new HackMapBlobCell(width, height, pixels);
    } catch (RuntimeException e) {
      throw new GdxRuntimeException("Invalid HackMap blob BMP: " + file.path(), e);
    }
  }

  /** Returns a copy of the normalized indexed pixels for diagnostics/tests. */
  byte[] copyPixels() {
    return pixels.clone();
  }

  /**
   * Encodes this blob as a one-frame DC6. Palette index 255 is replaced with
   * the marker color, matching d2hackmap's per-draw coltab remap.
   */
  public byte[] encodeDc6(int markerColorIndex) {
    byte[] rle = encodeRle(markerColorIndex & 0xFF);
    int totalSize = DC6_HEADER_SIZE + DC6_FRAME_OFFSET_SIZE
        + DC6_FRAME_HEADER_SIZE + rle.length + 3;
    ByteBuffer out = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

    // DC6Header used by the native LoadBmpCellFile implementation.
    out.putInt(6);
    out.putInt(1);
    out.putInt(0);
    out.putInt(0xEEEEEEEE);
    out.putInt(1);
    out.putInt(1);
    out.putInt(DC6_HEADER_SIZE + DC6_FRAME_OFFSET_SIZE);

    // One frame. Anchoring is applied by the Automap renderer at draw time.
    out.putInt(0);
    out.putInt(width);
    out.putInt(height);
    out.putInt(0);
    out.putInt(0);
    out.putInt(0);
    out.putInt(totalSize);
    out.putInt(rle.length);
    out.put(rle);
    out.put((byte) 0xEE).put((byte) 0xEE).put((byte) 0xEE);
    return out.array();
  }

  private byte[] encodeRle(int markerColorIndex) {
    // Worst case: one literal header per pixel plus one EOL per row.
    byte[] encoded = new byte[pixels.length * 2 + height];
    int dst = 0;
    // DC6 flip=0 stores the bottom scanline first.
    for (int y = height - 1; y >= 0; y--) {
      int row = y * width;
      int x = 0;
      while (x < width) {
        boolean transparent = pixels[row + x] == 0;
        int start = x++;
        while (x < width && x - start < 0x7F
            && (pixels[row + x] == 0) == transparent) {
          x++;
        }
        int count = x - start;
        if (transparent) {
          encoded[dst++] = (byte) (0x80 | count);
        } else {
          encoded[dst++] = (byte) count;
          for (int i = start; i < x; i++) {
            int index = pixels[row + i] & 0xFF;
            encoded[dst++] = (byte) (index == 0xFF ? markerColorIndex : index);
          }
        }
      }
      encoded[dst++] = (byte) 0x80;
    }

    byte[] result = new byte[dst];
    System.arraycopy(encoded, 0, result, 0, dst);
    return result;
  }
}
