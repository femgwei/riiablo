package com.riiablo.attributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgressionServiceTest {
  private final ProgressionService service = new ProgressionService(ExperienceTable.getInstance());

  @Test
  void awardsArePureAndLevelCrossingsAreDeterministic() {
    ProgressionService.Result result = service.award(500, 1, 0, 1000);
    assertEquals(500, result.oldExperience);
    assertEquals(1500, result.newExperience);
    assertEquals(3, result.newLevel);
    assertEquals(1000, result.appliedExperience);
    assertTrue(result.leveledUp);
  }

  @Test
  void negativeAwardsAndLevelsAreRejectedWithoutMutation() {
    ProgressionService.Result result = service.award(0, 0, 0, -50);
    assertEquals(0, result.newExperience);
    assertEquals(1, result.newLevel);
    assertEquals(0, result.appliedExperience);
    assertFalse(result.leveledUp);
  }

  @Test
  void maxLevelDoesNotOverflowOrAdvance() {
    ProgressionService.Result result = service.award(Long.MAX_VALUE, 99, 0, Long.MAX_VALUE);
    assertEquals(Long.MAX_VALUE, result.newExperience);
    assertEquals(99, result.newLevel);
    assertFalse(result.leveledUp);
  }
}
