package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NativeHirelingExperienceTableTest {
  @Test
  void nativeSkillRollHonorsDefaultChanceAndCumulativeRanges() {
    NativeHirelingExperienceTable.Row row = new NativeHirelingExperienceTable.Row(
        0, 1, 100, 40, 0, 10, 0, 20, 0, 20, 0,
        10, 0, 1, 2, 1, 0, 0,
        new int[] { 6, 7 }, new int[] { 1, 1 },
        new int[] { 1, 1 }, new int[] { 0, 0 })
        .withDefaultChance(10)
        .withChances(new int[] { 4, 8 }, new int[] { 0, 0 });
    NativeHirelingExperienceTable table = new NativeHirelingExperienceTable().add(row);

    assertEquals(-1, table.selectSkill(0, 1, 0));
    assertEquals(0, table.selectSkill(0, 1, 10));
    assertEquals(1, table.selectSkill(0, 1, 15));
  }

  @Test
  void nativeChancePerLevelUsesQuarterScaling() {
    NativeHirelingExperienceTable.Row row = new NativeHirelingExperienceTable.Row(
        0, 1, 100, 40, 0, 10, 0, 20, 0, 20, 0,
        10, 0, 1, 2, 1, 0, 0,
        new int[] { 6 }, new int[] { 1 },
        new int[] { 1 }, new int[] { 0 })
        .withChances(new int[] { 0 }, new int[] { 4 });
    NativeHirelingExperienceTable table = new NativeHirelingExperienceTable().add(row);

    // At level 5 the chance is 0 + (5 - 1) * 4 / 4 = 4. Roll 3 is in
    // the skill range and roll 0 is the no-skill outcome only when the
    // default chance is non-zero; with a zero default chance both are valid.
    assertEquals(0, table.selectSkill(0, 5, 3));
  }

  @Test
  void rebuildsSavedLevelWithoutOwnerLevelCap() {
    NativeHirelingExperienceTable table = new NativeHirelingExperienceTable()
        .add(new NativeHirelingExperienceTable.Row(0, 1, 100));

    assertEquals(1, table.levelForStoredExperience(0, 0));
    assertEquals(5, table.levelForStoredExperience(
        0, NativeHirelingExperienceTable.threshold(5, 100)));
    assertEquals(98, table.levelForStoredExperience(0, 0xFFFFFFFFL));
  }

  @Test
  void nativeLookupFallsBackToFirstRowBelowMinimumHirelingLevel() {
    NativeHirelingExperienceTable.Row first =
        new NativeHirelingExperienceTable.Row(0, 3, 100);
    NativeHirelingExperienceTable table = new NativeHirelingExperienceTable()
        .add(first)
        .add(new NativeHirelingExperienceTable.Row(0, 10, 200));

    assertEquals(first, table.row(0, 1));
    assertEquals(first, table.row(0, 9));
  }
}
