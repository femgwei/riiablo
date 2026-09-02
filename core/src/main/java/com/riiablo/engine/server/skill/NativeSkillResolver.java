package com.riiablo.engine.server.skill;

import com.riiablo.CharacterClass;
import com.riiablo.codec.excel.Skills;
import com.riiablo.save.CharData;

/**
 * Data-driven view of the native Skills.txt rules shared by all seven
 * character classes.  The native game does not use Java skill-id ranges to
 * decide whether a cast is legal; it resolves the row, class, prerequisites
 * and calculated mana from the table first.  Keeping that logic here prevents
 * the seven specialist implementations from drifting apart.
 */
public final class NativeSkillResolver {
  public static final int OK = 0;
  public static final int LEVEL_TOO_LOW = SkillExecutor.RESULT_LEVEL_TOO_LOW;
  public static final int MISSING_PREREQUISITE = SkillExecutor.RESULT_MISSING_PREREQ;
  public static final int WRONG_CLASS = 8;
  public static final int NOT_LEARNED = SkillExecutor.RESULT_UNAVAILABLE;

  private NativeSkillResolver() {}

  /** Returns the class id encoded by a Skills.txt charclass cell, or -1. */
  public static int classId(Skills.Entry skill) {
    if (skill == null || skill.charclass == null || skill.charclass.trim().isEmpty()) return -1;
    try {
      return Skills.getClassId(skill.charclass.trim().toLowerCase(java.util.Locale.ROOT));
    } catch (RuntimeException ignored) {
      return -1;
    }
  }

  /**
   * Checks class ownership using the table's charclass value.  Empty
   * charclass rows are native/system skills (Attack, Throw, scroll skills,
   * etc.) and are intentionally available to every class.
   */
  public static boolean belongsToClass(Skills.Entry skill, int characterClassId) {
    int owner = classId(skill);
    return owner < 0 || owner == characterClassId;
  }

  /** Effective native mana cost in display units (fixed-point shift applied). */
  public static float manaCost(Skills.Entry skill, int level) {
    if (skill == null) return 0f;
    int clampedLevel = Math.max(1, level);
    int shift = Math.max(0, Math.min(30, skill.manashift));
    double scale = (1L << shift) / 256.0;
    double calculated = (skill.mana + (clampedLevel - 1L) * skill.lvlmana) * scale;
    // D2 clamps against MinMana in the same fixed-point domain.  The table
    // stores MinMana in display units, so comparing after conversion keeps
    // fractional costs and low-level skills consistent with the native path.
    double minimum = Math.max(0, skill.minmana);
    return (float) Math.max(minimum, calculated);
  }

  /** Evaluates one of Skills.txt Calc1..Calc4 with native bounded semantics. */
  public static int calc(Skills.Entry skill, int level, int calcIndex) {
    if (skill == null || calcIndex < 1 || calcIndex > 4) return 0;
    String expression;
    switch (calcIndex) {
      case 1: expression = skill.calc1; break;
      case 2: expression = skill.calc2; break;
      case 3: expression = skill.calc3; break;
      default: expression = skill.calc4; break;
    }
    return SkillFormula.evaluate(expression, skill, level);
  }

  /**
   * Validates a player cast against native class, level, learned-skill and
   * prerequisite rules.  Item/default skills are represented by effectiveLevel
   * and therefore remain castable even when their base level is zero.
   */
  public static int validatePlayerCast(CharData data, Skills.Entry skill,
      int effectiveLevel, int casterLevel) {
    if (data == null || skill == null) return NOT_LEARNED;
    int characterClassId = data.charClass & 0xFF;
    // Native oskills (effective level supplied by an item) are usable across
    // classes.  A base/learned level, however, must still belong to the
    // character's class.  This distinction is why the resolver receives both
    // CharData and effectiveLevel rather than only a class id.
    if (!belongsToClass(skill, characterClassId)
        && (data.getBaseSkillLevel(skill.Id) > 0 || effectiveLevel <= 0)) {
      return WRONG_CLASS;
    }
    if (casterLevel < Math.max(1, skill.reqlevel)) return LEVEL_TOO_LOW;

    int level = Math.max(0, effectiveLevel);
    if (!isSystemSkill(skill) && level <= 0) return NOT_LEARNED;
    if (!prerequisitesMet(data, skill)) return MISSING_PREREQUISITE;
    return OK;
  }

  /** Native prerequisite rows are names, not Java constants. */
  public static boolean prerequisitesMet(CharData data, Skills.Entry skill) {
    return data != null && hasPrerequisite(data, skill != null ? skill.reqskill1 : null)
        && hasPrerequisite(data, skill != null ? skill.reqskill2 : null)
        && hasPrerequisite(data, skill != null ? skill.reqskill3 : null);
  }

  private static boolean hasPrerequisite(CharData data, String name) {
    if (name == null || name.trim().isEmpty()) return true;
    Skills.Entry required = com.riiablo.Riiablo.files.skills.get(name.trim());
    return required != null && data.getSkill(required.Id) > 0;
  }

  private static boolean isSystemSkill(Skills.Entry skill) {
    if (skill == null) return false;
    if (skill.Id >= 0 && skill.Id < 6) return true;
    String name = skill.skill == null ? "" : skill.skill.toLowerCase(java.util.Locale.ROOT);
    return "attack".equals(name) || "kick".equals(name) || "throw".equals(name)
        || "unsummon".equals(name) || "left hand throw".equals(name)
        || "left hand swing".equals(name);
  }

  /** Resolves the row's broad native category for specialist dispatch. */
  public static SkillExecutor.SkillData toSkillData(Skills.Entry skill) {
    if (skill == null) return null;
    SkillExecutor.SkillData data = new SkillExecutor.SkillData();
    data.skillId = skill.Id;
    data.skillName = skill.skill;
    data.charClass = classId(skill);
    data.reqLevel = Math.max(1, skill.reqlevel);
    data.baseMana = Math.max(0, Math.round(manaCost(skill, 1)));
    data.manaPerLevel = Math.max(0, Math.round(manaCost(skill, 2) - manaCost(skill, 1)));
    data.cooldown = 0;
    data.isPassive = skill.passive;
    data.isAura = skill.aura;
    data.skillType = skill.passive ? SkillExecutor.SKILL_TYPE_PASSIVE
        : skill.aura ? SkillExecutor.SKILL_TYPE_AURA
        : hasSummonMissile(skill) ? SkillExecutor.SKILL_TYPE_SUMMON
        : SkillExecutor.SKILL_TYPE_SPELL;
    data.requireTarget = !skill.passive && !skill.aura;
    data.requirePosition = !skill.passive;
    return data;
  }

  private static boolean hasSummonMissile(Skills.Entry skill) {
    String name = skill.skill == null ? "" : skill.skill.toLowerCase(java.util.Locale.ROOT);
    return name.contains("summon") || name.contains("golem") || name.contains("skeleton")
        || name.contains("valkyrie") || name.contains("decoy") || name.contains("trap");
  }
}
