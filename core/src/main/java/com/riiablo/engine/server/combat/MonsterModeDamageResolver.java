package com.riiablo.engine.server.combat;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.MonsterStatsCalculator;

/** Resolves the native MonStats attack and elemental profile for one monster mode. */
public final class MonsterModeDamageResolver {
  private MonsterModeDamageResolver() {}

  public static final class Profile {
    public int minDamage;
    public int maxDamage;
    public int attackRating;
    public final int[] elementalMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    public final int[] elementalMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    public int coldLength;
    public int poisonLength;
    public int matchedElementProfiles;

    public boolean hasDamage() {
      if (maxDamage > 0) return true;
      for (int i = CombatSystem.DAMAGE_FIRE; i < elementalMax.length; i++) {
        if (elementalMax[i] > 0) return true;
      }
      return false;
    }
  }

  /**
   * Mirrors D2MOO {@code sub_6FC627B0}: A2 uses the A2 profile, BL/SC/S1 use
   * S1, and all other attack modes use A1. Elemental rows are attached only
   * when their El#Mode matches the active animation mode and their chance
   * roll succeeds.
   */
  public static Profile resolve(MonStats.Entry monster, int level, int difficulty, int mode) {
    Profile profile = new Profile();
    if (monster == null) return profile;

    level = Math.max(1, level);
    difficulty = MathUtils.clamp(difficulty, 0, 2);
    short physicalFlag = physicalFlag(mode);
    MonsterStatsCalculator.MonsterStatsInit physical =
        new MonsterStatsCalculator.MonsterStatsInit();
    if (MonsterStatsCalculator.calculateMonsterStatsByLevel(
        monster.hcIdx, 1, difficulty, level, physicalFlag, physical)) {
      profile.attackRating = Math.max(0, physical.TH);
      if (physicalFlag == 0x10) {
        profile.minDamage = Math.max(0, physical.A2MinD);
        profile.maxDamage = Math.max(profile.minDamage, physical.A2MaxD);
      } else if (physicalFlag == 0x20) {
        profile.minDamage = Math.max(0, physical.S1MinD);
        profile.maxDamage = Math.max(profile.minDamage, physical.S1MaxD);
      } else {
        profile.minDamage = Math.max(0, physical.A1MinD);
        profile.maxDamage = Math.max(profile.minDamage, physical.A1MaxD);
      }
    }

    String[] modes = {monster.El1Mode, monster.El2Mode, monster.El3Mode};
    String[] types = {monster.El1Type, monster.El2Type, monster.El3Type};
    int[][] chances = {monster.El1Pct, monster.El2Pct, monster.El3Pct};
    for (int i = 0; i < modes.length; i++) {
      if (!modeMatches(modes[i], mode)) continue;
      int chance = arrayValue(chances[i], difficulty);
      if (chance <= 0 || (chance < 100 && MathUtils.random(99) >= chance)) continue;

      MonsterStatsCalculator.MonsterStatsInit element =
          new MonsterStatsCalculator.MonsterStatsInit();
      if (!MonsterStatsCalculator.calculateMonsterStatsByLevel(
          monster.hcIdx, 1, difficulty, level, (short) (0x40 << i), element)) {
        continue;
      }
      int type = damageType(types[i]);
      if (type <= CombatSystem.DAMAGE_PHYSICAL) continue;
      int min = Math.max(0, element.ElMinD);
      int max = Math.max(min, element.ElMaxD);
      int length = Math.max(0, element.ElDur);
      if (type == CombatSystem.DAMAGE_POISON) {
        // MonsterMode.cpp stores monster poison as per-frame damage and uses
        // twice the MonStats duration when installing the attack stat list.
        min *= 10;
        max *= 10;
        length *= 2;
        profile.poisonLength = Math.max(profile.poisonLength, length);
      } else if (type == CombatSystem.DAMAGE_COLD) {
        profile.coldLength = Math.max(profile.coldLength, length);
      }
      profile.elementalMin[type] += min;
      profile.elementalMax[type] += max;
      profile.matchedElementProfiles++;
    }
    return profile;
  }

  private static short physicalFlag(int mode) {
    if (mode == Engine.Monster.MODE_A2) return 0x10;
    if (mode == Engine.Monster.MODE_BL || mode == Engine.Monster.MODE_SC
        || mode == Engine.Monster.MODE_S1) return 0x20;
    return 0x08;
  }

  private static boolean modeMatches(String modeName, int mode) {
    return modeName != null && !modeName.isEmpty()
        && Riiablo.files.MonMode.index(modeName) == mode;
  }

  private static int damageType(String type) {
    if ("fire".equalsIgnoreCase(type)) return CombatSystem.DAMAGE_FIRE;
    if ("ltng".equalsIgnoreCase(type) || "lightning".equalsIgnoreCase(type)) {
      return CombatSystem.DAMAGE_LIGHTNING;
    }
    if ("cold".equalsIgnoreCase(type) || "freeze".equalsIgnoreCase(type)) {
      return CombatSystem.DAMAGE_COLD;
    }
    if ("pois".equalsIgnoreCase(type) || "poison".equalsIgnoreCase(type)) {
      return CombatSystem.DAMAGE_POISON;
    }
    if ("mag".equalsIgnoreCase(type) || "magic".equalsIgnoreCase(type)) {
      return CombatSystem.DAMAGE_MAGIC;
    }
    if ("rand".equalsIgnoreCase(type)) return MathUtils.random(1, 5);
    return CombatSystem.DAMAGE_PHYSICAL;
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }
}
