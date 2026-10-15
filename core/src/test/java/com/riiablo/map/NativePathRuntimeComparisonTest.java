package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.collision.D2Collision;
import com.d2moo.common.collision.D2CommonCollision;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgCoords;
import com.d2moo.common.path.D2AStar;
import com.d2moo.common.path.D2Path;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.map.pfa.GraphPath;
import org.junit.jupiter.api.Test;

/** Characterizes current runtime/reference differences, not desired parity contracts.
 * Update these fixtures deliberately when integrating the native path state machine.
 */
class NativePathRuntimeComparisonTest {
  @Test void blockedCoordinateGoalHasNativePartialButNoOrdinaryRuntimePath() {
    Map map = map();
    map.zones.first().or(10, 10, DT1.Tile.FLAG_BLOCK_WALK);
    int flags = NativeGroundPathProfile.PLAYER.terrainFlags;
    D2AStar.Result nativeResult = D2AStar.findPath(
        new D2Path.Point(3, 3), new D2Path.Point(10, 10),
        (x, y) -> map.isBlockedForPath(x, y, flags, 2, false), false);
    GraphPath runtimePath = new GraphPath();
    boolean runtimeReached = map.findPath(new Vector2(3, 3), new Vector2(10, 10),
        flags, 2, runtimePath, null);
    assertFalse(nativeResult.reachedTarget());
    assertFalse(nativeResult.path().isEmpty(), "native A* retains its best reachable result");
    assertFalse(runtimeReached, "ordinary runtime path requires an unblocked exact goal");
    assertEquals(0, runtimePath.getCount());
  }

  @Test void sizeOneStaticQueryUsesSizeRatherThanNativePresencePattern() {
    Map map = map();
    map.zones.first().or(5, 4, DT1.Tile.FLAG_BLOCK_WALK);
    D2ActiveRoom room = new D2ActiveRoom();
    D2DrlgCoords coords = new D2DrlgCoords();
    coords.setNSubtileWidth(24);
    coords.setNSubtileHeight(24);
    room.setCoords(coords);
    D2CommonCollision.allocRoomCollisionGrid(room);
    D2CommonCollision.setMask(room, 5, 4, D2Collision.COLLIDE_WALL);
    int flags = NativeGroundPathProfile.PLAYER.terrainFlags;
    assertFalse(map.isBlockedForPath(4, 4, flags, 1, false));
    assertEquals(0, D2CommonCollision.checkMaskWithSize(room, 4, 4, 1,
        D2Collision.COLLIDE_MASK_PLAYER_PATH));
    assertEquals(D2Collision.COLLIDE_WALL, D2CommonCollision.checkMaskWithPattern(
        room, 4, 4, D2Collision.PATTERN_SMALL_UNIT_PRESENCE, D2Collision.COLLIDE_MASK_PLAYER_PATH));
    assertTrue(map.isBlockedForPath(4, 4, flags, 2, false), "size two already checks a cross");
  }

  private static Map map() {
    Map map = new Map(0, 0);
    Map.Zone zone = new Map.Zone();
    zone.map = map;
    zone.width = zone.height = 24;
    zone.tilesX = zone.tilesY = 5;
    zone.flags = new byte[24 * 24];
    map.zones.add(zone);
    return map;
  }
}
