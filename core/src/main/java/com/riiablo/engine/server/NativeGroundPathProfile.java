package com.riiablo.engine.server;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.map.DT1;

/** Ground terrain projection of D2Common's player and monster path masks.
 * Map reads terrain/object/door bits from its persistent shared grid; unit
 * movement masks are tested by DynamicUnitCollisionSystem against that grid.
 */
public enum NativeGroundPathProfile {
  PLAYER(DT1.Tile.FLAG_BLOCK_WALK | DT1.Tile.FLAG_BLOCK_PLAYER_WALK),
  MONSTER(DT1.Tile.FLAG_BLOCK_WALK),
  FLYING(DT1.Tile.FLAG_BLOCK_JUMP);

  public final int terrainFlags;

  NativeGroundPathProfile(int terrainFlags) {
    this.terrainFlags = terrainFlags;
  }

  static NativeGroundPathProfile forUnit(boolean player) {
    return player ? PLAYER : MONSTER;
  }

  public static NativeGroundPathProfile forUnit(boolean player, MonStats.Entry stats) {
    if (player) return PLAYER;
    return stats != null && (stats.flying || isWraith(stats)) ? FLYING : MONSTER;
  }

  public static boolean isWraith(MonStats.Entry stats) {
    return hasBase(stats, "wraith1");
  }

  public static boolean hasNoPresence(MonStats.Entry stats) {
    return isWraith(stats) || hasBase(stats, "bird1") || hasBase(stats, "bird2")
        || hasBase(stats, "parrot");
  }

  private static boolean hasBase(MonStats.Entry stats, String base) {
    if (stats == null) return false;
    String id = stats.BaseId == null || stats.BaseId.isEmpty() ? stats.Id : stats.BaseId;
    return base.equalsIgnoreCase(id);
  }
}
