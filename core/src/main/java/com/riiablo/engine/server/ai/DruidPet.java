package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.skill.CorpseConsumption;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.skill.SkillCodes;

/** Shared owner/leash and ordinary attack behavior for Druid animal pets. */
abstract class DruidPet extends AI {
  protected float nextThink;
  protected int targetId = Engine.INVALID_ENTITY;
  protected String state = "IDLE";
  protected EntitySubscription corpses;

  DruidPet(int entityId) { super(entityId); }

  @Override public void initialize() {
    super.initialize();
    if (Riiablo.engine != null) corpses = Riiablo.engine.getAspectSubscriptionManager()
        .get(Aspect.all(Corpse.class, Monster.class, Position.class, AttributesWrapper.class));
  }

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out); AiRuntimeState.writeTimer(out, nextThink); out.writeInt(targetId);
    com.riiablo.engine.replay.CheckpointIO.writeString(out, state);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply); float timer = AiRuntimeState.readTimer(in); int target = in.readInt();
    String saved = com.riiablo.engine.replay.CheckpointIO.readString(in);
    if (apply) { nextThink = timer; targetId = target; state = saved; }
  }

  protected boolean alive() {
    if (!mAttributesWrapper.has(entityId)) return true;
    StatRef hp = mAttributesWrapper.get(entityId).attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp == null || hp.asFixed() > 0f;
  }

  protected int ownerId() {
    if (!mSummonedPet.has(entityId)) return Engine.INVALID_ENTITY;
    SummonedPet pet = mSummonedPet.get(entityId);
    return pet == null ? Engine.INVALID_ENTITY : pet.ownerId;
  }

  protected boolean ordinaryAttack(int victim) {
    if (!isInNativeMeleeRange(victim)) return false;
    stopMovement(); lookAt(victim); state = "ATTACK";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(SkillCodes.attack, victim, mPosition.get(victim).position);
    playSound(monsound + "_attack_1");
    return true;
  }

  protected int nearestCorpse(float maxDistance) {
    if (corpses == null || !mPosition.has(entityId)) return Engine.INVALID_ENTITY;
    Vector2 origin = mPosition.get(entityId).position;
    float best = maxDistance * maxDistance; int result = Engine.INVALID_ENTITY;
    IntBag entities = corpses.getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      if (id == entityId || !mPosition.has(id) || !mMonster.has(id)
          || !mCorpse.has(id) || !mAttributesWrapper.has(id)) continue;
      UnitStates states = mUnitStates.has(id) ? mUnitStates.get(id) : null;
      if (!CorpseConsumption.selectable(mCorpse.get(id), mMonster.get(id),
          mAttributesWrapper.get(id).attrs, states == null ? null : states.stateList)) continue;
      float distance = origin.dst2(mPosition.get(id).position);
      if (distance < best) { best = distance; result = id; }
    }
    return result;
  }

  protected boolean hasRage() {
    UnitStates states = mUnitStates.has(entityId) ? mUnitStates.get(entityId) : null;
    return states != null && states.stateList != null
        && states.stateList.hasState(StateId.FENRIS_RAGE);
  }

  @Override public String getState() { return state; }
}
