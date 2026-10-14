package com.riiablo.engine.server.ai;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.event.DeathEvent;

/** D2MOO AiBaal.cpp AITHINK_Fn140_BaalCrabClone (0x6FCCEB70). */
public class BaalCrabClone extends BaalCrab {
  public BaalCrabClone(int entityId) { super(entityId); }

  @Override
  protected boolean beforeDecision() {
    int minionOwner = monster.minionOwnerId;
    int owner = monster.baalLinkedOwnerId;
    if ((minionOwner >= 0 && mMonster.has(minionOwner) && !isLivingMonster(minionOwner))
        || owner == entityId || !isLivingMonster(owner)
        || mMonster.get(owner).baalLinkedOwnerId != entityId) {
      Attributes attrs = mAttributesWrapper.has(entityId) ? mAttributesWrapper.get(entityId).attrs : null;
      if (attrs != null && attrs.get(Stat.hitpoints) != null) attrs.get(Stat.hitpoints).set(0f);
      if (events != null) events.dispatch(DeathEvent.obtain(Engine.INVALID_ENTITY, entityId));
      kill();
      return false;
    }
    return super.beforeDecision();
  }

  @Override
  void execute(int action, int targetId) {
    if (targetId == Engine.INVALID_ENTITY) {
      // Native still rolls the table, but with no target it performs no action.
      idle(15);
      return;
    }
    super.execute(remapAction(action), targetId);
  }

  static int remapAction(int action) {
    return action == 7 || action == 9 || action == 14 || action == 15 ? 2 : action;
  }
}
