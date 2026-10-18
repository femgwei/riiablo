package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.Skills;

/** Independent D2MOO D2Common_11033 oracle, using the selected installation's row. */
final class NativeMasteryOracle {
  private NativeMasteryOracle() {}

  static int throwingCriticalChance(Skills.Entry skill, int level) {
    return criticalChance(skill, level);
  }

  static int criticalChance(Skills.Entry skill, int level) {
    assertEquals("dm56", skill.passivecalc[2], "native mastery critical formula");
    int minimum = skill.Param[4];
    int maximum = skill.Param[5];
    return Math.min(maximum,
        minimum + (maximum - minimum) * 110 * level / (level + 6) / 100);
  }
}
