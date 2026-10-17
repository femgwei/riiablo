package com.riiablo.engine.server.ai;

import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.state.UnitState;

/** D2MOO AiThink.cpp Fn022 (0x6FCD48B0) and its GreaterMummy target callback. */
public class GreaterMummy extends NativeDecisionAI {
  private int healTarget;
  private int reviveTarget;
  private int candidates;

  public GreaterMummy(int entityId) { super(entityId); }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat && rollAiChance(params[0])) {
      attack(targetId);
      return;
    }
    if (distance < 5 && rollAiChance(params[0])) {
      attack(targetId, Engine.Monster.MODE_A2);
      return;
    }
    scanAllies();
    if (hasSkill(1) && healTarget != Engine.INVALID_ENTITY && rollAiChance(params[2])) {
      castMummySkill(1, healTarget);
      return;
    }
    if (reviveTarget != Engine.INVALID_ENTITY && rollAiChance(params[1]) && hasSkill(0)
        && mCorpse.has(reviveTarget) && mCorpse.get(reviveTarget).usable
        && !mCorpse.get(reviveTarget).fading) {
      castMummySkill(0, reviveTarget);
      return;
    }
    if (hasSkill(2) && rollAiChance(params[3])) {
      castMummySkill(2, targetId);
      return;
    }
    if (candidates == 0) approach(targetId, 50);
    else if (rollAiPercentage() < 50) circle(targetId, 3);
    else idle(6);
    // Shared path lifecycle, not native three-step path truncation.
  }

  private void castMummySkill(int slot, int target) {
    // 1.10f seq_mummyres is SC frames 0..15, missile action at 13.
    // SC is a real mummy COF; do not resolve the sequence name as XX.
    if (castAt(slot, target, mPosition.get(target).position, Engine.Monster.MODE_SC)) {
      mSequence.get(entityId).nativeMummy = true;
    }
  }

  /** Native callback keeps the last eligible heal/corpse candidate, not the nearest. */
  void scanAllies() {
    healTarget = reviveTarget = Engine.INVALID_ENTITY;
    candidates = 0;
    if (monster.converted || monster.monstats.Align != 0 || mSummonedPet.has(entityId)
        || mMercenary.has(entityId)) return;
    boolean radament = "radament".equalsIgnoreCase(monster.monstats.Id);
    boolean allowUnique = radament && resolveAiDifficulty() == 0;
    long range = params[4] + (radament ? 10L : 0L);
    long range2 = range * range;
    IntBag units = getEnemyEntities().getEntities();
    for (int i = 0; i < units.size(); i++) {
      int id = units.get(i);
      if (id == entityId || !mMonster.has(id) || mPlayer.has(id)
          || mSummonedPet.has(id) || mMercenary.has(id)) continue;
      Monster candidate = mMonster.get(id);
      if (candidate.monstats == null || candidate.converted || candidate.monstats.Align != 0
          || !candidate.monstats.lUndead && !(radament && candidate.monstats.hUndead)
          || !allowUnique && MonsterRank.isUnique(candidate.rank)
          || !mNativeUnitFlags.has(id)
          || !mNativeUnitFlags.get(id).has(NativeUnitFlags.TARGETABLE)
          || hasStateMask(id, false) || !inCallbackRooms(id) || squaredDistance(id) > range2) continue;
      candidates++;
      int mode = mCofReference.has(id) ? mCofReference.get(id).mode : Engine.INVALID_MODE;
      if (mode == Engine.Monster.MODE_DD && !hasStateMask(id, true)) {
        reviveTarget = id;
      } else if (mode != Engine.Monster.MODE_DT && mAttributesWrapper.has(id)
          && isBestowEligible(candidate, mAttributesWrapper.get(id).attrs)) {
        healTarget = id;
      }
    }
  }

  int healTarget() { return healTarget; }
  int reviveTarget() { return reviveTarget; }

  private boolean hasStateMask(int id, boolean hide) {
    UnitStates states = mUnitStates.has(id) ? mUnitStates.get(id) : null;
    if (states == null || states.stateList == null) return false;
    for (UnitState state : states.stateList.getStates()) {
      States.Entry entry = files().States.get(state.stateId);
      if (entry != null && (hide ? entry.hide : entry.undead)) return true;
    }
    return false;
  }

  private long squaredDistance(int id) {
    Vector2 a = mPosition.get(entityId).position;
    Vector2 b = mPosition.get(id).position;
    long dx = (long) Math.round(a.x) - Math.round(b.x);
    long dy = (long) Math.round(a.y) - Math.round(b.y);
    return dx * dx + dy * dy;
  }

  private boolean inCallbackRooms(int id) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    MapWrapper target = mMapWrapper.has(id) ? mMapWrapper.get(id) : null;
    if (source != null && target != null && source.map != null && target.map != null
        && source.map != target.map) return false;
    if (source == null || source.zone == null) return target == null || target.zone == null;
    if (target == null || target.map != source.map || target.zone == null) return false;
    if (source.zone != target.zone)
      return source.map != null && source.map.areZonesAdjacent(source.zone, target.zone);
    Vector2 a = mPosition.get(entityId).position;
    Vector2 b = mPosition.get(id).position;
    return source.zone.areRoomsAdjacent(a.x, a.y, b.x, b.y);
  }

  public static boolean isBestowEligible(Monster candidate, Attributes attrs) {
    if (candidate == null || candidate.monstats == null || attrs == null
        || !candidate.monstats.lUndead && !candidate.monstats.hUndead
        || !attrs.contains(Stat.hitpoints) || !attrs.contains(Stat.maxhp)) return false;
    float hp = attrs.getValue(Stat.hitpoints, 0f);
    float max = attrs.getValue(Stat.maxhp, 0f);
    return hp > 0f && hp < max;
  }
}
