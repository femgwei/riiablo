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
 * FetishShaman AI implementation matching D2MOO's AITHINK_Fn065_FetishShaman logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = FETISHSHAMAN_AI_PARAM_HEAL_CHANCE_PCT (heal chance)
 * - params[1] = FETISHSHAMAN_AI_PARAM_HEAL_CAPABILITY (heal capability)
 * - params[2] = FETISHSHAMAN_AI_PARAM_HEAL_RANGE (heal range)
 * - params[3] = FETISHSHAMAN_AI_PARAM_CIRCLE_CHANCE_PCT (circle chance)
 * - params[4] = FETISHSHAMAN_AI_PARAM_HEAL_SEARCH_RANGE (heal search range)
 * 
 * Special: Can heal dead allies (raise dead) and has inferno skill.
 */
public class FetishShaman extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    CAST,
    HEAL,
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

  public FetishShaman(int entityId) {
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
   * Find nearby dead ally to heal (simplified).
   */
  private int findNearbyDeadAlly(float maxRange) {
    // TODO: Implement dead ally finding logic
    return Engine.INVALID_ENTITY;
  }

  /**
   * Check if in inferno state (simplified).
   */
  private boolean isInInfernoState() {
    // TODO: Implement inferno state check
    return false;
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

    // D2MOO: Check inferno state and skill range
    int skillLevel = 1;
    // TODO: Get skill level from monster's skill
    if (monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
      skillLevel = Math.max(1, 1); // Simplified
    }

    // D2MOO: If can use inferno skill (distance < skill level) and not in inferno state
    if (monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()
        && targetDistance < skillLevel && !isInInfernoState()) {
      // D2MOO: Use inferno skill (nSkill[0])
      // TODO: Implement skill casting
      stateMachine.changeState(State.CAST);
      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        lookAt(targetId);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      }
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: Check inferno state
    if (isInInfernoState()) {
      // D2MOO: Toggle off inferno state
      // TODO: Implement state toggle
    }

    // D2MOO: Check heal dead ally
    float healSearchRange = params.length > 4 ? params[4] : 15f;
    int deadAllyId = findNearbyDeadAlly(healSearchRange);
    if (deadAllyId != Engine.INVALID_ENTITY && monster.monstats.Skill3 != null && !monster.monstats.Skill3.isEmpty()
        && params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      // D2MOO: Use heal skill (nSkill[2])
      float healRange = params.length > 2 ? params[2] : 5f;
      Vector2 deadPos = mPosition.get(deadAllyId).position;
      float deadDist = entityPos.dst(deadPos);
      
      if (deadDist <= healRange) {
        // D2MOO: Use sequence skill
        // TODO: Implement skill casting
        stateMachine.changeState(State.HEAL);
        lookAt(deadAllyId);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, deadAllyId, deadPos);
        time = MathUtils.random(1f, 2);
        return;
      } else {
        // D2MOO: Walk to dead ally
        // D2MOO: D2GAME_AICORE_WalkToOwner_6FCD0B60(pGame, pUnit, arg.pClosestDeadTarget, 10)
        pathfinder.findPath(entityId, deadPos, false, deadAllyId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOO: Circle or idle
    if (params.length > 3 && !MathUtils.randomBoolean(params[3] / 100f)) {
      stateMachine.changeState(State.IDLE);
      time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    } else {
      // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 4u, 0)
      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    stateMachine.changeState(State.IDLE);
    time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
