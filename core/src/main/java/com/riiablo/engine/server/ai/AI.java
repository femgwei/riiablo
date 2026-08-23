package com.riiablo.engine.server.ai;

import java.lang.reflect.Constructor;
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
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Running;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

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
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<Interactable> mInteractable;
  protected ComponentMapper<PathWrapper> mPathWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.Casting> mCasting;

  protected CofManager cofs;
  protected Pathfinder pathfinder;

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
  }

  @Override
  public void interact(int src, int entityId) {}

  public void update(float delta) {}

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

  private static EntitySubscription enemyEntities;

  /**
   * 获取玩家实体订阅（用于远程怪查找目标）。懒初始化，与 D2MOD pTargetNodes 等价。
   */
  protected static EntitySubscription getEnemyEntities() {
    if (enemyEntities == null) {
      enemyEntities = Riiablo.engine.getAspectSubscriptionManager().get(
          Aspect.all(Class.class).one(Player.class));
    }
    return enemyEntities;
  }

  /**
   * 查找最近玩家，返回 targetId；outDistance[0] 为距离。
   * 优化：使用 aidist 限制查找范围，平方距离避免开方。D2MOD 使用 nAiDist 限制查找。
   */
  protected int findNearestTargetWithAidist(float[] outDistance) {
    Vector2 entityPos = mPosition.get(entityId).position;
    int targetId = Engine.INVALID_ENTITY;
    float best = Float.MAX_VALUE;
    float maxSearchDist = 35f;
    if (monster.monstats.aidist != null && monster.monstats.aidist.length > 0) {
      int aidist = monster.monstats.aidist[0];
      if (aidist > 0) maxSearchDist = aidist;
    }
    float maxSearchDistSq = maxSearchDist * maxSearchDist;
    IntBag entities = getEnemyEntities().getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int ent = entities.get(i);
      if (mClass.get(ent).type == Class.Type.PLR) {
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
}
