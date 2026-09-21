package com.riiablo.engine.server.monster;

import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.server.NativeDataTables;

/**
 * Small, data-only portion of D2MOO's MONSTERREGION initialization.
 *
 * D2MOO keeps separate normal and nightmare/hell monster lists in Levels.txt
 * and rolls density on a 0..99999 scale.  The map builders use this helper so
 * they do not silently fall back to the normal list when a difficulty list is
 * present, while tests can verify the selection without booting a world.
 */
public final class NativeMonsterRegion {
  private static final int DENSITY_ROLL_RANGE = 100000;
  private static final int MAX_DENSITY = 10000;
  private static final int GAME_TILE_SUBTILES = 5;
  private static final int POPULATION_CELL_SUBTILES = 3;

  private NativeMonsterRegion() {}

  /** Returns the difficulty-specific list, falling back only when it is empty. */
  public static String[] monsterColumns(Levels.Entry level, int difficulty) {
    if (level == null) return new String[0];
    String[] preferred = NativeDataTables.difficulty(difficulty) > 0 ? level.nmon : level.mon;
    if (hasValue(preferred)) return preferred;
    return level.mon == null ? new String[0] : level.mon;
  }

  /** D2MOO caps the number of selected region entries at thirteen. */
  public static int selectedEntryCount(Levels.Entry level, int difficulty) {
    if (level == null || level.NumMon <= 0) return 0;
    String[] columns = monsterColumns(level, difficulty);
    int populated = 0;
    // D2MOO first reads the difficulty-specific spawn list and then chooses
    // NumMon entries from that list without replacement.  Counting the whole
    // list here is important: a valid entry after an empty/placeholder cell
    // must not be silently excluded by the Java prefix scan.
    for (int i = 0; i < columns.length; i++) {
      if (columns[i] != null && !columns[i].isEmpty() && !"0".equals(columns[i])) populated++;
    }
    return Math.min(Math.min(level.NumMon, populated), 13);
  }

  public static int density(Levels.Entry level, int difficulty) {
    return level == null ? 0 : NativeDataTables.value(level.MonDen, difficulty, 0);
  }

  /** Inclusive native density check after D2Game's population-time MonDen cap. */
  public static boolean densityRoll(int monDen, int roll) {
    if (monDen <= 0) return false;
    int normalizedRoll = Math.floorMod(roll, DENSITY_ROLL_RANGE);
    return normalizedRoll <= Math.min(monDen, MAX_DENSITY);
  }

  /** Native {@code SparsePopulate} check used after a density hit. */
  public static boolean sparsePopulationRoll(int sparsePopulate, int roll) {
    if (sparsePopulate <= 0) return true;
    return Math.floorMod(roll, 100) <= Math.min(sparsePopulate, 100);
  }

  /**
   * Fallen and Scarab normal groups are deliberately single-member groups in
   * D2Game, regardless of their MinGrp/MaxGrp table values.
   */
  public static boolean isSingleMemberNormalGroup(String baseId, String id) {
    String base = baseId == null ? "" : baseId.toLowerCase(java.util.Locale.ROOT);
    String name = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
    return base.startsWith("fallen") || base.startsWith("scarab")
        || name.startsWith("fallen") || name.startsWith("scarab");
  }

  /**
   * Distributes D2Game's one attempt per 3x3 subtiles over riiablo's 5x5 game tiles.
   * The cumulative form avoids rounding every tile down to two attempts.
   */
  public static int populationAttemptsForGameTile(int tileIndex) {
    if (tileIndex < 0) return 0;
    int numerator = GAME_TILE_SUBTILES * GAME_TILE_SUBTILES;
    int denominator = POPULATION_CELL_SUBTILES * POPULATION_CELL_SUBTILES;
    long before = (long) tileIndex * numerator / denominator;
    long after = (long) (tileIndex + 1) * numerator / denominator;
    return (int) (after - before);
  }

  private static boolean hasValue(String[] values) {
    if (values == null) return false;
    for (String value : values) {
      if (value != null && !value.isEmpty() && !"0".equals(value)) return true;
    }
    return false;
  }
}
