package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;

/** D2MOO Fn120 and the shared Overseer/Nihlathak minion callback. */
public class Overseer extends NativeDecisionAI {
  private long rallyDeadline;
  private int lastAttacker = Engine.INVALID_ENTITY;
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeLong(rallyDeadline);
    out.writeInt(lastAttacker);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    long savedRallyDeadline = in.readLong();
    int savedLastAttacker = in.readInt();
    if (apply) {
      rallyDeadline = savedRallyDeadline;
      lastAttacker = savedLastAttacker;
    }
  }

  public Overseer(int entityId) { super(entityId); }
  @Override public void onNativeHit(int attackerId) { lastAttacker = attackerId; }

  protected static final class Minions {
    int count;
    int wounded = Engine.INVALID_ENTITY;
    int ordinary = Engine.INVALID_ENTITY;
  }

  protected final Minions findMinions(int healRangeSquared, int whipRangeSquared) {
    Minions found = new Minions();
    IntBag ids = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (id == entityId || !nativeNearbyMonster(id) || nativeLifePercent(id) <= 0) continue;
      Monster minion = mMonster.get(id);
      if (!"minion1".equals(minion.monstats.BaseId)
          || minion.converted != monster.converted || minion.monstats.Align != monster.monstats.Align) continue;
      UnitStates states = mUnitStates.get(id);
      if (states != null && states.stateList != null && states.stateList.hasState(StateId.BLOODLUST)) continue;
      found.count++;
      float distance = mPosition.get(entityId).position.dst2(mPosition.get(id).position);
      if (found.wounded < 0 && distance <= healRangeSquared && nativeLifePercent(id) < 50)
        found.wounded = id;
      if (found.ordinary < 0 && distance <= whipRangeSquared && minion.rank < 2)
        found.ordinary = id;
    }
    return found;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    long frame = nativeFrame();
    if (hasSkill(0) && nativeUncontrolled() && frame > rallyDeadline
        && lastAttacker >= 0 && mPosition.has(lastAttacker)
        && (!mMonster.has(lastAttacker) || nativeLifePercent(lastAttacker) > 0)) {
      cast(0, lastAttacker); rallyDeadline = frame + params[0]; return;
    }
    if (combat && rollAiChance(params[5])) {
      attack(targetId, rollAiChance(params[6]) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1);
      return;
    }
    Minions minions = findMinions(576, 400);
    if (hasSkill(1) && minions.wounded >= 0 && rollAiChance(params[1])) {
      cast(1, minions.wounded); return;
    }
    if (hasSkill(2) && minions.ordinary >= 0 && rollAiChance(params[2])
        && monster.monstats.Align != 2 && !monster.converted) {
      cast(2, minions.ordinary); return;
    }
    if (minions.count != 0) {
      if (distance < params[3] - params[4]) {
        escape(targetId, (int) (params[3] - distance) & 255, 0); return;
      }
      if (distance > params[3] + params[4]) { approach(targetId, 0); return; }
      if (rollAiPercentage() < 50) { circle(targetId, 5); return; }
    } else if (rollAiPercentage() < 60) { approach(targetId, 0); return; }
    idle(10);
  }
}
