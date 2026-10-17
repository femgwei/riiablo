package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.event.DeathEvent;

/** Fn117 countdown and MonsterUnique's delayed suicide modifier callback. */
public class SuicideMinion extends NativeDecisionAI {
  private long deadline;
  private long explosionFrame = -1;
  private boolean detonated;
  private boolean hitRequested;

  public SuicideMinion(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (deadline != 0) {
      if (nativeFrame() > deadline) { die(); return; }
      idle(params[1]);
      return;
    }
    if (combat) { deadline = nativeFrame() + params[4]; idle(params[1]); return; }
    if (rollAiChance(params[2])) approach(targetId, 0);
    else idle(params[1]);
  }

  @Override protected void thinkWithoutTarget() {
    if (deadline != 0 && nativeFrame() > deadline) die();
    else idle(params[1]);
  }

  @Override public void onNativeHit(int attackerId) { hitRequested = true; }

  @Override protected void beforeThink(float delta) {
    if (hitRequested) {
      hitRequested = false;
      com.riiablo.engine.server.component.NativeAiState memory = Riiablo.engine.getMapper(
          com.riiablo.engine.server.component.NativeAiState.class).get(entityId);
      if (mCofReference.has(entityId) && mCofReference.get(entityId).mode == 3
          || memory != null && memory.previousMode == 3) die();
    }
  }

  private void die() {
    if (explosionFrame >= 0) return;
    if (mAttributesWrapper.has(entityId)) {
      StatRef hp = mAttributesWrapper.get(entityId).attrs.get(Stat.hitpoints);
      if (hp != null) hp.set(0);
    }
    if (events != null) events.dispatch(DeathEvent.obtain(entityId, entityId));
    kill();
  }

  @Override public void kill() {
    if (explosionFrame < 0) explosionFrame = nativeFrame() + 4;
    super.kill();
  }

  @Override protected void thinkWhileDead(float delta) {
    if (detonated || explosionFrame < 0 || nativeFrame() < explosionFrame) return;
    detonated = true;
    ServerSkillSystem skills = Riiablo.engine.getSystem(ServerSkillSystem.class);
    if (skills != null) skills.resolveSuicideExplosion(entityId, params[0], params[3],
        rollAiRandomNumber());
  }
}
