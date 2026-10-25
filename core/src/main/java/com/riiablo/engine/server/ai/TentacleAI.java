package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Sequence;

/** Shared D2MOO Fn056/Fn057 submerge/emerge timers; durations are in seconds. */
abstract class TentacleAI extends NativeDecisionAI {
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_SUBMERGE_CHANCE = 1;
  static final int PARAM_SUBMERGE_SECONDS = 2;
  static final int PARAM_EMERGE_SECONDS = 3;
  static final int PARAM_STALL_DURATION = 4;
  static final int PARAM_ACTIVE_DISTANCE = 5;

  protected int phase;
  private double frame;
  private double changeAfter;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(phase);
    AiRuntimeState.writeFiniteDouble(out, frame);
    AiRuntimeState.writeFiniteDouble(out, changeAfter);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPhase = in.readInt();
    double savedFrame = AiRuntimeState.readFiniteDouble(in);
    double savedChangeAfter = AiRuntimeState.readFiniteDouble(in);
    if (apply) {
      phase = savedPhase;
      frame = savedFrame;
      changeAfter = savedChangeAfter;
    }
  }
  TentacleAI(int entityId) {
    super(entityId);
  }

  @Override
  protected void beforeThink(float delta) {
    frame += Math.max(0f, delta) / Animation.FRAME_DURATION;
  }

  @Override
  protected void thinkWithoutTarget() {
    // These stationary units must still submerge/expire when prey disappears.
    think(Engine.INVALID_ENTITY, Float.MAX_VALUE, false);
  }

  protected final boolean expired() {
    return frame > changeAfter;
  }

  @Override
  protected boolean actionBlocksDecision() {
    Sequence sequence = mSequence.get(entityId);
    if (sequence != null && sequence.nativeSubmerge && sequence.nativeSubmergeHeld) return false;
    return super.actionBlocksDecision();
  }

  protected final void submerge(int targetId, int delay) {
    Vector2 target = targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)
        ? mPosition.get(targetId).position : mPosition.get(entityId).position;
    if (castAt(0, targetId, target, Engine.Monster.MODE_S1)) {
      Sequence sequence = mSequence.get(entityId);
      // 1.10f MonSeq: S1 12..0 (tentacle), S1 20..0 (head).
      sequence.nativeSubmerge = true;
      sequence.nativeSubmergeLastFrame = "TentacleHead".equals(monster.monstats.AI) ? 20 : 12;
    }
    // Native schedules this delay and updates phase even if UseSkill fails.
    delayDecision(delay);
    phase = 1;
    changeAfter = frame + 25d * Math.max(0, params[PARAM_SUBMERGE_SECONDS]);
  }

  protected final void emerge() {
    cast(1, entityId); // native emergence always targets the unit itself
    phase = 2;
    changeAfter = frame + 25d * Math.max(0, params[PARAM_EMERGE_SECONDS]);
  }

  protected final void stall() {
    idle(params[PARAM_STALL_DURATION]);
  }
}
