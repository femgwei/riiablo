package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;

/**
 * PantherWoman AI implementation matching D2MOD's AITHINK_Fn018_PantherWoman logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = PANTHERWOMAN_AI_PARAM_APPROACH_CHANCE_PCT (approach chance)
 * - params[1] = PANTHERWOMAN_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[2] = PANTHERWOMAN_AI_PARAM_PACK_DISTANCE (pack distance)
 * - params[3] = PANTHERWOMAN_AI_PARAM_STALL_DURATION (idle time)
 * 
 * Special: Has pack behavior - groups with other PantherWoman monsters.
 */
public class PantherWoman extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    GROUP,
    DEAD;

    @Override public void enter(Integer entityId) {}
    @Override public void update(Integer entityId) {}
    @Override public void exit(Integer entityId) {}
    @Override public boolean onMessage(Integer entityId, Telegram telegram) {
      return false;
    }
  }

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<com.riiablo.engine.server.component.Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;

  private static EntitySubscription enemyEntities;
  private static EntitySubscription monsterEntities;  // All monsters to find pack members

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public PantherWoman(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    if (enemyEntities == null) {
      enemyEntities = Riiablo.engine.getAspectSubscriptionManager().get(Aspect
              .all(Class.class)
              .one(Player.class));
    }
    if (monsterEntities == null) {
      monsterEntities = Riiablo.engine.getAspectSubscriptionManager().get(Aspect
              .all(Class.class, Monster.class, Position.class));
    }
  }

  @Override
  public void kill() {
    if (stateMachine.getCurrentState() == State.DEAD) return;
    pathfinder.findPath(entityId, null);
    stateMachine.changeState(State.DEAD);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    Riiablo.audio.play(monsound + "_death_1", true);
  }

  /**
   * Check if monster is in combat (within melee range).
   */
  private boolean isInCombat(int targetId) {
    if (targetId == Engine.INVALID_ENTITY) return false;
    if (!mPosition.has(targetId)) return false;
    
    Vector2 entityPos = mPosition.get(entityId).position;
    Vector2 targetPos = mPosition.get(targetId).position;
    float distance = entityPos.dst(targetPos);
    float meleeRng = 1f + monster.monstats2.MeleeRng;
    
    return distance <= meleeRng;
  }

  /**
   * Find nearby pack member (same monster type).
   */
  private int findPackMember(float maxDistance) {
    if (monsterEntities == null) return Engine.INVALID_ENTITY;
    
    Vector2 entityPos = mPosition.get(entityId).position;
    int closestId = Engine.INVALID_ENTITY;
    float closestDistance = maxDistance * maxDistance;
    
    IntBag entities = monsterEntities.getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int ent = entities.get(i);
      if (ent == entityId || !mMonster.has(ent) || !mPosition.has(ent)) continue;
      
      // Check if same monster type (simplified - check if same AI class)
      // TODO: Properly check if same base monster type
      
      Vector2 targetPos = mPosition.get(ent).position;
      float dstSq = entityPos.dst2(targetPos);
      if (dstSq < closestDistance) {
        closestDistance = dstSq;
        closestId = ent;
      }
    }
    
    return closestId;
  }

  @Override
  public void update(float delta) {
    stateMachine.update();
    if (stateMachine.getCurrentState() == State.DEAD) {
      return;
    }

    nextAction -= delta;
    time -= delta;
    if (time > 0) {
      return;
    }

    time = SLEEP;

    // Find target
    int targetId = Engine.INVALID_ENTITY;
    float targetDistance = Float.MAX_VALUE;
    Vector2 entityPos = mPosition.get(entityId).position;
    
    IntBag entities = enemyEntities.getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int ent = entities.get(i);
      if (mClass.get(ent).type == Class.Type.PLR) {
        Vector2 targetPos = mPosition.get(ent).position;
        float dst = entityPos.dst(targetPos);
        if (dst < targetDistance) {
          targetDistance = dst;
          targetId = ent;
        }
      }
    }

    if (targetId == Engine.INVALID_ENTITY) {
      // No target, idle behavior
      switch (stateMachine.getCurrentState()) {
        case IDLE:
          if (nextAction < 0) {
            pathfinder.findPath(entityId, null);
            stateMachine.changeState(State.WANDER);
          }
          break;
        case WANDER:
          if (!mPathfind.has(entityId)) {
            nextAction = MathUtils.random(3f, 5);
            stateMachine.changeState(State.IDLE);
          } else {
            Vector2 dst = tmpVec2.set(mPosition.get(entityId).position);
            dst.add(MathUtils.random(-5, 5), MathUtils.random(-5, 5));
            pathfinder.findPath(entityId, dst);
          }
          break;
        default:
          stateMachine.changeState(State.IDLE);
          break;
      }
      return;
    }

    Vector2 targetPos = mPosition.get(targetId).position;
    boolean bCombat = isInCombat(targetId);

    // D2MOD: If in combat
    if (bCombat) {
      if (params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        Riiablo.audio.play(monsound + "_attack_1", true);
        time = MathUtils.random(1f, 2);
        return;
      } else {
        stateMachine.changeState(State.IDLE);
        time = params.length > 3 ? params[3] * com.riiablo.codec.Animation.FRAME_DURATION : 15f * com.riiablo.codec.Animation.FRAME_DURATION;
        return;
      }
    }

    // D2MOD: Not in combat
    if (params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 75, 0)
      // D2MOD: sub_6FCD0410(pGame, pUnit, pAiTickParam->pTarget, 7)
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Check pack behavior
    float packDistance = params.length > 2 ? params[2] : 10f;
    int packMemberId = findPackMember(packDistance * 2f);
    if (packMemberId != Engine.INVALID_ENTITY) {
      Vector2 packPos = mPosition.get(packMemberId).position;
      float packDistSq = entityPos.dst2(packPos);
      if (packDistSq > packDistance * packDistance) {
        // D2MOD: Too far from pack, move to pack member
        // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 75, 0)
        // D2MOD: AITACTICS_WalkToTargetUnitWithFlags(pGame, pUnit, arg.pTarget, 7)
        pathfinder.findPath(entityId, packPos, false, packMemberId);
        stateMachine.changeState(State.GROUP);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOD: Random idle or circle
    if (MathUtils.randomBoolean(0.75f)) {
      stateMachine.changeState(State.IDLE);
      time = params.length > 3 ? params[3] * com.riiablo.codec.Animation.FRAME_DURATION : 15f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    } else {
      // D2MOD: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 3u, 0)
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
