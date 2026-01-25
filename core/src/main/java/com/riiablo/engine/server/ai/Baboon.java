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
 * Baboon AI implementation matching D2MOO's AITHINK_Fn011_Baboon logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = BABOON_AI_PARAM_HURT_PCT (hurt percentage threshold)
 * - params[1] = BABOON_AI_PARAM_CIRCLE_CHANCE_PCT (circle chance)
 * - params[2] = BABOON_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[3] = BABOON_AI_PARAM_ATTACK_1_OR_2_CHANCE_PCT (A1 vs A2 chance)
 * - params[4] = BABOON_AI_PARAM_REGEN_BONUS (regen bonus)
 * 
 * Special: Has regeneration mechanism when hurt. Escapes when hurt to regenerate.
 */
public class Baboon extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    ESCAPE,
    REGEN,
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
  int aiParam0 = 0;  // Regen timer (countdown)
  int aiParam1 = 0;  // Attack state (0=can attack, 1=just attacked)
  int aiParam2 = 0;  // Regen bonus amount

  public Baboon(int entityId) {
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
   * Check if in special AI state (simplified).
   */
  private boolean checkSpecialAiState() {
    // TODO: Implement proper AI state check
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

    float lifePercent = getLifePercentage();
    float hurtPct = params.length > 0 ? params[0] : 50f;

    // D2MOO: Calculate velocity modifier based on run/velocity ratio
    int nVel = 0;
    // TODO: Calculate from monstats.nRun and monstats.nVelocity

    // D2MOO: If in regen state
    if (aiParam0 > 0) {
      aiParam1 = 0;
      aiParam0--;

      // D2MOO: Remove regen bonus if timer expired or life > 75%
      if (aiParam0 == 0 || lifePercent > 75) {
        // D2MOO: STATLIST_SetUnitStat(pUnit, STAT_HPREGEN, STATLIST_UnitGetStatValue(pUnit, STAT_HPREGEN, 0) - pAiTickParam->pAiControl->dwAiParam[2], 0)
        // TODO: Remove regen bonus from attributes
        aiParam2 = 0;
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

      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        boolean bCombat = isInCombat(targetId);

        // D2MOO: If in combat, 33% chance to attack
        if (bCombat && MathUtils.randomBoolean(0.33f)) {
          pathfinder.findPath(entityId, null);
          lookAt(targetId);
          stateMachine.changeState(State.ATTACK);
          byte attackMode = params.length > 3 && MathUtils.randomBoolean(params[3] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
          mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
          mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
          time = MathUtils.random(1f, 2);
          return;
        }

        // D2MOO: If life > 75%, exit regen state
        if (lifePercent > 75) {
          aiParam0 = 0;
          // D2MOO: AITACTICS_SetVelocity(pUnit, 13, nVel, 0)
          // D2MOO: sub_6FCD0410(pGame, pUnit, pAiTickParam->pTarget, 7)
          pathfinder.findPath(entityId, targetPos, false, targetId);
          stateMachine.changeState(State.APPROACH);
          time = MathUtils.random(1f, 2);
          return;
        }

        // D2MOO: If far (distance >= 24) and not in special state
        if (targetDistance >= 24 && !checkSpecialAiState()) {
          if (MathUtils.randomBoolean(0.33f)) {
            // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 4u, 0)
            pathfinder.findPath(entityId, targetPos, false, targetId);
            stateMachine.changeState(State.APPROACH);
          }
          stateMachine.changeState(State.IDLE);
          time = 20f * com.riiablo.codec.Animation.FRAME_DURATION;
          return;
        }

        // D2MOO: Try to escape
        // D2MOO: AITACTICS_SetVelocity(pUnit, 2, nVel, 0)
        // D2MOO: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pAiTickParam->pTarget, 15, 1)
        stateMachine.changeState(State.ESCAPE);
        Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
        Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(15f));
        pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
        if (!mPathfind.has(entityId)) {
          // Can't escape
          if (!bCombat) {
            // D2MOO: AITACTICS_WalkCloseToUnit(pGame, pUnit, 5u)
            pathfinder.findPath(entityId, targetPos, false, targetId);
            stateMachine.changeState(State.APPROACH);
          } else {
            // Attack
            pathfinder.findPath(entityId, null);
            lookAt(targetId);
            stateMachine.changeState(State.ATTACK);
            byte attackMode = params.length > 3 && MathUtils.randomBoolean(params[3] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
            mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
            mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
          }
        }
        time = MathUtils.random(1f, 2);
        return;
      }
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

    // D2MOO: If not in combat, approach
    if (!bCombat) {
      // D2MOO: sub_6FCD0410(pGame, pUnit, pAiTickParam->pTarget, 7)
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: In combat
    if (checkSpecialAiState()) {
      // D2MOO: If hurt, start regen
      if (lifePercent < hurtPct && MathUtils.randomBoolean(0.5f)) {
        aiParam0 = MathUtils.random(2, 6); // Random 2-6 ticks
        
        // D2MOO: Add regen bonus
        // TODO: Add regen bonus to attributes
        if (params.length > 4) {
          // Calculate regen bonus
          aiParam2 = params[4]; // Simplified
        }

        // D2MOO: AITACTICS_SetVelocity(pUnit, 2, nVel, 0)
        // D2MOO: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pAiTickParam->pTarget, 0xFu, 0)
        stateMachine.changeState(State.ESCAPE);
        Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
        Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(15f));
        pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOO: If not attacked yet, attack
      if (aiParam1 == 0) {
        aiParam1 = 1;
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        byte attackMode = params.length > 3 && MathUtils.randomBoolean(params[3] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
        mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOO: 20% chance to circle
      if (MathUtils.randomBoolean(0.2f)) {
        // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 3u, 0)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        aiParam1 = 0;
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOO: Normal attack logic
    if (aiParam1 == 0 || (params.length > 2 && MathUtils.randomBoolean(params[2] / 100f))) {
      aiParam1 = 1;
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      byte attackMode = params.length > 3 && MathUtils.randomBoolean(params[3] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
      mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: Circle or idle
    if (params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
      // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 3u, 0)
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      aiParam1 = 0;
      time = MathUtils.random(1f, 2);
      return;
    } else {
      stateMachine.changeState(State.IDLE);
      time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
