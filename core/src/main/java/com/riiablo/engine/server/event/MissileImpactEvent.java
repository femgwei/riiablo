package com.riiablo.engine.server.event;

import com.artemis.annotations.EntityId;
import com.badlogic.gdx.math.Vector2;
import net.mostlyoriginal.api.event.common.Event;

/**
 * Presentation-only notification emitted once an authoritative missile has
 * reached a native impact point.  Damage and server-side sub-missiles remain
 * separate concerns; clients use the source missile row to resolve
 * HitSound/CltHitSubMissile without inventing a second combat hit.
 */
public final class MissileImpactEvent implements Event {
  @EntityId public int missileEntityId;
  @EntityId public int targetEntityId;
  public int missileId;
  public int ownerId;
  public float x;
  public float y;
  /** Impact-facing direction copied before the source missile is deleted. */
  public float dx = 1f;
  public float dy;

  public static MissileImpactEvent obtain(int missileEntityId, int missileId,
      int ownerId, int targetEntityId, Vector2 position) {
    return obtain(missileEntityId, missileId, ownerId, targetEntityId, position,
        new Vector2(1f, 0f));
  }

  public static MissileImpactEvent obtain(int missileEntityId, int missileId,
      int ownerId, int targetEntityId, Vector2 position, Vector2 direction) {
    MissileImpactEvent event = new MissileImpactEvent();
    event.missileEntityId = missileEntityId;
    event.missileId = missileId;
    event.ownerId = ownerId;
    event.targetEntityId = targetEntityId;
    event.x = position == null ? 0f : position.x;
    event.y = position == null ? 0f : position.y;
    if (direction != null && !direction.isZero(0.0001f)) {
      float length = direction.len();
      event.dx = direction.x / length;
      event.dy = direction.y / length;
    }
    return event;
  }
}
