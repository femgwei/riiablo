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
 * Scarab AI implementation matching D2MOD's AITHINK_Fn020_Scarab logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = SCARAB_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[1] = SCARAB_AI_PARAM_ATTACK_1_OR_2_CHANCE_PCT (A1 vs A2 chance)
 * - params[2] = SCARAB_AI_PARAM_STALL_DURATION (idle time)
 * - params[3] = SCARAB_AI_PARAM_JAB_CHANCE_PCT (jab chance)
 * - params[4] = SCARAB_AI_PARAM_COMMAND_CHANCE_PCT (command minions chance)
 * 
 * Special: Can command minions and has jab attack.
 */
public class Scarab extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    JAB,
    COMMAND,
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
  int aiParam0 = 0;  // Command state (0=normal, 1=commanding)

  public Scarab(int entityId) {
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
   * Check if is minion owner (simplified).
   */
  private boolean isMinionOwner() {
    // TODO: Implement minion owner check
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

    // D2MOD: Check command state
    if (aiParam0 == 1) {
      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        boolean bCombat = isInCombat(targetId);
        
        // D2MOD: If in combat and has skill, use it
        if (bCombat && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
          // D2MOD: Use skill (nSkill[0])
          // TODO: Implement skill casting
          aiParam0 = 0;
          pathfinder.findPath(entityId, null);
          lookAt(targetId);
          stateMachine.changeState(State.ATTACK);
          mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
          mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
          time = MathUtils.random(1f, 2);
          return;
        } else {
          // D2MOD: AITACTICS_SetVelocity(pUnit, 2, 100, 0)
          // D2MOD: AITACTICS_WalkToTargetUnitWithFlags(pGame, pUnit, pAiTickParam->pTarget, 0)
          walkTo(targetPos, 100, targetId);
          stateMachine.changeState(State.APPROACH);
          time = MathUtils.random(1f, 2);
          return;
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

    // D2MOD: If not in combat
    if (!bCombat) {
      if (aiParam0 != 0) {
        // D2MOD: AITACTICS_SetVelocity(pUnit, 2, 0, 4u)
        // D2MOD: AITACTICS_WalkToTargetUnitWithFlags(pGame, pUnit, pAiTickParam->pTarget, 7)
        walkTo(targetPos, targetId);
        stateMachine.changeState(State.APPROACH);
        // D2MOD: 10% chance to exit command state
        if (MathUtils.randomBoolean(0.1f)) {
          aiParam0 = 0;
        }
        time = MathUtils.random(1f, 2);
        return;
      } else {
        // D2MOD: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 0, 0)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        aiParam0 = 1;
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOD: In combat
    if (params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      byte attackMode = params.length > 1 && MathUtils.randomBoolean(params[1] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
      mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    } else {
      // D2MOD: Check jab chance
      if (params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.JAB);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      } else {
        stateMachine.changeState(State.IDLE);
        time = params.length > 2 ? params[2] * com.riiablo.codec.Animation.FRAME_DURATION : 15f * com.riiablo.codec.Animation.FRAME_DURATION;
        return;
      }
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
