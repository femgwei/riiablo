package com.riiablo.engine.server.ai;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.event.DeathEvent;

/** D2MOO AiThink.cpp AITHINK_Fn056_Tentacle (0x6FCDB720). */
public class Tentacle extends TentacleAI {
  private int ownerId = Engine.INVALID_ENTITY;

  public Tentacle(int entityId) {
    super(entityId);
  }

  @Override
  protected boolean beforeDecision() {
    ownerId = monster.minionOwnerId;
    if (ownerId == entityId || ownerId < 0 || !mMonster.has(ownerId)) {
      die(Engine.INVALID_ENTITY);
      return false;
    }
    if (mCorpse.has(ownerId)
        || ownerMode() == Engine.Monster.MODE_DD && rollAiChance(40)) {
      int killer = mTarget.has(ownerId) ? mTarget.get(ownerId).target
          : mCasting.has(ownerId) ? mCasting.get(ownerId).targetId : Engine.INVALID_ENTITY;
      die(killer);
      return false;
    }
    return true;
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    boolean ownerSequence = ownerMode() == Engine.Monster.MODE_SC;
    if (hasSkill(0)) {
      if (phase == 0) {
        submerge(targetId, 8);
        return;
      }
      if (phase == 2 && expired()
          && (distance > params[PARAM_ACTIVE_DISTANCE]
              || !combat && rollAiChance(params[PARAM_SUBMERGE_CHANCE])
              || ownerSequence && rollAiChance(50))) {
        submerge(targetId, 8);
        return;
      }
    }

    if (hasSkill(1) && phase == 1 && expired()
        && (combat || distance < params[PARAM_ACTIVE_DISTANCE]
            || !ownerSequence && rollAiChance(5))) {
      emerge();
      return;
    }
    if (phase != 1 && combat && rollAiChance(params[PARAM_ATTACK_CHANCE])) {
      attack(targetId);
    } else {
      // Do not install neutral mode here: submerged S1 must stay submerged.
      stall();
    }
  }

  private int ownerMode() {
    if (ownerId < 0) return Engine.Monster.MODE_NU;
    if (mSequence.has(ownerId) && mSequence.get(ownerId).started) {
      return mSequence.get(ownerId).mode1;
    }
    if (mCofReference.has(ownerId)) return mCofReference.get(ownerId).mode;
    return Engine.Monster.MODE_NU;
  }

  private void die(int killer) {
    if (mAttributesWrapper.has(entityId)) {
      Attributes attrs = mAttributesWrapper.get(entityId).attrs;
      StatRef hp = attrs == null ? null : attrs.get(Stat.hitpoints);
      if (hp != null) hp.set(0f);
    }
    // Use the authoritative death/corpse/reward boundary, not animation alone.
    if (events != null) events.dispatch(DeathEvent.obtain(killer, entityId));
    kill(); // idempotent fallback for worlds without a corpse-event consumer
  }
}
