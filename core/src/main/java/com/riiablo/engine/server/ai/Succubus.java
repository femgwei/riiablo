package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.UnitStates;

/** D2MOO AITHINK_Fn118_Succubus (AiThink.cpp, 0x6FCE0CD0). */
public class Succubus extends NativeDecisionAI {
  public Succubus(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (!cursed(targetId) && distance < params[3] && rollAiChance(params[2])) {
      if (positiveSkill(0) && lifePercent(targetId) >= params[6]) {
        cast(0, targetId);
        return;
      }
      if (positiveSkill(1) && lifePercent(entityId) <= params[7]) {
        cast(1, targetId);
        return;
      }
      if (positiveSkill(2) && (stat(targetId, Stat.maxhp) > stat(targetId, Stat.maxmana)
          || !mPlayer.has(targetId))) {
        cast(2, targetId);
        return;
      }
      if (positiveSkill(3) && mPlayer.has(targetId)) {
        cast(3, targetId);
        return;
      }
    }
    if (combat) {
      if (rollAiChance(params[0])) attack(targetId);
      else idle(params[4]);
      return;
    }
    // Native reuses CURSE2_LEVEL (slot7) as the ranged Skill5 probability.
    if (positiveSkill(4) && params[7] > 0) {
      int rangedTarget = findTargetWithContinuity(targetId, new float[1]);
      if (rangedTarget != Engine.INVALID_ENTITY && rollAiChance(params[7])) {
        cast(4, rangedTarget);
        return;
      }
    }
    if (rollAiChance(params[1])) approach(targetId, 0);
    else idle(params[5]);
  }

  @Override protected void thinkWithoutTarget() {
    // The native null-target movement/scheduler path is not modeled here.
    idle(params[5]);
  }

  private boolean cursed(int targetId) {
    UnitStates states = mUnitStates.get(targetId);
    return states != null && states.stateList != null && states.stateList.hasCurse();
  }

  private boolean positiveSkill(int slot) {
    String[] names = {monster.monstats.Skill1, monster.monstats.Skill2,
        monster.monstats.Skill3, monster.monstats.Skill4, monster.monstats.Skill5};
    String name = names[slot];
    Skills.Entry skill = name == null || name.isEmpty() ? null : Riiablo.files.skills.get(name);
    return skill != null && skill.Id > 0;
  }

  private int lifePercent(int id) {
    // UNITS_GetCurrentLifePercentage truncates both fixed-point values first.
    int max = (int) stat(id, Stat.maxhp);
    return max == 0 ? 0 : (int) (100L * (int) stat(id, Stat.hitpoints) / max);
  }

  private float stat(int id, short stat) {
    Attributes attrs = mAttributesWrapper.has(id) ? mAttributesWrapper.get(id).attrs : null;
    return attrs == null ? 0f : attrs.getValue(stat, 0f);
  }
}
