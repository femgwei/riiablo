package com.riiablo.engine.server.ai;

/** D2MOO AITHINK_Fn087_TrapMelee (AiThink.cpp, 0x6FCDE960). */
public class TrapMelee extends NativeDecisionAI {
  public TrapMelee(int entityId) { super(entityId); }

  @Override protected void thinkWithoutTarget() { idle(40); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (!combat) idle(40);
    else if (rollAiChance(params[0])) attack(targetId);
    else idle(params[1]);
  }
}
