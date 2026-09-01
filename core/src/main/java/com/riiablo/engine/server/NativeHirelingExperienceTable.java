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
    public Row(int hirelingId, int level, int experiencePerLevel) {
      this.hirelingId = hirelingId;
      this.level = level;
      this.experiencePerLevel = Math.max(0, experiencePerLevel);
    }
  }

  private final Array<Row> rows = new Array<>(false, 32);

  public NativeHirelingExperienceTable add(int hirelingId, int level, int expPerLevel) {
    rows.add(new Row(hirelingId, level, expPerLevel));
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
        int levelColumn = firstColumn(parser, "Hireling Level", "HirelingLevel", "Level");
        int expColumn = firstColumn(parser, "Exp/Lvl", "ExpPerLvl", "Exp Per Lvl",
            "Exp per Level");
        if (expColumn < 0) {
          log.warn("[XP_MERC_TABLE] Hireling.txt has no Exp/Lvl column; native hireling level-up disabled");
          return table;
        }
        while (parser.nextLine() != null) {
          // TxtParser indexes data rows from zero; native hireling levels start at one.
          int rowIndex = parser.getIndex() + 1;
          int id = idColumn < 0 ? rowIndex : parseHirelingId(parser.getString(idColumn), rowIndex);
          int level = levelColumn < 0 ? rowIndex : parser.getInt(levelColumn);
          int expPerLevel = parser.getInt(expColumn);
          if (level > 0 && expPerLevel > 0) table.add(id, level, expPerLevel);
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

  public int size() { return rows.size; }
}
