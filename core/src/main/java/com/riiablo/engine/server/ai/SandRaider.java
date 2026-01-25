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
 * SandRaider AI implementation matching D2MOO's AITHINK_Fn008_SandRaider logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = SANDRAIDER_AI_PARAM_HURT_PCT (hurt percentage threshold)
 * - params[1] = SANDRAIDER_AI_PARAM_CIRCLE_CHANCE_PCT (circle chance)
 * - params[2] = SANDRAIDER_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[3] = SANDRAIDER_AI_PARAM_APPROACH (approach chance)
 * - params[4] = SANDRAIDER_AI_PARAM_CHARGE_DURATION (charge duration)
 * - params[5] = SANDRAIDER_AI_PARAM_CHARGE_COLOR (charge color: 0=none, 1=blue, 2=red)
 * - params[6] = SANDRAIDER_AI_PARAM_ATTACK2_OR_1_CHANCE_PCT (A2 vs A1 chance)
 * 
 * Special: Has charge-up mechanism that enables special skill when charged.
 */
public class SandRaider extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    CHARGE,
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
  
  // AI state tracking (similar to D2MOO's dwAiParam)
  int aiParam0 = 0;  // Charge counter
  int aiParam1 = 0;  // Charged state (0=not charged, 1=charged)
  int aiParam2 = 0;  // Hurt escape counter

  public SandRaider(int entityId) {
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

  /**
   * Find nearby friendly monster to escape to (simplified).
   */
  private int findNearbyFriendly(float maxRange) {
    // TODO: Implement friendly monster finding logic
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

    // D2MOO: Initialize charge state on first update
    if (aiParam0 == 0) {
      // D2MOO: STATES_ToggleState(pUnit, STATE_BLUE, 0) and STATE_RED, 0
      aiParam1 = 0;
    }

    aiParam0++;

    // D2MOO: Check charge duration
    int chargeDuration = params.length > 4 ? params[4] : 0;
    int chargeColor = params.length > 5 ? params[5] : 0;
    
    if (aiParam0 == chargeDuration && chargeDuration > 0) {
      // D2MOO: UNITS_SetOverlay(pUnit, nChargeColor == 1 ? 150 : 46, 0)
      stateMachine.changeState(State.CHARGE);
      time = (monster.monstats.aidel[0] + 1) * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    }

    if (aiParam0 > chargeDuration && chargeDuration > 0) {
      // D2MOO: STATES_ToggleState(pUnit, nChargeColor == 1 ? STATE_BLUE : STATE_RED, 1)
      aiParam1 = 1; // Charged
    }

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

    // D2MOO: Check if hurt and should escape
    if (aiParam2 < 7 && params.length > 0) {
      float lifePercent = getLifePercentage();
      if (lifePercent < params[0]) {
        int friendlyId = findNearbyFriendly(20f);
        if (friendlyId != Engine.INVALID_ENTITY) {
          Vector2 friendlyPos = mPosition.get(friendlyId).position;
          pathfinder.findPath(entityId, friendlyPos, false, friendlyId);
          stateMachine.changeState(State.APPROACH);
          time = MathUtils.random(1f, 2);
          return;
        }
        aiParam2++;
      }
    }

    // D2MOO: Circle around target if far and not charged
    if (targetDistance > 4 && aiParam1 == 0 && params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
      // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 0, 0)
      // Simplified: walk around target
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: If in combat
    if (bCombat) {
      // D2MOO: If charged, use skill
      if (aiParam1 == 1 && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
        // TODO: Use skill (nSkill[0])
        // For now, use normal attack
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        aiParam0 = 0;
        aiParam1 = 0;
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOO: Normal attack
      if (params.length > 2 && MathUtils.randomBoolean(params[2] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        byte attackMode = params.length > 6 && MathUtils.randomBoolean(params[6] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
        mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        Riiablo.audio.play(monsound + "_attack_1", true);
        time = MathUtils.random(1f, 2);
        return;
      }
    } else {
      // D2MOO: Not in combat
      if (aiParam1 == 1 || (params.length > 3 && MathUtils.randomBoolean(params[3] / 100f))) {
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOO: Reset charge if too long
    if (chargeDuration > 0) {
      int maxChargeTime = Math.max(24 - chargeDuration, 6) + chargeDuration;
      if (aiParam0 > maxChargeTime) {
        aiParam0 = 0;
        aiParam1 = 0;
      }
    }

    stateMachine.changeState(State.IDLE);
    time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
