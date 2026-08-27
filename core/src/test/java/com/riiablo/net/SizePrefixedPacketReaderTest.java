package com.riiablo.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class SizePrefixedPacketReaderTest {
  @Test
  void readsExactlyOneFrameWithoutConsumingTheNext() throws Exception {
    byte[] first = packet(1, 2, 3);
    byte[] second = packet(4, 5);
    byte[] stream = new byte[first.length + second.length];
    System.arraycopy(first, 0, stream, 0, first.length);
    System.arraycopy(second, 0, stream, first.length, second.length);
    ByteArrayInputStream in = new ByteArrayInputStream(stream);

    assertArrayEquals(new byte[] {1, 2, 3}, bytes(SizePrefixedPacketReader.readFrame(in, 64)));
    assertArrayEquals(new byte[] {4, 5}, bytes(SizePrefixedPacketReader.readFrame(in, 64)));
  }

  @Test
  void handlesPrefixAndPayloadSplitIntoShortReads() throws Exception {
    InputStream in = new FilterInputStream(new ByteArrayInputStream(packet(7, 8, 9, 10))) {
      @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        return super.read(bytes, offset, Math.min(2, length));
      }
    };

    assertArrayEquals(new byte[] {7, 8, 9, 10},
        bytes(SizePrefixedPacketReader.readFrame(in, 64)));
  }

  @Test
  void rejectsTruncatedAndOversizedFrames() {
    assertThrows(EOFException.class,
        () -> SizePrefixedPacketReader.readFrame(
            new ByteArrayInputStream(new byte[] {4, 0, 0}), 64));
    assertThrows(SizePrefixedPacketAccumulator.InvalidFrameException.class,
        () -> SizePrefixedPacketReader.readFrame(
            new ByteArrayInputStream(prefix(65)), 64));
  }

  private static byte[] packet(int... payload) {
    ByteBuffer buffer = ByteBuffer.allocate(4 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(payload.length);
    for (int value : payload) buffer.put((byte) value);
    return buffer.array();
  }

  private static byte[] prefix(int size) {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(size).array();
  }

  private static byte[] bytes(ByteBuffer buffer) {
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);
    return bytes;
  }
}
