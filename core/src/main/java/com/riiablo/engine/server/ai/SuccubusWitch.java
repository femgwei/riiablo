package com.riiablo.engine.server.ai;

import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;

/** D2MOO AITHINK_Fn119_SuccubusWitch, 0x6FCE0FE0. */
public class SuccubusWitch extends Succubus {
  public SuccubusWitch(int entityId) { super(entityId); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (!cursed(targetId) && distance < params[3] && rollAiChance(params[2])) {
      int slot = positiveSkill(0) && lifePercent(targetId) >= params[6] ? 0
          : positiveSkill(1) && lifePercent(entityId) <= params[7] ? 1
          : positiveSkill(2) && (stat(targetId, Stat.maxhp) > stat(targetId, Stat.maxmana)
              || !mPlayer.has(targetId)) ? 2
          : positiveSkill(3) && mPlayer.has(targetId) ? 3 : -1;
      if (slot >= 0) { cast(slot, targetId); return; }
    }
    if (combat) {
      if (!rollAiChance(params[2]) || !escape(targetId, params[3], 0)) {
        if (rollAiChance(params[0])) attack(targetId);
        else idle(params[5]);
      }
      return;
    }
    float[] selectedDistance = {distance};
    if (hasSkill(4) && params[7] > 0 && rollAiChance(params[4])) {
      int selected = findTargetWithContinuity(targetId, selectedDistance);
      distance = selectedDistance[0];
      if (selected != Engine.INVALID_ENTITY && rollAiChance(params[7])) {
        cast(4, selected); return;
      }
    }
    if (distance < params[3] && rollAiChance(params[2])
        && escape(targetId, params[3], 0)) return;
    if (!hasSkill(4)) {
      int selected = findTargetWithContinuity(targetId, selectedDistance);
      if (selected != Engine.INVALID_ENTITY && rollAiChance(params[4])) {
        attack(selected, Engine.Monster.MODE_S2); return;
      }
    }
    if (rollAiChance(params[1])) approach(targetId, 0);
    else if (rollAiPercentage() >= 50 || !pathfinder.findNativeOrbit(entityId,
        targetId, 6, (rollAiRandomNumber() & 255L) < 128L)) idle(params[5]);
  }
}
