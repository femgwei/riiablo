package com.riiablo.engine.server.ai;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.EventSystem;

public abstract class AI implements Interactable.Interactor {
  private static final Logger log = LogManager.getLogger(AI.class);

  public static final AI IDLE = new Idle();

  public static AI findAI(int entityId, String ai) {
    String fullClassName = "com.riiablo.engine.server.ai." + ai;
    try {
      java.lang.Class<?> clazz = java.lang.Class.forName(fullClassName);
      if (clazz == Idle.class) return AI.IDLE;
      Constructor constructor = clazz.getConstructor(int.class);
      return (AI) constructor.newInstance(entityId);
    } catch (ClassNotFoundException e) {
      // A missing native AI must not silently turn a spawned monster into a
      // shared idle singleton. Use the generic server-authoritative AI so it
      // can still acquire targets, move and perform the basic attack loop.
      log.warn("[AI_FALLBACK] entityId={} ai={} className={} using GenericMonster",
          entityId, ai, fullClassName);
      return new GenericMonster(entityId, ai);
    } catch (Throwable t) {
      log.error("[AI_FALLBACK] failed to load entityId={} ai={} className={} error={} using GenericMonster",
          entityId, ai, fullClassName, ExceptionUtils.getRootCauseMessage(t), t);
      return new GenericMonster(entityId, ai);
    }
  }

  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<PathWrapper> mPathWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.Casting> mCasting;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<UnitStates> mUnitStates;

  protected CofManager cofs;
  protected Pathfinder pathfinder;
  /** Shared event bus used to expose monster casts to server and client systems. */
  @Wire(failOnNull = false)
  protected EventSystem events;

  @Wire(name = "factory")
  protected EntityFactory factory;

  private static final Vector2 tmpVec2 = new Vector2();

  protected float SLEEP = Float.POSITIVE_INFINITY;
  protected int[] params = ArrayUtils.EMPTY_INT_ARRAY;

  @EntityId
  protected int entityId;
  protected Monster monster;
  protected String monsound;

  private boolean movementActive;
  private boolean lastMovementRunning;
  private int lastMovementVelocityBonus = Integer.MIN_VALUE;
  private float nextWarCryThink;

  public AI(int entityId) {
    this.entityId = entityId;
  }

  public void initialize() {
    if (this == IDLE) return;
    monster = mMonster.get(entityId);
    MonStats.Entry monstats = monster.monstats;

    // TODO: difficulty-based params
    params = new int[8];
    params[0] = monstats.aip1[0];
    params[1] = monstats.aip2[0];
    params[2] = monstats.aip3[0];
    params[3] = monstats.aip4[0];
    params[4] = monstats.aip5[0];
    params[5] = monstats.aip6[0];
    params[6] = monstats.aip7[0];
    params[7] = monstats.aip8[0];

    SLEEP = Animation.FRAME_DURATION * monstats.aidel[0];
    monsound = monstats.MonSound;
    log.info("[MONSTER_AI_INIT] entity={} monster={} ai={} aidel={} sleep={} params={}",
        entityId, monstats.Id, getClass().getSimpleName(), monstats.aidel[0], SLEEP,
        Arrays.toString(params));
  }

  @Override
  public void interact(int src, int entityId) {}

  public void update(float delta) {}

  /**
   * Shared D2MOO AI special-state bridge for Howl/Taunt. Native code swaps
   * every switchable monster to AISPECIALSTATE_TERROR/TAUNT, independently
   * of its ordinary AI function; keeping this in the base class preserves
   * that behavior for both specialized and fallback Java AIs.
   */
  public boolean updateWarCryControl(float delta) {
    if (!mUnitStates.has(entityId) || mUnitStates.get(entityId).stateList == null) {
      nextWarCryThink = 0f;
      return false;
    }
    UnitState terror = mUnitStates.get(entityId).stateList.getState(StateId.TERROR);
    UnitState taunt = mUnitStates.get(entityId).stateList.getState(StateId.TAUNT);
    UnitState control = terror != null ? terror : taunt;
    if (control == null) {
      nextWarCryThink = 0f;
      return false;
    }

    int sourceId = control.sourceEntityId;
    if (sourceId < 0 || !mPosition.has(sourceId) || !mPosition.has(entityId)) {
      mUnitStates.get(entityId).stateList.removeState(control.stateId);
      stopMovement();
      return true;
    }
    if (terror != null) {
      if (mCasting.has(entityId)) mCasting.remove(entityId);
      if (mSequence.has(entityId)) mSequence.remove(entityId);
    } else if (mCasting.has(entityId)) {
      if (mCasting.get(entityId).targetId == sourceId) return true;
      mCasting.remove(entityId);
      if (mSequence.has(entityId)) mSequence.remove(entityId);
    } else if (mSequence.has(entityId)) {
      mSequence.remove(entityId);
    }
    nextWarCryThink -= Math.max(0f, delta);
    if (nextWarCryThink > 0f) return true;
    nextWarCryThink = 0.2f;

    Vector2 source = mPosition.get(sourceId).position;
    Vector2 position = mPosition.get(entityId).position;
    float distance = position.dst(source);
    if (terror != null) {
      int activeRange = terror.runtimeValue > 0 ? terror.runtimeValue : 30;
      if (distance > activeRange) {
        stopMovement();
        return true;
      }
      Vector2 escape = new Vector2(position).sub(source);
      if (escape.isZero(0.0001f) && mAngle.has(entityId)) {
        escape.set(mAngle.get(entityId).target).scl(-1f);
      }
      if (escape.isZero(0.0001f)) escape.set(1f, 0f);
      escape.nor().scl(30f).add(position);
      runTo(escape, 0, Engine.INVALID_ENTITY);
      return true;
    }

    if (!isLiveTauntSource(sourceId)) {
      mUnitStates.get(entityId).stateList.removeState(StateId.TAUNT);
      stopMovement();
      return true;
    }
    float melee = 1f + (monster != null && monster.monstats2 != null
        ? monster.monstats2.MeleeRng : 0);
    if (distance <= melee) {
      stopMovement();
      lookAt(sourceId);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(SkillCodes.attack, sourceId, source);
      Riiablo.audio.play(monsound + "_attack_1", true);
    } else {
      walkTo(source, sourceId);
    }
    return true;
  }

  private boolean isLiveTauntSource(int sourceId) {
    if (!mPlayer.has(sourceId) || !mPosition.has(sourceId)) return false;
    if (mAttributesWrapper.has(sourceId)) {
      Attributes attrs = mAttributesWrapper.get(sourceId).attrs;
      StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
      if (hp != null && hp.asFixed() <= 0f) return false;
    }
    if (mMapWrapper.has(sourceId)) {
      MapWrapper target = mMapWrapper.get(sourceId);
      if (target.zone != null && target.zone.isTown()) return false;
      if (mMapWrapper.has(entityId)) {
        MapWrapper source = mMapWrapper.get(entityId);
        if (source.map != null && target.map != null && source.map != target.map) return false;
        if (source.zone != null && target.zone != null && source.zone != target.zone) return false;
      }
    }
    return true;
  }

  public String getState() {
    return "";
  }

  public void hit() {
    Riiablo.audio.play(monsound + "_hit_1", true);
  }

  public void kill() {}

  protected Angle lookAt(int target) {
    Vector2 targetPos = mPosition.get(target).position;
    Vector2 entityPos = mPosition.get(entityId).position;
    tmpVec2.set(targetPos).sub(entityPos);
    Angle angle = mAngle.get(entityId);
    angle.target.set(tmpVec2).nor();
    return angle;
  }

  /**
   * Starts a native-style monster movement action. The velocity argument is
   * the temporary bonus passed to AITACTICS_SetVelocity, not an absolute
   * world speed. A value of 75 therefore combines with the native monster
   * base 75% to produce 150% movement and animation speed.
   */
  protected boolean moveTo(
      Vector2 target,
      boolean running,
      int velocityBonusPercent,
      boolean raycast,
      int targetEntityId) {
    boolean found = pathfinder.findPath(entityId, target, raycast, targetEntityId);
    if (!found) {
      stopMovement();
      return false;
    }

    Velocity velocity = mVelocity.get(entityId);
    velocity.setModeSpeedBonusPercent(velocityBonusPercent);
    if (running) {
      mRunning.create(entityId);
    } else {
      mRunning.remove(entityId);
    }

    if (!movementActive
        || lastMovementRunning != running
        || lastMovementVelocityBonus != velocityBonusPercent) {
      log.info(
          "[MONSTER_MOVE] entity={} monster={} ai={} mode={} baseVelocity={} "
              + "velocityBonusPct={} effectiveSpeed={} target={} raycast={}",
          entityId,
          monster != null && monster.monstats != null ? monster.monstats.Id : "unknown",
          getClass().getSimpleName(),
          running ? "RUN" : "WALK",
          running ? velocity.runSpeed : velocity.walkSpeed,
          velocityBonusPercent,
          velocity.speed(running),
          targetEntityId,
          raycast);
    }
    movementActive = true;
    lastMovementRunning = running;
    lastMovementVelocityBonus = velocityBonusPercent;
    return true;
  }

  protected boolean walkTo(Vector2 target, int targetEntityId) {
    return moveTo(target, false, 0, false, targetEntityId);
  }

  protected boolean walkTo(Vector2 target, int velocityBonusPercent, int targetEntityId) {
    return moveTo(target, false, velocityBonusPercent, false, targetEntityId);
  }

  protected boolean runTo(Vector2 target, int velocityBonusPercent, int targetEntityId) {
    return moveTo(target, true, velocityBonusPercent, true, targetEntityId);
  }

  protected void stopMovement() {
    pathfinder.findPath(entityId, null);
    mRunning.remove(entityId);
    if (mVelocity.has(entityId)) mVelocity.get(entityId).clearModeSpeedBonus();
    movementActive = false;
  }

  protected int fire(Missiles.Entry missile) {
    Vector2 position = mPosition.get(entityId).position;
    Vector2 angle = mAngle.get(entityId).target;
    // ?? ServerEntityFactory ??4 ??????????ID
    if (factory instanceof com.riiablo.engine.server.ServerEntityFactory) {
      int missileId = Riiablo.files.Missiles.index(missile.Missile);
      if (missileId >= 0) {
        return ((com.riiablo.engine.server.ServerEntityFactory) factory).createMissile(missileId, angle, position, entityId);
      }
    }
    // ????3 ????
    return factory.createMissile(missile, angle, position);
  }

  /**
   * Native {@code AITACTICS_UseSkill}: resolves a MonStats Skill1..Skill8
   * slot, enters its configured monster animation mode and lets Actioneer
   * execute the Skills.txt server function on the animation keyframe.
   *
   * @param skillIndex zero-based MonStats skill slot
   */
  protected boolean useMonsterSkill(int skillIndex, int targetId, Vector2 targetVec) {
    return useMonsterSkill(skillIndex, targetId, targetVec, Engine.INVALID_MODE);
  }

  /**
   * Uses a monster skill whose {@code Sk#mode} names a native MonSeq entry.
   * riiablo does not yet step MonSeq.txt directly, so callers that know the
   * sequence's backing animation mode provide it here. This prevents a long
   * sequence name such as {@code seq_shamanresurrect} from being mistaken for
   * the {@code XX} monster mode and producing a nonexistent COF.
   */
  protected boolean useMonsterSkill(
      int skillIndex, int targetId, Vector2 targetVec, int sequenceMode) {
    if (monster == null || monster.monstats == null) return false;
    String skillName = monsterSkillName(monster.monstats, skillIndex);
    if (skillName == null || skillName.isEmpty()) return false;
    Skills.Entry skill = Riiablo.files.skills.get(skillName);
    if (skill == null) {
      log.warn("[MONSTER_SKILL] phase=lookup_failed entity={} monster={} slot={} skill={}",
          entityId, monster.monstats.Id, skillIndex + 1, skillName);
      return false;
    }

    String configuredMode = monsterSkillMode(monster.monstats, skillIndex);
    int mode = sequenceMode;
    if (mode < 0) {
      mode = configuredMode != null && !configuredMode.isEmpty()
          ? Riiablo.files.MonMode.index(configuredMode) : -1;
      if (mode < 0 && skill.monanim != null && !skill.monanim.isEmpty()) {
        mode = Riiablo.files.MonMode.index(skill.monanim);
      }
    }
    if (mode < 0) mode = Engine.Monster.MODE_S1;

    Vector2 resolvedTarget = targetVec;
    if (resolvedTarget == null && targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
      resolvedTarget = mPosition.get(targetId).position;
    }
    if (resolvedTarget == null) resolvedTarget = mPosition.get(entityId).position;

    // Keep monster skills on the same public cast lifecycle as player skills.
    // ServerSkillSystem ignores SkillCastEvent for monsters, while the client
    // SkillCastHandler consumes SkillStartEvent for stsound/castoverlay.  The
    // old path skipped both events, making native shaman presentation silent.
    if (events != null) {
      SkillCastEvent castEvent = SkillCastEvent.obtain(
          entityId, skill.Id, targetId, resolvedTarget.cpy());
      events.dispatch(castEvent);
      if (!castEvent.accepted) {
        log.info("[MONSTER_SKILL] phase=reject entity={} monster={} skillId={} skill={} "
                + "resultCode={}",
            entityId, monster.monstats.Id, skill.Id, skill.skill, castEvent.resultCode);
        return false;
      }
    } else {
      // A detached AI can still run in headless/unit contexts. Runtime worlds
      // always inject EventSystem; make the missing bus visible instead of
      // silently losing the presentation event.
      log.warn("[MONSTER_SKILL] phase=event_bus_missing entity={} skillId={} skill={}",
          entityId, skill.Id, skill.skill);
    }

    stopMovement();
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) lookAt(targetId);
    mSequence.create(entityId).sequence((byte) mode, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(skill.Id, targetId, resolvedTarget);
    if (events != null) {
      events.dispatch(SkillStartEvent.obtain(
          entityId, skill.Id, targetId, resolvedTarget.cpy(), skill.srvstfunc, skill.cltstfunc));
    }
    log.info("[MONSTER_SKILL] phase=cast entity={} monster={} slot={} skillId={} skill={} "
            + "level={} mode={} configuredMode={} sequenceOverride={} target={}",
        entityId, monster.monstats.Id, skillIndex + 1, skill.Id, skill.skill,
        monsterSkillLevel(monster.monstats, skillIndex), mode, configuredMode,
        sequenceMode, targetId);
    return true;
  }

  /** Returns whether a skill row can create at least one projectile. */
  protected static boolean hasProjectileMissile(Skills.Entry skill) {
    return skill != null && (hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
        || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
        || hasText(skill.cltmissilec) || hasText(skill.cltmissiled));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  private static String monsterSkillName(MonStats.Entry monstats, int index) {
    switch (index) {
      case 0: return monstats.Skill1;
      case 1: return monstats.Skill2;
      case 2: return monstats.Skill3;
      case 3: return monstats.Skill4;
      case 4: return monstats.Skill5;
      case 5: return monstats.Skill6;
      case 6: return monstats.Skill7;
      case 7: return monstats.Skill8;
      default: return null;
    }
  }

  private static String monsterSkillMode(MonStats.Entry monstats, int index) {
    switch (index) {
      case 0: return monstats.Sk1mode;
      case 1: return monstats.Sk2mode;
      case 2: return monstats.Sk3mode;
      case 3: return monstats.Sk4mode;
      case 4: return monstats.Sk5mode;
      case 5: return monstats.Sk6mode;
      case 6: return monstats.Sk7mode;
      case 7: return monstats.Sk8mode;
      default: return null;
    }
  }

  protected static int monsterSkillLevel(MonStats.Entry monstats, int index) {
    if (monstats == null) return 1;
    int level;
    switch (index) {
      case 0: level = monstats.Sk1lvl; break;
      case 1: level = monstats.Sk2lvl; break;
      case 2: level = monstats.Sk3lvl; break;
      case 3: level = monstats.Sk4lvl; break;
      case 4: level = monstats.Sk5lvl; break;
      case 5: level = monstats.Sk6lvl; break;
      case 6: level = monstats.Sk7lvl; break;
      case 7: level = monstats.Sk8lvl; break;
      default: level = 1;
    }
    return Math.max(1, level);
  }

  /**
   * 获取可参与目标筛选的实体订阅。具体阵营由 isValidEnemyTarget 判定。
   */
  protected EntitySubscription getEnemyEntities() {
    // Do not cache this globally. Dedicated server, local server and headless
    // tests can own separate ECS worlds; a static subscription makes later
    // worlds read the first world's target nodes and breaks authority.
    return Riiablo.engine.getAspectSubscriptionManager().get(Aspect.all(Position.class));
  }

  /**
   * 查找最近敌对目标，返回 targetId；outDistance[0] 为距离。
   * 优化：使用 aidist 限制查找范围，平方距离避免开方。D2MOD 使用 nAiDist 限制查找。
   */
  protected int findNearestTargetWithAidist(float[] outDistance) {
    Vector2 entityPos = mPosition.get(entityId).position;
    int targetId = Engine.INVALID_ENTITY;
    float best = Float.MAX_VALUE;
    float maxSearchDist = 35f;
    if (monster.monstats.aidist != null && monster.monstats.aidist.length > 0) {
      int difficulty = 0;
      if (mMapWrapper.has(entityId) && mMapWrapper.get(entityId).map != null) {
        difficulty = mMapWrapper.get(entityId).map.getDifficulty();
      }
      int index = Math.min(difficulty, monster.monstats.aidist.length - 1);
      int aidist = monster.monstats.aidist[index];
      if (aidist > 0) maxSearchDist = aidist;
    }
    float maxSearchDistSq = maxSearchDist * maxSearchDist;
    IntBag entities = getEnemyEntities().getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int ent = entities.get(i);
      if (isValidEnemyTarget(ent)) {
        Vector2 targetPos = mPosition.get(ent).position;
        float dx = targetPos.x - entityPos.x;
        float dy = targetPos.y - entityPos.y;
        float dstSq = dx * dx + dy * dy;
        if (dstSq <= maxSearchDistSq) {
          float dst = (float) Math.sqrt(dstSq);
          if (dst < best) {
            best = dst;
            targetId = ent;
          }
        }
      }
    }
    outDistance[0] = best;
    return targetId;
  }

  /**
   * Native AI target-node filtering.  D2MOO never lets an evil monster target
   * a player whose room is town, a dead player, or a player outside the
   * monster's active map.  Keeping this in one helper prevents individual
   * monster AIs from accidentally reverting to global nearest-player scans.
   */
  protected boolean isValidEnemyTarget(int targetId) {
    if (!mPosition.has(targetId) || targetId == entityId) return false;
    SummonedPet sourcePet = mSummonedPet.has(entityId) ? mSummonedPet.get(entityId) : null;
    if (sourcePet != null && sourcePet.passive) return false;
    boolean targetFriendly = mPlayer.has(targetId) || mMercenary.has(targetId)
        || mSummonedPet.has(targetId);
    boolean targetHostileMonster = mMonster.has(targetId) && !targetFriendly;
    if (sourcePet != null ? !targetHostileMonster : !targetFriendly) return false;
    if (mMapWrapper.has(targetId) && mMapWrapper.get(targetId).zone != null
        && mMapWrapper.get(targetId).zone.isTown()) {
      return false;
    }
    if (mAttributesWrapper.has(targetId)) {
      Attributes attrs = mAttributesWrapper.get(targetId).attrs;
      StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
      if (hp != null && hp.asFixed() <= 0f) return false;
    }
    // Player entities created by older/local worlds may not yet carry the
    // transient native flag component; preserve their established targeting
    // behavior until flags are available. Monster flags are always populated
    // by ServerEntityFactory and are checked by their own callers.
    NativeUnitFlags targetFlags = mNativeUnitFlags.has(targetId)
        ? mNativeUnitFlags.get(targetId) : null;
    if (targetFlags != null && (!NativeTargeting.isTargetable(targetFlags)
        || !NativeTargeting.canBeAttacked(targetFlags))) return false;
    if (mMapWrapper.has(entityId) && mMapWrapper.has(targetId)) {
      MapWrapper source = mMapWrapper.get(entityId);
      MapWrapper target = mMapWrapper.get(targetId);
      if (source.map != null && target.map != null && source.map != target.map) return false;
      if (sourcePet == null && monster != null && monster.spawnZone != null
          && (source.zone != monster.spawnZone || target.zone != monster.spawnZone)) return false;
      if (source.zone != null && target.zone != null && source.zone != target.zone) return false;
      if (source.zone != null && target.zone == source.zone
          && !source.zone.areRoomsAdjacent(
              mPosition.get(entityId).position.x, mPosition.get(entityId).position.y,
              mPosition.get(targetId).position.x, mPosition.get(targetId).position.y)) {
        return false;
      }
    }
    // Reuse the native aiDist limit for legacy AI implementations that still
    // iterate their own subscription. This keeps their behavior bounded while
    // they are migrated to findNearestTargetWithAidist().
    if (monster != null && monster.monstats != null
        && monster.monstats.aidist != null && monster.monstats.aidist.length > 0) {
      int difficulty = 0;
      if (mMapWrapper.has(entityId) && mMapWrapper.get(entityId).map != null) {
        difficulty = mMapWrapper.get(entityId).map.getDifficulty();
      }
      int index = Math.min(difficulty, monster.monstats.aidist.length - 1);
      int maxDistance = monster.monstats.aidist[index];
      if (maxDistance > 0 && mPosition.has(entityId)) {
        float max = maxDistance;
        if (mPosition.get(entityId).position.dst2(mPosition.get(targetId).position) > max * max) {
          return false;
        }
      }
    }
    return true;
  }
}
