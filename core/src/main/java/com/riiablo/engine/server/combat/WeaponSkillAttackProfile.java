package com.riiablo.engine.server.combat;

import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import java.util.function.ToIntFunction;

/** Explicitly audited weapon skills, not a replacement for every special profile. */
public final class WeaponSkillAttackProfile {
  public final int enhancedDamage, toHitPercent, sourceDamage, conversionPercent, conversionType;
  public final boolean sacrifice;

  private WeaponSkillAttackProfile(Skills.Entry skill, int level, ToIntFunction<String> hardPoints) {
    sacrifice = skill.Id == SkillId.SACRIFICE;
    String damageFormula = skill.Id == SkillId.ZEAL || skill.Id == SkillId.FEND ? skill.calc2 : skill.calc1;
    int formulaDamage = SkillFormula.evaluate(damageFormula, skill, level, hardPoints);
    // Zeal 1.10f stores its +6/level curve in Param3/Param4 while calc2 is
    // blank in the raw row; preserve the row's level-one zero bonus.
    int paramBase = skill.Param != null && skill.Param.length > 2 ? skill.Param[2] : 0;
    int paramPerLevel = skill.Param != null && skill.Param.length > 3 ? skill.Param[3] : 0;
    enhancedDamage = formulaDamage != 0 || skill.Id != SkillId.ZEAL
        ? formulaDamage : Math.max(0, paramBase + (level - 1) * paramPerLevel);
    toHitPercent = skill.ToHit + (level - 1) * skill.LevToHit;
    // Sacrifice passes its raw SrcDam to ApplyDamageBonuses; other callbacks default zero to 128.
    sourceDamage = skill.SrcDam == 0 && !sacrifice ? 128 : skill.SrcDam;
    conversionPercent = Math.max(0, Math.min(100,
        SkillFormula.evaluate(skill.calc4, skill, level, hardPoints)));
    conversionType = "mag".equalsIgnoreCase(skill.EType) ? CombatSystem.DAMAGE_MAGIC
        : "fire".equalsIgnoreCase(skill.EType) ? CombatSystem.DAMAGE_FIRE
        : "ltng".equalsIgnoreCase(skill.EType) ? CombatSystem.DAMAGE_LIGHTNING
        : "cold".equalsIgnoreCase(skill.EType) ? CombatSystem.DAMAGE_COLD
        : "pois".equalsIgnoreCase(skill.EType) ? CombatSystem.DAMAGE_POISON : CombatSystem.DAMAGE_PHYSICAL;
  }

  public static WeaponSkillAttackProfile create(Skills.Entry skill, int level,
      ToIntFunction<String> hardPoints) {
    if (skill == null) throw new IllegalArgumentException("missing weapon skill");
    switch (skill.Id) {
      case SkillId.FRENZY: case SkillId.BERSERK: case SkillId.ZEAL:
      case SkillId.SACRIFICE: case SkillId.JAB: case SkillId.FEND:
        return new WeaponSkillAttackProfile(skill, Math.max(1, level), hardPoints);
      default: throw new IllegalArgumentException("unaudited weapon skill " + skill.Id);
    }
  }
}
