package com.riiablo.engine.server.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkillFormulaTest {
  @Test void maggotHatchConditionalPreservesNativeLevelCurve() {
    String formula = "\"(lvl < 5) ? lvl : min(12,5+(lvl-5)/3)\"";
    int[] levels = {1, 2, 4, 5, 7, 8, 25, 26, 50};
    int[] expected = {1, 2, 4, 5, 5, 6, 11, 12, 12};
    for (int i = 0; i < levels.length; i++)
      assertEquals(expected[i], SkillFormula.evaluate(formula, null, levels[i]));
    assertEquals(7, SkillFormula.evaluate("1+2<4 ? 7 : 9", null, 1));
    assertEquals(9, SkillFormula.evaluate("2>=3 ? 7 : (1 ? 9 : 11)", null, 1));
  }

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
