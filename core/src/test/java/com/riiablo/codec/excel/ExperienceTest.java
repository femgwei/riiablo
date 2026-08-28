package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

class ExperienceTest extends RiiabloTest {
  @Test
  void loadsNativeThresholdsAndRatioMetadata() {
    Experience table = Riiablo.files.Experience;
    assertNotNull(table);
    assertEquals(99, table.max().Amazon);
    assertEquals(500, table.level(1).Amazon);
    assertEquals(22680, table.level(6).Amazon);
    assertEquals(3520485254L, table.level(98).Amazon);
    assertEquals(10, table.max().ExpRatio);
    assertEquals(1024, table.level(1).ExpRatio);
  }
}
