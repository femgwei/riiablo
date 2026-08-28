package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

class ExperienceTableNativeTest extends RiiabloTest {
  @Test
  void readsThresholdsAndRatiosFromNativeExperienceTable() {
    ExperienceTable table = ExperienceTable.getInstance();

    assertEquals(22680, table.getExperienceForNextLevel(6, 0));
    assertEquals(22680, table.getExperienceForCurrentLevel(7, 0));
    assertEquals(1024, table.getExpRatio(1));
    assertEquals(976, table.getExpRatio(70));
    assertEquals(10, table.getExpRatioShift());
    assertEquals(99, table.getMaxLevel(0));
  }
}
