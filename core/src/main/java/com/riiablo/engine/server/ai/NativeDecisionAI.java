package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.skill.SkillCodes;

/** Shared action lifecycle for directly ported AITHINK decision functions. */
abstract class NativeDecisionAI extends AI {
  private float nextThink;
  private String state = "IDLE";
  private boolean dead;

  NativeDecisionAI(int entityId) {
    super(entityId);
  }

  @Override
  public final void update(float delta) {
    if (dead || monster == null || !mPosition.has(entityId)) return;
    nextThink -= delta;
    if (mCasting.has(entityId) || mSequence.has(entityId) || nextThink > 0f) return;
    nextThink = Math.max(Animation.FRAME_DURATION, SLEEP);
    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    if (targetId == Engine.INVALID_ENTITY) {
      idle(1);
      return;
    }
    think(targetId, distance[0], isInNativeMeleeRange(targetId));
  }

  protected abstract void think(int targetId, float distance, boolean combat);

  protected final void idle(int frames) {
    stopMovement();
    state = "IDLE";
    nextThink = Math.max(1, frames) * Animation.FRAME_DURATION;
  }

  protected final void approach(int targetId, int velocityBonus) {
    state = "APPROACH";
    if (!walkTo(mPosition.get(targetId).position, velocityBonus, targetId)) idle(10);
  }

  protected final void attack(int targetId) {
    stopMovement();
    lookAt(targetId);
    state = "ATTACK";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(SkillCodes.attack, targetId, mPosition.get(targetId).position);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
    nextThink = 0f;
  }

  protected final boolean hasSkill(int slot) {
    String name = slot == 0 ? monster.monstats.Skill1 : monster.monstats.Skill2;
    return name != null && !name.isEmpty();
  }

  protected final void cast(int slot, int targetId) {
    if (useMonsterSkill(slot, targetId, mPosition.get(targetId).position)) {
      state = "CAST";
      nextThink = 0f;
    } else {
      // Native AITACTICS_UseSkill failure waits ten frames, not a basic attack.
      idle(10);
    }
  }

  @Override
  public void kill() {
    if (dead) return;
    dead = true;
    state = "DEAD";
    stopMovement();
    mCasting.remove(entityId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_death_1", true);
  }

  @Override
  public void onOwnerWarp() {
    super.onOwnerWarp();
    nextThink = 0f;
    if (!dead) state = "IDLE";
  }

  @Override
  public String getState() {
    return state;
  }
}
