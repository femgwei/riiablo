package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/**
 * D2MOO AITHINK_Fn116_Minion, including unexpired attack commands.
 */
public class Minion extends NativeDecisionAI {
  public Minion(int entityId) { super(entityId); }

  @Override protected boolean beforeDecision() {
    com.artemis.ComponentMapper<com.riiablo.engine.server.component.NativeAiCommand> commands =
        com.riiablo.Riiablo.engine.getMapper(com.riiablo.engine.server.component.NativeAiCommand.class);
    com.riiablo.engine.server.component.NativeAiCommand command = commands.get(entityId);
    if (command == null) return true;
    int target = command.targetId;
    if (!command.isAttack() || command.expiresAtFrame <= nativeFrame()
        || !command.targetIsCurrent(com.riiablo.Riiablo.engine)
        || !command.ownerIsCurrent(com.riiablo.Riiablo.engine)
        || target < 0 || !mPosition.has(target) || nativeLifePercent(target) <= 0) {
      if (!command.pop()) commands.remove(entityId);
      return true;
    }
    if (isInNativeMeleeRange(target))
      attack(target, rollAiChance(params[1]) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1);
    else approach(target, 0);
    return false;
  }

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
