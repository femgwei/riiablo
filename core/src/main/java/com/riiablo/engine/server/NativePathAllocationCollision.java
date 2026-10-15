package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.collision.D2Collision;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.codec.excel.MonStats;

/** Collision fields at PATH_AllocDynamicPath (#10152), not live path field reads.
 * Does not allocate a path, mutate a grid, or include later skill/death overrides. */
public final class NativePathAllocationCollision {
  public final int pattern, footprintMask, moveMask;
  private NativePathAllocationCollision(int pattern, int footprintMask, int moveMask) {
    this.pattern = pattern; this.footprintMask = footprintMask; this.moveMask = moveMask;
  }

  /** nativeIsRevive is explicit runtime metadata, never resurrection provenance. */
  public static NativePathAllocationCollision read(World world, int id, boolean nativeIsRevive) {
    NativePathUnitMetadata metadata = NativePathUnitMetadata.read(world, id);
    if (metadata.type != 0 && metadata.type != 1 && metadata.type != 3) {
      throw new IllegalArgumentException("dynamic ECS unit required");
    }
    int pattern = metadata.collisionPatternFromSize(metadata.sizeX, nativeIsRevive);
    if (metadata.type == 0) return new NativePathAllocationCollision(pattern,
        D2Collision.COLLIDE_PLAYER, D2Collision.COLLIDE_MASK_PLAYER_PATH);
    if (metadata.type == 3) return new NativePathAllocationCollision(pattern, 0, 0);
    MonStats.Entry stats = world.getMapper(Monster.class).get(id).monstats;
    if (stats == null || stats.BaseId == null || stats.BaseId.isEmpty()) {
      throw new IllegalArgumentException("resolved MonStats BaseId required for allocation collision");
    }
    if (NativeGroundPathProfile.isWraith(stats)) return new NativePathAllocationCollision(
        D2Collision.PATTERN_SMALL_NO_PRESENCE, D2Collision.COLLIDE_MONSTER,
        D2Collision.COLLIDE_DOOR | D2Collision.COLLIDE_MISSILE_BARRIER);
    int mask = stats.flying ? D2Collision.COLLIDE_MASK_FLYING_UNIT : stats.opendoors
        ? D2Collision.COLLIDE_MASK_MONSTER_THAT_CAN_OPEN_DOORS : D2Collision.COLLIDE_MASK_MONSTER_PATH;
    return new NativePathAllocationCollision(pattern, D2Collision.COLLIDE_MONSTER, mask);
  }
}
