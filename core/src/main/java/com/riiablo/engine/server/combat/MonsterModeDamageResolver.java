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
    return resolve(monster, level, difficulty, mode, 1, true);
  }

  /** Resolves a mode profile with the game-wide LoD multiplayer combat rules. */
  public static Profile resolve(MonStats.Entry monster, int level, int difficulty, int mode,
      int playerCount, boolean expansion) {
    return resolve(Riiablo.files, bound -> MathUtils.random(bound - 1), monster,
        level, difficulty, mode, playerCount, expansion);
  }

  public static Profile resolve(com.riiablo.Files files, java.util.function.IntUnaryOperator random,
      MonStats.Entry monster, int level, int difficulty, int mode, int playerCount, boolean expansion) {
    return resolveWithElements(files, monster, level, difficulty, mode, playerCount, expansion,
        resolveElements(files, random, monster, level, difficulty, mode, playerCount, expansion));
  }

  /** Reads physical columns but consumes the already selected action stat-list. */
  public static Profile resolveWithElements(com.riiablo.Files files, MonStats.Entry monster,
      int level, int difficulty, int mode, int playerCount, boolean expansion,
      NativeMonsterModeElements elements) {
    Profile profile = new Profile();
    if (monster == null) return profile;

    level = Math.max(1, level);
    difficulty = MathUtils.clamp(difficulty, 0, 2);
    int combatMultiplier = MonsterStatsCalculator.nativeCombatMultiplier(
        monster, difficulty, playerCount, expansion);
    short physicalFlag = physicalFlag(mode);
    MonsterStatsCalculator.MonsterStatsInit physical =
        new MonsterStatsCalculator.MonsterStatsInit();
    if (MonsterStatsCalculator.calculateMonsterStatsByLevel(files,
        monster.hcIdx, expansion ? 1 : 0, difficulty, level, physicalFlag, physical)) {
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
      profile.attackRating = MonsterStatsCalculator.scaleMonsterCombatValue(
          profile.attackRating, combatMultiplier);
      profile.minDamage = MonsterStatsCalculator.scaleMonsterCombatValue(
          profile.minDamage, combatMultiplier);
      profile.maxDamage = Math.max(profile.minDamage,
          MonsterStatsCalculator.scaleMonsterCombatValue(profile.maxDamage, combatMultiplier));
    }

    if (elements == null) elements = new NativeMonsterModeElements();
    System.arraycopy(elements.minimum, 0, profile.elementalMin, 0, elements.minimum.length);
    System.arraycopy(elements.maximum, 0, profile.elementalMax, 0, elements.maximum.length);
    profile.coldLength = elements.coldLength;
    profile.poisonLength = elements.poisonLength;
    profile.matchedElementProfiles = elements.matchedRows;
    return profile;
  }

  /** Shared table adapter only; callers still own when and how often to refresh. */
  public static NativeMonsterModeElements resolveElements(com.riiablo.Files files,
      java.util.function.IntUnaryOperator random, MonStats.Entry monster, int level,
      int difficulty, int mode, int playerCount, boolean expansion) {
    NativeMonsterModeElements result = new NativeMonsterModeElements();
    if (monster == null || files == null) return result;
    difficulty = MathUtils.clamp(difficulty, 0, 2);
    int multiplier = MonsterStatsCalculator.nativeCombatMultiplier(
        monster, difficulty, playerCount, expansion);
    String[] modes = {monster.El1Mode, monster.El2Mode, monster.El3Mode};
    String[] types = {monster.El1Type, monster.El2Type, monster.El3Type};
    int[][] chances = {monster.El1Pct, monster.El2Pct, monster.El3Pct};
    for (int i = 0; i < modes.length; i++) {
      int rowMode = modes[i] == null ? -1 : files.MonMode.index(modes[i]);
      if (!NativeMonsterModeElements.selects(rowMode, mode,
          arrayValue(chances[i], difficulty), random)) continue;

      MonsterStatsCalculator.MonsterStatsInit element =
          new MonsterStatsCalculator.MonsterStatsInit();
      if (!MonsterStatsCalculator.calculateMonsterStatsByLevel(files,
          monster.hcIdx, expansion ? 1 : 0, difficulty, Math.max(1, level),
          NativeMonsterModeElements.tableFlags(i), element)) {
        continue;
      }
      // 1.10f D2Common's noRatio branch writes S1MinD/S1MaxD, not ElMinD/ElMaxD.
      // This caller zero-initializes the result each time; preserve the original quirk
      // without changing the generic calculator's existing noRatio contract.
      result.set(nativeType(types[i]), monster.noRatio ? 0 : element.ElMinD,
          monster.noRatio ? 0 : element.ElMaxD, element.ElDur, multiplier, random);
    }
    return result;
  }

  private static short physicalFlag(int mode) {
    if (mode == Engine.Monster.MODE_A2) return 0x10;
    if (mode == Engine.Monster.MODE_BL || mode == Engine.Monster.MODE_SC
        || mode == Engine.Monster.MODE_S1) return 0x20;
    return 0x08;
  }

  private static int nativeType(String type) {
    if ("fire".equalsIgnoreCase(type)) return 1;
    if ("ltng".equalsIgnoreCase(type) || "lightning".equalsIgnoreCase(type)) {
      return 2;
    }
    if ("cold".equalsIgnoreCase(type)) return 4;
    if ("pois".equalsIgnoreCase(type) || "poison".equalsIgnoreCase(type)) {
      return 5;
    }
    if ("mag".equalsIgnoreCase(type) || "magic".equalsIgnoreCase(type)) {
      return 3;
    }
    if ("rand".equalsIgnoreCase(type)) return 10;
    return 0;
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }
}
