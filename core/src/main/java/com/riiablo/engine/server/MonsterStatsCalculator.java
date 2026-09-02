package com.riiablo.engine.server;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;

/** Native monster level/stat rules used by D2Common and D2Game. */
public class MonsterStatsCalculator {
  public static class MonsterStatsInit {
    public int minHP;
    public int maxHP;
    public int AC;
    public int TH;
    public int Exp;
    public int A1MinD;
    public int A1MaxD;
    public int A2MinD;
    public int A2MaxD;
    public int S1MinD;
    public int S1MaxD;
    public int ElMinD;
    public int ElMaxD;
    public int ElDur;
  }

  /** Mirrors DATATBLS_CalculateMonsterStatsByLevel. */
  public static boolean calculateMonsterStatsByLevel(
      int monsterId,
      int gameType,
      int difficulty,
      int level,
      short flags,
      MonsterStatsInit result) {
    if (result == null || Riiablo.files == null
        || Riiablo.files.monstats == null || Riiablo.files.MonLvl == null) {
      return false;
    }

    MonStats.Entry monstats = Riiablo.files.monstats.get(monsterId);
    if (monstats == null || level < 0 || Riiablo.files.MonLvl.size() <= 0) return false;

    level = Math.max(0, Math.min(level, Riiablo.files.MonLvl.size() - 1));
    MonLvl.Entry monLvl = Riiablo.files.MonLvl.get(level);
    if (monLvl == null) return false;

    difficulty = NativeDataTables.difficulty(difficulty);
    boolean expansion = gameType != 0;
    int[] hp = expansion ? monLvl.LHP : monLvl.HP;
    int[] ac = expansion ? monLvl.LAC : monLvl.AC;
    int[] th = expansion ? monLvl.LTH : monLvl.TH;
    int[] dm = expansion ? monLvl.LDM : monLvl.DM;
    int[] xp = expansion ? monLvl.LXP : monLvl.XP;

    if ((flags & 1) != 0) {
      result.minHP = scaled(monstats.noRatio, monstats.minHP, hp, difficulty);
      result.maxHP = scaled(monstats.noRatio, monstats.maxHP, hp, difficulty);
    }
    if ((flags & 2) != 0) {
      result.AC = scaled(monstats.noRatio, monstats.AC, ac, difficulty);
    }
    if ((flags & 4) != 0) {
      result.Exp = scaled(monstats.noRatio, monstats.Exp, xp, difficulty);
    }
    if ((flags & 8) != 0) {
      result.TH = scaled(monstats.noRatio, monstats.A1TH, th, difficulty);
      result.A1MinD = scaled(monstats.noRatio, monstats.A1MinD, dm, difficulty);
      result.A1MaxD = scaled(monstats.noRatio, monstats.A1MaxD, dm, difficulty);
    }
    if ((flags & 0x10) != 0) {
      result.TH = scaled(monstats.noRatio, monstats.A2TH, th, difficulty);
      result.A2MinD = scaled(monstats.noRatio, monstats.A2MinD, dm, difficulty);
      result.A2MaxD = scaled(monstats.noRatio, monstats.A2MaxD, dm, difficulty);
    }
    if ((flags & 0x20) != 0) {
      result.TH = scaled(monstats.noRatio, monstats.S1TH, th, difficulty);
      result.S1MinD = scaled(monstats.noRatio, monstats.S1MinD, dm, difficulty);
      result.S1MaxD = scaled(monstats.noRatio, monstats.S1MaxD, dm, difficulty);
    }

    int element = (flags & 0x40) != 0 ? 0
        : (flags & 0x80) != 0 ? 1
        : (flags & 0x100) != 0 ? 2 : -1;
    if (element >= 0) {
      int[] min = element == 0 ? monstats.El1MinD
          : element == 1 ? monstats.El2MinD : monstats.El3MinD;
      int[] max = element == 0 ? monstats.El1MaxD
          : element == 1 ? monstats.El2MaxD : monstats.El3MaxD;
      int[] duration = element == 0 ? monstats.El1Dur
          : element == 1 ? monstats.El2Dur : monstats.El3Dur;
      result.ElMinD = scaled(monstats.noRatio, min, dm, difficulty);
      result.ElMaxD = scaled(monstats.noRatio, max, dm, difficulty);
      result.ElDur = value(duration, difficulty);
    }

    return true;
  }

  /** Mirrors the level selection in MONSTER_InitializeStatsAndSkills. */
  static int resolveMonsterLevel(
      MonStats.Entry monster, Levels.Entry level, int difficulty, boolean expansion) {
    if (monster == null) return 1;
    difficulty = NativeDataTables.difficulty(difficulty);
    int monsterLevel = NativeDataTables.monsterLevel(monster, difficulty);
    if (expansion && difficulty > 0 && !monster.noRatio && !monster.boss
        && level != null) {
      int areaLevel = NativeDataTables.areaLevel(level, difficulty, true);
      if (areaLevel > 0) monsterLevel = areaLevel;
    }
    return monsterLevel;
  }

  /** Evil monsters capture the connected-player count; allied units use one. */
  static int nativePlayerCount(MonStats.Entry monster, int connectedPlayers) {
    return monster != null && monster.Align == 0 ? Math.max(1, connectedPlayers) : 1;
  }

  static int nativeHpBonus(int playerCount) {
    if (playerCount >= 9) return 10 * (5 * playerCount - 10);
    return new int[] {0, 0, 50, 100, 150, 200, 250, 300, 350}
        [MathUtils.clamp(playerCount, 0, 8)];
  }

  static int nativeExperienceBonus(int playerCount) {
    if (playerCount >= 9) return 2 * (5 * playerCount + 130);
    return new int[] {0, 0, 50, 100, 150, 200, 250, 300, 350}
        [MathUtils.clamp(playerCount, 0, 8)];
  }

  static int nativeRankLevelBonus(int rank) {
    if (rank == com.riiablo.engine.server.monster.MonsterRank.CHAMPION) return 2;
    if (rank == com.riiablo.engine.server.monster.MonsterRank.UNIQUE
        || rank == com.riiablo.engine.server.monster.MonsterRank.SUPER_UNIQUE) return 3;
    return 0;
  }

  static int nativeRankExperience(int experience, int rank) {
    if (rank == com.riiablo.engine.server.monster.MonsterRank.CHAMPION) {
      return experience * 3;
    }
    if (rank == com.riiablo.engine.server.monster.MonsterRank.UNIQUE
        || rank == com.riiablo.engine.server.monster.MonsterRank.SUPER_UNIQUE) {
      return experience * 5;
    }
    return experience;
  }

  private static int scaled(boolean noRatio, int[] raw, int[] multiplier, int difficulty) {
    int value = value(raw, difficulty);
    return noRatio ? value : applyRatio(value(multiplier, difficulty), value, 100);
  }

  private static int value(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }

  /** Mirrors DATATBLS_ApplyRatio, including its overflow-avoidance ordering. */
  static int applyRatio(int value, int multiplier, int divisor) {
    if (divisor == 0) return 0;
    if (value <= 0x100000) {
      if (multiplier <= 0x10000) return multiplier * value / divisor;
      if (divisor <= (multiplier >> 4)) return value * (multiplier / divisor);
    } else if (divisor <= (value >> 4)) {
      return multiplier * (value / divisor);
    }
    return (int) ((long) multiplier * value / divisor);
  }
}
