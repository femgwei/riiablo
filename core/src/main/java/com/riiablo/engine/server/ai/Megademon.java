package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;

/** D2MOO AITHINK_Fn089_Megademon (AiThink.cpp, 0x6FCDF780). */
public class Megademon extends NativeDecisionAI {
  // Local elapsed frames, pending the shared native game-frame clock.
  private float frame;
  private float infernoAfter;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, frame);
    com.riiablo.engine.replay.CheckpointIO.writeFloat(out, infernoAfter);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    float savedFrame = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    float savedInfernoAfter = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    if (apply) {
      frame = savedFrame;
      infernoAfter = savedInfernoAfter;
    }
  }

  public Megademon(int entityId) { super(entityId); }

  @Override protected void beforeThink(float delta) {
    frame += Math.max(0f, delta) / Animation.FRAME_DURATION;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    boolean skill = hasSkill(0);
    UnitStates states = mUnitStates.get(entityId);
    boolean inferno = states != null && states.stateList != null
        && states.stateList.hasState(StateId.INFERNO);
    if (!skill || combat || distance >= monsterSkillLevel(monster.monstats, 0)) {
      if (inferno) states.stateList.removeState(StateId.INFERNO);
    } else if (inferno) {
      states.stateList.removeState(StateId.INFERNO);
    } else {
      if (frame > infernoAfter && rollAiChance(params[0])) {
        inferno(targetId);
        return;
      }
      approachOrIdle(targetId);
      return;
    }

    if (!combat) {
      approachOrIdle(targetId);
    } else if (skill && frame > infernoAfter && rollAiChance(params[1])) {
      inferno(targetId);
    } else if (rollAiChance(params[2])) {
      attack(targetId);
    } else if (rollAiChance(params[4])) {
      // Shared circle approximates sub_6FCD0E80; native velocity types 5/6
      // and its unit-RNG direction selection are not implemented here.
      circle(targetId, 3f);
    } else {
      idle(5);
    }
  }

  private void inferno(int targetId) {
    // Native code consumes the timer even when UseSkill fails.
    infernoAfter = frame + params[5];
    castAt(0, targetId, mPosition.get(targetId).position);
  }

  private void approachOrIdle(int targetId) {
    // Shared path movement does not yet expose native walk flags 7.
    if (rollAiChance(params[3])) approach(targetId, 0);
    else idle(10);
  }
}
