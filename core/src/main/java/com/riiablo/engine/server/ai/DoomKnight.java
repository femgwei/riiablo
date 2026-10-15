package com.riiablo.engine.server.ai;

/** D2MOO AITHINK_Fn072_DoomKnight (AiThink.cpp, 0x6FCDD790). */
public class DoomKnight extends NativeDecisionAI {
  public DoomKnight(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[0])) attack(targetId);
      else idle(params[1]);
    } else if (rollAiChance(params[2])) {
      // sub_6FCD0410 sets velocity type 13, then walks with flags 7.
      // Shared approach preserves the walking action but does not project
      // those native path/velocity-type semantics yet.
      approach(targetId, 0);
    } else {
      idle(params[3]);
    }
  }
}
