package com.riiablo.engine.server.ai;

/** Native Fn109 spirit behavior: follow/escape, never ordinary attack. */
public final class Totem extends DruidPet {
  public Totem(int entityId) { super(entityId); }

  @Override public void update(float delta) {
    if (monster == null || !mPosition.has(entityId) || !alive()) return;
    nextThink -= Math.max(0f, delta);
    if (nextThink > 0f) return;
    nextThink = Math.max(0.2f, SLEEP);
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;
    int owner = ownerId();
    if (owner < 0 || !mPosition.has(owner)) { stopMovement(); state = "IDLE_NO_OWNER"; return; }
    float ownerDistance = mPosition.get(entityId).position.dst(mPosition.get(owner).position);
    if (ownerDistance > 18f) { state = followSummonOwner(4f) ? "REGROUP" : "IDLE"; return; }
    float[] distance = { Float.MAX_VALUE };
    int hostile = findNearestTargetWithAidist(distance);
    if (hostile >= 0 && distance[0] < 4f) {
      state = "ESCAPE";
      walkTo(mPosition.get(owner).position, owner);
      return;
    }
    state = followSummonOwner(4f) ? "FOLLOW" : "IDLE";
    if (state.equals("IDLE")) stopMovement();
  }
}
