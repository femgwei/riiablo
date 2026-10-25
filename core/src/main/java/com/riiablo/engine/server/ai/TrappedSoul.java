package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.NativeUnitFlags;

/** D2MOO AITHINK_Fn099_TrappedSoul (AiThink.cpp, 0x6FCE9980). */
public class TrappedSoul extends NativeDecisionAI {
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeBoolean(awakened);
    AiRuntimeState.writeFiniteDouble(out, frame);
    AiRuntimeState.writeFiniteDouble(out, nextAttackFrame);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    boolean savedAwakened = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    double savedFrame = AiRuntimeState.readFiniteDouble(in);
    double savedNextAttackFrame = AiRuntimeState.readFiniteDouble(in);
    if (apply) {
      awakened = savedAwakened;
      frame = savedFrame;
      nextAttackFrame = savedNextAttackFrame;
    }
  }
  private boolean awakened;
  // Local frame clock pending native global game-frame/event scheduling.
  private double frame;
  private double nextAttackFrame;

  public TrappedSoul(int entityId) { super(entityId); }

  @Override protected void beforeThink(float delta) {
    frame += Math.max(0f, delta) / Animation.FRAME_DURATION;
  }

  @Override protected boolean beforeDecision() {
    mNativeUnitFlags.create(entityId).set(NativeUnitFlags.NO_TREASURE_CLASS);
    return true;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (distance >= 5) {
      inactive(targetId);
      return;
    }
    if (!awakened) {
      awakened = true;
      nextAttackFrame = frame;
      mode(Engine.Monster.MODE_S2, targetId);
      return;
    }
    if (!combat || frame <= nextAttackFrame) {
      mode(Engine.Monster.MODE_S1, targetId);
      return;
    }

    Vector2 self = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    int x = (int) self.x, y = (int) self.y;
    int tx = (int) target.x, ty = (int) target.y;
    if (x >= tx && y <= ty) {
      strike(Engine.Monster.MODE_A1, targetId);
    } else if ((x > tx && y > ty) || y < ty) {
      mode(Engine.Monster.MODE_S1, targetId);
      nextAttackFrame = frame + 5;
    } else {
      strike(Engine.Monster.MODE_A2, targetId);
    }
  }

  @Override protected void thinkWithoutTarget() {
    inactive(Engine.INVALID_ENTITY);
  }

  private void inactive(int targetId) {
    if (awakened) mode(Engine.Monster.MODE_S1, targetId);
    else idle(15);
  }

  private void mode(int mode, int targetId) {
    startMode(mode, targetId);
    returnToSkillOne();
  }

  private void strike(int mode, int targetId) {
    attack(targetId, mode);
    nextAttackFrame = frame + 35;
    returnToSkillOne();
  }

  private void returnToSkillOne() {
    // MonsterMode.cpp sub_6FC63940 / sub_6FC63A30 return all four modes to S1.
    mSequence.get(entityId).mode2 = Engine.Monster.MODE_S1;
  }
}
