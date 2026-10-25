package com.riiablo.engine.server.ai;

import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.MapWrapper;

/** D2MOO AITHINK_Fn114_ReanimatedHorde, 0x6FCE0050. */
public class ReanimatedHorde extends NativeDecisionAI {
  private float resurrectionDelay = -1f;
  private boolean deathScheduled;
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out); com.riiablo.engine.replay.CheckpointIO.writeFloat(out, resurrectionDelay); out.writeBoolean(deathScheduled);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply); float delay = com.riiablo.engine.replay.CheckpointIO.readFloat(in);
    boolean scheduled = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    if (apply) { resurrectionDelay = delay; deathScheduled = scheduled; }
  }
  public ReanimatedHorde(int entityId) { super(entityId); }

  @Override protected boolean recoverRadiusMovement(com.badlogic.gdx.math.Vector2 destination,
      int budget, int bonus) {
    return RadiusMovementRecovery.recover(this, destination, budget, bonus);
  }

  @Override public void kill() {
    if (deathScheduled) return;
    deathScheduled = true;
    super.kill();
    // MonsterUnique.cpp sub_6FC6DD20 schedules the native MONUMOD callback.
    if (monster.nativeSelfResurrect && !monster.converted && monster.monstats.Align == 0
        && !monster.shatteredAtDeath && rollAiChance(params[7])) {
      resurrectionDelay = (10 * params[0] + 1) * Animation.FRAME_DURATION;
    }
  }

  @Override protected void thinkWhileDead(float delta) {
    if (resurrectionDelay < 0f) return;
    resurrectionDelay -= Math.max(0f, delta);
    if (resurrectionDelay > 0f) return;
    resurrectionDelay = -1f;
    if (monster.shatteredAtDeath || monster.converted || monster.monstats.Align != 0
        || com.riiablo.engine.server.monster.MonsterRank.isUnique(monster.rank)
        || monster.nativeSelfResurrections >= 2 || factory == null) return;
    com.riiablo.engine.server.component.UnitStates states = mUnitStates.get(entityId);
    if (states != null && states.stateList != null) {
      for (com.riiablo.engine.server.state.UnitState state : states.stateList.getStates()) {
        com.riiablo.codec.excel.States.Entry definition = files().States.get(state.stateId);
        if (definition != null && definition.undead) return;
      }
    }
    MapWrapper owner = mMapWrapper.get(entityId);
    com.badlogic.gdx.math.Vector2 position = mPosition.get(entityId).position;
    if (owner != null && owner.map != null && owner.zone != null
        && owner.map.isBlockedForPath(owner.zone, (int) position.x,
            (int) position.y, 0x1C09, 1, false)) {
      resurrectionDelay = (10 * params[0] + 1) * Animation.FRAME_DURATION;
      return;
    }
    // The factory owns corpse reservation, full life, movement restoration,
    // AI replacement and the configured SrvSt61 resurrection animation.
    if (factory.resurrectMonster(entityId, entityId)) {
      monster.nativeSelfResurrections++;
      com.riiablo.engine.server.component.AIWrapper replacement = com.riiablo.Riiablo.engine
          .getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(entityId);
      if (replacement != null && replacement.ai instanceof ReanimatedHorde)
        ((ReanimatedHorde) replacement.ai).delayDecision(51);
    }
  }
  @Override protected boolean beforeDecision() {
    mNativeUnitFlags.create(entityId).set(NativeUnitFlags.MONSTER_TARGET);
    return true;
  }
  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      if (rollAiChance(params[0])) attack(targetId); else idle(params[1]);
    } else if (hasSkill(1) && chargeLineClear(targetId) && distance < params[2]
        && distance > 5 && rollAiChance(params[3])) cast(1, targetId);
    else if (rollAiChance(params[4])) approach(targetId, 0);
    else if (rollAiChance(params[5])) walkInRadius(targetId, 4, 0, 0);
    else idle(params[6]);
  }
}
