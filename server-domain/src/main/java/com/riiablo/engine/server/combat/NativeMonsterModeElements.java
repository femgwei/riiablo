package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import java.util.function.IntUnaryOperator;

/** One refresh of MonsterMode::sub_6FC627B0's five supported elemental stats.
 * Values are source stats, NOT a final damage packet: poison is raw 8.8 rate.
 * The host owns table reads, RNG state and the lifetime of this refresh.
 */
public final class NativeMonsterModeElements {
  public final int[] minimum = new int[DAMAGE_TYPE_COUNT];
  public final int[] maximum = new int[DAMAGE_TYPE_COUNT];
  public int coldLength, poisonLength, matchedRows;

  /** Verified in 1.10f D2Game 6FC62B22..6FC62B47: addition, not 0x40 << row. */
  public static short tableFlags(int row) {
    if (row < 0 || row > 2) throw new IllegalArgumentException("element row");
    return (short) (0x40 + row);
  }

  public static boolean selects(int rowMode, int mode, int chance, IntUnaryOperator random) {
    // ElMode is an unsigned byte; zero means no row, including mode DT=0.
    chance &= 0xff;
    return rowMode > 0 && rowMode == mode && chance > 0
        && (chance >= 100 || random.applyAsInt(100) < chance);
  }

  /** Native EType numbering: fire=1, ltng=2, magic=3, cold=4, pois=5, rand=10.
   * Repeated successful rows SET a stat, they do not sum or maximize it.
   * Other native types remain outside this five-channel migration.
   */
  public void set(int nativeType, int min, int max, int length, int multiplier,
      IntUnaryOperator random) {
    min = scaled(min, multiplier);
    max = scaled(max, multiplier);
    length = scaled(length, multiplier);
    if (nativeType == 10) {
      nativeType = 1 + random.applyAsInt(5);
      if (length == 0) length = 25;
    }
    int type;
    switch (nativeType) {
      case 1: type = DAMAGE_FIRE; break;
      case 2: type = DAMAGE_LIGHTNING; break;
      case 3: type = DAMAGE_MAGIC; break;
      case 4: type = DAMAGE_COLD; coldLength = length; break;
      case 5:
        type = DAMAGE_POISON;
        min = signedInt((long) min * 10); max = signedInt((long) max * 10);
        poisonLength = signedInt((long) length * 2);
        break;
      default: return;
    }
    minimum[type] = min;
    maximum[type] = max;
    matchedRows++;
  }

  private static int scaled(int value, int multiplier) {
    return signedInt(value + (long) multiplier * value / 128);
  }

  private static int signedInt(long value) {
    return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
  }
}
