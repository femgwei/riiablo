package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;

/** D2MOO AITHINK_Fn085_HighPriest and its lowest-life ally callback. */
public class HighPriest extends NativeDecisionAI {
  private long healHydraDeadline;
  private boolean engaged;

  public HighPriest(int entityId) { super(entityId); }


  private int woundedAlly() {
    int selected = Engine.INVALID_ENTITY, life = 75;
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class)).getEntities();
    Vector2 source = mPosition.get(entityId).position;
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (!nativeNearbyMonster(id) || mMonster.get(id).converted != monster.converted
          || mMonster.get(id).monstats.Align != 0
          || source.dst2(mPosition.get(id).position) > 2500) continue;
      int candidate = nativeLifePercent(id);
      if (candidate > 0 && candidate < life) { selected = id; life = candidate; }
    }
    return selected;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    long frame = nativeFrame();
    if (!engaged) {
      if (combat) {
        if (!rollAiChance(params[0])) escape(targetId, 6, 0);
        else { engaged = true; attack(targetId); }
        return;
      }
      if (hasSkill(1) && frame > healHydraDeadline && rollAiChance(params[1])) {
        int ally = woundedAlly();
        if (ally != Engine.INVALID_ENTITY) {
          healHydraDeadline = frame + params[2]; cast(1, ally); return;
        }
      }
      if (hasSkill(0) && frame > healHydraDeadline && distance < params[7]
          && rollAiChance(params[3])) {
        int index = (int) (rollAiRandomNumber() & 3L);
        int[] x = {-5, 5, 5, -5}, y = {-5, -5, 5, 5};
        Vector2 point = mPosition.get(targetId).position.cpy().add(x[index], y[index]);
        castAt(0, targetId, point); healHydraDeadline = frame + 100; return;
      }
      if (params[4] > 0) {
        Missiles.Entry missile = Riiablo.files.Missiles.get(monster.monstats.MissS1);
        if (missile != null && distance < missile.Range - 2 && rollAiChance(params[4])) {
          attack(targetId, Engine.Monster.MODE_S1); return;
        }
      }
      if (rollAiPercentage() < 80) {
        if (distance <= params[7]) circle(targetId, 3);
        else pathfinder.findNativePath(entityId, targetId, 6, 0);
        return;
      }
    }
    engaged = true;
    if (combat) {
      if (rollAiChance(params[6])) attack(targetId, Engine.Monster.MODE_S1);
      else if (rollAiChance(params[5])) { escape(targetId, 6, 0); engaged = false; }
      else if (rollAiPercentage() >= 90) idle(10);
      else attack(targetId);
      return;
    }
    if (distance < 6 && rollAiChance(params[6])) {
      attack(targetId, Engine.Monster.MODE_S1); return;
    }
    if (rollAiChance(params[5])) { engaged = false; idle(10); }
    else if (rollAiPercentage() >= 70) walkClose(12, 0);
    else approach(targetId, 0);
  }
}
