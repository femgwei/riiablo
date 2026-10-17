package com.riiablo.engine.server.ai;

import com.artemis.ComponentMapper;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
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

  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<com.riiablo.engine.server.component.Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;
  
  // AI state tracking
  int aiParam0 = 0;  // Ordinary pursuit phase, independent of AiCommand.

  public Scarab(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void kill() {
    if (stateMachine.getCurrentState() == State.DEAD) return;
    pathfinder.findPath(entityId, null);
    stateMachine.changeState(State.DEAD);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_death_1", true);
  }

  /**
   * Check if monster is in combat (within melee range).
   */
  private boolean isInCombat(int targetId) {
    return isInNativeMeleeRange(targetId);
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

    float[] targetDistances = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(targetDistances);
    float targetDistance = targetDistances[0];
    Vector2 entityPos = mPosition.get(entityId).position;

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
        // Native AI_RollPercentage > 10 clears the ordinary pursuit phase.
        if (rollAiPercentage() > 10) {
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

    // Fn020 rolls attack first. Jab is only eligible after that roll succeeds.
    if (rollAiChance(params[0])) {
      if (monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()
          && rollAiChance(params[3])) {
        useMonsterSkill(0, targetId, targetPos);
        stateMachine.changeState(State.JAB);
        return;
      }
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      // Native attack-choice helper selects A1 when the chance succeeds.
      byte attackMode = rollAiChance(params[1]) ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2;
      mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }
    // Fn020 ends a declined attack with IdleInNeutralMode, cancelling movement.
    stopMovement();
    stateMachine.changeState(State.IDLE);
    time = params[2] * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
