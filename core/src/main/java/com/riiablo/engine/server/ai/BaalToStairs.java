package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.quest.Act5QuestSystem;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;

/** Fn138 follows the Worldstone portal and retires the transition monster. */
public class BaalToStairs extends NativeDecisionAI {
  private boolean retired;
  public BaalToStairs(int entityId) { super(entityId); }
  @Override protected boolean beforeDecision() {
    if (retired) return false;
    MapWrapper source = mMapWrapper.get(entityId);
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Object.class, Position.class, MapWrapper.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      Object object = Riiablo.engine.getMapper(Object.class).get(id);
      MapWrapper target = mMapWrapper.get(id);
      if (object.base == null || object.base.Id != NativeQuestObjectResolver.BAAL_PORTAL
          || source == null || target.zone != source.zone || target.map != source.map) continue;
      com.badlogic.gdx.math.Vector2 self = mPosition.get(entityId).position;
      com.badlogic.gdx.math.Vector2 portal = mPosition.get(id).position;
      float distance = com.riiablo.engine.server.combat.NativeMeleeDistance.between(
          (int) self.x, (int) self.y, mSize.has(entityId) ? mSize.get(entityId).size : 1,
          (int) portal.x, (int) portal.y, mSize.has(id) ? mSize.get(id).size : 1);
      if (distance > 25) continue;
      if (distance >= params[0]) { approach(id, 0); return false; }
      Act5QuestSystem quests = Riiablo.engine.getSystem(Act5QuestSystem.class);
      if (quests == null) { idle(25); return false; }
      if (!quests.openWorldstoneChamberPortal()) { idle(25); return false; }
      retired = true; stopMovement(); Riiablo.engine.delete(entityId); return false;
    }
    idle(25); return false;
  }
  @Override protected void think(int targetId, float distance, boolean combat) { idle(25); }
}
