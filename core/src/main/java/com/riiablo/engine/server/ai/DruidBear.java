package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.MapWrapper;

/** Native DruidBear control for the Grizzly summon. */
public final class DruidBear extends DruidPet {
  private static final int OWNER_TELEPORT_DISTANCE = 50;
  private static final int OWNER_RUN_DISTANCE = 28;
  private static final int OWNER_WALK_DISTANCE = 18;
  private static final int OWNER_IDLE_DISTANCE = 16;
  private static final int TARGET_RANGE = 28;
  private final Ray<Vector2> approachRay = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> approachCollision = new Collision<>(new Vector2(), new Vector2());

  public DruidBear(int entityId) { super(entityId); }

  @Override public void update(float delta) {
    if (monster == null || !mPosition.has(entityId) || !alive()) return;
    nextThink -= Math.max(0f, delta);
    if (nextThink > 0f) return;
    nextThink = Math.max(0.15f, SLEEP);
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;
    int owner = ownerId();
    if (owner < 0 || !mPosition.has(owner)) {
      idleFor(10);
      state = "IDLE_NO_OWNER";
      return;
    }

    // Fn112 uses AIUTIL_GetDistanceToCoordinates_FullUnitSize. The global
    // SummonedPetSystem normally performs the mode-3 relocation above 50, but
    // detached worlds without that system can only request a walk fallback.
    int ownerDistance = fullSourceSizeDistance(owner);
    if (ownerDistance > OWNER_TELEPORT_DISTANCE) {
      state = "REGROUP";
      walkTo(mPosition.get(owner).position, 100, owner);
      return;
    }
    if (ownerDistance > OWNER_RUN_DISTANCE) {
      state = "REGROUP";
      walkTo(mPosition.get(owner).position, 100, owner);
      return;
    }
    if (ownerDistance > OWNER_WALK_DISTANCE && regroupWithOwner(owner)) return;

    float[] distance = { Float.MAX_VALUE };
    // Fn112 searches the bear's own range before falling back to the owner's
    // potential target. The generic pet helper reverses those priorities.
    targetId = findNearestOrdinaryEnemy(distance, TARGET_RANGE);
    if (targetId < 0 || !hasClearApproach(targetId)) {
      targetId = ownerTargetFallback();
    }
    if (targetId < 0 || !mPosition.has(targetId)) {
      if (ownerDistance <= OWNER_IDLE_DISTANCE) {
        idleFor(15);
        state = "IDLE";
      } else {
        state = "FOLLOW";
        walkTo(mPosition.get(owner).position, 0, owner);
      }
      return;
    }

    Vector2 target = mPosition.get(targetId).position;
    if (isInNativeMeleeRange(targetId)) {
      // Fn112 rolls skill-vs-ordinary attack using aip3. Choosing A1
      // delays its next decision for aip1 frames without cancelling A1.
      if (rollAiChance(param(2))) {
        if (useMonsterSkill(0, targetId, target)) state = "SMITE";
        else { idleFor(10); state = "IDLE"; }
        return;
      }
      if (ordinaryAttack(targetId)) {
        idleFor(param(0));
        return;
      }
      idleFor(param(0));
      return;
    }

    // AITHINK_Fn112 sets the temporary velocity to the native run bonus and
    // then calls WalkToTargetUnit. This is a faster walk animation, not the
    // player's run mode.
    int velocityBonus = 0;
    if (rollAiChance(param(1))) velocityBonus = runVelocityBonus();
    state = "APPROACH";
    if (!walkTo(target, velocityBonus, targetId)) {
      idleFor(10);
    }
  }

  private int param(int index) {
    return params != null && index >= 0 && index < params.length
        ? Math.max(0, params[index]) : 0;
  }

  private int runVelocityBonus() {
    if (monster == null || monster.monstats == null || monster.monstats.Velocity <= 0) return 100;
    return Math.min(100,
        100 * monster.monstats.Run / monster.monstats.Velocity - 100);
  }

  private int fullSourceSizeDistance(int owner) {
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(owner).position;
    int size = mSize.has(entityId) ? Math.max(0, mSize.get(entityId).size) : 0;
    int dx = Math.abs(Math.abs(Math.round(source.x) - Math.round(target.x)) - size);
    int dy = Math.abs(Math.abs(Math.round(source.y) - Math.round(target.y)) - size);
    int min = Math.min(dx, dy), max = Math.max(dx, dy);
    return (min + 2 * max) / 2;
  }

  private boolean regroupWithOwner(int owner) {
    CofReference ownerCof = mCofReference.has(owner) ? mCofReference.get(owner) : null;
    int mode = ownerCof == null ? Engine.Player.MODE_NU : ownerCof.mode;
    boolean moved = false;
    if (mode == Engine.Player.MODE_WL || mode == Engine.Player.MODE_TW) {
      moved = walkTo(mPosition.get(owner).position, 0, owner);
    } else if (mode == Engine.Player.MODE_RN) {
      moved = walkTo(mPosition.get(owner).position, 100, owner);
    }
    if (moved) state = "REGROUP";
    return moved;
  }

  private int ownerTargetFallback() {
    int ownerTarget = summonOwnerTarget();
    if (ownerTarget != Engine.INVALID_ENTITY && isValidEnemyTarget(ownerTarget)
        && mPosition.has(ownerTarget)
        && fullSourceSizeDistance(ownerTarget) < TARGET_RANGE
        && hasClearApproach(ownerTarget)) {
      return ownerTarget;
    }
    return Engine.INVALID_ENTITY;
  }

  private boolean hasClearApproach(int targetId) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (source == null || source.map == null || source.zone == null) return true;
    Vector2 start = mPosition.get(entityId).position;
    Vector2 end = mPosition.get(targetId).position;
    int distance = fullSourceSizeDistance(targetId);
    int offset = distance < 3 ? 2 : distance < 11 ? 3 : distance < 25 ? 4 : 3;
    int offsetX = Integer.compare(Math.round(start.x), Math.round(end.x)) * offset;
    int offsetY = Integer.compare(Math.round(start.y), Math.round(end.y)) * offset;
    approachRay.start.set(start);
    // AIUTIL_HasClearApproachToTarget tests the centre and two perpendicular
    // points at the target edge. All three must be blocked for the approach
    // to be rejected; this is different from a single centre-line ray.
    for (int i = 0; i < 3; i++) {
      if (i == 1) approachRay.end.set(end.x - offsetY, end.y + offsetX);
      else if (i == 2) approachRay.end.set(end.x + offsetY, end.y - offsetX);
      else approachRay.end.set(end);
      if (!source.map.castRay(source.zone, approachRay, 0xC01, 0, approachCollision, false)) return true;
    }
    return false;
  }

  private void idleFor(int frames) {
    stopMovement();
    nextThink = Math.max(1, frames) * Animation.FRAME_DURATION;
  }

  @Override public void onOwnerWarp() {
    super.onOwnerWarp();
    nextThink = 0f;
    targetId = Engine.INVALID_ENTITY;
    state = "IDLE";
  }
}
