package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Mercenary;

/** Applies the values calculated by MONSTERAI_UpdateMercStatsAndSkills. */
public final class NativeHirelingStatsUpdater {
  private NativeHirelingStatsUpdater() {}

  public static boolean apply(Attributes attrs, NativeHirelingExperienceTable.Stats stats) {
    if (attrs == null || stats == null) return false;
    StatListRef base = attrs.base();
    StatListRef aggregate = attrs.aggregate();
    base.put(Stat.level, stats.level);
    aggregate.put(Stat.level, stats.level);
    base.put(Stat.nextexp, (int) Math.min(Integer.MAX_VALUE, stats.nextExperience));
    aggregate.put(Stat.nextexp, (int) Math.min(Integer.MAX_VALUE, stats.nextExperience));
    base.put(Stat.strength, stats.strength);
    aggregate.put(Stat.strength, stats.strength);
    base.put(Stat.dexterity, stats.dexterity);
    aggregate.put(Stat.dexterity, stats.dexterity);
    base.put(Stat.maxhp, (float) stats.hitpoints);
    aggregate.put(Stat.maxhp, (float) stats.hitpoints);
    base.put(Stat.hitpoints, (float) stats.hitpoints);
    aggregate.put(Stat.hitpoints, (float) stats.hitpoints);
    base.put(Stat.armorclass, stats.defense);
    aggregate.put(Stat.armorclass, stats.defense);
    base.put(Stat.secondary_mindamage, stats.damageMin);
    aggregate.put(Stat.secondary_mindamage, stats.damageMin);
    base.put(Stat.secondary_maxdamage, stats.damageMax);
    aggregate.put(Stat.secondary_maxdamage, stats.damageMax);
    base.put(Stat.tohit, stats.attackRate);
    aggregate.put(Stat.tohit, stats.attackRate);
    base.put(Stat.fireresist, stats.resist);
    aggregate.put(Stat.fireresist, stats.resist);
    base.put(Stat.lightresist, stats.resist);
    aggregate.put(Stat.lightresist, stats.resist);
    base.put(Stat.coldresist, stats.resist);
    aggregate.put(Stat.coldresist, stats.resist);
    base.put(Stat.poisonresist, stats.resist);
    aggregate.put(Stat.poisonresist, stats.resist);
    base.putEncoded(Stat.hpregen, stats.hpRegenEncoded);
    aggregate.putEncoded(Stat.hpregen, stats.hpRegenEncoded);
    return true;
  }

  public static void applySkills(Mercenary mercenary,
      NativeHirelingExperienceTable.Stats stats) {
    if (mercenary == null || stats == null) return;
    for (int i = 0; i < stats.skills.length; i++) {
      int skillId = stats.skills[i];
      if (skillId < 0 || stats.skillModes[i] >= 16) return;
      Skills.Entry skill = Riiablo.files == null || Riiablo.files.skills == null
          ? null : Riiablo.files.skills.get(skillId);
      if (skill == null) return;
      int level = stats.level >= skill.reqlevel ? stats.skillLevels[i] : 0;
      if (level > 0) mercenary.setSkill(i, skillId, level);
    }
  }
}
