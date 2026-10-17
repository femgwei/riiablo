package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.UnitStates;

/** D2MOO AITHINK_Fn074_OblivionKnight, 0x6FCDDBB0. */
public class OblivionKnight extends NativeDecisionAI {
  private long curseDeadline;
  private int previousTarget = Engine.INVALID_ENTITY;
  public OblivionKnight(int entityId) { super(entityId); }

  private int nearestDoomKnight() {
    int selected = Engine.INVALID_ENTITY;
    float nearest = 2501;
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (id == entityId || !nativeNearbyMonster(id) || nativeLifePercent(id) <= 0) continue;
      Monster candidate = mMonster.get(id);
      if (!"doomknight1".equals(candidate.monstats.BaseId) || candidate.monstats.Align != 0
          || candidate.converted != monster.converted) continue;
      NativeUnitFlags flags = mNativeUnitFlags.get(id);
      if (flags != null && !flags.has(NativeUnitFlags.TARGETABLE)) continue;
      float distance = mPosition.get(entityId).position.dst2(mPosition.get(id).position);
      if (distance <= 2500 && distance < nearest) { nearest = distance; selected = id; }
    }
    return selected;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    long frame = nativeFrame();
    int ally = nearestDoomKnight();
    if (distance < params[0]) {
      Skills.Entry skill = hasSkill(3) ? files().skills.get(monster.monstats.Skill4) : null;
      States.Entry curse = skill == null || skill.auratargetstate == null
          || skill.auratargetstate.isEmpty() ? null : files().States.get(skill.auratargetstate);
      UnitStates targetStates = mUnitStates.get(targetId);
      if (curse != null && curse.id > 0 && (targetStates == null
          || targetStates.stateList == null || !targetStates.stateList.hasState(curse.id))) {
        cast(3, targetId); curseDeadline = frame + params[2]; return;
      }
      if (ally != Engine.INVALID_ENTITY && mPosition.get(ally).position.dst2(
          mPosition.get(targetId).position) > distance * distance
          && walkToPoint(mPosition.get(ally).position, 0, ally)) return;
      if (escape(targetId, 10, 50)) return;
      if (hasSkill(2)) { cast(2, targetId); return; }
    }
    float[] selectedDistance = {Float.MAX_VALUE};
    int selected = findTargetWithContinuity(previousTarget, selectedDistance);
    previousTarget = selected;
    if (selected != Engine.INVALID_ENTITY && selectedDistance[0] < params[1]) {
      if (hasSkill(5) && frame > curseDeadline && rollAiChance(params[3])) {
        cast(5, targetId); curseDeadline = frame + params[2]; return;
      }
      if (rollAiChance(params[4])) {
        if (hasSkill(2) && rollAiChance(params[5])) { cast(2, selected); return; }
        CofComponents parts = Riiablo.engine.getMapper(CofComponents.class).get(entityId);
        if (hasSkill(0) && (parts == null || parts.component[10] < 4)) {
          cast(0, selected); return;
        }
      }
    }
    if (distance > params[7] && rollAiChance(params[6])) { approach(targetId, 0); return; }
    if (rollAiPercentage() >= 70) idle(10);
    else circle(targetId, 3);
  }
}
