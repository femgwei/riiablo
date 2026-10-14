package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn057_TentacleHead (0x6FCDBAA0). */
public class TentacleHead extends TentacleAI {
  public TentacleHead(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (hasSkill(0)) {
      if (phase == 0) {
        submerge(targetId, 8);
        return;
      }
      if (phase == 2 && expired()
          && (distance > params[PARAM_ACTIVE_DISTANCE]
              || !combat && rollAiChance(params[PARAM_SUBMERGE_CHANCE]))) {
        submerge(targetId, 20);
        return;
      }
    }

    if (hasSkill(1) && phase == 1 && expired()
        && (combat || distance < params[PARAM_ACTIVE_DISTANCE])) {
      emerge();
      return;
    }
    if (phase != 1) {
      // Native sub_6FCF2CC0 reacquires a target and does not require bCombat:
      // the head's ordinary A1 carries its MonStats MissA1 ranged attack.
      float[] distanceOut = {Float.MAX_VALUE};
      int shotTarget = findNearestTargetWithAidist(distanceOut);
      if (rollAiChance(params[PARAM_ATTACK_CHANCE]) && shotTarget != Engine.INVALID_ENTITY) {
        attack(shotTarget);
        return;
      }
    }
    stall();
  }
}
