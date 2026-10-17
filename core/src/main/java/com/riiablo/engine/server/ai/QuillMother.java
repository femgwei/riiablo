package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeAiCommand;

/** D2MOO AITHINK_Fn075_QuillMother, 0x6FCDE150. */
public class QuillMother extends NativeDecisionAI {
  public QuillMother(int entityId) { super(entityId); }

  @Override protected void thinkWithoutTarget() {
    if (nativeUncontrolled()) commandMinions(Engine.INVALID_ENTITY);
    idle(params[3]);
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (nativeUncontrolled()) {
      commandMinions(targetId);
      if (combat) attack(targetId); else approach(targetId, 0);
    } else if (combat) {
      if (rollAiChance(params[0])) attack(targetId); else idle(params[2]);
    } else if (rollAiChance(params[1])) approach(targetId, 0);
    else idle(params[3]);
  }

  private void commandMinions(int targetId) {
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (mMonster.get(id).minionOwnerId == entityId)
        Riiablo.engine.getMapper(NativeAiCommand.class).create(id)
            .copy(Riiablo.engine, entityId, NativeAiCommand.ATTACK, targetId);
    }
  }
}
