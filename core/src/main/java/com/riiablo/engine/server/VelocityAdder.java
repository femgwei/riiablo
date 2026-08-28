package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

@All({Position.class, Velocity.class})
public class VelocityAdder extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(VelocityAdder.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Size> mSize;

  @Wire(name = "map", failOnNull = false)
  protected Map map;

  private final Vector2 desired = new Vector2();
  private final Vector2 candidate = new Vector2();
  private final Vector2 start = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());

  @Override
  protected void process(int entityId) {
    // MissileCollisionSystem performs swept movement and collision resolution
    // in one place. Moving missiles here as well would advance them twice.
    if (mMissile.has(entityId)) return;
    Velocity velocity = mVelocity.get(entityId);
    if (velocity.stateMovementLocked) return;

    Position position = mPosition.get(entityId);
    start.set(position.position);
    float scale = world.delta * velocity.stateSpeedMultiplier;
    desired.set(position.position).mulAdd(velocity.velocity, scale);
    if (canMove(entityId, position.position, desired)) {
      position.position.set(desired);
      return;
    }

    // D2Common's unit collision resolver attempts axis-preserving movement
    // after a diagonal move is blocked. This lets units slide along walls.
    boolean moved = false;
    float dx = desired.x - position.position.x;
    float dy = desired.y - position.position.y;
    if (Math.abs(dx) >= Math.abs(dy)) {
      candidate.set(position.position.x + dx, position.position.y);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
      candidate.set(position.position.x, position.position.y + dy);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
    } else {
      candidate.set(position.position.x, position.position.y + dy);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
      candidate.set(position.position.x + dx, position.position.y);
      if (canMove(entityId, position.position, candidate)) {
        position.position.set(candidate);
        moved = true;
      }
    }
    if (!moved) velocity.velocity.setZero();
    log.debug("[MOVEMENT_COLLISION] entity={} from=({}, {}) desired=({}, {}) result=({}, {}) slid={} blocked={}",
        entityId, start.x, start.y, desired.x, desired.y,
        position.position.x, position.position.y, moved, !moved);
  }

  private boolean canMove(int entityId, Vector2 from, Vector2 to) {
    if (from.epsilonEquals(to, 0.0001f)) return true;
    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (wrapper != null && wrapper.zone != null && wrapper.zone.hasNativeRoomTopology()) {
      Map.RoomEx source = wrapper.zone.findRoomEx(from.x, from.y);
      Map.RoomEx target = wrapper.zone.findRoomEx(to.x, to.y);
      // RoomEx rectangles may leave a one-subtile seam. Let the collision
      // grid decide seam traversal, but never permit a direct jump between
      // two non-adjacent exported rooms.
      if (source != null && target != null
          && !wrapper.zone.areRoomsAdjacent(from.x, from.y, to.x, to.y)) {
        log.debug("[ROOM_MOVEMENT_BOUNDARY] entity={} fromRoom={} toRoom={} action=reject",
            entityId, source.id, target.id);
        return false;
      }
    }
    // Some headless/unit worlds intentionally omit generated zones. Preserve
    // the legacy velocity behaviour there; generated maps use the authoritative
    // collision grid below.
    if (map == null || map.getZone(from) == null) return true;
    ray.set(from, to);
    int size = mSize.has(entityId) ? mSize.get(entityId).size : Size.INSIGNIFICANT;
    return !map.castRay(ray, DT1.Tile.FLAG_BLOCK_WALK, size, collision);
  }
}
