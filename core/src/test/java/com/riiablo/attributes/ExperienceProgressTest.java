package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies the exact values consumed by the client experience bar. */
class ExperienceProgressTest {
  @Test
  void progressIsEmptyHalfFullAndResetsAtLevelBoundary() {
    ExperienceTable table = ExperienceTable.getInstance();
    long levelOneStart = table.getExperienceForCurrentLevel(1, 0);
    long levelTwoStart = table.getExperienceForCurrentLevel(2, 0);
    long levelOneEnd = table.getExperienceForNextLevel(1, 0);
    long halfway = levelOneStart + (levelOneEnd - levelOneStart) / 2;

    assertEquals(0f, table.getProgress(1, 0, levelOneStart), 0.0001f);
    assertEquals(0.5f, table.getProgress(1, 0, halfway), 0.0001f);
    assertEquals(1f, table.getProgress(1, 0, levelOneEnd), 0.0001f);
    assertEquals(0f, table.getProgress(2, 0, levelTwoStart), 0.0001f,
        "after level-up progress must restart from the new level threshold");
    assertEquals(0f, table.getProgress(1, 0, -100), 0.0001f);
    assertEquals(1f, table.getProgress(1, 0, Long.MAX_VALUE), 0.0001f);
  }
}
