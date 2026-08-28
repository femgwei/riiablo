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
 * Brute AI implementation matching D2MOD's AITHINK_Fn007_Brute logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = unused
 * - params[1] = BRUTE_AI_PARAM_CIRCLE_CHANCE_PCT (unused by D2MOO)
 * - params[2] = BRUTE_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[3] = BRUTE_AI_PARAM_ATTACK1_OR_2_CHANCE_PCT (A1 vs A2 chance)
 * 
 * D2MOD: Speed decreases as health decreases (100 - life percentage, clamped to 40-100)
 */
public class Brute extends AI {
  static final int PARAM_ATTACK_CHANCE = 2;
  static final int PARAM_ATTACK1_OR_2_CHANCE = 3;

  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
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
  protected ComponentMapper<com.riiablo.engine.server.component.AttributesWrapper> mAttributesWrapper;

  private EntitySubscription enemyEntities;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public Brute(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    enemyEntities = Riiablo.engine.getAspectSubscriptionManager().get(Aspect
            .all(Class.class)
            .one(Player.class));
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
   * Get current life percentage (0-100).
   * D2MOD: UNITS_GetCurrentLifePercentage(pUnit)
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
      if (isValidEnemyTarget(ent)) {
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
      // D2MOD: BRUTE_AI_PARAM_ATTACK_CHANCE_PCT
      if (MathUtils.randomBoolean(params[PARAM_ATTACK_CHANCE] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        // D2MOD: BRUTE_AI_PARAM_ATTACK1_OR_2_CHANCE_PCT
        byte attackMode = MathUtils.randomBoolean(params[PARAM_ATTACK1_OR_2_CHANCE] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
        mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        Riiablo.audio.play(monsound + "_attack_1", true);
        time = MathUtils.random(1f, 2);
        return;
      } else {
        // D2MOD: Second chance to attack (same param)
        if (MathUtils.randomBoolean(params[PARAM_ATTACK_CHANCE] / 100f)) {
          // D2MOD: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 4u, 0)
          // This appears to be a special attack or skill, but we'll use normal attack for now
          pathfinder.findPath(entityId, null);
          lookAt(targetId);
          stateMachine.changeState(State.ATTACK);
          mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
          mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
          time = MathUtils.random(1f, 2);
          return;
        } else {
          // Idle
          stateMachine.changeState(State.IDLE);
          time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
          return;
        }
      }
    }

    // D2MOD: Not in combat
    // D2MOD: Speed decreases as health decreases: nSpeedMalus = D2Clamp(UNITS_GetCurrentLifePercentage(pUnit), 40, 100)
    // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 100 - nSpeedMalus, 0)
    float lifePercent = getLifePercentage();
    float speedMalus = MathUtils.clamp(lifePercent, 40f, 100f);
    float speedModifier = 100f - speedMalus;
    
    // D2MOD: sub_6FCD0410(pGame, pUnit, pAiTickParam->pTarget, 7)
    // This appears to be a walk-to-target function with flags=7
    walkTo(targetPos, MathUtils.round(speedModifier), targetId);
    stateMachine.changeState(State.APPROACH);
    time = MathUtils.random(1f, 2);
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
