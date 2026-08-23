package com.d2moo.common.datatbls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class D2TxtFileParserTest {
  @Test
  void parsesBomMixedLineEndingsCommentsAndEmptyColumns() {
    byte[] data = ("\uFEFFId\tName\tValue\r"
        + "// comment\r\n"
        + "1\t Rogue Encampment \t\n"
        + "2\tBlood Moor\t0xFFFFFFFF")
        .getBytes(StandardCharsets.UTF_8);

    List<String[]> rows = D2TxtFileParser.parseTxtFile(data);

    assertEquals(3, rows.size());
    assertArrayEquals(new String[] {"Id", "Name", "Value"}, rows.get(0));
    assertArrayEquals(new String[] {"1", "Rogue Encampment", ""}, rows.get(1));
    assertArrayEquals(new String[] {"2", "Blood Moor", "0xFFFFFFFF"}, rows.get(2));
  }

  @Test
  void parsesFullUint32HexUsingNativeIntBits() {
    assertEquals(-1, D2TxtFileParser.parseInt("0xFFFFFFFF", 7));
    assertEquals(Integer.MIN_VALUE, D2TxtFileParser.parseInt("0x80000000", 7));
    assertEquals(7, D2TxtFileParser.parseInt("0x100000000", 7));
  }
}
