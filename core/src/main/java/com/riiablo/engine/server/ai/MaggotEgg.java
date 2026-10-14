package com.riiablo.engine.server.ai;

/** D2MOO AiThink.cpp AITHINK_Fn040_MaggotEgg (0x6FCD8D20). */
public class MaggotEgg extends NativeDecisionAI {
  static final int PARAM_STALL_DURATION = 0;
  static final int PARAM_HATCH_CHANCE = 1;

  private boolean hatched;

  public MaggotEgg(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (hatched) {
      // Native kills the egg after the hatch action has completed.
      kill();
      return;
    }
    if (hasSkill(0) && rollAiChance(params[PARAM_HATCH_CHANCE])) {
      if (castAt(0, targetId, mPosition.get(targetId).position)) {
        hatched = true;
        idle(params[PARAM_STALL_DURATION]);
      }
      return;
    }
    idle(params[PARAM_STALL_DURATION]);
  }
}
