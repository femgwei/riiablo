package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.UnitState;

/** D2MOO AITHINK_Fn071_Regurgitator and the soft-corpse search callback. */
public class Regurgitator extends NativeDecisionAI {
  private int phase, attempts;
  private int food = Engine.INVALID_ENTITY;
  private NativePathIdentity.Token foodIdentity;
  public Regurgitator(int entityId) { super(entityId); }

  private boolean deadCorpse(int id) {
    return id >= 0 && mMonster.has(id) && mPosition.has(id) && mCofReference.has(id)
        && mCofReference.get(id).mode == Engine.Monster.MODE_DD;
  }
  private float squaredDistance(int id) {
    return mPosition.get(entityId).position.dst2(mPosition.get(id).position);
  }
  private void resetFood() {
    phase = attempts = 0; food = Engine.INVALID_ENTITY; foodIdentity = null;
  }
  private void selectFood(int id) {
    food = id;
    NativePathIdentity identity = Riiablo.engine.getMapper(NativePathIdentity.class).get(id);
    foodIdentity = identity == null ? null : identity.token();
    attempts = 0;
  }
  private int findFood() {
    int selected = Engine.INVALID_ENTITY;
    float nearest = params[5] * params[5];
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (id == entityId || !nativeNearbyMonster(id) || !deadCorpse(id)
          || mMercenary.has(id)) continue;
      Monster candidate = mMonster.get(id);
      NativeUnitFlags flags = mNativeUnitFlags.get(id);
      if (candidate.monstats.Align != 0 || candidate.converted || candidate.shatteredAtDeath
          || candidate.monstats2 == null || !candidate.monstats2.soft
          || flags != null && !flags.has(NativeUnitFlags.TARGETABLE)) continue;
      UnitStates states = mUnitStates.get(id);
      boolean undeadState = false;
      if (states != null && states.stateList != null) {
        for (UnitState state : states.stateList.getStates()) {
          com.riiablo.codec.excel.States.Entry row = Riiablo.files.States.get(state.stateId);
          if (row != null && row.undead) { undeadState = true; break; }
        }
      }
      if (undeadState) continue;
      float distance = squaredDistance(id);
      if (distance <= nearest) { selected = id; nearest = distance; }
    }
    return selected;
  }
  private void approachFood(int id) {
    pathfinder.findNativePath(entityId, id, 1, 0);
  }
  @Override protected void think(int targetId, float distance, boolean combat) {
    if (phase == 2 || phase == 3) {
      if (!deadCorpse(food) || foodIdentity != null
          && !foodIdentity.isCurrent(Riiablo.engine, food)) {
        resetFood(); walkClose(8, 0); return;
      }
      if (phase == 2) {
        if (squaredDistance(food) > 4) {
          if (attempts >= 6) { resetFood(); walkClose(8, 0); return; }
          approachFood(food); attempts++;
        }
        phase = 3; idle(8); return;
      }
      if (hasSkill(0)) { cast(0, food); phase = 4; return; }
      resetFood(); walkClose(8, 0); return;
    }
    if (phase == 4) {
      if (combat) escape(targetId, 8, 0);
      else { attack(targetId, Engine.Monster.MODE_A2); phase = 5; }
      return;
    }
    if (phase == 5) { escape(targetId, 16, 0); phase = 0; return; }
    int candidate = findFood();
    if (phase == 1) {
      if (candidate < 0) {
        if (rollAiPercentage() < 20) resetFood(); else walkClose(8, 0);
        return;
      }
      selectFood(candidate);
      if (squaredDistance(candidate) <= 2) { phase = 3; idle(8); }
      else { phase = 2; approachFood(candidate); }
      return;
    }
    if (candidate >= 0 && (squaredDistance(candidate) < 9 && rollAiChance(params[1])
        || rollAiChance(params[4]))) {
      selectFood(candidate); phase = 2; approachFood(candidate); return;
    }
    if (combat) {
      if (rollAiChance(params[0])) attack(targetId); else idle(15);
    } else if (rollAiChance(params[2])) approach(targetId, 0);
    else if (candidate >= 0 && rollAiChance(params[3])) {
      selectFood(candidate); phase = 2; approachFood(candidate);
    } else idle(8);
  }
}
