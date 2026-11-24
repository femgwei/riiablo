package com.riiablo.engine.server.ai;

import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.CorpseConsumption;
import com.riiablo.engine.server.skill.SkillFormula;

/** D2MOO Fn111 for Carrion Vine and Solar Creeper, distinct from poison Fn110. */
public final class CycleOfLife extends Vines {
  public CycleOfLife(int entityId) {
    super(entityId);
  }

  @Override public void update(float delta) {
    if (monster == null || !mPosition.has(entityId)) return;
    if (mAttributesWrapper.has(entityId) && mAttributesWrapper.get(entityId).attrs != null
        && mAttributesWrapper.get(entityId).attrs.getValue(Stat.hitpoints, 1f) <= 0f) return;
    float elapsed = Math.max(0f, delta);
    nextThink -= elapsed;
    // A strict '< gameFrame' deadline must remain distinguishable from equality.
    // These inherited clocks are already part of Vines' checkpoint schema.
    skillCooldown -= elapsed;
    if (nextThink > 0f) return;
    nextThink = Math.max(Animation.FRAME_DURATION, SLEEP);
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;
    int owner = mSummonedPet.has(entityId) ? mSummonedPet.get(entityId).ownerId : -1;
    if (owner < 0 || !mPosition.has(owner)) {
      stopMovement();
      state = "IDLE_NO_OWNER";
      return;
    }

    // PetMove mode 3 is projected through the existing owner-follow service;
    // do not let a nearby corpse override Fn111's owner leash.
    if (unitDistance(owner) >= param(4)) {
      state = followSummonOwner(6f) ? "REGROUP" : "IDLE";
      return;
    }

    Skills.Entry skill = skillForSlot(0);
    int corpse = Engine.INVALID_ENTITY;
    if (skill != null && skill.srvstfunc == 63) {
      int range = Math.max(5, Math.min(50,
          SkillFormula.evaluate(skill.aurarangecalc, skill, resolveMonsterSkillLevel(0))));
      corpse = findUsableCorpse(range);
      // Native selects a corpse first and applies this second distance gate
      // afterwards. It does not rescan for a nearer corpse if the gate fails.
      if (corpse >= 0 && unitDistance(corpse) >= param(1)) corpse = Engine.INVALID_ENTITY;
    }
    MapWrapper map = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    boolean town = map != null && map.zone != null && map.zone.isTown();
    if ((corpse < 0 || town) && followSummonOwner(6f)) {
      state = "FOLLOW";
      return;
    }

    if (corpse >= 0 && isInNativeMeleeRange(corpse)
        && ownerNeedsResource(skill) && skillCooldown < 0f) {
      state = useMonsterSkill(0, corpse, mPosition.get(corpse).position) ? "CAST" : "WAIT_CAST";
      skillCooldown = param(0) * Animation.FRAME_DURATION;
      return;
    }

    // Fn111 escapes a living melee enemy on a 25% roll, independently of
    // the corpse's poison state/resistance. Fn110's poison escape is wrong here.
    int threat = findNearestOrdinaryEnemy(new float[] {Float.MAX_VALUE}, 35f);
    if (threat >= 0 && isInNativeMeleeRange(threat) && rollAiChance(25)) {
      Vector2 at = mPosition.get(entityId).position;
      Vector2 enemy = mPosition.get(threat).position;
      int distance = param(3);
      Vector2 away = new Vector2(Math.round(at.x) + Integer.compare(Math.round(at.x),
          Math.round(enemy.x)) * distance, Math.round(at.y) + Integer.compare(Math.round(at.y),
          Math.round(enemy.y)) * distance);
      state = "ESCAPE";
      walkTo(away, 0, Engine.INVALID_ENTITY);
      return;
    }
    if (corpse >= 0) {
      // Even with a full resource bar Fn111 may approach a corpse; only
      // starting SrvSt63 is gated by the owner's resource deficit.
      state = "APPROACH";
      walkTo(mPosition.get(corpse).position, corpse);
      return;
    }
    stopMovement();
    state = "IDLE";
    nextThink = Math.max(1, param(2)) * Animation.FRAME_DURATION;
  }

  private int param(int index) {
    return params != null && params.length > index ? Math.max(0, params[index]) : 0;
  }

  /** D2Common #10397, with half of each unit's footprint on both axes. */
  private int unitDistance(int target) {
    Vector2 a = mPosition.get(entityId).position, b = mPosition.get(target).position;
    int size = (mSize.has(entityId) ? Math.max(0, mSize.get(entityId).size) / 2 : 0)
        + (mSize.has(target) ? Math.max(0, mSize.get(target).size) / 2 : 0);
    int dx = Math.max(0, Math.abs(Math.round(a.x) - Math.round(b.x)) - size);
    int dy = Math.max(0, Math.abs(Math.round(a.y) - Math.round(b.y)) - size);
    return Math.max(dx, dy) + Math.min(dx, dy) / 2;
  }

  private int findUsableCorpse(int radius) {
    if (corpseEntities == null) return Engine.INVALID_ENTITY;
    Vector2 at = mPosition.get(entityId).position;
    IntBag corpses = corpseEntities.getEntities();
    for (int i = 0; i < corpses.size(); i++) {
      int id = corpses.get(i);
      if (id == entityId || !mPosition.has(id) || !mMonster.has(id)
          || !mCorpse.has(id) || !mAttributesWrapper.has(id)) continue;
      Monster candidate = mMonster.get(id);
      // Dead units cannot pass AI.isValidEnemyTarget's live-unit filter.
      if (candidate.monstats == null || candidate.monstats.Align != 0 || candidate.converted
          || mSummonedPet.has(id) || mMercenary.has(id)) continue;
      if (mMapWrapper.has(entityId) && mMapWrapper.has(id)) {
        MapWrapper source = mMapWrapper.get(entityId), target = mMapWrapper.get(id);
        if (source.map != null && target.map != null && source.map != target.map) continue;
        if (source.zone != null && target.zone != null && source.zone != target.zone) continue;
        Vector2 p = mPosition.get(id).position;
        if (source.zone != null && source.zone == target.zone
            && !source.zone.areRoomsAdjacent(at.x, at.y, p.x, p.y)) continue;
      }
      UnitStates states = mUnitStates.has(id) ? mUnitStates.get(id) : null;
      if (!CorpseConsumption.selectable(mCorpse.get(id), candidate,
          mAttributesWrapper.get(id).attrs, states == null ? null : states.stateList)) continue;
      Vector2 target = mPosition.get(id).position;
      int dx = Math.round(at.x) - Math.round(target.x);
      int dy = Math.round(at.y) - Math.round(target.y);
      if ((long) dx * dx + (long) dy * dy <= (long) radius * radius) return id;
    }
    return Engine.INVALID_ENTITY;
  }
}
