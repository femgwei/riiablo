package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Monster;

/** D2MOO AITHINK_Fn121_MinionSpawner, 0x6FCE1B90. */
public class MinionSpawner extends NativeDecisionAI {
  private long deadline;
  private int count;
  public MinionSpawner(int entityId) { super(entityId); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    if (count >= params[0]) return;
    if (distance <= params[3] && nativeFrame() >= deadline) {
      int nearby = 0;
      IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
          Aspect.all(Monster.class)).getEntities();
      for (int i = 0; i < ids.size(); i++) {
        int id = ids.get(i);
        if (id == entityId || !nativeNearbyMonster(id) || nativeLifePercent(id) <= 0
            || mMonster.get(id).converted || mMonster.get(id).monstats.Align != 0) continue;
        String base = mMonster.get(id).monstats.BaseId;
        if ("minion1".equals(base) || "suicideminion1".equals(base)) nearby++;
      }
      if (hasSkill(0) && nearby < params[1]) {
        deadline = nativeFrame() + params[2];
        count++; cast(0, targetId); return;
      }
    }
    idle(params[1]);
  }
}
