package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** Native Raven/Fn107 pet behavior with a finite peck budget. */
public final class Raven extends DruidPet {
  private int pecks = -1;
  public Raven(int entityId) { super(entityId); }

  @Override public void initialize() {
    super.initialize();
    int level = monster != null ? Math.max(1, resolveMonsterSkillLevel(0)) : 1;
    pecks = 12 + level - 1;
  }

  @Override public void update(float delta) {
    if (monster == null || !mPosition.has(entityId) || !alive()) return;
    if (pecks <= 0) { state = "EXHAUSTED"; stopMovement(); return; }
    nextThink -= Math.max(0f, delta);
    if (nextThink > 0f) return;
    nextThink = Math.max(0.2f, SLEEP);
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;
    if (ownerId() < 0 || !mPosition.has(ownerId())) { stopMovement(); state = "IDLE_NO_OWNER"; return; }
    if (mPosition.get(entityId).position.dst(mPosition.get(ownerId()).position) > 35f) {
      state = followSummonOwner(5f) ? "REGROUP" : "IDLE"; return;
    }
    float[] distance = { Float.MAX_VALUE };
    targetId = findSummonTarget(targetId, distance, 24f);
    if (targetId < 0 || !mPosition.has(targetId)) {
      state = followSummonOwner(5f) ? "FOLLOW" : "IDLE";
      if (state.equals("IDLE")) stopMovement();
      return;
    }
    if (ordinaryAttack(targetId)) { pecks--; return; }
    state = "APPROACH"; walkTo(mPosition.get(targetId).position, targetId);
  }
}
