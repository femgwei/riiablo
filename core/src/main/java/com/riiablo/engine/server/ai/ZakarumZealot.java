package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn048_ZakarumZealot (0x6FCD9640). */
public class ZakarumZealot extends NativeDecisionAI {
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_ATTACK2_CHANCE = 1;
  static final int PARAM_HURT_PERCENT = 2;
  static final int PARAM_RUN_CHANCE = 3;

  private boolean attackArmed;

  public ZakarumZealot(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    int velocityBonus = runVelocityBonus();
    if (!combat) {
      attackArmed = false;
      if (rollAiChance(params[PARAM_RUN_CHANCE])) {
        approach(targetId, velocityBonus, true);
      } else {
        approach(targetId, velocityBonus, false);
      }
      return;
    }

    if (attackArmed && !rollAiChance(params[PARAM_ATTACK_CHANCE])) {
      attackArmed = false;
      idle(10);
      return;
    }
    attackArmed = true;
    int mode = rollAiChance(params[PARAM_ATTACK2_CHANCE])
        ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
    attack(targetId, mode);
  }

  private int runVelocityBonus() {
    if (monster == null || monster.monstats == null || monster.monstats.Velocity <= 0) {
      return 0;
    }
    int ratio = 100 * monster.monstats.Run / monster.monstats.Velocity;
    return Math.max(0, Math.min(120, ratio - 100));
  }
}
