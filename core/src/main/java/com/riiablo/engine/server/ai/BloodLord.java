package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn125_BloodLord (0x6FCE2760). */
public class BloodLord extends NativeDecisionAI {
  public BloodLord(int entityId) { super(entityId); }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[0])) {
        if (rollAiChance(params[2])) {
          castAt(0, targetId, mPosition.get(targetId).position, Engine.Monster.MODE_A2);
        } else {
          attack(targetId);
        }
        return;
      }
    } else if (rollAiChance(params[1])) {
      approach(targetId, 0);
      return;
    }
    idle(params[3]);
  }
}
