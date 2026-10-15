package com.riiablo.engine.server;

import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;

/** D2MOO SrvDo100's separate elemental-damage and freeze-length packet. */
public final class NativeDiabCold {
  private NativeDiabCold() {}

  public static boolean supports(Skills.Entry skill) {
    return skill != null && skill.Id == 194 && skill.srvdofunc == 100;
  }

  /** SKILLS_GetElementalLength uses three tiers and a percentage synergy. */
  public static int freezeLength(Skills.Entry skill, int level) {
    if (skill == null || level <= 0) return 0;
    long length = skill.ELen;
    if (skill.ELevLen != null) {
      int[] levels = {Math.min(7, level - 1), Math.max(0, Math.min(8, level - 8)),
          Math.max(0, level - 16)};
      for (int i = 0; i < Math.min(3, skill.ELevLen.length); i++)
        length += (long) levels[i] * skill.ELevLen[i];
    }
    int synergy = SkillFormula.evaluate(skill.ELenSymPerCalc, skill, level);
    length += length * synergy / 100L;
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, length));
  }
}
