package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LosslessTxtTableTest {
  @Test
  void preservesSentinelsEmptyCellsDuplicateHeadersAndRawRows() throws Exception {
    byte[] bytes = ("\uFEFFId\tName\t\tName\r\n"
        + "Expansion\t\t\t\r\n"
        + "1\t Fallen \t\r\n").getBytes(StandardCharsets.UTF_8);

    LosslessTxtTable table = LosslessTxtTable.parse(bytes);

    assertEquals(4, table.columnCount());
    assertEquals(2, table.rowCount());
    assertEquals(2, table.columnIndexes("name").size());
    assertEquals("Expansion", table.get(0, "id"));
    assertEquals(" Fallen ", table.get(1, "Name"));
    assertEquals("", table.get(1, 3));
    assertEquals("1\t Fallen \t", table.row(1).rawLine());
    assertEquals(3, table.row(1).sourceLine());
  }

  @Test
  void typedAccessUsesNativeBooleanAndUint32RulesWithoutChangingRawCells() throws Exception {
    LosslessTxtTable table = LosslessTxtTable.parse(
        "Id\tEnabled\tLooseBool\tMask\tBad\n1\t1\t2\t0xFFFFFFFF\txyz\n"
            .getBytes(StandardCharsets.ISO_8859_1));

    assertEquals(Integer.valueOf(1), table.getInt(0, "Id"));
    assertEquals(Integer.valueOf(-1), table.getInt(0, "Mask"));
    assertNull(table.getInt(0, "Bad"));
    assertTrue(table.getBoolean(0, "Enabled"));
    assertFalse(table.getBoolean(0, "LooseBool"));
    assertTrue(table.getNativeBit(0, "LooseBool"));
    assertEquals("0xFFFFFFFF", table.get(0, "Mask"));
  }

  @Test
  void nativeNumericAccessDefaultsDisabledStarValues() throws Exception {
    LosslessTxtTable table = LosslessTxtTable.parse(
        "Function\tFlag\n*16\t2\n".getBytes(StandardCharsets.ISO_8859_1));

    assertNull(table.getInt(0, "Function"));
    assertEquals(0, table.getNativeInt(0, "Function"));
    assertTrue(table.getNativeBit(0, "Flag"));
    assertEquals("*16", table.get(0, "Function"));
  }

  @Test
  void comparatorReportsStableHeaderAndFieldCoordinates() throws Exception {
    LosslessTxtTable expected = LosslessTxtTable.parse(
        "Id\tState\n0\tnone\n1\tfrozen\n".getBytes(StandardCharsets.ISO_8859_1));
    LosslessTxtTable actual = LosslessTxtTable.parse(
        "Id\tstateName\n0\tnone\n1\tcold\n".getBytes(StandardCharsets.ISO_8859_1));

    List<TxtTableComparator.Difference> differences =
        TxtTableComparator.compare("States.txt", expected, actual);

    assertEquals(2, differences.size());
    assertEquals(TxtTableComparator.Kind.HEADER, differences.get(0).kind);
    assertEquals(-1, differences.get(0).row);
    assertEquals(1, differences.get(0).column);
    assertEquals(TxtTableComparator.Kind.FIELD, differences.get(1).kind);
    assertEquals(1, differences.get(1).row);
    assertEquals(1, differences.get(1).column);
    assertTrue(differences.get(1).toString().contains("States.txt"));
  }

  @Test
  void lookupIsCaseInsensitiveButValuesRemainCaseSensitive() throws Exception {
    LosslessTxtTable table = LosslessTxtTable.parse(
        "Skill\tId\nFire Bolt\t36\n".getBytes(StandardCharsets.ISO_8859_1));

    assertEquals(0, table.columnIndex("SKILL"));
    assertEquals(0, table.findFirst("skill", "Fire Bolt"));
    assertEquals(-1, table.findFirst("skill", "fire bolt"));
    assertEquals("", table.get(0, "missing"));
  }

  @Test
  void preservesBlankRowsAndSurplusCells() throws Exception {
    LosslessTxtTable expected = LosslessTxtTable.parse(
        "Id\tName\n\n1\tfallen\textra\n".getBytes(StandardCharsets.ISO_8859_1));
    LosslessTxtTable actual = LosslessTxtTable.parse(
        "Id\tName\n\n1\tfallen\n".getBytes(StandardCharsets.ISO_8859_1));

    assertEquals(2, expected.rowCount());
    assertEquals("", expected.row(0).rawLine());
    assertEquals(3, expected.valueColumnCount());
    List<TxtTableComparator.Difference> differences =
        TxtTableComparator.compare("MonStats.txt", expected, actual);
    assertEquals(1, differences.size());
    assertEquals(2, differences.get(0).column);
    assertEquals("extra", differences.get(0).expected);
  }

  @Test
  void manifestSeparatesRawAndSemanticIdentity() throws Exception {
    byte[] lf = "Id\tName\n1\tfallen\n".getBytes(StandardCharsets.ISO_8859_1);
    byte[] crlf = "Id\tName\r\n1\tfallen\r\n".getBytes(StandardCharsets.ISO_8859_1);

    TxtTableManifest left = TxtTableManifest.create(lf);
    TxtTableManifest right = TxtTableManifest.create(crlf);

    assertFalse(left.rawSha256.equals(right.rawSha256));
    assertEquals(left.headerSha256, right.headerSha256);
    assertEquals(left.semanticSha256, right.semanticSha256);
    assertEquals(1, left.rows);
    assertEquals(2, left.headerColumns);
  }
}
