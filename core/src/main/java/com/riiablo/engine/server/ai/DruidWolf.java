package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;

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
    if (mPosition.get(entityId).position.dst(mPosition.get(owner).position) > 28f) {
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
    targetId = findSummonTarget(targetId, distance, 24f);
    if (targetId < 0 || !mPosition.has(targetId)) {
      state = followSummonOwner(5f) ? "FOLLOW" : "IDLE";
      if (state.equals("IDLE")) stopMovement();
      return;
    }
    Vector2 target = mPosition.get(targetId).position;
    if (ordinaryAttack(targetId)) return;
    state = "APPROACH"; walkTo(target, targetId);
  }
}
