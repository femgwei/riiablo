package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class D2KeyTest {
  @Test
  void readsAndWritesCharacterFormWithoutChangingOpaqueBytes() throws Exception {
    byte[] source = new byte[6 + 2 * D2Key.RECORD_SIZE + D2Key.TRAILER_SIZE];
    source[0] = 0x25;
    source[6] = 0x41;
    source[8] = 1;
    source[16] = 0x43;
    source[22] = 1;
    source[24] = 0x5A;
    source[25] = (byte) 0xFF;

    D2Key key = D2Key.read(source);
    assertEquals(2, key.recordCount());
    assertEquals(0x41, key.record(0).keyCode());
    assertEquals(1, key.record(0).mappingMarker());
    assertEquals(0x43, key.record(1).keyCode());
    assertEquals(0xFF5A0001, key.record(1).actionId());

    key.record(1).setKeyCode(0x42);
    byte[] expected = source.clone();
    expected[16] = 0x42;
    assertArrayEquals(expected, key.toBytes());
  }

  @Test
  void acceptsDefaultKeyHeaderAndCreatesCharacterSizedFile() throws Exception {
    byte[] source = new byte[10 + D2Key.RECORD_SIZE + D2Key.TRAILER_SIZE];
    source[0] = 'W';
    source[1] = 'S';
    source[2] = 0x25;
    source[4] = 0x7A;
    source[5] = 0x04;
    D2Key key = D2Key.read(source);
    assertEquals(1, key.recordCount());
    assertEquals(source.length, key.toBytes().length);

    D2Key empty = D2Key.emptyCharacter();
    assertEquals(D2Key.CHARACTER_RECORD_COUNT, empty.recordCount());
    assertEquals(1142, empty.toBytes().length);
  }
}
