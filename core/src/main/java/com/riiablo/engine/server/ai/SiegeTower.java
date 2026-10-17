package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Monster;

/** Fn113 and the shared Fn113/Fn115 Imp recruitment callback. */
public class SiegeTower extends NativeDecisionAI {
  public SiegeTower(int entityId) { super(entityId); }

  protected final void recruitImp(int maxDistanceSquared) {
    if (monster.nativeOwnerId >= 0 && nativeLifePercent(monster.nativeOwnerId) > 0) return;
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class)).getEntities();
    Vector2 source = mPosition.get(entityId).position;
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (id == entityId || !nativeNearbyMonster(id) || nativeLifePercent(id) <= 0) continue;
      Monster imp = mMonster.get(id);
      if (!"imp1".equals(imp.monstats.BaseId) || imp.monstats.Align != 0
          || imp.converted != monster.converted || imp.nativeRequestedMountId != -1
          || source.dst2(mPosition.get(id).position) > maxDistanceSquared) continue;
      int old = imp.nativeRequestedMountId;
      if (old < 0 || !mPosition.has(old)
          || source.dst2(mPosition.get(id).position)
              <= mPosition.get(old).position.dst2(mPosition.get(id).position)) {
        imp.nativeRequestedMountId = entityId;
        com.riiablo.engine.server.component.NativePathIdentity identity = Riiablo.engine
            .getMapper(com.riiablo.engine.server.component.NativePathIdentity.class).get(entityId);
        imp.nativeRequestedMountIdentity = identity == null ? null : identity.token();
      }
      return;
    }
  }

  @Override protected void thinkWithoutTarget() { idle(params[0]); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    recruitImp(400); idle(params[0]);
  }
}
