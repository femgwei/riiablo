package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;

/** D2MOO AiThink.cpp AITHINK_Fn093_ArcaneTower (0x6FCDFA50). */
public class ArcaneTower extends NativeDecisionAI {
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(phase);
    out.writeInt(remaining);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, nextAction);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPhase = in.readInt();
    int savedRemaining = in.readInt();
    float savedNextAction = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    if (apply) {
      phase = savedPhase;
      remaining = savedRemaining;
      nextAction = savedNextAction;
    }
  }
  static final int PARAM_NUM_SKILL_1 = 0;
  static final int PARAM_SKILL_1_STALL = 1;
  static final int PARAM_SKILL_1_LONG_DELAY = 2;
  static final int PARAM_NUM_SKILL_2 = 3;
  static final int PARAM_SKILL_2_STALL = 4;
  static final int PARAM_SKILL_2_LONG_DELAY = 5;

  private int phase;
  private int remaining;
  private float nextAction;

  public ArcaneTower(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    nextAction = Math.max(0f, nextAction - Math.max(0f, delta));
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (remaining == 0) {
      remaining = phase == 0 ? params[PARAM_NUM_SKILL_1] : params[PARAM_NUM_SKILL_2];
    }
    if (nextAction > 0f) {
      idle(10);
      return;
    }

    if (phase == 0 && hasSkill(0)) {
      cast(0, targetId);
      --remaining;
      nextAction = framesToSeconds(remaining > 0
          ? params[PARAM_SKILL_1_STALL] : params[PARAM_SKILL_1_LONG_DELAY]);
      if (remaining <= 0) {
        phase = 1;
        remaining = params[PARAM_NUM_SKILL_2];
      }
      return;
    }

    attack(targetId);
    --remaining;
    nextAction = framesToSeconds(remaining > 0
        ? params[PARAM_SKILL_2_LONG_DELAY] : params[PARAM_SKILL_2_STALL]);
    if (remaining <= 0) {
      phase = 0;
      remaining = params[PARAM_NUM_SKILL_1];
    }
  }

  private static float framesToSeconds(int frames) {
    return Math.max(0, frames) * Animation.FRAME_DURATION;
  }
}
