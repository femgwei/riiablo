package com.riiablo.engine.server.missile;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats2;

/** Native Molten Boulder SrvHit47/48 and SrvDmg14 data policies. */
public final class NativeMoltenBoulder {
  public static final int[] FIRE_X = {2,-2,0,0,-3,0,3,-1,1,-1,2,-4,-3,-1,0,1,3,4};
  public static final int[] FIRE_Y = {-2,-2,2,5,3,3,3,2,1,-1,-1,-2,-2,-3,-4,-3,-3,-2};
  private NativeMoltenBoulder() {}

  public static boolean isEmerge(Missiles.Entry row) {
    return row != null && row.pSrvHitFunc == 48;
  }

  public static boolean isRolling(Missiles.Entry row) {
    return row != null && row.pSrvHitFunc == 47;
  }

  public static boolean burstsOnTarget(boolean player, MonStats2.Entry monster) {
    return !player && monster != null && monster.large;
  }

  /** Exact SrvDmg14 size multiplier before the native modulo100 roll. */
  public static int knockbackChance(Missiles.Entry row, boolean player, MonStats2.Entry monster) {
    if (row == null || row.pSrvDmgFunc != 14 || row.dParam == null || row.dParam.length < 2) return 0;
    int size = row.dParam[0], chance = row.dParam[1];
    if (player) return size < 1 ? 0 : chance;
    if (monster == null) return 0;
    if (size < 1) return monster.small ? chance : 0;
    if (size == 1) return monster.small ? 2 * chance : monster.large ? 0 : chance;
    return monster.small ? 3 * chance : monster.large ? chance : 2 * chance;
  }
}
