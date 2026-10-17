package com.riiablo.engine.server.ai;

/** D2MOO AITHINK_Fn070_FingerMage, 0x6FCDCCD0. */
public class FingerMage extends NativeDecisionAI {
  private boolean fleeing;
  private int tries;
  public FingerMage(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    int health = nativeLifePercent(entityId);
    int speed = monster.monstats.Velocity > 0
        ? Math.min(120, Math.max(0, 100 * monster.monstats.Run / monster.monstats.Velocity - 100)) : 0;
    if (nativeUncontrolled()) {
      fleeing = false;
      if (combat) { attack(targetId); return; }
      if (hasSkill(0) && distance < params[4]) { cast(0, targetId); return; }
    }
    if (fleeing) {
      tries++;
      if (health > params[1] || rollAiPercentage() < 25 || tries > params[5]) fleeing = false;
      else if (distance < 14) { escape(targetId, 14, speed); return; }
      idle(15);
      return;
    }
    if (monster.minionOwnerId < 0 && !mSummonedPet.has(entityId) && health < params[0]) {
      fleeing = true;
      tries = 0;
      escape(targetId, 9, 0);
      return;
    }
    if (combat) {
      if (rollAiChance(params[2])) attack(targetId);
      else if (!hasSkill(0) || !rollAiChance(params[3])) circle(targetId, 5);
      else cast(0, targetId);
    } else if (distance >= params[4]) {
      if (distance >= params[6]) idle(15);
      else approach(targetId, 0);
    } else if (hasSkill(0) && rollAiChance(params[3])) cast(0, targetId);
    else if (mUnitStates.has(targetId) && mUnitStates.get(targetId).stateList != null
        && mUnitStates.get(targetId).stateList.hasState(
            com.riiablo.engine.server.state.StateId.FINGERMAGECURSE)) approach(targetId, 0);
    else idle(params[7]);
  }
}
