package com.riiablo.engine.server.ai;

/** D2MOO AITHINK_Fn069_VileDog (AiThink.cpp, 0x6FCDCBF0). */
public class VileDog extends NativeDecisionAI {
  private boolean initialized;

  public VileDog(int entityId) { super(entityId); }

  @Override protected boolean beforeDecision() {
    // Native aiParam[0] is set before target/combat branches, including no target.
    if (!initialized) {
      initialized = true;
      idle(5);
      return false;
    }
    return true;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[0])) attack(targetId);
      else idle(params[1]);
    } else if (rollAiChance(params[2])) {
      // Shared movement has no projection of native WalkToTarget flags 7 yet.
      approach(targetId, 0);
    } else {
      idle(10);
    }
  }
}
