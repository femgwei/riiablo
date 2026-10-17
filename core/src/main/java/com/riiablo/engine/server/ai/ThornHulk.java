package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;

/** D2MOO AITHINK_Fn027_ThornHulk, 0x6FCD6D60. */
public class ThornHulk extends NativeDecisionAI {
  private int burst, recovery;
  private boolean waitAfterFrenzy;
  public ThornHulk(int entityId) { super(entityId); }

  @Override protected boolean actionBlocksDecision() {
    if (super.actionBlocksDecision()) return true;
    if (waitAfterFrenzy) {
      waitAfterFrenzy = false;
      delayDecision(params[4]);
      return true;
    }
    return false;
  }

  @Override protected void thinkWithoutTarget() { burst = 0; idle(1); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (!combat) { burst = 0; approach(targetId, 0); return; }
    if (burst > 0) {
      frenzy(targetId);
      if (--burst <= 0) recovery = 3;
      return;
    }
    if (rollAiChance(params[0])) {
      if (recovery > 0 || !rollAiChance(params[3])) {
        recovery--;
        attack(targetId, rollAiChance(params[1])
            ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1);
      } else {
        frenzy(targetId);
        burst = params[5];
      }
    } else if (rollAiChance(params[2])) circle(targetId, 4);
    else idle(15);
  }

  private void frenzy(int targetId) {
    if (castAt(0, targetId, mPosition.get(targetId).position, Engine.Monster.MODE_A2))
      waitAfterFrenzy = true;
  }
}
