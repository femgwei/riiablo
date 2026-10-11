package com.riiablo.engine.server;

import com.riiablo.map.DT1;

/** Ground terrain projection of D2Common's player and monster path masks.
 * Objects and doors remain in Map's separate reference layers; unit markers
 * are tested by DynamicUnitCollisionSystem.
 */
public enum NativeGroundPathProfile {
  PLAYER(DT1.Tile.FLAG_BLOCK_WALK | DT1.Tile.FLAG_BLOCK_PLAYER_WALK),
  MONSTER(DT1.Tile.FLAG_BLOCK_WALK);

  public final int terrainFlags;

  NativeGroundPathProfile(int terrainFlags) {
    this.terrainFlags = terrainFlags;
  }

  static NativeGroundPathProfile forUnit(boolean player) {
    return player ? PLAYER : MONSTER;
  }
}
