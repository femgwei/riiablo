package com.riiablo.engine.server.ai;

/** D2MOO AiThink.cpp AITHINK_Fn034_Andariel (0x6FCD8090). */
public class Andariel extends NativeDecisionAI {
  static final int PARAM_SPRAY_IN_MELEE_CHANCE = 0;
  static final int PARAM_STALL_CHANCE = 1;
  static final int PARAM_FIRE_OR_ENGAGE_CHANCE = 2;
  static final int PARAM_SPRAY_FROM_DISTANCE_CHANCE = 3;

  public Andariel(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (hasSkill(0) && rollAiChance(params[PARAM_SPRAY_IN_MELEE_CHANCE])) cast(0, targetId);
      else attack(targetId);
      return;
    }
    if (rollAiChance(params[PARAM_STALL_CHANCE])) {
      idle(5);
      return;
    }
    if (rollAiChance(params[PARAM_FIRE_OR_ENGAGE_CHANCE])) {
      if (hasSkill(0) && rollAiChance(params[PARAM_SPRAY_FROM_DISTANCE_CHANCE])) {
        cast(0, targetId);
        return;
      }
      if (hasSkill(1)) {
        cast(1, targetId);
        return;
      }
    }
    approach(targetId, 0);
  }
}
