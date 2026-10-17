package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.state.StateId;

/** AITHINK_Fn128, sharing the native minion callback with Overseer. */
public class Nihlathak extends Overseer {
  public Nihlathak(int entityId) { super(entityId); }

  @Override protected boolean beforeDecision() {
    if (mUnitStates.has(entityId) && mUnitStates.get(entityId).stateList != null)
      mUnitStates.get(entityId).stateList.removeState(StateId.INFERNO);
    return true;
  }

  private void teleportInRange() {
    Vector2 self = mPosition.get(entityId).position;
    int range = params[1];
    int x = range > 0 ? (int) (rollAiRandomNumber() % (2L * range)) - range : 0;
    int y = range > 0 ? (int) (rollAiRandomNumber() % (2L * range)) - range : 0;
    castAt(0, Engine.INVALID_ENTITY, new Vector2((int) self.x + x, (int) self.y + y));
  }

  @Override protected void thinkWithoutTarget() {
    if (hasSkill(0) && nativeUncontrolled()) teleportInRange();
    else idle(25);
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (hasSkill(0) && combat && rollAiChance(params[0])) { teleportInRange(); return; }
    if (distance < params[4] && rollAiPercentage() < 40 && escape(targetId, 5, 0)) return;
    if (hasSkill(2) && rollAiChance(params[2])) {
      int corpse = explosionTarget(targetId);
      if (corpse >= 0) { cast(2, corpse); return; }
    }
    if (hasSkill(3) && rollAiPercentage() < 60 && distance < 14) { cast(3, targetId); return; }
    if (hasSkill(1) && rollAiChance(params[3])) {
      Minions minions = findMinions(0, 625);
      if (minions.ordinary >= 0) { cast(1, minions.ordinary); return; }
      if (hasSkill(4)) {
        MapWrapper wrapper = mMapWrapper.get(entityId);
        Vector2 self = mPosition.get(entityId).position;
        if (wrapper != null && wrapper.zone != null && wrapper.map != null
            && !wrapper.map.isBlockedForPath(wrapper.zone, (int) self.x,
                (int) self.y, 0x1C09, monsterFootprint("evilHut"), false)) {
          MonStats.Entry minion = InvisoSpawner.regionalVariant(wrapper.zone, "minion1");
          if (minion != null) {
            monster.nativeSpawnClassId = minion.hcIdx;
            cast(4, targetId);
            return;
          }
        }
      }
      if (!walkClose(6, 0)) idle(10);
      return;
    }
    if (hasSkill(3) && distance < 14) { cast(3, targetId); return; }
    if (rollAiPercentage() < 60) approach(targetId, 0);
    delayDecision(5);
  }

  private int explosionTarget(int targetId) {
    IntBag corpses = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Corpse.class)).getEntities();
    int selected = Engine.INVALID_ENTITY;
    long newest = Long.MIN_VALUE;
    for (int i = 0; i < corpses.size(); i++) {
      int id = corpses.get(i);
      Corpse corpse = mCorpse.get(id);
      if (!corpse.usable || corpse.fading || !nativeNearbyMonster(id)
          || mMercenary.has(id) || mMonster.get(id).monstats2 == null
          || !mMonster.get(id).monstats2.corpseSel
          || mPosition.get(id).position.dst2(mPosition.get(targetId).position) > 100) continue;
      if (corpse.insertionOrder > newest) { selected = id; newest = corpse.insertionOrder; }
    }
    return selected;
  }
}
