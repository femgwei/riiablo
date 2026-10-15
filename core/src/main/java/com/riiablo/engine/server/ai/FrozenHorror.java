package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;

/** D2MOO AITHINK_Fn124_FrozenHorror (AiThink.cpp, 0x6FCE25D0). */
public class FrozenHorror extends NativeDecisionAI {
  public FrozenHorror(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    UnitStates states = mUnitStates.get(entityId);
    boolean inferno = states != null && states.stateList != null
        && states.stateList.hasState(StateId.INFERNO);
    if (hasSkill(0) && distance < monsterSkillLevel(monster.monstats, 0)
        && rollAiChance(params[2]) && !inferno) {
      castAt(0, targetId, mPosition.get(targetId).position, Engine.Monster.MODE_A2);
      return;
    }
    if (inferno) states.stateList.removeState(StateId.INFERNO);
    if (combat) {
      if (rollAiChance(params[0])) { attack(targetId); return; }
    } else if (rollAiChance(params[1])) {
      approach(targetId, 0);
      return;
    }
    idle(params[3]);
  }
}
