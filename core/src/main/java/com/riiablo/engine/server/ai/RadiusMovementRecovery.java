package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.map.DT1;

/** Host-coordinate adapter for native type-15 partial-progress recovery. */
final class RadiusMovementRecovery {
  private RadiusMovementRecovery() {}

  static boolean recover(NativeDecisionAI ai, Vector2 destination, int budget, int bonus) {
    MapWrapper wrapper = ai.mMapWrapper.get(ai.entityId);
    DynamicUnitCollisionSystem collision = Riiablo.engine.getSystem(DynamicUnitCollisionSystem.class);
    if (wrapper == null || wrapper.map == null || wrapper.zone == null || collision == null) return false;
    Vector2 source = ai.mPosition.get(ai.entityId).position;
    int sx = Math.round(source.x), sy = Math.round(source.y);
    int size = ai.mSize.has(ai.entityId) ? ai.mSize.get(ai.entityId).size : 1;
    Vector2 point = recoveryPoint(sx, sy, (int) destination.x, (int) destination.y,
        budget, (x, y) -> !wrapper.zone.contains(x, y)
            || !wrapper.zone.areRoomsAdjacent(sx, sy, x, y)
            || wrapper.map.isBlockedForPath(wrapper.zone, x, y, DT1.Tile.FLAG_BLOCK_WALK, size, false)
            || !collision.isFreeForPath(ai.entityId, Engine.INVALID_ENTITY, x, y, size));
    return point != null && ai.walkToPoint(point, bonus, Engine.INVALID_ENTITY);
  }

  /** Translate only the local search to uint16 space, not collision queries. */
  static Vector2 recoveryPoint(int sx, int sy, int tx, int ty, int budget,
      com.d2moo.common.path.D2Toward.Collision blocked) {
    int ox = 32768 - sx, oy = 32768 - sy;
    int x = tx + ox, y = ty + oy;
    if (x < 0 || x > 65535 || y < 0 || y > 65535) return null;
    com.d2moo.common.path.D2WallFollow.Result result = com.d2moo.common.path.D2WallFollow.compute(
        new com.d2moo.common.path.D2Path.Point(32768, 32768),
        new com.d2moo.common.path.D2Path.Point(x, y), Math.min(77, Math.max(0, budget)), false,
        (cx, cy) -> blocked.blocked(cx - ox, cy - oy));
    for (int i = result.points().size() - 1; i >= 0; i--) {
      com.d2moo.common.path.D2Path.Point last = result.points().get(i);
      int hx = last.x - ox, hy = last.y - oy;
      if ((hx != sx || hy != sy) && !blocked.blocked(hx, hy)) return new Vector2(hx, hy);
    }
    return null;
  }
}
