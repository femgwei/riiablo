package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/**
 * Ordinary (no AI command) branch of D2MOO AITHINK_Fn116_Minion, 0x6FCE0A50.
 * Command-directed targets require the native AI command queue, still pending.
 */
public class Minion extends NativeDecisionAI {
  public Minion(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[0])) {
        // Preserve the original bug: A2 chance reads melee stall (slot 1),
        // not the attack-choice parameter documented in slot 4.
        attack(targetId, rollAiChance(params[1])
            ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1);
      } else {
        idle(params[1]);
      }
    } else if (rollAiChance(params[2])) {
      approach(targetId, 0);
    } else {
      idle(params[3]);
    }
  }
}
