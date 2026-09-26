package com.riiablo.engine.server.skill;

import com.riiablo.CharacterClass;
import com.riiablo.codec.excel.Skills;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.skill.SkillCodes;

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

  /**
   * Returns whether a player skill may be started in town.  The native
   * Skills.txt flag remains the default allow-list, but player summon skills
   * are deliberately allowed here: their result is an owned pet/entity, not
   * a direct attack on a town target.  Specialist handlers still validate
   * their own target/placement rules (for example Bone Wall ground checks).
   * Keep a conservative system-skill fallback for reduced/custom tables.
   */
  public static boolean isAllowedInTown(Skills.Entry skill) {
    if (skill == null) return true;
    if (isPlayerSummonSkill(skill)) return true;
    if (com.riiablo.Riiablo.files != null
        && com.riiablo.Riiablo.files.NativeSkills != null) {
      com.riiablo.codec.excel.NativeSkills nativeSkills = com.riiablo.Riiablo.files.NativeSkills;
      if (nativeSkills.source().columnIndex("InTown") >= 0) {
        com.riiablo.codec.excel.NativeSkills.Entry nativeSkill = nativeSkills.get(skill.Id);
        if (nativeSkill != null) return nativeSkill.bool("InTown");
      }
    }
    // If the native row is unavailable, still preserve the most visible
    // vanilla rule: basic weapon actions cannot be used in town.
    return !isSystemSkill(skill);
  }

  /**
   * Returns whether a skill may be submitted from the caster's current room.
   * Keeping the room check next to the native table lookup lets both HUD and
   * input code use exactly the same InTown semantics.  The server still
   * performs its own authoritative validation; this method is only an input
   * side-effect guard (so a rejected click does not start an animation).
   */
  public static boolean isAllowedInTown(Skills.Entry skill, boolean inTown) {
    return !inTown || isAllowedInTown(skill);
  }

  /**
   * Returns whether a Skills.txt row creates a player-owned summon/trap unit.
   * The summon columns are preferred, with SrvDoFunc fallbacks for native rows
   * whose reduced/custom exports omit the Summon column (notably Hydra).
   * Bone Wall and Bone Prison deliberately remain ground/unit skills: their
   * handlers require a non-town placement and must not be opened by this
   * town-cast exception.
   */
  public static boolean isPlayerSummonSkill(Skills.Entry skill) {
    if (skill == null) return false;
    if (skill.srvdofunc == 60 || skill.srvdofunc == 62) return false;
    if (skill.summon != null && !skill.summon.trim().isEmpty()
        && skill.pettype != null && !skill.pettype.trim().isEmpty()) return true;
    switch (skill.srvdofunc) {
      case 15:  // Amazon Decoy
      case 16:  // Amazon Valkyrie
      case 31:  // Necromancer Skeleton/Skeletal Mage
      case 44:  // Assassin Blade Sentinel
      case 45:  // Assassin Sentry traps
      case 49:  // Assassin Shadow Warrior/Master
      case 56:  // Necromancer Clay/Blood/Fire Golem
      case 57:  // Necromancer Iron Golem
      case 58:  // Necromancer Revive
      case 114: // Druid Raven
      case 115: // Druid Vines
      case 119: // Druid Wolves/Spirits/Grizzly
      case 144: // Sorceress Hydra
        return true;
      default:
        return false;
    }
  }

  /** Returns the native TargetableOnly rule used by unmodified mouse input. */
  public static boolean isTargetableOnly(Skills.Entry skill) {
    if (skill == null) return false;
    if (com.riiablo.Riiablo.files != null
        && com.riiablo.Riiablo.files.NativeSkills != null) {
      com.riiablo.codec.excel.NativeSkills nativeSkills =
          com.riiablo.Riiablo.files.NativeSkills;
      if (nativeSkills.source().columnIndex("TargetableOnly") >= 0) {
        com.riiablo.codec.excel.NativeSkills.Entry nativeSkill = nativeSkills.get(skill.Id);
        if (nativeSkill != null) return nativeSkill.bool("TargetableOnly");
      }
    }
    // Preserve basic Attack semantics for reduced/custom tables.
    return skill.Id == SkillCodes.attack;
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

  /** Shared client/server boundary check for fractional fixed-point mana costs. */
  public static boolean hasEnoughMana(float currentMana, float manaCost) {
    return manaCost <= 0f || currentMana + 0.0001f >= manaCost;
  }

  /** Skills whose native animation/projectile path requires a bow or crossbow. */
  public static boolean isAmazonBowSkill(Skills.Entry skill) {
    if (skill == null || skill.skill == null) return false;
    switch (skill.skill.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "magic arrow":
      case "fire arrow":
      case "cold arrow":
      case "multiple shot":
      case "exploding arrow":
      case "ice arrow":
      case "guided arrow":
      case "strafe":
      case "immolation arrow":
      case "freezing arrow":
        return true;
      default:
        return false;
    }
  }

  /** Whether this skill consumes the quiver paired with the equipped ranged weapon. */
  public static boolean requiresRangedAmmo(Skills.Entry skill, Item weapon) {
    if (!ItemData.isRangedWeapon(weapon) || skill == null || skill.noammo) return false;
    return skill.Id == SkillCodes.attack || skill.decquant || isAmazonBowSkill(skill);
  }

  /**
   * Whether this skill uses a javelin, throwing knife, or throwing axe quantity.
   * Keep this aligned with Actioneer's native throw animation/keyframe paths.
   */
  public static boolean isThrowableSkill(Skills.Entry skill) {
    return skill != null && (skill.Id == SkillCodes.throw_
        || skill.Id == SkillCodes.left_hand_throw
        || skill.cltdofunc == 3 || skill.cltdofunc == 5
        || skill.srvdofunc == 3 || skill.srvdofunc == 5);
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
