package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.NativeAiCommand;

/** D2MOO AITHINK_Fn096_FetishBlowgun, 0x6FCDFD50. */
public class FetishBlowgun extends NativeDecisionAI {
  private int phase, loops;
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeInt(phase);
    out.writeInt(loops);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPhase = in.readInt();
    int savedLoops = in.readInt();
    if (apply) {
      phase = savedPhase;
      loops = savedLoops;
    }
  }

  public FetishBlowgun(int entityId) { super(entityId); }
  @Override protected boolean beforeDecision() {
    com.artemis.ComponentMapper<NativeAiCommand> commands =
        Riiablo.engine.getMapper(NativeAiCommand.class);
    NativeAiCommand command = commands.get(entityId);
    if (command == null) return true;
    int type = command.command, target = command.targetId;
    boolean valid = command.targetIsCurrent(Riiablo.engine);
    if (!command.pop()) commands.remove(entityId);
    if (!valid || !mPosition.has(target)) return true;
    if (type == NativeAiCommand.ATTACK) {
      phase = loops = 0; attack(target); return false;
    }
    if (type == NativeAiCommand.MOVE) {
      phase = loops = 0;
      if (pathfinder.findNativeChase(entityId, target))
        mVelocity.get(entityId).setModeSpeedBonusPercent(50);
      return false;
    }
    return true;
  }
  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[1])) phase = 2;
    } else {
      if (distance > params[0]) { approach(targetId, 50); return; }
      if (distance < 6 && rollAiChance(params[1])) phase = 2;
    }
    int selected = findTargetWithContinuity(targetId, new float[1]);
    switch (phase) {
      case 0:
        if (++loops > rollAiRandomNumber() % 3 + 3) { phase = 1; loops = 0; }
        if (selected != Engine.INVALID_ENTITY) attack(selected); else circle(targetId, 4);
        return;
      case 1:
        phase = loops = 0;
        orbit(targetId, 6, 50);
        return;
      case 2:
        if (distance <= 12) {
          if (!escape(targetId, 14, 50)) {
            if (selected != Engine.INVALID_ENTITY) attack(selected);
            phase = loops = 0;
          }
        } else if (rollAiPercentage() >= 20 || !orbit(targetId, 4, 0)) {
          phase = loops = 0; idle(10);
        }
        return;
      default: idle(10);
    }
  }
  private boolean orbit(int targetId, int budget, int bonus) {
    boolean moved = pathfinder.findNativeOrbit(entityId, targetId, budget,
        (rollAiRandomNumber() & 255L) < 128L);
    if (moved) mVelocity.get(entityId).setModeSpeedBonusPercent(bonus);
    else idle(10);
    return moved;
  }
}
