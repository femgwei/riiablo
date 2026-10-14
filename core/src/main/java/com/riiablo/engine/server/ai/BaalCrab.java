package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** D2MOO AiBaal.cpp Fn135 / MainSkillHandler. Boss target scoring pending. */
public class BaalCrab extends NativeDecisionAI {
  // Fixed SkillsIds.h constants, NOT zero-based MonStats slots.
  static final int SKILL_TENTACLE = 315;
  static final int SKILL_NOVA = 316;
  static final int SKILL_INFERNO = 317;
  static final int SKILL_COLD_MISSILES = 318;

  private final Vector2 anchor = new Vector2();
  private final Vector2 destination = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
  private boolean anchored;

  public BaalCrab(int entityId) { super(entityId); }

  @Override
  protected boolean beforeDecision() {
    if (!anchored) {
      anchor.set(Math.round(mPosition.get(entityId).position.x),
          Math.round(mPosition.get(entityId).position.y));
      anchored = true;
    }
    return true;
  }

  @Override
  protected void thinkWithoutTarget() {
    MapWrapper map = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    Vector2 position = mPosition.get(entityId).position;
    boolean blocked = map != null && map.map != null && map.zone != null
        && map.map.isBlockedForPath(map.zone, Math.round(position.x), Math.round(position.y),
            DT1.Tile.FLAG_BLOCK_JUMP, 2, false);
    execute(blocked ? 4 : rollAiRandomNumber() % 100 < 8 ? 9 : 1, Engine.INVALID_ENTITY);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    int count = 0;
    IntBag units = getEnemyEntities().getEntities();
    for (int i = 0; i < units.size(); i++) {
      if (isValidEnemyTarget(units.get(i))) ++count;
    }
    int sourceSize = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    int targetSize = mSize.has(targetId) ? mSize.get(targetId).size : 1;
    int fromAnchor = distanceWithSize(mPosition.get(targetId).position, anchor,
        (targetSize >>> 1) + 1);
    boolean barrier = hasBarrier(targetId);
    int[] table = weights(resolveAiDifficulty(), combat, barrier,
        distanceWithSize(mPosition.get(entityId).position, mPosition.get(targetId).position,
            sourceSize), lifePercent(entityId), lifePercent(targetId), isCold(targetId),
        count, fromAnchor > 75, fromAnchor > 100);
    if (!combat && !barrier) table[15] = cloneWeight(monster.baalClonesCreated);
    execute(selectAction(table, rollAiRandomNumber()), targetId);
  }

  /** Indices and fixed modes from AIBAAL_MainSkillHandler, exposed for branch tests. */
  void execute(int action, int targetId) {
    Vector2 target = targetId < 0 ? mPosition.get(entityId).position
        : mPosition.get(targetId).position;
    switch (action) {
      case 1:
        idle(resolveAiDifficulty() == 0 ? 35 : resolveAiDifficulty() == 1 ? 15 : 5);
        break;
      case 2:
      case 6:
        if (targetId >= 0) circle(targetId, 12f); else idle(5);
        break;
      case 3:
        if (targetId >= 0) circle(targetId, 6f); else idle(5);
        break;
      case 4:
        wander(16);
        break;
      case 5:
        if (!walkToPoint(anchor, 0, Engine.INVALID_ENTITY)) idle(10);
        break;
      case 8:
        if (targetId < 0) {
          idle(5);
        } else if (hasSkill(5) && (stat(targetId, Stat.maxhp, 0) > stat(targetId, Stat.maxmana, 0)
            || !mPlayer.has(targetId))) {
          cast(5, targetId);
        } else if (hasSkill(6) && mPlayer.has(targetId)) {
          cast(6, targetId);
        }
        break;
      case 9:
        castFixed(SKILL_TENTACLE, Engine.Monster.MODE_S2, targetId, target);
        break;
      case 10:
        if (targetId >= 0) attack(targetId, Engine.Monster.MODE_A2); else idle(5);
        break;
      case 11:
        castFixed(SKILL_NOVA, Engine.Monster.MODE_S3, targetId, target);
        break;
      case 12:
        castFixed(SKILL_INFERNO, Engine.Monster.MODE_S1, targetId, target);
        break;
      case 13:
        castFixed(SKILL_COLD_MISSILES, Engine.Monster.MODE_A1, targetId, target);
        break;
      case 14:
        if (targetId < 0) { idle(5); break; }
        Vector2 source = mPosition.get(entityId).position;
        int distance = Math.max(1, distanceBetweenUnits(source, target,
            mSize.has(entityId) ? mSize.get(entityId).size : 1,
            mSize.has(targetId) ? mSize.get(targetId).size : 1));
        int x = Math.round(source.x);
        int y = Math.round(source.y);
        destination.set(x + 25 * (x - Math.round(target.x)) / distance,
            y + 25 * (y - Math.round(target.y)) / distance);
        if (canLand(destination)) castAt(4, Engine.INVALID_ENTITY, destination);
        else idle(5);
        break;
      case 15:
        spawnClone(targetId);
        idle(5);
        break;
      default:
        idle(5);
    }
    // Fn135 unconditionally replaces its AI event with Idle(25), preserving
    // the action/movement just started (unlike IdleInNeutralMode).
    delayDecision(25);
  }

  static int cloneWeight(int created) {
    return created >= 2 ? 0 : 10 * (2 - Math.max(0, created));
  }

  private void spawnClone(int targetId) {
    // The native minion callback excludes self, regardless of monster class.
    IntBag units = Riiablo.engine.getAspectSubscriptionManager()
        .get(Aspect.all(Monster.class)).getEntities();
    for (int i = 0; i < units.size(); i++) {
      int id = units.get(i);
      if (id != entityId && mMonster.get(id).minionOwnerId == entityId
          && isLivingMonster(id)) return;
    }
    int peer = monster.baalLinkedOwnerId;
    // Also catches a clone created this tick, before subscription insertion.
    if (isLivingMonster(peer) && mMonster.get(peer).baalLinkedOwnerId == entityId) return;

    MonStats.Entry row = Riiablo.files.monstats.get("baalclone");
    MonStats2.Entry stats2 = row == null ? null : Riiablo.files.monstats2.get(row.MonStatsEx);
    Attributes source = mAttributesWrapper.has(entityId) ? mAttributesWrapper.get(entityId).attrs : null;
    if (row == null || !"BaalCrabClone".equals(row.AI) || stats2 == null || source == null
        || source.get(Stat.maxhp) == null || source.get(Stat.hitpoints) == null) return;

    // MONSTERS_GetMinionSpawnInfo consumes two draws even though action15
    // discards those coordinates, then rolls two more around the target.
    rollAiRandomNumber();
    rollAiRandomNumber();
    Vector2 center = targetId >= 0 && mPosition.has(targetId)
        ? mPosition.get(targetId).position : mPosition.get(entityId).position;
    destination.set(Math.round(center.x) + (int) (rollAiRandomNumber() % 24) - 12,
        Math.round(center.y) + (int) (rollAiRandomNumber() % 24) - 12);
    if (!canLand(destination, Math.max(1, stats2.SizeX))) return;
    int id = factory.createMonster(row.hcIdx, destination.x, destination.y);
    if (id == Engine.INVALID_ENTITY) return;
    mNativeUnitFlags.create(id).set(NativeUnitFlags.BAAL_CLONE);
    AIWrapper wrapper = Riiablo.engine.getMapper(AIWrapper.class).get(id);
    Attributes attrs = mAttributesWrapper.has(id) ? mAttributesWrapper.get(id).attrs : null;
    if (!mMonster.has(id) || attrs == null || wrapper == null
        || !(wrapper.ai instanceof BaalCrabClone)) {
      Riiablo.engine.delete(id);
      return;
    }
    // Divide the encoded fixed-point values, not rounded whole HP.
    attrs.base().putEncoded(Stat.maxhp, source.get(Stat.maxhp).encodedValues() / 3);
    attrs.base().putEncoded(Stat.hitpoints, source.get(Stat.hitpoints).encodedValues() / 3);
    attrs.base().putEncoded(Stat.hpregen, 0);
    attrs.reset();
    mMonster.get(id).minionOwnerId = entityId;
    mMonster.get(id).baalLinkedOwnerId = entityId;
    monster.minionOwnerId = entityId;
    monster.baalLinkedOwnerId = id;
    monster.baalClonesCreated++;
    ((BaalCrabClone) wrapper.ai).delayDecision(15);
  }

  protected final boolean isLivingMonster(int id) {
    if (id < 0 || !mMonster.has(id) || mCorpse.has(id)) return false;
    int mode = mSequence.has(id) ? mSequence.get(id).mode1
        : mCofReference.has(id) ? mCofReference.get(id).mode : Engine.Monster.MODE_NU;
    return mode != Engine.Monster.MODE_DT && mode != Engine.Monster.MODE_DD
        && stat(id, Stat.hitpoints, 1f) > 0f;
  }

  private void wander(int radius) {
    boolean xEdge = (rollAiRandomNumber() & 1) != 0;
    int offset = (int) (rollAiRandomNumber() % radius);
    int dx = xEdge ? radius : offset;
    int dy = xEdge ? offset : radius;
    if ((rollAiRandomNumber() & 1) != 0) dx = -dx;
    if ((rollAiRandomNumber() & 1) != 0) dy = -dy;
    Vector2 source = mPosition.get(entityId).position;
    destination.set(Math.round(source.x) + dx, Math.round(source.y) + dy);
    if (!walkToPoint(destination, 0, Engine.INVALID_ENTITY)) idle(10);
  }

  private boolean hasBarrier(int targetId) {
    MapWrapper map = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (map == null || map.map == null || map.zone == null) return false;
    ray.set(mPosition.get(entityId).position, mPosition.get(targetId).position);
    return map.map.castRay(map.zone, ray, DT1.Tile.FLAG_BLOCK_JUMP, 0, collision, false);
  }

  private boolean canLand(Vector2 point) {
    return canLand(point, mSize.has(entityId) ? mSize.get(entityId).size : 1);
  }

  private boolean canLand(Vector2 point, int size) {
    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (wrapper == null || wrapper.map == null || wrapper.zone == null) return false;
    Map map = wrapper.map;
    Map.Zone zone = map.getZone(point);
    if (zone == null || zone.isTown() || zone != wrapper.zone) return false;
    Vector2 source = mPosition.get(entityId).position;
    if (!zone.areRoomsAdjacent(source.x, source.y, point.x, point.y)) return false;
    if (map.isBlockedForPath(zone, Math.round(point.x), Math.round(point.y),
        DT1.Tile.FLAG_BLOCK_WALK, size, false)) return false;
    DynamicUnitCollisionSystem collision = Riiablo.engine.getSystem(DynamicUnitCollisionSystem.class);
    return collision == null || collision.isFreeForLanding(entityId,
        Math.round(point.x), Math.round(point.y), size);
  }

  private boolean isCold(int targetId) {
    return mUnitStates.has(targetId) && mUnitStates.get(targetId).stateList != null
        && mUnitStates.get(targetId).stateList.getState(StateId.COLD) != null;
  }

  private int lifePercent(int id) {
    // UNITS_GetCurrentLifePercentage truncates both fixed-point stats first.
    int max = (int) stat(id, Stat.maxhp, 100f);
    return max == 0 ? 0 : (int) (100L * (int) stat(id, Stat.hitpoints, 100f) / max);
  }

  private float stat(int id, short stat, float fallback) {
    Attributes attrs = mAttributesWrapper.has(id) ? mAttributesWrapper.get(id).attrs : null;
    return attrs == null ? fallback : attrs.getValue(stat, fallback);
  }

  /** AIUTIL_GetDistanceToCoordinatesWithSize, including its absolute subtraction. */
  static int distanceWithSize(Vector2 unit, Vector2 point, int size) {
    int dx = Math.abs(Math.abs(Math.round(unit.x) - Math.round(point.x)) - size);
    int dy = Math.abs(Math.abs(Math.round(unit.y) - Math.round(point.y)) - size);
    return (Math.min(dx, dy) + 2 * Math.max(dx, dy)) / 2;
  }

  /** UNITS_GetDistanceToOtherUnit subtracts both half sizes, then clamps. */
  static int distanceBetweenUnits(Vector2 source, Vector2 target, int sourceSize, int targetSize) {
    int size = sourceSize / 2 + targetSize / 2;
    int dx = Math.max(0, Math.abs(Math.round(source.x) - Math.round(target.x)) - size);
    int dy = Math.max(0, Math.abs(Math.round(source.y) - Math.round(target.y)) - size);
    return (Math.min(dx, dy) + 2 * Math.max(dx, dy)) / 2;
  }

  /** Native base tables with available ECS inputs. Special-skill, portal,
   * boss-score and player-count-bonus adjustments remain unprojected. */
  static int[] weights(int difficulty, boolean melee, boolean barrier, int distance,
      int life, int targetLife, boolean cold, int count, boolean medium, boolean far) {
    if (melee) {
      int[] w = {0, 50, 0, 0, 0, 0, 0, 0, 20, 30, 150, 10, 10, 70, 40, 0};
      if (difficulty == 0) { w[1] = 75; w[13] = 45; }
      if (targetLife < 33) w[10] += 50;
      w[1] += 100 - life;
      if (cold) { w[8] = 0; w[13] = 0; w[11] = 0; }
      if (barrier) { w[11] = 0; w[13] = 0; w[12] = 0; }
      return w;
    }
    if (barrier) {
      int[] w = {0, 100, 20, 20, 20, 0, 20, 0, 80, 70, 0, 0, 0, 0, 0, 0};
      if (difficulty == 0) w[1] = 125;
      if (count < 2) { w[2] = 45; w[10] = 25; }
      w[1] += 100 - life;
      if (medium) { w[2] = 0; w[6] = 0; w[3] += 15; w[11] += 25; w[13] += 25; }
      if (far) w[5] += 60;
      return w;
    }
    int[] w = {0, 0, 5, 5, 5, 0, 5, 0, 40, 40, 0, 70, 80, 60, 20, 20};
    if (difficulty == 0) w[13] = 40;
    if (distance > 35) { w[6] = 15; w[13] = 0; }
    w[1] = 100 - life + (difficulty == 0 ? 125 : 100);
    if (distance > 25) { w[14] = 30; w[11] = 0; }
    if (count < 2) w[11] -= 10;
    if (count > 3) w[13] += 25;
    if (medium) { w[3] = 25; w[2] = 0; w[6] += 25; w[14] += 35; }
    if (far) w[5] = 60;
    return w;
  }

  /** Native signed weights are intentional: far single-target Nova can be -10. */
  static int selectAction(int[] weights, long roll) {
    int total = 0;
    for (int value : weights) total += value;
    if (total <= 0) return 1;
    int selected = (int) ((roll & 0xFFFFFFFFL) % total);
    int threshold = 0;
    for (int i = 0; i < weights.length; i++) {
      threshold += weights[i];
      if (selected < threshold) return i;
    }
    return 1;
  }
}
