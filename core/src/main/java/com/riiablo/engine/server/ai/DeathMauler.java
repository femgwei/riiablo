package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AITHINK_Fn130_DeathMauler (AiThink.cpp, 0x6FCEEAD0). */
public class DeathMauler extends NativeDecisionAI {
  public DeathMauler(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[0])) {
        attack(targetId);
        return;
      }
    } else {
      if (hasSkill(0) && distance < params[2] && rollAiChance(params[3])) {
        // MonStats names a MonSeq, whose backing mode is A2 (not Skills XX).
        if (castAt(0, targetId, mPosition.get(targetId).position, Engine.Monster.MODE_A2))
          mSequence.get(entityId).nativeDeathMaul = true;
        return;
      }
      if (rollAiChance(params[1])) {
        approach(targetId, 0);
        return;
      }
    }
    idle(15);
  }
}
