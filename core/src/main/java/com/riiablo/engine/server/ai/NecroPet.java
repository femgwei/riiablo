package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.skill.SkillCodes;

/**
 * Native {@code AITHINK_Fn067_NecroPet} control flow used by Necromancer
 * golems and by monsters converted through Revive's special AI state.
 *
 * <p>D2 stores the branch selector in AI-control runtime parameter 0. The
 * current Java AI bridge initializes that control snapshot from the active
 * MonStats AI parameters, while {@link #useSkillBranch(boolean)} lets a caller
 * restore an explicit runtime value. Skill mode is only enabled when Skill1 is
 * present, so ordinary golems remain on the melee branch.</p>
 */
public final class NecroPet extends AI {
  static final float REGROUP_DISTANCE = 28f;
  static final float ORDINARY_SCAN_DISTANCE = 24f;
  static final float ORDINARY_KEEP_DISTANCE = 6f;
  static final float OWNER_TARGET_DISTANCE = 36f;
  static final float SKILL_SCAN_DISTANCE = 15f;
  static final float SKILL_OWNER_TARGET_DISTANCE = 20f;
  static final float FOLLOW_STOP_DISTANCE = 4f;
  static final float THINK_DELAY = 10f / 25f;

  private boolean skillBranch;
  private boolean skillBranchConfigured;
  float nextThink;
  int targetId = Engine.INVALID_ENTITY;
  String state = "IDLE";

  public NecroPet(int entityId) {
    super(entityId);
  }

  @Override
  public void initialize() {
    super.initialize();
    if (!skillBranchConfigured) {
      skillBranch = params.length > 0 && params[0] != 0
          && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty();
    }
  }

  /** Mirrors a non-zero native AI-control runtime parameter 0. */
  public NecroPet useSkillBranch(boolean enabled) {
    skillBranch = enabled;
    skillBranchConfigured = true;
    return this;
  }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId) || !isAlive()) return;

    nextThink -= Math.max(0f, delta);
    if (nextThink > 0f) return;
    nextThink = THINK_DELAY;

    // AITACTICS actions remain authoritative until Actioneer consumes their
    // animation keyframe and finish event.
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;

    int ownerId = ownerId();
    if (ownerId == Engine.INVALID_ENTITY || !mPosition.has(ownerId)) {
      targetId = Engine.INVALID_ENTITY;
      stopMovement();
      state = "IDLE_NO_OWNER";
      return;
    }

    float ownerDistance = mPosition.get(entityId).position.dst(
        mPosition.get(ownerId).position);
    if (ownerDistance > REGROUP_DISTANCE) {
      targetId = Engine.INVALID_ENTITY;
      state = followSummonOwner(FOLLOW_STOP_DISTANCE) ? "REGROUP" : "IDLE";
      return;
    }

    if (isInTown()) {
      targetId = Engine.INVALID_ENTITY;
      state = followSummonOwner(FOLLOW_STOP_DISTANCE) ? "FOLLOW_TOWN" : "IDLE_TOWN";
      if (!state.equals("FOLLOW_TOWN")) stopMovement();
      return;
    }

    float[] distance = { Float.MAX_VALUE };
    targetId = skillBranch
        ? selectSkillTarget(targetId, distance)
        : selectOrdinaryTarget(targetId, distance);
    if (targetId == Engine.INVALID_ENTITY) {
      state = followSummonOwner(FOLLOW_STOP_DISTANCE) ? "FOLLOW" : "IDLE";
      if (!state.equals("FOLLOW")) stopMovement();
      return;
    }

    Vector2 target = mPosition.get(targetId).position;
    if (skillBranch) {
      if (rollAiChance(80) && useMonsterSkill(0, targetId, target)) {
        state = "CAST";
        return;
      }
      // Native uses a second roll: 25% of the non-cast branch repositions,
      // otherwise it idles for ten frames.
      if (!rollAiChance(75)) {
        state = "REPOSITION";
        walkTo(target, targetId);
      } else {
        state = "IDLE";
        stopMovement();
      }
      return;
    }

    float melee = 1f + (monster.monstats2 != null ? monster.monstats2.MeleeRng : 0);
    if (distance[0] <= melee) {
      stopMovement();
      lookAt(targetId);
      if (rollAiChance(80)) {
        state = "ATTACK";
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(SkillCodes.attack, targetId, target);
        Riiablo.audio.play(monsound + "_attack_1", true);
      } else {
        state = "IDLE_COMBAT";
      }
      return;
    }

    state = "APPROACH";
    walkTo(target, targetId);
  }

  private int selectOrdinaryTarget(int previous, float[] outDistance) {
    int target = previous;
    if (target == Engine.INVALID_ENTITY || !isValidEnemyTarget(target)) {
      target = findNearestOrdinaryEnemy(outDistance, ORDINARY_SCAN_DISTANCE);
    } else {
      outDistance[0] = distanceTo(target);
    }
    if (target != Engine.INVALID_ENTITY && outDistance[0] <= ORDINARY_KEEP_DISTANCE) {
      return target;
    }
    return selectOwnerTarget(outDistance, OWNER_TARGET_DISTANCE);
  }

  private int selectSkillTarget(int previous, float[] outDistance) {
    int target = previous;
    if (target == Engine.INVALID_ENTITY || !isValidEnemyTarget(target)) {
      target = findNearestOrdinaryEnemy(outDistance, SKILL_SCAN_DISTANCE);
    } else {
      outDistance[0] = distanceTo(target);
    }
    if (target != Engine.INVALID_ENTITY && outDistance[0] <= SKILL_SCAN_DISTANCE) {
      return target;
    }
    return selectOwnerTarget(outDistance, SKILL_OWNER_TARGET_DISTANCE);
  }

  private int selectOwnerTarget(float[] outDistance, float maximumDistance) {
    int target = summonOwnerTarget();
    if (target == Engine.INVALID_ENTITY || !isValidEnemyTarget(target)) {
      outDistance[0] = Float.MAX_VALUE;
      return Engine.INVALID_ENTITY;
    }
    float distance = distanceTo(target);
    if (distance >= maximumDistance) {
      outDistance[0] = Float.MAX_VALUE;
      return Engine.INVALID_ENTITY;
    }
    outDistance[0] = distance;
    return target;
  }

  private float distanceTo(int otherId) {
    return mPosition.get(entityId).position.dst(mPosition.get(otherId).position);
  }

  private int ownerId() {
    if (!mSummonedPet.has(entityId)) return Engine.INVALID_ENTITY;
    SummonedPet pet = mSummonedPet.get(entityId);
    return pet != null ? pet.ownerId : Engine.INVALID_ENTITY;
  }

  private boolean isAlive() {
    if (!mAttributesWrapper.has(entityId)) return true;
    StatRef hp = mAttributesWrapper.get(entityId).attrs.get(
        Stat.hitpoints, StatRef.obtain());
    return hp == null || hp.asFixed() > 0f;
  }

  private boolean isInTown() {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper != null && wrapper.zone != null && wrapper.zone.isTown();
  }

  @Override
  public void onOwnerWarp() {
    super.onOwnerWarp();
    targetId = Engine.INVALID_ENTITY;
    nextThink = 0f;
    state = "IDLE";
  }

  @Override
  public void kill() {
    if (state.equals("DEAD")) return;
    stopMovement();
    targetId = Engine.INVALID_ENTITY;
    state = "DEAD";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    Riiablo.audio.play(monsound + "_death_1", true);
  }

  @Override
  public String getState() {
    return state;
  }
}
