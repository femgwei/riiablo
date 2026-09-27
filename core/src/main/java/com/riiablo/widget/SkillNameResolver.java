package com.riiablo.widget;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.codec.excel.Skills;

/** Resolves user-facing skill names through skills -> skilldesc -> TBL. */
public final class SkillNameResolver {
  private SkillNameResolver() {}

  /** Returns a skill entry by its numeric runtime/save id. */
  public static Skills.Entry entry(int skillId) {
    return Riiablo.files == null || Riiablo.files.skills == null || skillId < 0
        ? null : Riiablo.files.skills.get(skillId);
  }

  /** Returns a skill entry by its internal Skills.txt identifier. */
  public static Skills.Entry entry(String internalSkillId) {
    return Riiablo.files == null || Riiablo.files.skills == null
        || internalSkillId == null || internalSkillId.isEmpty()
        ? null : Riiablo.files.skills.get(internalSkillId);
  }

  /** Resolves the skilldesc row referenced by a Skills.txt entry. */
  public static SkillDesc.Entry description(Skills.Entry skill) {
    return skill == null || Riiablo.files == null || Riiablo.files.skilldesc == null
        || skill.skilldesc == null || skill.skilldesc.isEmpty()
        ? null : Riiablo.files.skilldesc.get(skill.skilldesc);
  }

  /**
   * Returns the localized display name. The internal {@code skill} field is
   * deliberately never used as a UI fallback.
   */
  public static String name(Skills.Entry skill) {
    SkillDesc.Entry desc = description(skill);
    if (desc == null || desc.str_name == null || desc.str_name.isEmpty()
        || Riiablo.string == null) return "";
    String value = Riiablo.string.lookup(desc.str_name);
    return value == null || value.isEmpty() || value.startsWith("ERROR:") ? "" : value;
  }

  /**
   * Resolves the alternate skill name used by the native Character Screen.
   * SkillDesc.txt documents this field as the selected-skill name for that
   * screen; it is distinct from the regular skill-tree {@code str name}.
   */
  public static String characterScreenName(Skills.Entry skill) {
    SkillDesc.Entry desc = description(skill);
    if (desc == null || desc.str_alt == null || desc.str_alt.isEmpty()
        || Riiablo.string == null) return name(skill);
    String value = Riiablo.string.lookup(desc.str_alt);
    return value == null || value.isEmpty() || value.startsWith("ERROR:")
        ? name(skill) : value;
  }

  public static String name(int skillId) {
    return name(entry(skillId));
  }

  public static String nameByInternalId(String internalSkillId) {
    return name(entry(internalSkillId));
  }
}
