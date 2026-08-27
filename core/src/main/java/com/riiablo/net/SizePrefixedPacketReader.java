package com.riiablo.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads exactly one little-endian size-prefixed frame from a blocking stream. */
public final class SizePrefixedPacketReader {
  private SizePrefixedPacketReader() {}

  public static ByteBuffer readFrame(InputStream in, int maxFrameSize) throws IOException {
    if (in == null) throw new NullPointerException("in");
    byte[] prefix = new byte[Integer.BYTES];
    readFully(in, prefix, 0, prefix.length);
    int size = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN).getInt();
    if (size <= 0 || size > maxFrameSize) {
      throw new SizePrefixedPacketAccumulator.InvalidFrameException(size, maxFrameSize);
    }
    byte[] payload = new byte[size];
    readFully(in, payload, 0, payload.length);
    return ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static void readFully(InputStream in, byte[] bytes, int offset, int length)
      throws IOException {
    int total = 0;
    while (total < length) {
      int read = in.read(bytes, offset + total, length - total);
      if (read < 0) {
        throw new EOFException("Incomplete size-prefixed frame: read=" + total
            + " expected=" + length);
      }
      if (read == 0) continue;
      total += read;
    }
  }
}
