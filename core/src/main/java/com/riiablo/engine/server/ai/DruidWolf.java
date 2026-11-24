package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;

/** Native DruidWolf control for Spirit Wolf and Dire Wolf. */
public final class DruidWolf extends DruidPet {
  public DruidWolf(int entityId) { super(entityId); }

  @Override public void update(float delta) {
    if (monster == null || !mPosition.has(entityId) || !alive()) return;
    nextThink -= Math.max(0f, delta);
    if (nextThink > 0f) return;
    nextThink = Math.max(0.15f, SLEEP);
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;
    int owner = ownerId();
    if (owner < 0 || !mPosition.has(owner)) { stopMovement(); state = "IDLE_NO_OWNER"; return; }
    boolean spirit = "spiritwolf".equalsIgnoreCase(monster.monstats.Id);
    if (spirit && updateSpiritOwner(owner)) return;
    if (!spirit && mPosition.get(entityId).position.dst(mPosition.get(owner).position) > 28f) {
      state = followSummonOwner(5f) ? "REGROUP" : "IDLE"; return;
    }

    boolean dire = monster.monstats != null && "fenris".equalsIgnoreCase(monster.monstats.Id);
    if (dire && !hasRage()) {
      int corpse = nearestCorpse(10f);
      if (corpse >= 0 && useMonsterSkill(0, corpse, mPosition.get(corpse).position)) {
        state = "EAT_CORPSE"; return;
      }
    }
    float[] distance = { Float.MAX_VALUE };
    int targetRange = spirit ? Math.max(0, params[3]) : 24;
    targetId = findSummonTarget(targetId, distance, targetRange);
    if (spirit && targetId >= 0 && mPosition.has(targetId)
        && unitDistance(entityId, targetId) >= targetRange) targetId = Engine.INVALID_ENTITY;
    if (targetId < 0 || !mPosition.has(targetId)) {
      state = followSummonOwner(5f) ? "FOLLOW" : "IDLE";
      if (state.equals("IDLE")) stopMovement();
      return;
    }
    Vector2 target = mPosition.get(targetId).position;
    if (ordinaryAttack(targetId)) {
      if (spirit) nextThink = Math.max(0, params[0]) * Animation.FRAME_DURATION;
      return;
    }
    if (spirit) {
      // Fn108 does not pursue a remote target away from a stationary owner.
      if (unitDistance(owner, targetId) >= targetRange) {
        state = followSummonOwner(10f) ? "FOLLOW" : "IDLE";
        if (state.equals("IDLE")) stopMovement();
        return;
      }
      int bonus = monster.monstats.Velocity <= 0 ? 100
          : Math.min(100 * monster.monstats.Run / monster.monstats.Velocity - 100, 100);
      state = "APPROACH"; runTo(target, bonus, targetId);
    } else { state = "APPROACH"; walkTo(target, targetId); }
  }

  /** #10397 owner/target distance; unlike melee #10399 it has no small-distance table. */
  private int unitDistance(int source, int target) {
    Vector2 a = mPosition.get(source).position, b = mPosition.get(target).position;
    int size = (mSize.has(source) ? mSize.get(source).size / 2 : 0)
        + (mSize.has(target) ? mSize.get(target).size / 2 : 0);
    int dx = Math.max(0, Math.abs((int) a.x - (int) b.x) - size);
    int dy = Math.max(0, Math.abs((int) a.y - (int) b.y) - size);
    return Math.max(dx, dy) + Math.min(dx, dy) / 2;
  }

  private boolean updateSpiritOwner(int owner) {
    MapWrapper wrapper = mMapWrapper.get(entityId);
    if (wrapper != null && wrapper.zone != null && wrapper.zone.isTown()) {
      state = followSummonOwner(6f) ? "FOLLOW" : "IDLE";
      nextThink = 33 * Animation.FRAME_DURATION;
      if (state.equals("IDLE")) stopMovement();
      return true;
    }
    int distance = unitDistance(entityId, owner);
    // Spirit Wolf's slot zero is Teleport 2; Dire Wolf uses a different slot.
    // Let the normal S1/Actioneer chain execute it, rather than overwriting position here.
    if (distance > 50 && useMonsterSkill(0, Engine.INVALID_ENTITY, mPosition.get(owner).position)) {
      state = "TELEPORT_OWNER";
      nextThink = 10 * Animation.FRAME_DURATION;
      return true;
    }
    CofReference ownerCof = mCofReference.get(owner);
    int mode = ownerCof == null ? Engine.Player.MODE_NU : ownerCof.mode;
    boolean run = distance > params[4]
        || distance > params[2] && mode == Engine.Player.MODE_RN;
    boolean walk = distance > params[2]
        && (mode == Engine.Player.MODE_WL || mode == Engine.Player.MODE_TW);
    if (run || walk) {
      boolean moved = run ? runTo(mPosition.get(owner).position, 100, owner)
          : walkTo(mPosition.get(owner).position, owner);
      if (moved) { state = "REGROUP"; return true; }
    }
    return false;
  }
}
