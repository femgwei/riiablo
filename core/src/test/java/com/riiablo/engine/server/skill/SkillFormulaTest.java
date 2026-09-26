package com.riiablo.engine.server.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkillFormulaTest {
  @Test
  void nativeDurationFramesAreDisplayedAsSeconds() {
    // Dopplezon Param1/Param2 are 250/125 frames: level 1 is 10 seconds,
    // level 2 is 15 seconds.  The server must still keep the frame values.
    assertEquals(10, SkillFormula.durationSeconds(250));
    assertEquals(15, SkillFormula.durationSeconds(375));
  }

  @Test
  void durationConversionNeverShowsSubsecondPositiveDurationAsZero() {
    assertEquals(0, SkillFormula.durationSeconds(0));
    assertEquals(1, SkillFormula.durationSeconds(1));
  }
}
