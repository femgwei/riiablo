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
 * FallenShaman AI implementation matching D2MOD's AITHINK_Fn013_FallenShaman logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = FALLENSHAMAN_AI_PARAM_RAISE_CHANCE_PCT (raise dead chance)
 * - params[1] = FALLENSHAMAN_AI_PARAM_RAISE_RANGE (raise range)
 * - params[2] = FALLENSHAMAN_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[3] = FALLENSHAMAN_AI_PARAM_ATTACK1_OR_2_CHANCE_PCT (A1 vs A2 chance)
 */
public class FallenShaman extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    CAST,
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

  public FallenShaman(int entityId) {
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
   * D2MOD: bCombat = UNITS_IsInMeleeRange(pUnit, pTarget, 0)
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
   * Find nearby corpse to raise (simplified version).
   * D2MOD: AITHINK_TargetCallback_FallenShaman searches for corpses
   */
  private int findNearbyCorpse(float maxRange) {
    // TODO: Implement corpse finding logic
    // For now, return INVALID_ENTITY
    return Engine.INVALID_ENTITY;
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

    // D2MOD: Check if should raise dead (if not in combat and has skill)
    if (!bCombat && params.length > 1) {
      float raiseRange = params[1];
      int corpseId = findNearbyCorpse(raiseRange);
      if (corpseId != Engine.INVALID_ENTITY && params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
        // D2MOD: Use raise dead skill (nSkill[0])
        // TODO: Implement skill casting for raise dead
        stateMachine.changeState(State.CAST);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOD: If in combat, check attack chance
    if (bCombat) {
      // D2MOD: FALLENSHAMAN_AI_PARAM_ATTACK_CHANCE_PCT
      if (params.length > 2 && MathUtils.randomBoolean(params[2] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        // D2MOD: FALLENSHAMAN_AI_PARAM_ATTACK1_OR_2_CHANCE_PCT
        byte attackMode = params.length > 3 && MathUtils.randomBoolean(params[3] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
        mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        Riiablo.audio.play(monsound + "_attack_1", true);
        time = MathUtils.random(1f, 2);
        return;
      } else {
        // No attack, idle
        stateMachine.changeState(State.IDLE);
        time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
        return;
      }
    }

    // D2MOD: Not in combat, approach target
    pathfinder.findPath(entityId, targetPos, false, targetId);
    stateMachine.changeState(State.APPROACH);
    time = MathUtils.random(1f, 2);
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
