package com.riiablo.engine.server.ai;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.DiabPrison;
import com.riiablo.engine.server.event.DeathEvent;

/** Idle Fn000 plus MonsterUnique::KillMinions (0x6FC6CE50), no timed expiry. */
public class BonePrison extends AI {
  protected ComponentMapper<DiabPrison> mDiabPrison;
  private boolean dead;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeBoolean(dead);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    boolean savedDead = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    if (apply) {
      dead = savedDead;
    }
  }

  public BonePrison(int entityId) { super(entityId); }

  @Override public void kill() {
    if (dead) return;
    dead = true;
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    DiabPrison link = mDiabPrison.get(entityId);
    IntArray members = link == null ? null : link.members;
    if (members == null) return;
    IntArray liveMembers = new IntArray(members.size);
    // Clear the entire owner/minion relation before recursive death callbacks.
    for (int i = 0; i < members.size; i++) {
      int id = members.get(i);
      if (!mDiabPrison.has(id) || mDiabPrison.get(id).members != members) continue;
      liveMembers.add(id);
      mDiabPrison.get(id).members = null;
      if (mMonster.has(id)) mMonster.get(id).minionOwnerId = Engine.INVALID_ENTITY;
    }
    for (int i = 0; i < liveMembers.size; i++) {
      int id = liveMembers.get(i);
      if (id == entityId || !mMonster.has(id)) continue;
      if (mAttributesWrapper.has(id) && mAttributesWrapper.get(id).attrs != null
          && mAttributesWrapper.get(id).attrs.get(Stat.hitpoints) != null)
        mAttributesWrapper.get(id).attrs.get(Stat.hitpoints).set(0f);
      mSequence.create(id).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
      if (events != null) events.dispatch(DeathEvent.obtain(Engine.INVALID_ENTITY, id));
    }
  }

  @Override public String getState() { return dead ? "DEAD" : "IDLE"; }
}
