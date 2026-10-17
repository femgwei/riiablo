package com.riiablo.engine.server.combat;

/**
 * Integer unit-footprint distance used by D2Common {@code #10399}.
 *
 * <p>Diablo II does not use Euclidean centre distance for melee attacks. Its
 * small-distance table and large-distance approximation both account for the
 * collision size of the two units. Keeping this helper free of ECS state
 * makes the native boundary independently testable.</p>
 */
public final class NativeMeleeDistance {
  private static final int[] SMALL_DISTANCE = {
      -1, -1, -1, 0, 2, 4, 6, 8,
      -1, -1, 0, 1, 2, 4, 6, 8,
      -1, 0, 0, 2, 3, 5, 7, 8,
      0, 1, 2, 2, 4, 5, 7, 8,
      2, 2, 3, 4, 5, 6, 7, 9,
      4, 4, 5, 5, 6, 7, 8, 9,
      6, 6, 7, 7, 7, 8, 10, 10,
      8, 8, 8, 8, 9, 9, 10, 11,
  };

  private NativeMeleeDistance() {}

  /** Exact Java projection of D2Common 1.10f {@code D2Common_10399}. */
  public static int between(int x1, int y1, int size1, int x2, int y2, int size2) {
    int dx = Math.abs(x2 - x1);
    int dy = Math.abs(y2 - y1);
    size1 = Math.max(0, size1);
    size2 = Math.max(0, size2);

    if (dx >= 8 || dy >= 8 || size1 >= 4 || size2 >= 4) {
      int sizeDiff = (size2 >>> 1) + (size1 >>> 1);
      int x = Math.max(0, dx - sizeDiff);
      int y = Math.max(0, dy - sizeDiff);
      return x <= y ? x + 2 * y : y + 2 * x;
    }

    int distance = SMALL_DISTANCE[dx + 8 * dy];
    if (distance < 0) return 0;
    if (size1 == 3 || size2 == 3) distance--;
    distance = Math.max(0, distance);
    if (size1 <= 1 || size2 <= 1) distance++;
    return distance;
  }

  public static boolean isInRange(
      int x1, int y1, int size1, int x2, int y2, int size2,
      int meleeRange, int rangeBonus) {
    int distance = between(x1, y1, size1, x2, y2, size2);
    return distance <= 0 || Math.max(0, meleeRange) + rangeBonus + 1 >= distance;
  }
}
