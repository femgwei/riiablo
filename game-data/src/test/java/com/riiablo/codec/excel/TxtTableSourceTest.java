package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TxtTableSourceTest {
  @Test void sourceIdentityIsImmutableAndContentSensitive() throws Exception {
    byte[] first = "Id\tName\n1\talpha\n".getBytes(StandardCharsets.ISO_8859_1);
    byte[] second = "Id\tName\n1\tbeta\n".getBytes(StandardCharsets.ISO_8859_1);
    TxtTableSource a = TxtTableSource.fromBytes("data\\global\\excel\\Test.txt",
        TxtTableSource.Version.DIABLO_II_110F, first);
    TxtTableSource same = TxtTableSource.fromBytes("copy.txt",
        TxtTableSource.Version.DIABLO_II_110F, first.clone());
    TxtTableSource changed = TxtTableSource.fromBytes("copy.txt",
        TxtTableSource.Version.DIABLO_II_110F, second);

    assertTrue(a.matches(same));
    assertFalse(a.matches(changed));
    assertEquals(1, a.manifest.rows);
    assertEquals(TxtTableSource.Version.DIABLO_II_110F, a.version);
    assertEquals("data\\global\\excel\\Test.txt", a.path);
  }

  @Test void nativeProjectionRetainsSourceIdentityWhenParsed() throws Exception {
    NativeSkills skills = NativeSkills.parse(
        "skill\tId\nFire Bolt\t1\n".getBytes(StandardCharsets.ISO_8859_1));
    assertNotNull(skills.tableSource());
    assertEquals(TxtTableSource.Version.UNSPECIFIED, skills.tableSource().version);
    assertEquals(1, skills.tableSource().manifest.rows);
  }
}
