package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.quest.Act5QuestSystem;
import com.riiablo.engine.server.state.StateId;

/** Fn133 dispatches the distinct Talic, Madawc and Korlic handlers. */
public class Ancient extends NativeDecisionAI {
  public Ancient(int entityId) { super(entityId); }

  @Override protected boolean beforeDecision() {
    Act5QuestSystem quests = Riiablo.engine.getSystem(Act5QuestSystem.class);
    if (quests != null && !quests.isAncientCombatActive(entityId)) { idle(25); return false; }
    return true;
  }

  private boolean hasNamedState(int id, String name) {
    if (name == null || name.isEmpty()) return false;
    States.Entry state = Riiablo.files.States.get(name);
    UnitStates states = mUnitStates.get(id);
    return state != null && state.id > 0 && states != null && states.stateList != null
        && states.stateList.hasState(state.id);
  }

  private void meleeOrApproach(int targetId, boolean combat) {
    if (!combat) { approach(targetId, 0); delayDecision(10); return; }
    if (rollAiChance(params[2])) {
      attack(targetId, (rollAiRandomNumber() & 1L) != 0
          ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2);
    } else idle(25);
  }

  @Override protected void thinkWithoutTarget() { idle(25); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    String id = monster.monstats.Id;
    if ("ancientbarb1".equals(id)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states != null && states.stateList != null
          && states.stateList.hasState(StateId.UNINTERRUPTABLE)) { idle(25); return; }
      if (hasSkill(0) && distance < params[0] && rollAiChance(params[1])) {
        Vector2 source = mPosition.get(entityId).position;
        Vector2 target = mPosition.get(targetId).position;
        int scale = Math.max(1, com.riiablo.engine.server.combat.NativeMeleeDistance.between(
            (int) source.x, (int) source.y, 1, (int) target.x, (int) target.y, 1));
        castAt(0, Engine.INVALID_ENTITY, new Vector2(
            (int) target.x + ((int) target.x - (int) source.x) * params[3] / scale,
            (int) target.y + ((int) target.y - (int) source.y) * params[3] / scale));
      } else meleeOrApproach(targetId, combat);
    } else if ("ancientbarb2".equals(id)) {
      if (combat && rollAiChance(params[3]) && escape(targetId, params[4], 0)) {
        delayDecision(25); return;
      }
      Skills.Entry shout = hasSkill(0) ? Riiablo.files.skills.get(monster.monstats.Skill1) : null;
      if (shout != null && !hasNamedState(entityId, shout.aurastate)
          && !hasNamedState(targetId, shout.auratargetstate) && rollAiChance(params[2])
          && castAt(0, Engine.INVALID_ENTITY, new Vector2())) return;
      if (distance < params[0] && rollAiChance(params[1])) { attack(targetId); return; }
      if (!pathfinder.findNativePath(entityId, targetId, (int) distance - params[0], 0)) idle(10);
    } else if ("ancientbarb3".equals(id)) {
      if (hasSkill(0) && distance < params[0] && rollAiChance(params[1])) {
        cast(0, targetId); return;
      }
      meleeOrApproach(targetId, combat);
    }
  }
}
