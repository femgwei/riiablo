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
import com.riiablo.engine.server.component.AttributesWrapper;

/**
 * Fetish AI implementation matching D2MOO's AITHINK_Fn030_Fetish logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = FETISH_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[1] = FETISH_AI_PARAM_STALL_DURATION (idle time)
 * - params[2] = FETISH_AI_PARAM_ATTACK_LOOP (attack loop count)
 * - params[3] = FETISH_AI_PARAM_WEAK_PCT (weak percentage threshold)
 * 
 * Special: Has attack loop mechanism and escape behavior when weak.
 */
public class Fetish extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    ESCAPE,
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
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  private static EntitySubscription enemyEntities;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;
  
  // AI state tracking
  int aiParam0 = 0;  // State (0=normal, 1=attacking, 2=escaping)
  int aiParam1 = 0;  // Attack loop counter

  public Fetish(int entityId) {
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
   * Get current life percentage (0-100).
   */
  private float getLifePercentage() {
    if (!mAttributesWrapper.has(entityId)) return 100f;
    com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    com.riiablo.attributes.StatRef hitpoints = attrs.get(com.riiablo.attributes.Stat.hitpoints);
    com.riiablo.attributes.StatRef maxhp = attrs.get(com.riiablo.attributes.Stat.maxhp);
    if (hitpoints == null || maxhp == null || maxhp.asFixed() <= 0) return 100f;
    return (hitpoints.asFixed() / maxhp.asFixed()) * 100f;
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
    float lifePercent = getLifePercentage();

    // D2MOO: State machine
    switch (aiParam0) {
      case 0: // Normal state
        if (bCombat) {
          aiParam0 = 1;
          aiParam1 = 0;
          
          if (params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
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
            time = params.length > 1 ? params[1] * com.riiablo.codec.Animation.FRAME_DURATION : 15f * com.riiablo.codec.Animation.FRAME_DURATION;
            return;
          }
        }
        
        // D2MOO: AITACTICS_SetVelocity(pUnit, 13, 50, 0)
        // D2MOO: AITACTICS_WalkToTargetUnitWithFlags(pGame, pUnit, pAiTickParam->pTarget, 7)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;

      case 1: // Attacking state
        aiParam1++;
        
        int attackLoop = params.length > 2 ? params[2] : 3;
        float weakPct = params.length > 3 ? params[3] : 50f;
        
        if (aiParam1 > attackLoop && lifePercent > weakPct) {
          aiParam0 = 2;
          aiParam1 = 0;
          // D2MOO: AITACTICS_SetVelocity(pUnit, 2, 50, 0)
          // D2MOO: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pAiTickParam->pTarget, 14, 1)
          stateMachine.changeState(State.ESCAPE);
          Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
          Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(14f));
          pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
          time = MathUtils.random(1f, 2);
          return;
        }
        
        if (bCombat) {
          if (params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
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
            time = params.length > 1 ? params[1] * com.riiablo.codec.Animation.FRAME_DURATION : 15f * com.riiablo.codec.Animation.FRAME_DURATION;
            return;
          }
        }
        
        // D2MOO: AITACTICS_SetVelocity(pUnit, 13, 50, 0)
        // D2MOO: AITACTICS_WalkToTargetUnitWithFlags(pGame, pUnit, pAiTickParam->pTarget, 0)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;

      case 2: // Escaping state
        // D2MOO: Continue escaping
        stateMachine.changeState(State.ESCAPE);
        Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
        Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(14f));
        pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
        if (!mPathfind.has(entityId)) {
          // Can't escape, return to normal
          aiParam0 = 0;
          pathfinder.findPath(entityId, targetPos, false, targetId);
          stateMachine.changeState(State.APPROACH);
        }
        time = MathUtils.random(1f, 2);
        return;
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
