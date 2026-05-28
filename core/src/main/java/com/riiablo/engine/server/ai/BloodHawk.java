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
 * BloodHawk AI implementation matching D2MOD's AITHINK_Fn005_BloodHawk logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = BLOODHAWK_AI_PARAM_CHARGE_CHANCE_PCT (charge chance)
 * - params[1] = BLOODHAWK_AI_PARAM_WANDER_CHANCE_PCT (wander chance)
 * - params[2] = BLOODHAWK_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[3] = BLOODHAWK_AI_PARAM_RUN_VELOCITY (run velocity)
 * - params[4] = BLOODHAWK_AI_PARAM_CHARGE_VELOCITY (charge velocity)
 * 
 * Special: Has charge mechanism and escape behavior when too close.
 */
public class BloodHawk extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    CHARGE,
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

  private static EntitySubscription enemyEntities;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;
  
  // AI state tracking
  int aiParam0 = 0;  // Charge state (0=not charging, 1=charging)

  public BloodHawk(int entityId) {
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

    // D2MOD: If charging and in combat, attack
    if (aiParam0 == 1 && stateMachine.getCurrentState() == State.CHARGE) {
      // Check if in combat now
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

      if (targetId != Engine.INVALID_ENTITY && isInCombat(targetId)) {
        aiParam0 = 0;
        Vector2 targetPos = mPosition.get(targetId).position;
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    aiParam0 = 0;

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
      if (params.length > 2 && !MathUtils.randomBoolean(params[2] / 100f)) {
        // D2MOD: Try to escape
        // D2MOD: AITACTICS_SetVelocity(pUnit, 0, AI_GetParamValue(pGame, pAiTickParam, BLOODHAWK_AI_PARAM_RUN_VELOCITY), 0)
        stateMachine.changeState(State.ESCAPE);
        Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
        Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(4f));
        pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
        if (!mPathfind.has(entityId)) {
          // Can't escape, attack
          pathfinder.findPath(entityId, null);
          lookAt(targetId);
          stateMachine.changeState(State.ATTACK);
          mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
          mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        }
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOD: Attack
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Check charge chance
    if (params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, AI_GetParamValue(pGame, pAiTickParam, BLOODHAWK_AI_PARAM_CHARGE_VELOCITY), pAiTickParam->nTargetDistance)
      aiParam0 = 1;
      stateMachine.changeState(State.CHARGE);
      pathfinder.findPath(entityId, targetPos, false, targetId);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: If very close (distance <= 3), escape
    if (targetDistance <= 3) {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, AI_GetParamValue(pGame, pAiTickParam, BLOODHAWK_AI_PARAM_RUN_VELOCITY), 0)
      stateMachine.changeState(State.ESCAPE);
      Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
      Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(4f));
      pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
      if (!mPathfind.has(entityId)) {
        // Can't escape, attack
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      }
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Wander or walk close
    if (params.length > 1 && !MathUtils.randomBoolean(params[1] / 100f)) {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 0, 0)
      // D2MOD: AITACTICS_WalkCloseToUnit(pGame, pUnit, 3u)
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    } else {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, -50, 0)
      // D2MOD: AITACTICS_WalkCloseToUnit(pGame, pUnit, 4u)
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
