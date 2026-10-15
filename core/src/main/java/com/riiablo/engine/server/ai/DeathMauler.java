package com.riiablo.engine.server.ai;

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
        // Preserve configured skill/mode resolution. DeathMaul Do136 and its
        // MonSeq are still unsupported by the shared runtime, with warnings.
        castAt(0, targetId, mPosition.get(targetId).position);
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
