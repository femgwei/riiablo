package com.riiablo.engine.server.ai;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;

/**
 * SkeletonMage AI implementation matching D2MOO's AITHINK_Fn064_SkeletonMage logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = SKELETONMAGE_AI_PARAM_SHOOT_CHANCE_PCT (shoot chance)
 * - params[1] = SKELETONMAGE_AI_PARAM_APPROACH_DISTANCE (approach distance)
 * - params[2] = SKELETONMAGE_AI_PARAM_APPROACH_CHANCE_PCT (approach chance)
 * - params[3] = SKELETONMAGE_AI_PARAM_TOO_CLOSE_DISTANCE (too close distance)
 * - params[4] = SKELETONMAGE_AI_PARAM_WALK_AWAY_CHANCE_PCT (walk away chance)
 * - params[5] = SKELETONMAGE_AI_PARAM_FIRE_DIST (fire distance)
 * - params[6] = SKELETONMAGE_AI_PARAM_CIRCLE_CHANCE_PCT (circle chance)
 * - params[7] = SKELETONMAGE_AI_PARAM_STALL_DURATION (idle time)
 * 
 * Special: Ranged attack AI using spells. Maintains distance from target.
 */
public class SkeletonMage extends AI {
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
  Missiles.Entry missile;

  public SkeletonMage(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    // Get missile from MissA1 or MissA2
    String missileName = monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty() 
        ? monster.monstats.MissA1 
        : (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty() ? monster.monstats.MissA2 : null);
    if (missileName != null) {
      missile = Riiablo.files.Missiles.get(missileName);
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

  @Override
  public void update(float delta) {
    stateMachine.update();
    if (stateMachine.getCurrentState() == State.DEAD) {
      return;
    }

    nextAction -= delta;
    time -= delta;

    // 远程怪即时反应：玩家进入射程立即攻击，不等到 time 结束
    float[] outDist = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(outDist);
    float targetDistance = outDist[0];
    float fireDist = params.length > 5 ? params[5] : 15f;
    if (targetId != Engine.INVALID_ENTITY
        && stateMachine.getCurrentState() != State.ATTACK
        && stateMachine.getCurrentState() != State.ESCAPE
        && targetDistance <= fireDist) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      Vector2 targetPos = mPosition.get(targetId).position;
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
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
    Vector2 entityPos = mPosition.get(entityId).position;
    float approachDistance = params.length > 1 ? params[1] : 10f;
    float tooCloseDistance = params.length > 3 ? params[3] : 5f;
    float fireDistance = params.length > 5 ? params[5] : 15f;

    // D2MOO: If too far, approach
    if (targetDistance > approachDistance && params.length > 2 && MathUtils.randomBoolean(params[2] / 100f)) {
      // D2MOO: AITACTICS_SetVelocity(pUnit, 0, 10, 0)
      // D2MOO: AITACTICS_WalkToTargetUnitWithSteps(pGame, pUnit, pTarget, AI_GetParamValue(pGame, pAiTickParam, SKELETONMAGE_AI_PARAM_APPROACH_DISTANCE))
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: If too close, walk away
    if (targetDistance <= tooCloseDistance && params.length > 4 && MathUtils.randomBoolean(params[4] / 100f)) {
      // D2MOO: AITACTICS_SetVelocity(pUnit, 0, 25, 0)
      // D2MOO: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pTarget, 5u, 1)
      stateMachine.changeState(State.ESCAPE);
      Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
      Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(5f));
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

    // D2MOO: If in fire range, shoot
    if (targetDistance < fireDistance && params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: If within approach distance or no approach chance
    if (targetDistance <= approachDistance || (params.length > 2 && !MathUtils.randomBoolean(params[2] / 100f))) {
      if (params.length > 6 && !MathUtils.randomBoolean(params[6] / 100f)) {
        // Idle
        stateMachine.changeState(State.IDLE);
        time = params.length > 7 ? params[7] * com.riiablo.codec.Animation.FRAME_DURATION : 15f * com.riiablo.codec.Animation.FRAME_DURATION;
        return;
      } else {
        // Circle
        // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 4u, 0)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }
    } else {
      // D2MOO: Approach
      // D2MOO: AITACTICS_SetVelocity(pUnit, 0, 10, 0)
      // D2MOO: AITACTICS_WalkToTargetUnitWithSteps(pGame, pUnit, pAiTickParam->pTarget, AI_GetParamValue(pGame, pAiTickParam, SKELETONMAGE_AI_PARAM_APPROACH_DISTANCE))
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
