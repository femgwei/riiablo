package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.DT1;

/** D2Game AITHINK_Fn023_Vulture (0x6FCD4DD0): ground/air movement cycle. */
public class Vulture extends NativeDecisionAI {
  int aiParam0, aiParam1, aiParam2;
  private byte groundIdle, groundWalk, groundRun;
  private boolean savedModes;

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
    if (map != null && map.map != null && map.map.isBlockedForPath(map.zone,
        (int) p.x, (int) p.y, DT1.Tile.FLAG_BLOCK_WALK, size, false)) return false;
    if (collision != null && !collision.isFreeForLanding(entityId, (int) p.x, (int) p.y, size)) return false;
    airborne(false);
    if (collision != null) collision.tryLand(entityId, (int) p.x, (int) p.y, size);
    if (targetId != Engine.INVALID_ENTITY) walkInRadius(targetId, 2, 3, 0);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_S2, Engine.Monster.MODE_NU);
    aiParam0 = -1;
    delayDecision(12);
    return true;
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
      if (x * x + y * y <= 121 && nativeLifePercent(id) <= params[2]) return true;
    }
    return false;
  }

  @Override protected void thinkWithoutTarget() {
    if (aiParam0 > 0) {
      if (!land(Engine.INVALID_ENTITY)) { aiParam0 = 8; delayDecision(12); }
    } else idle(12);
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    Vector2 s = mPosition.get(entityId).position;
    Vector2 t = mPosition.get(targetId).position;
    int dx = (int) s.x - (int) t.x, dy = (int) s.y - (int) t.y;
    if (aiParam0 == 0 && monster.minionOwnerId < 0 && dx * dx + dy * dy > 144
        && rollAiPercentage() < 60) {
      airborne(true);
      aiParam0 = (int) (rollAiRandomNumber() & 7) + 24;
      walkInRadius(targetId, 8, 8, 0);
      delayDecision(12);
      return;
    }
    if (aiParam0 > 0) {
      if (aiParam0 == 1 || (distance < 6 && rollAiPercentage() < 15)
          || woundedNearby()) {
        if (land(targetId)) return;
        aiParam0 = 8;
        delayDecision(12);
        return;
      }
      airborne(true);
      if (aiParam1 == 0 || aiParam2 == 0
          || Math.max(Math.abs((int) s.x - aiParam1), Math.abs((int) s.y - aiParam2)) <= 1) {
        int radius = aiParam0 + 8;
        aiParam1 = (int) t.x + (int) (rollAiRandomNumber() % (2 * radius)) - radius;
        aiParam2 = (int) t.y + (int) (rollAiRandomNumber() % (2 * radius)) - radius;
        int minimum = Math.max(12, Math.min(36, 2 * radius));
        while (Math.max(Math.abs(aiParam1 - (int) s.x), Math.abs(aiParam2 - (int) s.y)) < minimum) {
          aiParam1 += aiParam1 >= (int) s.x ? 1 : -1;
          aiParam2 += aiParam2 >= (int) s.y ? 1 : -1;
        }
      }
      walkToPoint(new Vector2(aiParam1, aiParam2), 0, Engine.INVALID_ENTITY);
      aiParam0--;
      delayDecision(12);
      return;
    }
    if (!combat) {
      if (aiParam0 != -1 && rollAiPercentage() >= params[4]) { idle(params[1]); return; }
      if (rollAiPercentage() < params[3]) circle(targetId, 6);
      else walkInRadius(targetId, 9, 0, 0);
      aiParam0 = 0;
      delayDecision(12);
    } else if (rollAiPercentage() < params[0]) attack(targetId);
    else idle(params[1]);
  }

  @Override public String getState() {
    return monster != null && monster.vultureAirborne ? "FLYING" : super.getState();
  }

  @Override public void kill() { airborne(false); super.kill(); }
}
