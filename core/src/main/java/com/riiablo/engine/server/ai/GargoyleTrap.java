package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Sequence;

/** D2MOO AITHINK_Fn063_GargoyleTrap (0x6FCDBCE0). */
public class GargoyleTrap extends AI {
  static final int PARAM_SHOOT_DISTANCE = 0;
  static final int PARAM_SHOOT_CHANCE = 1;
  static final int PARAM_FIRE_RECOVERY = 2;
  static final int PARAM_STALL_DURATION = 3;

  private float nextThink;
  private String state = "IDLE";

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    AiRuntimeState.writeTimer(out, nextThink);
    com.riiablo.engine.replay.CheckpointIO.writeString(out, state);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    float savedNextThink = AiRuntimeState.readTimer(in);
    String savedState = com.riiablo.engine.replay.CheckpointIO.readString(in);
    if (apply) {
      nextThink = savedNextThink;
      state = savedState;
    }
  }

  public GargoyleTrap(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId)) return;
    nextThink -= delta;
    if (nextThink > 0f || mCasting.has(entityId) || mSequence.has(entityId)) return;
    nextThink = Math.max(Animation.FRAME_DURATION, SLEEP);

    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    if (targetId == Engine.INVALID_ENTITY) {
      idle(params[PARAM_STALL_DURATION]);
      return;
    }
    mTarget.create(entityId).target = targetId;
    lookAt(targetId);
    float dx = Math.abs(mPosition.get(targetId).position.x - mPosition.get(entityId).position.x);
    float dy = Math.abs(mPosition.get(targetId).position.y - mPosition.get(entityId).position.y);
    if ((dx < 6f || dy < 6f) && hasSkill()
        && distance[0] < params[PARAM_SHOOT_DISTANCE]
        && rollAiChance(params[PARAM_SHOOT_CHANCE])) {
      if (useMonsterSkill(0, targetId, mPosition.get(targetId).position)) {
        state = "CAST";
        nextThink = Math.max(1, params[PARAM_FIRE_RECOVERY]) * Animation.FRAME_DURATION;
        return;
      }
    }
    idle(params[PARAM_STALL_DURATION]);
  }

  private boolean hasSkill() {
    return monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty();
  }

  private void idle(int frames) {
    stopMovement();
    state = "IDLE";
    nextThink = Math.max(1, frames) * Animation.FRAME_DURATION;
  }

  @Override
  public void kill() {
    stopMovement();
    mCasting.remove(entityId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    state = "DEAD";
  }

  @Override
  public String getState() {
    return state;
  }
}
