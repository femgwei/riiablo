package com.riiablo.engine.server.ai;

/** D2MOO AiThink.cpp AITHINK_Fn047_FlyingScimitar (0x6FCD94D0). */
public class FlyingScimitar extends NativeDecisionAI {
  static final int PARAM_APPROACH_CHANCE = 0;
  static final int PARAM_ATTACK_CHANCE = 1;
  static final int PARAM_STALL_DURATION = 2;
  static final int PARAM_CIRCLE_CHANCE = 3;

  public FlyingScimitar(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[PARAM_ATTACK_CHANCE])) {
        attack(targetId);
        return;
      }
      if (rollAiChance(params[PARAM_CIRCLE_CHANCE])) {
        circle(targetId, 8f);
        return;
      }
    } else {
      if (rollAiChance(params[PARAM_APPROACH_CHANCE])) {
        approach(targetId, 0);
        return;
      }
      // D2MOO intentionally performs the same approach roll a second time;
      // preserve both the branch and its RNG consumption.
      if (rollAiChance(params[PARAM_APPROACH_CHANCE])) {
        approach(targetId, 0);
        return;
      }
    }
    idle(params[PARAM_STALL_DURATION]);
  }
}
