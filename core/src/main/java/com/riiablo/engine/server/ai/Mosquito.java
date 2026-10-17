package com.riiablo.engine.server.ai;

/** D2MOO AITHINK_Fn024_Mosquito, 0x6FCD5850. */
public class Mosquito extends NativeDecisionAI {
  private int phase, loops;
  public Mosquito(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[2]) || loops == 0) {
        loops++;
        if (hasSkill(0) && rollAiChance(params[3])) cast(0, targetId);
        else attack(targetId);
        return;
      }
      if (rollAiPercentage() > 20) { idle(15); return; }
      phase = 2;
      loops = 0;
    }
    switch (phase) {
      case 0:
        if (pathfinder.findNativeChase(entityId, targetId))
          mVelocity.get(entityId).setModeSpeedBonusPercent(100);
        break;
      case 1:
        walkClose(4, 50);
        if (++loops > params[4]) { phase = 0; loops = 0; }
        break;
      case 2:
        escape(targetId, 10, 100);
        phase = 1;
        loops = 0;
        break;
      default: phase = loops = 0; idle(10);
    }
  }
}
