package com.riiablo.engine.server;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.combat.MonsterStatsSnapshot;
import com.riiablo.engine.server.combat.NativeCombatContext;
import com.riiablo.engine.server.combat.NativeMonsterStatsCalculator;
import com.riiablo.engine.server.monster.MonsterAffix;

/** Host table lookup/attribute adapter; pure stat rules live in NativeMonsterStatsCalculator. */
public class MonsterStatsCalculator {
  public static class MonsterStatsInit extends NativeMonsterStatsCalculator.TableStats {}

  /** Mirrors DATATBLS_CalculateMonsterStatsByLevel. */
  public static boolean calculateMonsterStatsByLevel(
      int monsterId,
      int gameType,
      int difficulty,
      int level,
      short flags,
      MonsterStatsInit result) {
    return calculateMonsterStatsByLevel(Riiablo.files, monsterId, gameType, difficulty, level, flags, result);
  }

  public static boolean calculateMonsterStatsByLevel(com.riiablo.Files files,
      int monsterId, int gameType, int difficulty, int level, short flags, MonsterStatsInit result) {
    if (result == null || files == null
        || files.monstats == null || files.MonLvl == null) {
      return false;
    }

    MonStats.Entry monstats = files.monstats.get(monsterId);
    if (monstats == null || level < 0 || files.MonLvl.size() <= 0) return false;

    level = Math.max(0, Math.min(level, files.MonLvl.size() - 1));
    MonLvl.Entry monLvl = files.MonLvl.get(level);
    if (monLvl == null) return false;

    return NativeMonsterStatsCalculator.calculateTableStats(
        NativeCombatContext.forGameplay(difficulty, null, gameType != 0),
        monstats, monLvl, flags, result);
  }

  /** Table lookup adapter for the pure, immutable pre-affix spawn snapshot. */
  public static MonsterStatsSnapshot spawnSnapshot(com.riiablo.Files files, MonStats.Entry monster,
      int gameType, int difficulty, int baseLevel, int rank, int connectedPlayers) {
    if (files == null || files.MonLvl == null || files.MonLvl.size() <= 0 || baseLevel < 0) return null;
    MonLvl.Entry level = files.MonLvl.get(Math.min(baseLevel, files.MonLvl.size() - 1));
    return NativeMonsterStatsCalculator.spawnSnapshot(
        NativeCombatContext.forGameplay(difficulty, null, gameType != 0), monster, level,
        baseLevel, rank, connectedPlayers);
  }

  /** Mirrors the level selection in MONSTER_InitializeStatsAndSkills. */
  static int resolveMonsterLevel(
      MonStats.Entry monster, Levels.Entry level, int difficulty, boolean expansion) {
    return NativeMonsterStatsCalculator.resolveMonsterLevel(monster, level,
        NativeCombatContext.forGameplay(difficulty, null, expansion));
  }

  /** Evil monsters capture the connected-player count; allied units use one. */
  static int nativePlayerCount(MonStats.Entry monster, int connectedPlayers) {
    return NativeMonsterStatsCalculator.playerCount(monster, connectedPlayers);
  }

  /**
   * Returns the LoD Nightmare/Hell multiplayer combat multiplier used by
   * D2Game's monster mode stat refresh (sub_6FC627B0).  The value is a
   * fixed-point numerator over 128, not a percentage.
   *
   * <p>The classic game has a separate non-expansion adjustment.  This fork
   * runs the expansion rules, so callers must pass {@code expansion=true}
   * when they want the LoD multiplayer bonus.
   */
  public static int nativeCombatMultiplier(
      MonStats.Entry monster, int difficulty, int playerCount, boolean expansion) {
    return NativeMonsterStatsCalculator.combatMultiplier(monster, difficulty, playerCount, expansion);
  }

  /** Applies the native fixed-point combat multiplier with integer truncation. */
  public static int scaleMonsterCombatValue(int value, int multiplier) {
    return NativeMonsterStatsCalculator.scaleCombatValue(value, multiplier);
  }

  /** Applies the LoD multiplayer bonus to one resolved physical attack profile. */
  static void applyNativeCombatMultiplier(MonsterStatsInit stats, int multiplier) {
    if (stats == null || multiplier <= 0) return;
    stats.TH = scaleMonsterCombatValue(stats.TH, multiplier);
    stats.A1MinD = scaleMonsterCombatValue(stats.A1MinD, multiplier);
    stats.A1MaxD = scaleMonsterCombatValue(stats.A1MaxD, multiplier);
    stats.A2MinD = scaleMonsterCombatValue(stats.A2MinD, multiplier);
    stats.A2MaxD = scaleMonsterCombatValue(stats.A2MaxD, multiplier);
    stats.S1MinD = scaleMonsterCombatValue(stats.S1MinD, multiplier);
    stats.S1MaxD = scaleMonsterCombatValue(stats.S1MaxD, multiplier);
    stats.ElMinD = scaleMonsterCombatValue(stats.ElMinD, multiplier);
    stats.ElMaxD = scaleMonsterCombatValue(stats.ElMaxD, multiplier);
    stats.ElDur = scaleMonsterCombatValue(stats.ElDur, multiplier);
  }

  static int nativeHpBonus(int playerCount) {
    return NativeMonsterStatsCalculator.hpBonus(playerCount);
  }

  static int nativeExperienceBonus(int playerCount) {
    return NativeMonsterStatsCalculator.experienceBonus(playerCount);
  }

  static int nativeRankLevelBonus(int rank) {
    return NativeMonsterStatsCalculator.rankLevelBonus(rank);
  }

  static int nativeRankExperience(int experience, int rank) {
    return NativeMonsterStatsCalculator.rankExperience(experience, rank);
  }

  /** Compatibility facade for the stock host's rank HP rule (integer HP, fixed multipliers). */
  static int nativeRankHitpoints(int hitpoints, int rank, int difficulty) {
    return NativeMonsterStatsCalculator.rankHitpoints(hitpoints, rank, difficulty);
  }

  /** Applies the spawn-time portion of D2Game's fixed SuperUnique UMods. */
  static void applyNativeAffixStats(
      StatListRef base, MonStats.Entry monster, int difficulty, long affixes) {
    applyNativeAffixStats(Riiablo.files, base, monster, difficulty, affixes);
  }

  static void applyNativeAffixStats(com.riiablo.Files files,
      StatListRef base, MonStats.Entry monster, int difficulty, long affixes) {
    if (base == null || affixes == MonsterAffix.NONE) return;
    difficulty = NativeDataTables.difficulty(difficulty);

    if (MonsterAffix.hasAffix(affixes, MonsterAffix.EXTRA_STRONG)) {
      int difficultyBonus = new int[] {90, 75, 66}[difficulty];
      if (files != null && files.DifficultyLevels != null
          && files.DifficultyLevels.get(difficulty) != null) {
        difficultyBonus = files.DifficultyLevels.get(difficulty).UniqueDamageBonus;
      }
      base.put(Stat.damagepercent,
          base.getValue(Stat.damagepercent, 0) + 150 * difficultyBonus / 100);
      base.put(Stat.item_tohit_percent,
          base.getValue(Stat.item_tohit_percent, 0) + 100 * difficultyBonus / 100);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.EXTRA_FAST)
        && monster != null && monster.Velocity > 0) {
      int velocity = MathUtils.clamp(2048 / monster.Velocity - 128, 10, 100);
      base.put(Stat.velocitypercent,
          base.getValue(Stat.velocitypercent, 0) + velocity);
    }

    if (MonsterAffix.hasAffix(affixes, MonsterAffix.STONE_SKIN)) {
      base.put(Stat.armorclass, 2 * base.getValue(Stat.armorclass, 0));
      base.put(Stat.damageresist, base.getValue(Stat.damageresist, 0) + 50);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.MAGIC_RESISTANT)) {
      addElementalResistance(base, 40, false);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.FIRE_ENCHANTED)) {
      base.put(Stat.fireresist, base.getValue(Stat.fireresist, 0) + 75);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.COLD_ENCHANTED)) {
      base.put(Stat.coldresist, base.getValue(Stat.coldresist, 0) + 75);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.LIGHTNING_ENCHANTED)) {
      base.put(Stat.lightresist, base.getValue(Stat.lightresist, 0) + 75);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.POISON_ENCHANTED)) {
      base.put(Stat.poisonresist, base.getValue(Stat.poisonresist, 0) + 75);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.MANA_BURN)) {
      base.put(Stat.magicresist, base.getValue(Stat.magicresist, 0) + 20);
    }
    if (MonsterAffix.hasAffix(affixes, MonsterAffix.SPECTRAL_HIT)) {
      addElementalResistance(base, 20, true);
    }
  }

  private static void addElementalResistance(StatListRef base, int amount, boolean below75Only) {
    short[] stats = {Stat.coldresist, Stat.fireresist, Stat.lightresist};
    int immunities = 0;
    short[] immunityStats = {Stat.fireresist, Stat.lightresist, Stat.coldresist,
        Stat.poisonresist, Stat.damageresist, Stat.magicresist};
    for (short stat : immunityStats) if (base.getValue(stat, 0) >= 100) immunities++;
    if (immunities >= 2) return;
    for (short stat : stats) {
      int value = base.getValue(stat, 0);
      if (below75Only && value >= 75 || value >= 100) continue;
      base.put(stat, value + amount);
      if (value + amount >= 100 && ++immunities >= 2) return;
    }
  }

  /** Mirrors DATATBLS_ApplyRatio, including its overflow-avoidance ordering. */
  static int applyRatio(int value, int multiplier, int divisor) {
    return NativeMonsterStatsCalculator.applyRatio(value, multiplier, divisor);
  }
}
