package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AiThink.cpp AITHINK_Fn046_ElementalBeast (0x6FCD93A0). */
public class ElementalBeast extends NativeDecisionAI {
  static final int PARAM_APPROACH_CHANCE = 0;
  static final int PARAM_ACTIVATION_DISTANCE = 1;
  static final int PARAM_STALL_DURATION = 2;

  private int phase;

  public ElementalBeast(int entityId) {
    super(entityId);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    switch (phase) {
      case 0:
        if (distance < params[PARAM_ACTIVATION_DISTANCE]) {
          if (startSkill1(targetId)) phase = 1;
          return;
        }
        break;
      case 1:
        if (combat) {
          if (startSkill1(targetId)) phase = 2;
          return;
        }
        if (rollAiChance(params[PARAM_APPROACH_CHANCE])) {
          approach(targetId, 0);
        } else {
          idle(params[PARAM_STALL_DURATION]);
        }
        return;
      case 2:
        // SUNITDMG_KillMonster receives the beast as defender, so the native
        // post-skill transition removes this unit rather than its target.
        kill();
        return;
      default:
        phase = 0;
        break;
    }
    idle(params[PARAM_STALL_DURATION]);
  }

  private boolean startSkill1(int targetId) {
    if (hasSkill(0)) return castAt(0, targetId, mPosition.get(targetId).position);
    // poisonorb is a native mode-only ElementalBeast row in 1.10f.
    startMode(Engine.Monster.MODE_S1, targetId);
    return true;
  }
}
