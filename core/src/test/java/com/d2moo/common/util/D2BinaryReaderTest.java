package com.d2moo.common.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class D2BinaryReaderTest {
  @Test
  void readsSignedAndUnsignedLittleEndianValues() {
    byte[] data = {
        (byte) 0x80,
        (byte) 0xFF,
        0x34,
        0x12,
        (byte) 0xEF,
        (byte) 0xCD,
        (byte) 0xAB,
        (byte) 0x89
    };

    assertEquals(-128, D2BinaryReader.readInt8(data, 0));
    assertEquals(128, D2BinaryReader.readUInt8(data, 0));
    assertEquals(-128, D2BinaryReader.readInt16(data, 0));
    assertEquals(0xFF80, D2BinaryReader.readUInt16(data, 0));
    assertEquals(0x1234FF80, D2BinaryReader.readInt32(data, 0));
    assertEquals(0x89ABCDEF1234FF80L, D2BinaryReader.readInt64(data, 0));
    assertEquals(0x89ABCDEFL, D2BinaryReader.readUInt32(data, 4));
  }

  @Test
  void readsAsciiWithoutPlatformCharsetOrWhitespaceTrimming() {
    byte[] data = {' ', 'A', ' ', 0, 'B'};

    assertEquals(" A ", D2BinaryReader.readNullTerminatedString(data, 0, data.length));
    assertEquals(" A ", D2BinaryReader.readString(data, 0, 3));
  }

  @Test
  void validatesRangesWithoutIntegerOverflow() {
    byte[] data = {1, 2, 3, 4};

    assertTrue(D2BinaryReader.hasEnoughData(data, 4, 0));
    assertFalse(D2BinaryReader.hasEnoughData(data, Integer.MAX_VALUE, 2));
    assertFalse(D2BinaryReader.hasEnoughData(data, 2, Integer.MAX_VALUE));
    assertEquals(0, D2BinaryReader.readInt32(data, 1));
    assertArrayEquals(new byte[0], D2BinaryReader.readBytes(data, 3, 2));
  }

  @Test
  void returnsIndependentByteRanges() {
    byte[] data = {1, 2, 3, 4};

    byte[] copy = D2BinaryReader.readBytes(data, 1, 2);
    data[1] = 9;

    assertArrayEquals(new byte[] {2, 3}, copy);
  }
}
