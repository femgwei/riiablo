package com.riiablo.engine.server.ai;

/** D2MOO AiThink.cpp AITHINK_Fn141_BaalMinion (0x6FCF0420). */
public class BaalMinion extends NativeDecisionAI {
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_APPROACH_CHANCE = 1;
  static final int PARAM_SKILL_CHANCE_OR_MELEE_STALL = 2;
  static final int PARAM_SKILL_STALL_DURATION = 3;

  public BaalMinion(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[PARAM_ATTACK_CHANCE])) {
        if (hasSkill(0) && rollAiChance(params[PARAM_SKILL_CHANCE_OR_MELEE_STALL])) {
          cast(0, targetId);
        } else {
          attack(targetId);
        }
      } else {
        idle(Math.max(1, params[PARAM_SKILL_CHANCE_OR_MELEE_STALL]));
      }
      return;
    }

    if (rollAiChance(params[PARAM_APPROACH_CHANCE])) {
      approach(targetId, 0);
    } else {
      idle(Math.max(1, params[PARAM_SKILL_STALL_DURATION]));
    }
  }
}
