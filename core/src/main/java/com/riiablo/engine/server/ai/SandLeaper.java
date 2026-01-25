package com.riiablo.engine.server.ai;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;

/**
 * SandLeaper AI implementation matching D2MOO's AITHINK_Fn017_SandLeaper logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = SANDLEAPER_AI_PARAM_LEAP_CHANCE_PCT (leap chance)
 * - params[1] = SANDLEAPER_AI_PARAM_ATTACK_CHANCE_PCT (attack chance)
 * - params[2] = SANDLEAPER_AI_PARAM_APPROACH_CHANCE_PCT (approach chance)
 * - params[3] = SANDLEAPER_AI_PARAM_CIRCLE_CHANCE_PCT (circle chance)
 * 
 * Special: Has leap skill to close distance quickly.
 */
public class SandLeaper extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    LEAP,
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

  public SandLeaper(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
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

    // 远程怪即时反应：玩家进入跳跃/近战范围（<5 或近战）立即攻击，不等到 time 结束
    float[] outDist = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(outDist);
    float targetDistance = outDist[0];
    boolean inRange = targetId != Engine.INVALID_ENTITY && (targetDistance < 5f || isInCombat(targetId));
    if (inRange
        && stateMachine.getCurrentState() != State.ATTACK
        && stateMachine.getCurrentState() != State.LEAP) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      Vector2 targetPos = mPosition.get(targetId).position;
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2f);
      return;
    }

    if (time > 0) {
      return;
    }

    time = SLEEP;

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

    // D2MOO: Check if should use leap skill (distance < 5)
    if (targetDistance < 5 && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()
        && params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      // D2MOO: Use leap skill (nSkill[0])
      // TODO: Implement skill casting for leap
      stateMachine.changeState(State.LEAP);
      lookAt(targetId);
      // For now, use normal attack as placeholder
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: If in combat
    if (bCombat) {
      if (params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        Riiablo.audio.play(monsound + "_attack_1", true);
        time = MathUtils.random(1f, 2);
        return;
      }
    } else {
      // D2MOO: Not in combat
      if (targetDistance > 10) {
        // D2MOO: AITACTICS_SetVelocity(pUnit, 0, 75, 0)
        // D2MOO: D2GAME_AICORE_WalkToOwner_6FCD0B60(pGame, pUnit, pAiTickParam->pTarget, 5u)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }

      if (params.length > 2 && MathUtils.randomBoolean(params[2] / 100f)) {
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }

      if (params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
        // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 4u, 0)
        // Circle around target
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
