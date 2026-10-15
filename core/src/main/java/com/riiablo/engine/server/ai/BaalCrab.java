package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.NativeSkills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.DynamicUnitCollisionSystem;
import com.riiablo.engine.server.NativeGroundPathProfile;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.NativeAiPathCache;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;

/** D2MOO AiBaal.cpp Fn135 / MainSkillHandler, with ECS player-owner target groups. */
public class BaalCrab extends NativeDecisionAI {
  // Fixed SkillsIds.h constants, NOT zero-based MonStats slots.
  static final int SKILL_TENTACLE = 315;
  static final int SKILL_NOVA = 316;
  static final int SKILL_INFERNO = 317;
  static final int SKILL_COLD_MISSILES = 318;
  static final int SKILL_IMMOLATION_ARROW = 27;
  static final int SKILL_FIRE_WALL = 51;
  static final int SKILL_METEOR = 56;
  static final int SKILL_BLIZZARD = 59;

  private final Vector2 anchor = new Vector2();
  private final Vector2 destination = new Vector2();
  private final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> collision = new Collision<>(new Vector2(), new Vector2());
  private boolean anchored;
  private int targetCount;
  private int maxTargetScore;

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

  /** Fn135/Fn140 pass null score args: the optional anchor score bonus is zero. */
  @Override
  protected int findNearestTargetWithAidist(float[] outDistance) {
    targetCount = 0;
    maxTargetScore = 0;
    if (mNativeAiTargetOverride.has(entityId)) {
      // Preserve the shared explicit Attract/Confuse authority boundary.
      int id = super.findNearestTargetWithAidist(outDistance);
      if (id >= 0) { targetCount = 1; maxTargetScore = targetScore(id); }
      return id;
    }
    int selected = Engine.INVALID_ENTITY;
    IntBag units = getEnemyEntities().getEntities();
    NativePlayerTargetSlots slots = Riiablo.engine.getSystem(NativePlayerTargetSlots.class);
    int heads = slots != null ? NativePlayerTargetSlots.SLOT_COUNT : units.size();
    for (int i = 0; i < heads; i++) {
      // Production worlds use persistent native slots. Keep the legacy scan
      // for minimal/custom worlds which have not installed the slot system.
      int player = slots != null ? slots.playerAtSlot(i) : units.get(i);
      if (player < 0 || !mPlayer.has(player) || !mPosition.has(player) || !cullPlayerHead(player)) continue;
      // Native includes a dead player head in the count but gives it score 0.
      targetCount++;
      int score = eligibleScore(player);
      if (score > maxTargetScore) { maxTargetScore = score; selected = player; }
      // Targets.cpp inserts new children directly after their player head.
      // Resolve child ties by membership events, without letting a child jump
      // ahead of an equal-score head or an earlier player group.
      boolean selectedCurrentChild = false;
      long selectedChildOrder = 0;
      for (int j = units.size() - 1; j >= 0; j--) {
        int child = units.get(j);
        if (child == player || playerOwner(child) != player || !sameActAndMap(child)
            || detachedMonsterNode(child)) continue;
        long order = playerChildOrder(child);
        if (slots != null && !slots.acceptsChildInsertion(player, order)) continue;
        targetCount++;
        score = eligibleScore(child);
        if (score > maxTargetScore
            || score > 0 && score == maxTargetScore && selectedCurrentChild && order > selectedChildOrder) {
          maxTargetScore = score;
          selected = child;
          selectedCurrentChild = true;
          selectedChildOrder = order;
        }
      }
    }
    selected = node9Fallback(units, selected);
    outDistance[0] = selected < 0 ? Float.MAX_VALUE
        : distanceWithSize(mPosition.get(entityId).position, mPosition.get(selected).position, 0);
    if (selected < 0) stopMovement();
    return selected;
  }

  int lastTargetCount() { return targetCount; }
  int lastMaxTargetScore() { return maxTargetScore; }

  private int playerOwner(int id) {
    if (isNode9(id)) return Engine.INVALID_ENTITY;
    if (mMercenary.has(id)) return mMercenary.get(id).ownerId;
    if (mSummonedPet.has(id)) return mSummonedPet.get(id).ownerId;
    // SkillPal.cpp Conversion joins the caster's player group, NOT node9.
    if (mMonster.has(id) && mMonster.get(id).converted) return mMonster.get(id).conversionOwnerId;
    return Engine.INVALID_ENTITY;
  }

  private boolean isNode9(int id) {
    if (!mMonster.has(id)) return false;
    if (mSummonedPet.has(id) && mSummonedPet.get(id).boneWall) return true;
    // A retained curse flag cannot hide a newer Conversion insertion; nor
    // may a second state steal a node already inserted into the player group.
    return mUnitStates.has(id) && mUnitStates.get(id).stateList != null
        && mUnitStates.get(id).stateList.nativeNode9Order() != 0;
  }

  private long playerChildOrder(int id) {
    if (mMercenary.has(id)) return mMercenary.get(id).nativeTargetOrder;
    if (mSummonedPet.has(id)) return mSummonedPet.get(id).nativeTargetOrder;
    return mUnitStates.has(id) && mUnitStates.get(id).stateList != null
        ? mUnitStates.get(id).stateList.nativeConversionOrder() : 0;
  }

  private long node9Order(int id) {
    if (mSummonedPet.has(id) && mSummonedPet.get(id).boneWall) {
      return mSummonedPet.get(id).nativeTargetOrder;
    }
    return mUnitStates.has(id) && mUnitStates.get(id).stateList != null
        ? mUnitStates.get(id).stateList.nativeNode9Order() : 0;
  }

  private boolean detachedMonsterNode(int id) {
    if (!mMonster.has(id)) return false;
    if (mCorpse.has(id)) return true;
    int mode = mSequence.has(id) ? mSequence.get(id).mode1
        : mCofReference.has(id) ? mCofReference.get(id).mode : Engine.INVALID_MODE;
    // MonsterMode.cpp removes the node on entering DT, not merely at HP zero.
    return mode == Engine.Monster.MODE_DT || mode == Engine.Monster.MODE_DD;
  }

  private int node9Fallback(IntBag units, int primary) {
    int candidate = Engine.INVALID_ENTITY;
    int best = 0;
    long bestOrder = 0;
    // Native scores the ENTIRE node9 bucket before its <5 gate; a distant
    // higher scorer therefore prevents a nearby lower scorer being used.
    for (int i = units.size() - 1; i >= 0; i--) {
      int id = units.get(i);
      if (!isNode9(id) || !sameActAndMap(id)) continue;
      long order = node9Order(id);
      // Retained curse flags / a wall component are not proof that a node
      // removed by MonsterMode is still registered, even after a raw revive.
      if (order == 0) continue;
      int score = eligibleScore(id, true);
      if (score > best || score > 0 && score == best && order > bestOrder) {
        best = score;
        bestOrder = order;
        candidate = id;
      }
    }
    if (candidate < 0 || primary >= 0 && isInNativeMeleeRange(primary)
        || distanceWithSize(mPosition.get(entityId).position, mPosition.get(candidate).position, 0) >= 5
        || !noTowardPoints(primary)) return primary;
    // node9 never contributes to nCount and need not outscore the primary.
    maxTargetScore = best;
    return candidate;
  }

  private boolean noTowardPoints(int primary) {
    // A lazily attached component starts UNKNOWN. Only ServerEntityFactory's
    // actual allocation boundary can establish fresh zero coordinates/points.
    NativeAiPathCache cache = mNativeAiPathCache.create(entityId);
    Vector2 source = mPosition.get(entityId).position;
    int sx = Math.round(source.x), sy = Math.round(source.y);
    if (primary >= 0) {
      Vector2 target = mPosition.get(primary).position;
      int tx = Math.round(target.x), ty = Math.round(target.y);
      // This early return does NOT update coordinates or clear old points.
      if (tx == 0 || ty == 0) return cache.pointsKnown && !cache.hasPoints;
    }
    if (sx == 0 && sy == 0) {
      cache.pathPoints(false);
      return true;
    }
    if (primary >= 0) {
      Vector2 target = mPosition.get(primary).position;
      cache.targetCoordinates(Math.round(target.x), Math.round(target.y));
    }
    // PATH_SetTargetUnit(null) clears the pointer, NOT tTargetCoord. Do not
    // read a removed/recycled entity or substitute Pathfind.destination.
    if (!cache.coordinatesKnown) {
      // An unprojectable recomputation also makes the retained point count
      // unknown; a later zero-coordinate unit must not reuse an older count.
      cache.pointsKnown = false;
      return false;
    }
    int tx = cache.targetX, ty = cache.targetY;
    if (tx == 0 && ty == 0 || sx == tx && sy == ty
        || Math.abs((long) tx - sx) > 100 || Math.abs((long) ty - sy) > 100) {
      cache.pathPoints(false);
      return true;
    }
    // A half-zero cached coordinate would hit PATH_RayTrace assertions.
    if (tx == 0 || ty == 0) { cache.invalidate(); return false; }
    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    // Missing terrain is unknown, not proof of zero native path points.
    if (wrapper == null || wrapper.map == null || wrapper.zone == null) {
      cache.pointsKnown = false;
      return false;
    }
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    int flags = NativeGroundPathProfile.forUnit(false, monster.monstats).terrainFlags;
    boolean doors = monster.monstats != null && monster.monstats.opendoors
        && NativeGroundPathProfile.forUnit(false, monster.monstats) != NativeGroundPathProfile.FLYING;
    DynamicUnitCollisionSystem dynamic = Riiablo.engine.getSystem(DynamicUnitCollisionSystem.class);
    // Positive-velocity, zero-offset TOWARD projection. Use the monster's
    // allocation budget (14) and target-unit tolerance (1); runtime native
    // budget/velocity/advanced-target state is not represented by ECS Pathfind.
    // Keep active movement untouched: even a partial ray is nonzero points,
    // not proof that the destination is reachable.
    boolean present = NativeTowardPathProbe.hasPathPointsAfterNormalization(sx, sy, tx, ty, 14, 1, (x, y) ->
        wrapper.map.isBlockedForPath(wrapper.zone, x, y, flags, size, doors)
            || dynamic != null && !dynamic.isFreeForPath(wrapper.zone,
                entityId, Engine.INVALID_ENTITY, x, y, size));
    cache.pathPoints(present);
    // Successful unitless paths may rewrite tTargetCoord to the final path
    // point. The boolean probe does not compute that endpoint: mark unknown.
    if (primary < 0 && present) cache.coordinatesKnown = false;
    return !present;
  }

  private boolean cullPlayerHead(int id) {
    return sameActAndMap(id)
        && (!mMapWrapper.has(id) || mMapWrapper.get(id).zone == null || !mMapWrapper.get(id).zone.isTown())
        && distanceWithSize(mPosition.get(entityId).position, mPosition.get(id).position, 0) < 55;
  }

  private boolean sameActAndMap(int id) {
    MapWrapper source = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    MapWrapper target = mMapWrapper.has(id) ? mMapWrapper.get(id) : null;
    if (source != null && target != null && source.map != null && target.map != null
        && source.map != target.map) return false;
    int sourceAct = unitAct(source);
    int targetAct = unitAct(target);
    return sourceAct < 0 || targetAct < 0 || sourceAct == targetAct;
  }

  private static int unitAct(MapWrapper wrapper) {
    if (wrapper == null) return -1;
    if (wrapper.zone != null && wrapper.zone.level != null) return wrapper.zone.level.Act;
    return wrapper.map != null ? wrapper.map.getAct() : -1;
  }

  private int eligibleScore(int id) {
    return eligibleScore(id, false);
  }

  private int eligibleScore(int id, boolean node9) {
    if (!(node9 ? isValidSpecialEnemyTargetWithoutAiScope(id)
        : isValidEnemyTargetWithoutAiScope(id)) || mCorpse.has(id)) return 0;
    int mode = mSequence.has(id) ? mSequence.get(id).mode1
        : mCofReference.has(id) ? mCofReference.get(id).mode : Engine.INVALID_MODE;
    if (mode == Engine.Player.MODE_DT
        || mode == (mPlayer.has(id) ? Engine.Player.MODE_DD : Engine.Monster.MODE_DD)) return 0;
    return targetScore(id);
  }

  /** AIBAAL_GetTargetScore integer arithmetic; normal boss calls have pArgs=null. */
  int targetScore(int id) {
    if (mMonster.has(id) && (mMonster.get(id).monstats == null
        || mMonster.get(id).monstats.threat <= 1)) return 0;
    int resist = (statInt(id, Stat.fireresist) + statInt(id, Stat.coldresist)
        + statInt(id, Stat.lightresist)
        + 4 * (statInt(id, Stat.damageresist) + 2 * statInt(id, Stat.magicresist))) / 15;
    long damage = (long) statInt(id, Stat.maxdamage) + statInt(id, Stat.firemaxdam)
        + statInt(id, Stat.lightmaxdam) + statInt(id, Stat.magicmaxdam)
        + statInt(id, Stat.coldmaxdam) + (statInt(id, Stat.poisonmaxdam) >> 8);
    int damageWeight = (int) damage / 2;
    int rankWeight = (handAttackRank(id, Input.Buttons.LEFT)
        + handAttackRank(id, Input.Buttons.RIGHT)) / 4;
    int collisionWeight = isInNativeMeleeRange(id) ? 100 : hasBarrier(id) ? 0 : 75;
    int score = (resist + 5 * collisionWeight
        + 2 * (damageWeight + rankWeight + (isCold(id) ? 200 : 0))
        + (lifePercent(id) >= 20 ? 300 : 0)) / 22;
    return score == 0 ? 1 : score;
  }

  private int handAttackRank(int id, int button) {
    CharData data = mPlayer.has(id) ? mPlayer.get(id).data : null;
    if (data == null) return 0;
    int skillId = data.getAction(button);
    NativeSkills.Entry skill = Riiablo.files.NativeSkills.get(skillId);
    return skill == null ? 0
        : effectiveHandSkillLevel(id, data, skillId) * (skill.nativeInteger("attackrank") & 0xFF);
  }

  private int effectiveHandSkillLevel(int id, CharData data, int skillId) {
    int level = data.getSkill(skillId);
    if (level <= 0) return 0;
    if (mUnitStates.has(id) && mUnitStates.get(id).stateList != null) {
      level += mUnitStates.get(id).stateList.getTotalSkillModifier();
    }
    return Math.max(0, level);
  }

  /** AI_CheckSpecialSkillsOnPrimeEvil checks selected hands, not used/casting skill.
   * CharData projects available player hands; native monster/charged pointers remain separate. */
  boolean hasSpecialPrimeEvilSkill(int id) {
    CharData data = mPlayer.has(id) ? mPlayer.get(id).data : null;
    return data != null && (isSpecialPrimeEvilHand(id, data, Input.Buttons.RIGHT)
        || isSpecialPrimeEvilHand(id, data, Input.Buttons.LEFT));
  }

  private boolean isSpecialPrimeEvilHand(int id, CharData data, int button) {
    int skillId = data.getAction(button);
    // An unowned action ID is not a native selected skill pointer.
    if (data.getSkill(skillId) <= 0) return false;
    switch (skillId) {
      case SKILL_BLIZZARD:
      case SKILL_METEOR:
        return true;
      case SKILL_FIRE_WALL:
        return effectiveHandSkillLevel(id, data, skillId) > 3;
      case SKILL_IMMOLATION_ARROW:
        return effectiveHandSkillLevel(id, data, skillId) > 7;
      default:
        return false;
    }
  }

  private int statInt(int id, short stat) {
    Attributes attrs = mAttributesWrapper.has(id) ? mAttributesWrapper.get(id).attrs : null;
    return attrs == null ? 0 : attrs.getValue(stat, 0);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    execute(selectAction(decisionWeights(targetId, combat), rollAiRandomNumber()), targetId);
  }

  int[] decisionWeights(int targetId, boolean combat) {
    int difficulty = resolveAiDifficulty();
    int sourceSize = mSize.has(entityId) ? mSize.get(entityId).size : 1;
    int targetSize = mSize.has(targetId) ? mSize.get(targetId).size : 1;
    int fromAnchor = distanceWithSize(mPosition.get(targetId).position, anchor,
        (targetSize >>> 1) + 1);
    boolean barrier = hasBarrier(targetId);
    boolean medium = fromAnchor > 75;
    int[] table = weights(difficulty, combat, barrier,
        distanceWithSize(mPosition.get(entityId).position, mPosition.get(targetId).position,
            sourceSize), lifePercent(entityId), lifePercent(targetId), isCold(targetId),
        targetCount, medium, fromAnchor > 100);
    if (!combat && !barrier) {
      table[15] = cloneWeight(monster.baalClonesCreated);
      if (maxTargetScore > 60) table[8] = 70;
      // Monster.cpp MONSTER_GetPlayerCountBonus assigns nDifficulty from the
      // game difficulty, NOT nPlayerCount. This gate follows the score boost.
      if (targetCount < 2 && difficulty < 2) table[8] = 0;
      if (!medium && hasSpecialPrimeEvilSkill(targetId)) {
        table[6] += 30;
        table[11] += 10;
        table[14] += 15;
      }
    } else if (!combat && !medium && hasSpecialPrimeEvilSkill(targetId)) {
      table[14] += 50;
      table[13] += 50;
    }
    return table;
  }

  /** Indices and fixed modes from AIBAAL_MainSkillHandler, exposed for branch tests. */
  void execute(int action, int targetId) {
    // Native mode/skill/movement callbacks are not all ported cache writers.
    // Retain no trusted cache across an action, including neutral-mode idle.
    invalidateNativePathCache();
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

  /** Native base tables; decisionWeights adds score, single-target difficulty,
   * special selected skills and clone count. Portal lookup remains unprojected. */
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
