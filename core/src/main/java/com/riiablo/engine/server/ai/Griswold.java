package com.riiablo.engine.server.ai;

/** D2MOO AiThink.cpp AITHINK_Fn090_Griswold (0x6FCE4FF0). */
public class Griswold extends NativeDecisionAI {
  public Griswold(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(80)) {
        attack(targetId);
        return;
      }
    } else if (rollAiChance(50)) {
      approach(targetId, 0);
      return;
    }
    idle(10);
  }
}
