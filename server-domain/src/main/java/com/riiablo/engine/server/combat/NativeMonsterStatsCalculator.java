package com.riiablo.engine.server.combat;

import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterRank;
import java.util.Objects;

/**
 * Host-free monster table and spawn arithmetic. Rows are borrowed only during a call.
 * No entity mutation, table lookup, random draw, skill or affix application occurs here.
 *
 * <p>Preserves the host's existing contracts, including raw noRatio elemental output
 * and fixed stock rank multipliers. This is not the complete Classic initialization
 * chain or an implementation of arbitrary MonUMod/MonProp effects.
 */
public final class NativeMonsterStatsCalculator {
  private NativeMonsterStatsCalculator() {}

  public static final int MAX_HITPOINTS = (1 << 23) - 1;

  /** Compatibility output sink; the host keeps its public flag-based API. */
  public static class TableStats {
    public int minHP, maxHP, AC, TH, Exp;
    public int A1MinD, A1MaxD, A2MinD, A2MaxD, S1MinD, S1MaxD;
    public int ElMinD, ElMaxD, ElDur;
  }

  /** Whole-point damage endpoints and AR before hit rolls and mitigation. */
  public static final class Attack {
    public final int minDamage, maxDamage, attackRating;

    private Attack(int minDamage, int maxDamage, int attackRating) {
      this.minDamage = minDamage;
      this.maxDamage = maxDamage;
      this.attackRating = attackRating;
    }
  }

  /**
   * DATATBLS_CalculateMonsterStatsByLevel arithmetic over already resolved rows.
   * Only requested fields change. Multiple attack bits overwrite TH in A1/A2/S1
   * order; elemental bits retain the host's first-selected-row compatibility API.
   * Native mode callers separately preserve D2Common's noRatio output-field quirk.
   */
  public static boolean calculateTableStats(NativeCombatContext context, MonStats.Entry monster,
      MonLvl.Entry level, int flags, TableStats result) {
    if (monster == null || level == null || result == null) return false;
    Objects.requireNonNull(context, "context");
    int difficulty = context.difficulty();
    boolean expansion = context.expansion();
    int[] hp = expansion ? level.LHP : level.HP;
    int[] ac = expansion ? level.LAC : level.AC;
    int[] th = expansion ? level.LTH : level.TH;
    int[] dm = expansion ? level.LDM : level.DM;
    int[] xp = expansion ? level.LXP : level.XP;
    if ((flags & 1) != 0) {
      result.minHP = scaled(monster.noRatio, monster.minHP, hp, difficulty);
      result.maxHP = scaled(monster.noRatio, monster.maxHP, hp, difficulty);
    }
    if ((flags & 2) != 0) result.AC = scaled(monster.noRatio, monster.AC, ac, difficulty);
    if ((flags & 4) != 0) result.Exp = scaled(monster.noRatio, monster.Exp, xp, difficulty);
    if ((flags & 8) != 0) {
      result.TH = scaled(monster.noRatio, monster.A1TH, th, difficulty);
      result.A1MinD = scaled(monster.noRatio, monster.A1MinD, dm, difficulty);
      result.A1MaxD = scaled(monster.noRatio, monster.A1MaxD, dm, difficulty);
    }
    if ((flags & 0x10) != 0) {
      result.TH = scaled(monster.noRatio, monster.A2TH, th, difficulty);
      result.A2MinD = scaled(monster.noRatio, monster.A2MinD, dm, difficulty);
      result.A2MaxD = scaled(monster.noRatio, monster.A2MaxD, dm, difficulty);
    }
    if ((flags & 0x20) != 0) {
      result.TH = scaled(monster.noRatio, monster.S1TH, th, difficulty);
      result.S1MinD = scaled(monster.noRatio, monster.S1MinD, dm, difficulty);
      result.S1MaxD = scaled(monster.noRatio, monster.S1MaxD, dm, difficulty);
    }
    int element = (flags & 0x40) != 0 ? 0 : (flags & 0x80) != 0 ? 1
        : (flags & 0x100) != 0 ? 2 : -1;
    if (element >= 0) {
      int[] min = element == 0 ? monster.El1MinD : element == 1 ? monster.El2MinD : monster.El3MinD;
      int[] max = element == 0 ? monster.El1MaxD : element == 1 ? monster.El2MaxD : monster.El3MaxD;
      int[] duration = element == 0 ? monster.El1Dur : element == 1 ? monster.El2Dur : monster.El3Dur;
      result.ElMinD = scaled(monster.noRatio, min, dm, difficulty);
      result.ElMaxD = scaled(monster.noRatio, max, dm, difficulty);
      result.ElDur = value(duration, difficulty);
    }
    return true;
  }

  /** LoD area-level selection before rank level bonuses; malformed rows retain host fallbacks. */
  public static int resolveMonsterLevel(MonStats.Entry monster, Levels.Entry area,
      NativeCombatContext context) {
    if (monster == null) return 1;
    int difficulty = context.difficulty();
    int level = Math.max(1, columnOrNormal(monster.Level, difficulty, 1));
    if (context.expansion() && difficulty > 0 && !monster.noRatio && !monster.boss && area != null) {
      int areaLevel = columnOrNormal(area.MonLvlEx, difficulty, 0);
      if (areaLevel > 0) level = areaLevel;
    }
    return level;
  }

  /** Snapshot of the current host's spawn attributes, before affixes/champion subtypes. */
  public static MonsterStatsSnapshot spawnSnapshot(NativeCombatContext context,
      MonStats.Entry monster, MonLvl.Entry level, int baseLevel, int rank) {
    return spawnSnapshot(context, monster, level, baseLevel, rank, context.playerCount());
  }

  /**
   * Explicit host count preserves the existing 9+ player/Mod branches. The convenience
   * overload uses NativeCombatContext's 1..8 count. Call only at spawn, not each tick.
   */
  public static MonsterStatsSnapshot spawnSnapshot(NativeCombatContext context,
      MonStats.Entry monster, MonLvl.Entry level, int baseLevel, int rank, int connectedPlayers) {
    TableStats base = new TableStats();
    if (!calculateTableStats(context, monster, level, 7, base)) return null;
    int count = playerCount(monster, connectedPlayers);
    int multiplier = combatMultiplier(monster, context.difficulty(), count, context.expansion());
    return new MonsterStatsSnapshot(baseLevel, level.Level, baseLevel + rankLevelBonus(rank),
        rank, context.difficulty(), count, hpBonus(count), multiplier,
        Math.min(base.minHP, base.maxHP), Math.max(base.minHP, base.maxHP), base.AC,
        rankExperience(base.Exp + spawnPercentage(base.Exp, experienceBonus(count)), rank),
        tableAttack(context, monster, level, 8, multiplier),
        tableAttack(context, monster, level, 0x10, multiplier),
        tableAttack(context, monster, level, 0x20, multiplier));
  }

  /** Current mode adapter contract: normalized table endpoints plus captured-count LoD bonus. */
  public static Attack attackProfile(NativeCombatContext context, MonStats.Entry monster,
      MonLvl.Entry level, int attackFlag, int capturedPlayerCount) {
    Attack raw = tableAttack(context, monster, level, attackFlag, 0);
    int multiplier = combatMultiplier(monster, context.difficulty(), capturedPlayerCount,
        context.expansion());
    int min = scaleCombatValue(Math.max(0, raw.minDamage), multiplier);
    int max = scaleCombatValue(Math.max(Math.max(0, raw.minDamage), raw.maxDamage), multiplier);
    return new Attack(min, Math.max(min, max),
        scaleCombatValue(Math.max(0, raw.attackRating), multiplier));
  }

  private static Attack tableAttack(NativeCombatContext context, MonStats.Entry monster,
      MonLvl.Entry level, int flag, int multiplier) {
    if (flag != 8 && flag != 0x10 && flag != 0x20) {
      throw new IllegalArgumentException("Expected one A1/A2/S1 flag");
    }
    TableStats stats = new TableStats();
    if (!calculateTableStats(context, monster, level, flag, stats)) return new Attack(0, 0, 0);
    int min = flag == 8 ? stats.A1MinD : flag == 0x10 ? stats.A2MinD : stats.S1MinD;
    int max = flag == 8 ? stats.A1MaxD : flag == 0x10 ? stats.A2MaxD : stats.S1MaxD;
    return new Attack(multiplier > 0 ? scaleCombatValue(min, multiplier) : min,
        multiplier > 0 ? scaleCombatValue(max, multiplier) : max,
        multiplier > 0 ? scaleCombatValue(stats.TH, multiplier) : stats.TH);
  }

  public static int playerCount(MonStats.Entry monster, int connectedPlayers) {
    return monster != null && monster.Align == 0 ? Math.max(1, connectedPlayers) : 1;
  }

  /** Numerator over 128; Classic's separate attack adjustments are not migrated here. */
  public static int combatMultiplier(MonStats.Entry monster, int difficulty, int count, boolean expansion) {
    if (!expansion || monster == null || monster.Align != 0 || difficulty <= 0 || count < 2) return 0;
    return count >= 9 ? 8 * count - 16 : 8 * (count - 1);
  }

  public static int scaleCombatValue(int value, int multiplier) {
    if (value <= 0 || multiplier <= 0) return Math.max(0, value);
    return value + multiplier * value / 128;
  }

  public static int hpBonus(int count) {
    return count >= 9 ? 10 * (5 * count - 10) : 50 * Math.max(0, Math.min(8, count) - 1);
  }

  public static int experienceBonus(int count) {
    return count >= 9 ? 2 * (5 * count + 130) : 50 * Math.max(0, Math.min(8, count) - 1);
  }

  public static int rankLevelBonus(int rank) {
    return rank == MonsterRank.CHAMPION ? 2 : MonsterRank.isUnique(rank) ? 3 : 0;
  }

  public static int rankExperience(int experience, int rank) {
    return rank == MonsterRank.CHAMPION ? experience * 3
        : MonsterRank.isUnique(rank) ? experience * 5 : experience;
  }

  /** Retains the stock host's integer-HP/float rank contract, not native 8.8 UMod arithmetic. */
  public static int rankHitpoints(int hp, int rank, int difficulty) {
    if (hp <= 0) return Math.max(0, hp);
    return Math.min((int) (hp * MonsterRank.getHpMultiplier(rank, difficulty)), MAX_HITPOINTS);
  }

  public static int spawnHitpoints(int baseHp, int count, int rank, int difficulty) {
    int hp = Math.min(baseHp + spawnPercentage(baseHp, hpBonus(count)), MAX_HITPOINTS);
    return rankHitpoints(hp, rank, difficulty);
  }

  // Spawn historically uses wide multiplication, unlike the table ratio helper.
  // Keep this distinction during extraction (including large-HP/Mod inputs).
  private static int spawnPercentage(int value, int percent) {
    return (int) ((long) value * percent / 100);
  }

  /** DATATBLS_ApplyRatio / MONSTERUNIQUE_CalculatePercentage division ordering. */
  public static int applyRatio(int value, int multiplier, int divisor) {
    if (divisor == 0) return 0;
    if (value <= 0x100000) {
      if (multiplier <= 0x10000) return multiplier * value / divisor;
      if (divisor <= (multiplier >> 4)) return value * (multiplier / divisor);
    } else if (divisor <= (value >> 4)) {
      return multiplier * (value / divisor);
    }
    return (int) ((long) multiplier * value / divisor);
  }

  private static int scaled(boolean noRatio, int[] raw, int[] multiplier, int difficulty) {
    int value = value(raw, difficulty);
    return noRatio ? value : applyRatio(value(multiplier, difficulty), value, 100);
  }

  private static int value(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }

  private static int columnOrNormal(int[] values, int index, int fallback) {
    return values == null || values.length == 0 ? fallback : values[index < values.length ? index : 0];
  }
}
