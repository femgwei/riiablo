package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.Map;

/** D2MOO SrvSt38: path construction and movement use different collision masks. */
final class WhirlwindCollision {
  static final int START_MASK = 0x0c01; // WALL | OBJECT | DOOR
  static final int MOVE_MASK = 0x1025; // WALL | MISSILE_BARRIER | BLANK | NO_PATH

  static boolean blocks(int nativeFlags, boolean constructing) {
    return (nativeFlags & (constructing ? START_MASK : MOVE_MASK)) != 0;
  }

  private static boolean blocked(Map map, Vector2 point, int size, boolean constructing) {
    int x = Map.round(point.x), y = Map.round(point.y);
    int radius = size >= 2 ? 1 : 0;
    for (int dy = -radius; dy <= radius; dy++) {
      for (int dx = -radius; dx <= radius; dx++) {
        if (size == 2 && dx != 0 && dy != 0) continue;
        Map.Zone zone = map.getZone(x + dx, y + dy);
        if (zone == null) return true; // native invalid cell: COLLIDE_BLANK
        int flags = zone.nativeCollisionFlagsWorld(x + dx, y + dy);
        if (blocks(flags, constructing)) return true;
      }
    }
    return false;
  }

  /** Matches the existing floating-world ray sampling; returns the last clear point. */
  static boolean cast(Map map, Vector2 start, Vector2 end, int size,
      boolean constructing, Vector2 lastClear) {
    return cast(map,start,end,size,constructing,lastClear,-1,null);
  }

  static boolean cast(Map map, Vector2 start, Vector2 end, int size,
      boolean constructing, Vector2 lastClear, int sourceId, DynamicUnitCollisionSystem units) {
    lastClear.set(start);
    if (map == null || map.getZone(start) == null) { lastClear.set(end); return false; }
    float distance = start.dst(end);
    int samples = Math.max(1, (int) Math.ceil(distance * 5f));
    if (units != null) units.refreshNow();
    Vector2 point = new Vector2();
    for (int i = 0; i <= samples; i++) {
      point.set(start).lerp(end, (float) i / samples);
      boolean blocked = units != null && sourceId >= 0 && units.hasNativePathCollision(sourceId)
          ? units.nativePathBlocked(sourceId,Map.round(point.x),Map.round(point.y),size,
              constructing ? START_MASK : MOVE_MASK)
          : blocked(map, point, size, constructing);
      if (blocked) return true;
      lastClear.set(point);
    }
    return false;
  }
}
