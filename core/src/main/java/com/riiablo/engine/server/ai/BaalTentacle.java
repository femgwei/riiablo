package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn139_BaalTentacle (0x6FCF02D0). */
public class BaalTentacle extends NativeDecisionAI {
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_STALL_DURATION = 1;
  static final int PARAM_STAY_ALIVE_RANDOM_RANGE_SECONDS = 2;

  private int ownerId = Engine.INVALID_ENTITY;
  private float frame;
  private float expireFrame = Float.NaN;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(ownerId);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, frame);
    // NaN is the uninitialized lifetime sentinel; finite clocks retain the shared strict codec.
    out.writeBoolean(!Float.isNaN(expireFrame));
    if (!Float.isNaN(expireFrame)) com.riiablo.engine.replay.CheckpointIO.writeFloat(out, expireFrame);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedOwnerId = in.readInt();
    float savedFrame = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    float savedExpireFrame = com.riiablo.engine.replay.CheckpointIO.readBoolean(in)
        ? com.riiablo.engine.replay.CheckpointIO.readFloat(in) : Float.NaN;
    if (apply) {
      ownerId = savedOwnerId;
      frame = savedFrame;
      expireFrame = savedExpireFrame;
    }
  }
  public BaalTentacle(int entityId) {
    super(entityId);
  }

  @Override
  protected boolean beforeDecision() {
    ownerId = monster.minionOwnerId;
    if (ownerId == entityId || ownerId < 0 || !mMonster.has(ownerId)
        || mCorpse.has(ownerId)) {
      kill();
      return false;
    }
    return true;
  }

  @Override
  protected void beforeThink(float delta) {
    if (Float.isNaN(expireFrame)) {
      int range = Math.max(0, params[PARAM_STAY_ALIVE_RANDOM_RANGE_SECONDS]);
      long random = range == 0 ? 0 : rollAiRandomNumber() % range;
      expireFrame = frame + 25f * (random + range);
    }
    frame += Math.max(0f, delta) / Animation.FRAME_DURATION;
    if (frame > expireFrame) kill();
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat && rollAiChance(params[PARAM_ATTACK_CHANCE])) {
      attack(targetId);
    } else {
      idle(Math.max(1, params[PARAM_STALL_DURATION]));
    }
  }
}
