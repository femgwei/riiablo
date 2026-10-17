package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;

/** D2MOO AITHINK_Fn068_VileMother, including directional spawn probes. */
public class VileMother extends NativeDecisionAI {
  private int spawned;
  public VileMother(int entityId) { super(entityId); }

  private MonStats.Entry childVariant() {
    MonStats.Entry mother = Riiablo.files.monstats.get(monster.monstats.BaseId);
    MonStats.Entry child = Riiablo.files.monstats.get("vilechild1");
    for (int i = 0; i < 128 && mother != null && child != null
        && mother.hcIdx != monster.monstats.hcIdx; i++) {
      mother = Riiablo.files.monstats.get(mother.NextInClass);
      child = Riiablo.files.monstats.get(child.NextInClass);
    }
    return child;
  }

  private int childrenNearby(MonStats.Entry child) {
    if (child == null) return 0;
    int count = 0;
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (id != entityId && nativeNearbyMonster(id) && nativeLifePercent(id) > 0
          && mMonster.get(id).monstats.hcIdx == child.hcIdx
          && mPosition.get(entityId).position.dst2(mPosition.get(id).position) <= 25) count++;
    }
    return count;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (spawned < params[0] && monster.monstats.Align == 0 && !monster.converted
        && rollAiChance(params[2]) && childrenNearby(childVariant()) < params[1]) {
      MapWrapper owner = mMapWrapper.get(entityId);
      Vector2 source = mPosition.get(entityId).position;
      Vector2 target = mPosition.get(targetId).position;
      int direction = com.d2moo.common.path.D2PathStep.computeDirection(
          (int) source.x, (int) source.y, (int) target.x, (int) target.y);
      int index = ((direction + 4) / 8) & 7;
      int[] probes = {6, 4, 2, 2, 2, 0, 6, 6};
      int[] dx = {0, -1, -1, -1, 0, 1, 1, 1};
      int[] dy = {-1, -1, 0, 1, 1, 1, 0, -1};
      for (int i = 0; i < 8; i++, index = (index + 1) & 7) {
        int x = (int) source.x + 3 * dx[probes[index]];
        int y = (int) source.y + 3 * dy[probes[index]];
        if (hasSkill(0) && owner != null && owner.map != null && owner.zone != null
            && !owner.map.isBlockedForPath(owner.zone, x, y, 0x1C09,
                monsterFootprint("vilemother1"), false)) {
          castAt(0, Engine.INVALID_ENTITY, new Vector2(x, y)); spawned++; return;
        }
      }
    }
    if (combat) {
      if (rollAiChance(params[3])) attack(targetId); else idle(params[6]);
    } else if (spawned >= params[0] || rollAiChance(params[4])) approach(targetId, 0);
    else if (rollAiChance(params[5])) circle(targetId, 4);
    else idle(15);
  }
}
