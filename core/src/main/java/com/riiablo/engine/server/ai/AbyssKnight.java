package com.riiablo.engine.server.ai;

import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.UnitStates;

/** D2MOO AITHINK_Fn073_AbyssKnight, 0x6FCDD850. */
public class AbyssKnight extends NativeDecisionAI {
  private int recovery;
  public AbyssKnight(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    Skills.Entry armor = hasSkill(1) ? files().skills.get(monster.monstats.Skill2) : null;
    States.Entry state = armor == null ? null : files().States.get(armor.aurastate);
    UnitStates active = mUnitStates.get(entityId);
    if (state != null && (active == null || active.stateList == null
        || !active.stateList.hasState(state.id)) && nativeLifePercent(entityId) < params[0]
        && rollAiChance(params[1])) {
      castAt(1, Engine.INVALID_ENTITY, new com.badlogic.gdx.math.Vector2());
      return;
    }
    if (combat) {
      if (rollAiChance(params[2])) attack(targetId); else idle(params[3]);
      return;
    }
    if (distance < params[4] && recovery <= 0) recovery = params[5];
    if (hasSkill(0) && recovery == 0 && monster.componentVariants[10] < 4) {
      cast(0, targetId);
      recovery = params[5];
      return;
    }
    if (recovery > 0) recovery--;
    if (rollAiChance(params[6])) {
      int budget = (int) (rollAiRandomNumber() & 1L) + 6;
      pathfinder.findNativePath(entityId, targetId, budget, 0);
    } else if (distance < params[7]) circle(targetId, 3);
    else idle(15);
  }
}
