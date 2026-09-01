package com.riiablo.engine.server;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.TxtParser;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Adapter for native Hireling.txt Exp/Lvl rows and thresholds. */
public final class NativeHirelingExperienceTable {
  private static final Logger log = LogManager.getLogger(NativeHirelingExperienceTable.class);

  public static final class Row {
    public final int hirelingId, level, experiencePerLevel;
    public final int hitpoints, hitpointsPerLevel, defense, defensePerLevel;
    public final int strength, strengthPerLevel, dexterity, dexterityPerLevel;
    public final int attackRate, attackRatePerLevel, damageMin, damageMax, damagePerLevel;
    public final int resist, resistPerLevel;
    /** Native Hireling.txt default chance before any skill is considered. */
    public int defaultChance;
    public final int[] chances = new int[6], chancePerLevels = new int[6];
    public final int[] skills, skillModes, skillLevels, skillLevelsPerLevel;

    public Row(int hirelingId, int level, int experiencePerLevel) {
      this(hirelingId, level, experiencePerLevel,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0,
          new int[6], new int[6], new int[6], new int[6]);
    }

    public Row(int hirelingId, int level, int experiencePerLevel,
        int hitpoints, int hitpointsPerLevel, int defense, int defensePerLevel,
        int strength, int strengthPerLevel, int dexterity, int dexterityPerLevel,
        int attackRate, int attackRatePerLevel, int damageMin, int damageMax,
        int damagePerLevel, int resist, int resistPerLevel,
        int[] skills, int[] skillModes, int[] skillLevels, int[] skillLevelsPerLevel) {
      this.hirelingId = hirelingId;
      this.level = level;
      this.experiencePerLevel = Math.max(0, experiencePerLevel);
      this.hitpoints = hitpoints;
      this.hitpointsPerLevel = hitpointsPerLevel;
      this.defense = defense;
      this.defensePerLevel = defensePerLevel;
      this.strength = strength;
      this.strengthPerLevel = strengthPerLevel;
      this.dexterity = dexterity;
      this.dexterityPerLevel = dexterityPerLevel;
      this.attackRate = attackRate;
      this.attackRatePerLevel = attackRatePerLevel;
      this.damageMin = damageMin;
      this.damageMax = damageMax;
      this.damagePerLevel = damagePerLevel;
      this.resist = resist;
      this.resistPerLevel = resistPerLevel;
      this.skills = copy(skills);
      this.skillModes = copy(skillModes);
      this.skillLevels = copy(skillLevels);
      this.skillLevelsPerLevel = copy(skillLevelsPerLevel);
    }

    private static int[] copy(int[] values) {
      int[] result = new int[6];
      if (values != null) {
        System.arraycopy(values, 0, result, 0, Math.min(6, values.length));
      }
      return result;
    }

    public Row withChances(int[] chances, int[] chancePerLevels) {
      if (chances != null) System.arraycopy(chances, 0, this.chances, 0,
          Math.min(6, chances.length));
      if (chancePerLevels != null) System.arraycopy(chancePerLevels, 0, this.chancePerLevels, 0,
          Math.min(6, chancePerLevels.length));
      return this;
    }

    public Row withDefaultChance(int defaultChance) {
      this.defaultChance = Math.max(0, defaultChance);
      return this;
    }
  }

  /** Values produced by D2Game MONSTERAI_UpdateMercStatsAndSkills. */
  public static final class Stats {
    public int level, strength, dexterity, hitpoints, defense;
    public int damageMin, damageMax, attackRate, resist, hpRegenEncoded;
    public long nextExperience;
    public final int[] skills = new int[6];
    public final int[] skillModes = new int[6];
    public final int[] skillLevels = new int[6];

    Stats() {
      java.util.Arrays.fill(skills, -1);
    }
  }

  private final Array<Row> rows = new Array<>(false, 32);

  public NativeHirelingExperienceTable add(int hirelingId, int level, int expPerLevel) {
    rows.add(new Row(hirelingId, level, expPerLevel));
    return this;
  }

  public NativeHirelingExperienceTable add(Row row) {
    if (row != null) rows.add(row);
    return this;
  }

  /** Loads the real table from the mounted Diablo II MPQ files. */
  public static NativeHirelingExperienceTable load() {
    NativeHirelingExperienceTable table = new NativeHirelingExperienceTable();
    if (Riiablo.mpqs == null) {
      log.warn("[XP_MERC_TABLE] MPQ manager is not mounted; native hireling level-up disabled");
      return table;
    }
    try {
      FileHandle file = Riiablo.mpqs.resolve("data\\global\\excel\\Hireling.txt");
      if (file == null || !file.exists()) {
        log.warn("[XP_MERC_TABLE] Hireling.txt not found; native hireling level-up disabled");
        return table;
      }
      TxtParser parser = TxtParser.loadFromFile(file);
      try {
        int idColumn = firstColumn(parser, "Id", "HirelingId", "Hireling Id", "Hireling", "Class");
        int versionColumn = firstColumn(parser, "Version");
        int levelColumn = firstColumn(parser, "Hireling Level", "HirelingLevel", "Level");
        int expColumn = firstColumn(parser, "Exp/Lvl", "ExpPerLvl", "Exp Per Lvl",
            "Exp per Level");
        if (expColumn < 0) {
          log.warn("[XP_MERC_TABLE] Hireling.txt has no Exp/Lvl column; native hireling level-up disabled");
          return table;
        }
        while (parser.nextLine() != null) {
          // This server creates expansion characters; D2Common selects only
          // version 100 Hireling.txt records for them.
          if (versionColumn >= 0 && parser.getInt(versionColumn) != 100) continue;
          // TxtParser indexes data rows from zero; native hireling levels start at one.
          int rowIndex = parser.getIndex() + 1;
          int id = idColumn < 0 ? rowIndex : parseHirelingId(parser.getString(idColumn), rowIndex);
          int level = levelColumn < 0 ? rowIndex : parser.getInt(levelColumn);
          int expPerLevel = parser.getInt(expColumn);
          if (level > 0 && expPerLevel > 0) {
            table.add(new Row(id, level, expPerLevel,
                intValue(parser, "HP", "Hitpoints", "Hit Points"),
                intValue(parser, "HP/Lvl", "Hitpoints/Lvl", "HitpointsPerLvl", "Hit Points/Lvl"),
                intValue(parser, "Defense", "Def"),
                intValue(parser, "Def/Lvl", "Defense/Lvl", "DefensePerLvl"),
                intValue(parser, "Str", "Strength"),
                intValue(parser, "Str/Lvl", "StrPerLvl", "Strength/Lvl"),
                intValue(parser, "Dex", "Dexterity"),
                intValue(parser, "Dex/Lvl", "DexPerLvl", "Dexterity/Lvl"),
                intValue(parser, "AR", "Attack Rate", "AttackRate"),
                intValue(parser, "AR/Lvl", "Attack Rate/Lvl", "AttackRatePerLvl"),
                intValue(parser, "Dmg-Min", "Dmg Min", "Damage Min"),
                intValue(parser, "Dmg-Max", "Dmg Max", "Damage Max"),
                intValue(parser, "Dmg/Lvl", "DmgPerLvl", "Damage/Lvl"),
                intValue(parser, "Resist", "Resistance"),
                intValue(parser, "Resist/Lvl", "ResistPerLvl", "Resistance/Lvl"),
                indexedSkillValues(parser), indexedValues(parser, "Mode"),
                indexedValues(parser, "Level"), indexedValues(parser, "LvlPerLvl"))
                .withDefaultChance(intValue(parser, "DefaultChance", "Default Chance"))
                .withChances(indexedValues(parser, "Chance"),
                    indexedValues(parser, "ChancePerLvl", "Chance/Lvl")));
          }
        }
        log.info("[XP_MERC_TABLE] loaded {} Hireling.txt rows (idColumn={}, levelColumn={}, expColumn={})",
            table.size(), idColumn, levelColumn, expColumn);
      } finally {
        parser.close();
      }
    } catch (Throwable ignored) {
      // Old installations may not expose Hireling.txt; preserve safe behavior.
      log.warn("[XP_MERC_TABLE] failed to load Hireling.txt; native hireling level-up disabled: {}",
          ignored.toString());
    }
    return table;
  }

  private static int firstColumn(TxtParser parser, String... names) {
    for (String name : names) {
      int column = parser.getColumnId(name);
      if (column >= 0) return column;
    }
    return -1;
  }

  private static int intValue(TxtParser parser, String... names) {
    int column = firstColumn(parser, names);
    return column < 0 ? 0 : parser.getInt(column);
  }

  private static int[] indexedValues(TxtParser parser, String... prefixes) {
    int[] values = new int[6];
    for (int i = 0; i < values.length; i++) {
      int n = i + 1;
      String[] names = new String[prefixes.length * 3];
      for (int j = 0; j < prefixes.length; j++) {
        names[j * 3] = prefixes[j] + " " + n;
        names[j * 3 + 1] = prefixes[j] + n;
        names[j * 3 + 2] = prefixes[j] + "_" + n;
      }
      int column = firstColumn(parser, names);
      values[i] = column < 0 ? 0 : parser.getInt(column);
    }
    return values;
  }

  private static int[] indexedSkillValues(TxtParser parser) {
    int[] values = new int[6];
    java.util.Arrays.fill(values, -1);
    for (int i = 0; i < values.length; i++) {
      int n = i + 1;
      int column = firstColumn(parser, "Skill" + n, "Skill " + n, "Skill_" + n);
      if (column < 0) continue;
      String value = parser.getString(column);
      if (value == null || value.trim().isEmpty()) continue;
      try {
        values[i] = Integer.parseInt(value.trim());
      } catch (NumberFormatException ignored) {
        if (Riiablo.files != null && Riiablo.files.skills != null) {
          values[i] = Riiablo.files.skills.index(value.trim());
        }
      }
    }
    return values;
  }

  private static int parseHirelingId(String value, int fallback) {
    if (value == null) return fallback;
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ignored) {
      String id = value.trim().toLowerCase();
      if (id.contains("rogue") || id.contains("scout")) return 0;
      if (id.contains("desert") || id.contains("hireling2")) return 1;
      if (id.contains("iron") || id.contains("wolf")) return 2;
      if (id.contains("barb")) return 3;
      return fallback;
    }
  }

  public Row row(int hirelingId, int level) {
    Row best = null;
    for (Row row : rows) {
      if (row.hirelingId != hirelingId || row.level > level) continue;
      if (best == null || row.level > best.level) best = row;
    }
    return best;
  }

  /** Mirrors MONSTERS_GetHirelingExpForNextLevel without 32-bit overflow. */
  public long thresholdForHireling(int hirelingId, int level) {
    Row row = row(hirelingId, level);
    return row == null ? 0L : threshold(level, row.experiencePerLevel);
  }

  /** Next-level threshold using the record selected for the current level. */
  public long nextThreshold(int hirelingId, int currentLevel) {
    Row row = row(hirelingId, currentLevel);
    return row == null ? 0L : threshold(currentLevel + 1, row.experiencePerLevel);
  }

  /**
   * Mirrors the hireling-only cap in SUNITDMG_ComputeExperienceGain: one award
   * cannot exceed one sixty-fourth of the current level's experience span.
   */
  public long maximumAward(int hirelingId, int currentLevel) {
    Row row = row(hirelingId, currentLevel);
    if (row == null) return 0L;
    long current = threshold(currentLevel, row.experiencePerLevel);
    long next = threshold(currentLevel + 1, row.experiencePerLevel);
    return Math.max(0L, next - current) >>> 6;
  }

  public static long threshold(int level, int experiencePerLevel) {
    if (level <= 0 || experiencePerLevel <= 0) return 0L;
    return (long) experiencePerLevel * level * level * (level + 1L);
  }

  public int levelForExperience(int hirelingId, int currentLevel,
      long experience, int ownerLevel) {
    int level = Math.max(1, currentLevel);
    int maxLevel = Math.max(level, Math.min(98, ownerLevel - 1));
    Row currentRow = row(hirelingId, level);
    if (currentRow == null) return level;
    while (level < maxLevel) {
      // SUNITDMG_AddExperienceForHireling keeps the record selected at the
      // beginning of the award while checking all levels reached by it.
      long next = threshold(level + 1, currentRow.experiencePerLevel);
      if (next <= 0L || experience < next) break;
      level++;
    }
    return level;
  }

  public Stats stats(int hirelingId, int level) {
    Row row = row(hirelingId, level);
    if (row == null) return null;
    int levelUps = Math.max(0, level - row.level);
    Stats stats = new Stats();
    stats.level = level;
    stats.nextExperience = level < 98
        ? threshold(level + 1, row.experiencePerLevel) : 0L;
    stats.strength = Math.max(row.strength + levelUps * row.strengthPerLevel / 8, 10);
    stats.dexterity = Math.max(row.dexterity + levelUps * row.dexterityPerLevel / 8, 10);
    stats.hitpoints = Math.max(row.hitpoints + levelUps * row.hitpointsPerLevel, 40);
    stats.defense = Math.max(row.defense + levelUps * row.defensePerLevel, 0);
    stats.damageMin = Math.max(row.damageMin + levelUps * row.damagePerLevel / 8, 0);
    stats.damageMax = Math.max(row.damageMax + levelUps * row.damagePerLevel / 8, 1);
    stats.attackRate = Math.max(row.attackRate + levelUps * row.attackRatePerLevel, 0);
    stats.resist = Math.max(row.resist + levelUps * row.resistPerLevel / 4, 0);
    stats.hpRegenEncoded = Math.max((stats.hitpoints << 8) / 2000, 0);
    for (int i = 0; i < stats.skills.length; i++) {
      int skillLevel = row.skillLevels[i]
          + ((levelUps * row.skillLevelsPerLevel[i]) >> 5);
      stats.skills[i] = row.skills[i];
      stats.skillModes[i] = row.skillModes[i];
      stats.skillLevels[i] = Math.max(0, Math.min(32, skillLevel));
    }
    return stats;
  }

  /** Selects one native skill slot using Hireling.txt Chance/ChancePerLvl. */
  public int selectSkill(int hirelingId, int level, int roll) {
    Row row = row(hirelingId, level);
    if (row == null) return -1;
    int levelUps = Math.max(0, level - row.level);
    int total = row.defaultChance;
    for (int i = 0; i < row.skills.length; i++) {
      if (row.skills[i] < 0 || row.skillModes[i] >= 16 || row.skillLevels[i] <= 0) continue;
      // D2Game scales ChancePerLvl by one quarter before adding it to the
      // cumulative roll range (sub_6FCE4830).
      int chance = Math.max(0, row.chances[i] + levelUps * row.chancePerLevels[i] / 4);
      total += chance;
    }
    if (total <= 0) return -1;
    // The native random helper rolls in [0, nChance], hence the inclusive
    // upper bound here. Values below DefaultChance intentionally mean that
    // no hireling skill was selected.
    int value = Math.floorMod(roll, total + 1);
    if (value < row.defaultChance) return -1;
    int cumulative = row.defaultChance;
    for (int i = 0; i < row.skills.length; i++) {
      if (row.skills[i] < 0 || row.skillModes[i] >= 16 || row.skillLevels[i] <= 0) continue;
      int chance = Math.max(0, row.chances[i] + levelUps * row.chancePerLevels[i] / 4);
      cumulative += chance;
      if (value <= cumulative) return i;
    }
    return -1;
  }

  public int size() { return rows.size; }
}
