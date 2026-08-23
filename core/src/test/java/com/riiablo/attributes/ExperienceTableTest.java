package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.CharacterClass;

class ExperienceTableTest {
  @Test
  void levelOneProgressStartsAtZero() {
    ExperienceTable table = ExperienceTable.getInstance();

    assertEquals(0L, table.getExperienceForCurrentLevel(1, CharacterClass.AMAZON.id));
    assertEquals(500L, table.getExperienceForNextLevel(1, CharacterClass.AMAZON.id));
  }

  @Test
  void levelTwoProgressUsesLevelOneThreshold() {
    ExperienceTable table = ExperienceTable.getInstance();

    assertEquals(500L, table.getExperienceForCurrentLevel(2, CharacterClass.AMAZON.id));
    assertEquals(1500L, table.getExperienceForNextLevel(2, CharacterClass.AMAZON.id));
  }

  @Test
  void maxLevelHasNoFiniteNextThreshold() {
    assertEquals(Long.MAX_VALUE,
        ExperienceTable.getInstance().getExperienceForNextLevel(ExperienceTable.MAX_LEVEL, 0));
  }

}
