package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** D2Game AITHINK_Fn023_Vulture (0x6FCD4DD0): ground/air movement cycle. */
public class Vulture extends NativeDecisionAI {
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_STALL_DURATION = 1;
  static final int PARAM_WOUNDED_PCT = 2;
  static final int PARAM_CIRCLE_CHANCE = 3;
  static final int PARAM_MOVE_CHANCE = 4;
  int aiParam0, aiParam1, aiParam2;
  private byte groundIdle, groundWalk, groundRun;
  private boolean savedModes;
  private String landingStatus = "NOT_REQUESTED";
  private long lastTraceFrame = Long.MIN_VALUE / 2;
  private static final com.riiablo.logger.Logger log =
      com.riiablo.logger.LogManager.getLogger(Vulture.class);

  public Vulture(int entityId) { super(entityId); }

  private void airborne(boolean flying) {
    boolean wasFlying = monster.vultureAirborne;
    monster.vultureAirborne = flying;
    if (flying && !wasFlying) {
      DynamicUnitCollisionSystem collision = Riiablo.engine.getSystem(DynamicUnitCollisionSystem.class);
      if (collision != null) collision.removeMonsterSpawnPresence(entityId);
    }
    MovementModes modes = Riiablo.engine.getMapper(MovementModes.class).get(entityId);
    if (modes != null) {
      if (!savedModes) {
        groundIdle = modes.NU; groundWalk = modes.WL; groundRun = modes.RN;
        savedModes = true;
      }
      modes.set(flying ? Engine.Monster.MODE_S1 : groundIdle,
          flying ? Engine.Monster.MODE_S1 : groundWalk,
          flying ? Engine.Monster.MODE_S1 : groundRun);
    }
    NativeUnitFlags flags = Riiablo.engine.getMapper(NativeUnitFlags.class).get(entityId);
    if (flags != null) {
      if (flying) flags.clear(NativeUnitFlags.MONSTER_TARGET);
      else flags.set(NativeUnitFlags.MONSTER_TARGET);
    }
  }

  private boolean land(int targetId) {
    Vector2 p = mPosition.get(entityId).position;
    MapWrapper map = mMapWrapper.get(entityId);
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    DynamicUnitCollisionSystem collision = Riiablo.engine.getSystem(DynamicUnitCollisionSystem.class);
    // sub_6FCBDFE0 calls COLLISION_GetFreeCoordinates (limit 50), rather
    // than requiring the exact airborne cell to be walkable. Keep the search
    // inside the input RoomEx, as bAllowNeighborRooms is false for landing.
    Map.RoomEx room = map == null || map.zone == null ? null : map.zone.findRoomEx(p.x, p.y);
    int originX = (int) p.x, originY = (int) p.y;
    Vector2 free = null;
    search:
    for (int radius = 0; radius < 50; radius++) {
      for (int y = -radius; y <= radius; y++) {
        for (int x = -radius; x <= radius; x++) {
          if (Math.max(Math.abs(x), Math.abs(y)) != radius) continue;
          int cx = originX + x, cy = originY + y;
          if (map != null && map.zone != null) {
            if (!map.zone.contains(cx, cy)) continue;
            if (room != null && map.zone.findRoomEx(cx, cy) != room) continue;
          }
          if (map != null && map.map != null && map.map.isBlockedForPath(map.zone,
              cx, cy, DT1.Tile.FLAG_BLOCK_WALK, size, false)) continue;
          if (collision != null && !collision.isFreeForLanding(entityId, cx, cy, size)) continue;
          free = new Vector2(cx, cy);
          break search;
        }
      }
    }
    if (free == null) { landingStatus = "NO_FREE_GROUND"; return false; }
    Vector2 previous = p.cpy();
    stopMovement();
    p.set(free);
    airborne(false);
    if (collision != null && !collision.tryLand(entityId, (int) p.x, (int) p.y, size)) {
      p.set(previous);
      airborne(true);
      landingStatus = "RESERVATION_REJECTED";
      return false;
    }
    if (targetId != Engine.INVALID_ENTITY) walkInRadius(targetId, 2, 3, 0);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_S2, Engine.Monster.MODE_NU);
    aiParam0 = -1;
    aiParam1 = aiParam2 = 0;
    landingStatus = "S2_STARTED";
    delayDecision(12);
    return true;
  }

  private boolean differentRoom(int targetId) {
    MapWrapper own = mMapWrapper.get(entityId);
    MapWrapper other = mMapWrapper.get(targetId);
    if (own == null || own.zone == null || !own.zone.hasNativeRoomTopology()) return false;
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    return other != null && other.zone != null && other.zone != own.zone
        || own.zone.findRoomEx(source.x, source.y) != own.zone.findRoomEx(target.x, target.y);
  }

  @Override protected boolean recoverRadiusMovement(Vector2 destination, int budget, int bonus) {
    if (monster.vultureAirborne) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    DynamicUnitCollisionSystem collision = Riiablo.engine.getSystem(DynamicUnitCollisionSystem.class);
    if (wrapper == null || wrapper.map == null || wrapper.zone == null || collision == null) return false;
    Vector2 source = mPosition.get(entityId).position;
    int sx = Math.round(source.x), sy = Math.round(source.y);
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    Vector2 point = radiusRecoveryPoint(sx, sy, (int) destination.x, (int) destination.y,
        budget, (x, y) -> !wrapper.zone.contains(x, y)
            || !wrapper.zone.areRoomsAdjacent(sx, sy, x, y)
            || wrapper.map.isBlockedForPath(wrapper.zone, x, y, DT1.Tile.FLAG_BLOCK_WALK, size, false)
            || !collision.isFreeForPath(entityId, Engine.INVALID_ENTITY, x, y, size));
    return point != null && walkToPoint(point, bonus, Engine.INVALID_ENTITY);
  }

  /** Native type-15 wall-follow partial progress for a rejected coordinate order.
   * Translate only this local search to uint16 space; collision queries stay in host space. */
  static Vector2 radiusRecoveryPoint(int sx, int sy, int tx, int ty, int budget,
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

  private boolean resolveFlightWaypoint() {
    MapWrapper own = mMapWrapper.get(entityId);
    if (own == null || own.zone == null) return true;
    Vector2 desired = new Vector2(aiParam1, aiParam2), free = new Vector2();
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    if (!own.zone.findFreeCoordinates(desired, size, 49, 0xFF, true, free)) return false;
    Vector2 source = mPosition.get(entityId).position;
    if (own.zone.hasNativeRoomTopology()
        && !own.zone.areRoomsAdjacent(source.x, source.y, free.x, free.y)) return false;
    aiParam1 = (int) free.x;
    aiParam2 = (int) free.y;
    return true;
  }

  private static int coordinateDistance(int x, int y) {
    return (Math.min(Math.abs(x), Math.abs(y)) + 2 * Math.max(Math.abs(x), Math.abs(y))) / 2;
  }

  private boolean woundedNearby() {
    com.artemis.utils.IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        com.artemis.Aspect.all(Position.class, AttributesWrapper.class)
            .one(Player.class, Monster.class)).getEntities();
    Vector2 source = mPosition.get(entityId).position;
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (id == entityId || nativeLifePercent(id) <= 0) continue;
      Vector2 p = mPosition.get(id).position;
      int x = (int) source.x - (int) p.x, y = (int) source.y - (int) p.y;
      if (x * x + y * y <= 121 && nativeLifePercent(id) <= params[PARAM_WOUNDED_PCT]) return true;
    }
    return false;
  }

  /** Read-only once-per-second sample around the reported approach/hover boundary. */
  private void traceApproach(int targetId, float distance, boolean combat) {
    if ((!monster.vultureAirborne && distance > 8f) || nativeFrame() - lastTraceFrame < 25) return;
    lastTraceFrame = nativeFrame();
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = targetId == Engine.INVALID_ENTITY ? null : mPosition.get(targetId).position;
    log.info("[VULTURE_APPROACH] frame={} entity={} target={} state={} pos={} targetPos={} "
            + "distance={} combat={} flight={} waypoint=({}, {}) landing={} path={} sequence={} casting={}",
        nativeFrame(), entityId, targetId, getState(), source, target, distance, combat,
        aiParam0, aiParam1, aiParam2, landingStatus, mPathfind.has(entityId),
        mSequence.has(entityId), mCasting.has(entityId));
  }

  @Override protected void thinkWithoutTarget() {
    traceApproach(Engine.INVALID_ENTITY, Float.MAX_VALUE, false);
    if (aiParam0 > 0) {
      if (!land(Engine.INVALID_ENTITY)) { aiParam0 = 8; delayDecision(12); }
    } else idle(12);
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    traceApproach(targetId, distance, combat);
    Vector2 s = mPosition.get(entityId).position;
    Vector2 t = mPosition.get(targetId).position;
    int dx = (int) s.x - (int) t.x, dy = (int) s.y - (int) t.y;
    if (dx * dx + dy * dy > 144 && differentRoom(targetId)) {
      if (aiParam0 < 1) { walkInRadius(targetId, 9, 0, 0); delayDecision(12); return; }
      if (land(Engine.INVALID_ENTITY)) return;
    }
    if (aiParam0 == 0 && monster.minionOwnerId < 0 && dx * dx + dy * dy > 144
        && rollAiPercentage() < 60) {
      airborne(true);
      aiParam0 = (int) (rollAiRandomNumber() & 7) + 24;
      walkInRadius(targetId, 8, 8, 0);
      delayDecision(12);
      return;
    }
    if (aiParam0 > 0) {
      if (aiParam0 == 1 || woundedNearby()
          || (distance < 6 && rollAiPercentage() < 15)) {
        if (land(targetId)) return;
        aiParam0 = 8;
        delayDecision(12);
        return;
      }
      airborne(true);
      if (aiParam1 == 0 || aiParam2 == 0
          || coordinateDistance((int) s.x - aiParam1, (int) s.y - aiParam2) <= 1) {
        int radius = aiParam0 + 8;
        aiParam1 = (int) t.x + (int) (rollAiRandomNumber() % (2 * radius)) - radius;
        aiParam2 = (int) t.y + (int) (rollAiRandomNumber() % (2 * radius)) - radius;
        int minimum = Math.max(12, Math.min(36, 2 * radius));
        while (coordinateDistance(aiParam1 - (int) s.x, aiParam2 - (int) s.y) < minimum) {
          aiParam1 += Integer.compare(aiParam1, (int) s.x);
          aiParam2 += Integer.compare(aiParam2, (int) s.y);
          if (aiParam1 == (int) s.x && aiParam2 == (int) s.y) { aiParam1++; aiParam2++; }
        }
      }
      if (!resolveFlightWaypoint()) {
        // A rejected air coordinate must not leave an unchanging path over
        // void. Retry landing in the current room before choosing another.
        aiParam1 = aiParam2 = 0;
        if (land(Engine.INVALID_ENTITY)) return;
        delayDecision(12);
        return;
      }
      walkToPoint(new Vector2(aiParam1, aiParam2), 0, Engine.INVALID_ENTITY);
      aiParam0--;
      delayDecision(12);
      return;
    }
    if (!combat) {
      if (aiParam0 != -1 && rollAiPercentage() >= params[PARAM_MOVE_CHANCE]) { idle(params[PARAM_STALL_DURATION]); return; }
      if (rollAiPercentage() < params[PARAM_CIRCLE_CHANCE]) circle(targetId, 6);
      else walkInRadius(targetId, 9, 0, 0);
      aiParam0 = 0;
      delayDecision(12);
    } else if (rollAiPercentage() < params[PARAM_ATTACK_CHANCE]) attack(targetId);
    else idle(params[PARAM_STALL_DURATION]);
  }

  @Override public String getDecisionDiagnostics() {
    return super.getDecisionDiagnostics() + " flight=" + aiParam0
        + " waypoint=(" + aiParam1 + "," + aiParam2 + ") landing=" + landingStatus;
  }

  @Override public String getState() {
    return monster != null && monster.vultureAirborne ? "FLYING" : super.getState();
  }

  @Override public void kill() { airborne(false); super.kill(); }
}
