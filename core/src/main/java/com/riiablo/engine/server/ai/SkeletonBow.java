package com.riiablo.engine.server.ai;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;

/**
 * SkeletonBow AI implementation matching D2MOD's AITHINK_Fn037_SkeletonBow logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = SKELETONBOW_AI_PARAM_SHOOT_CHANCE_PCT (shoot chance)
 * - params[1] = SKELETONBOW_AI_PARAM_STALL_DURATION (idle time)
 * - params[2] = SKELETONBOW_AI_PARAM_APPROACH_CHANCE_PCT (approach chance)
 * - params[3] = SKELETONBOW_AI_PARAM_WALK_STEPS (walk steps)
 * - params[4] = SKELETONBOW_AI_PARAM_TARGET_DISTANCE (approach radius, not shooting range)
 * 
 * Special: Ranged attack AI using bow.
 */
public class SkeletonBow extends AI {
  private static final boolean TRACE_DECISIONS = Boolean.getBoolean("riiablo.aiDecisionTrace");
  private static final float SHOOT_DISTANCE = 20f;
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

  public SkeletonBow(int entityId) {
    super(entityId);
    stateMachine = new CheckpointStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    // Get missile from MissA1 or MissA2
    String missileName = monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty() 
        ? monster.monstats.MissA1 
        : (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty() ? monster.monstats.MissA2 : null);
    if (missileName != null) {
      missile = files().Missiles.get(missileName);
    }
  }

  @Override
  public void kill() {
    if (stateMachine.getCurrentState() == State.DEAD) return;
    pathfinder.findPath(entityId, null);
    stateMachine.changeState(State.DEAD);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    playSound(monsound + "_death_1");
  }

  /**
   * Check if monster is in combat (within melee range).
   */
  private boolean isInCombat(int targetId) {
    return isInNativeMeleeRange(targetId);
  }

  /**
   * Check if in special AI state (simplified).
   */
  private boolean checkSpecialAiState() {
    // TODO: Implement proper AI state check
    return false;
  }

  private float neutralAiDelay() {
    // MONSTERMODE_StartNeutral schedules the next AI think after AIdel,
    // defaulting to 15 frames. The active-action guard keeps this delay
    // pending until the animation has actually completed.
    return SLEEP > 0 ? SLEEP : 15 * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  private void traceDecision(String action, int target, int roll, int secondary, boolean accepted) {
    if (!TRACE_DECISIONS) return;
    com.riiablo.engine.server.event.MonsterAiDecisionEvent event =
        new com.riiablo.engine.server.event.MonsterAiDecisionEvent();
    event.entityId=entityId;event.targetId=target;event.action=action;
    event.roll=roll;event.secondaryRoll=secondary;event.accepted=accepted;
    events.dispatch(event);
  }

  @Override
  public void update(float delta) {
    stateMachine.update();
    if (stateMachine.getCurrentState() == State.DEAD) {
      return;
    }
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;

    nextAction -= delta;
    time -= delta;

    float[] outDist = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(outDist);
    float targetDistance = outDist[0];

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
            nextAction = aiRandom(3f, 5);
            stateMachine.changeState(State.IDLE);
          } else {
            Vector2 dst = tmpVec2.set(mPosition.get(entityId).position);
            dst.add(aiRandom(-5, 5), aiRandom(-5, 5));
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

    // D2MOD: Check special AI state first
    if (checkSpecialAiState()) {
      // D2MOD: Find better target and attack
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      time = neutralAiDelay();
      return;
    }

    // D2MOD: If target is within range (distance < 20)
    if (targetDistance < SHOOT_DISTANCE) {
      // D2MOD: Check shoot chance
      int shootRoll = params.length > 0 ? rollAiPercentage() : -1;
      if (params.length > 0 && shootRoll < params[0]) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        playSound(monsound + "_attack_1");
        time = neutralAiDelay();
        traceDecision("SHOOT",targetId,shootRoll,-1,true);
        return;
      }

      // D2MOD: 20% chance to circle
      int circleRoll=rollAiPercentage();
      if (circleRoll < 20) {
        boolean moved = pathfinder.findNativeOrbit(entityId, targetId, 3,
            (rollAiRandomNumber() & 255L) < 128L);
        stateMachine.changeState(moved ? State.APPROACH : State.IDLE);
        time = neutralAiDelay();
        traceDecision("CIRCLE",targetId,shootRoll,circleRoll,moved);
        return;
      }

      // D2MOD: Idle
      stateMachine.changeState(State.IDLE);
      time = params.length > 1 ? params[1] * com.riiablo.codec.Animation.FRAME_DURATION : 15f * com.riiablo.codec.Animation.FRAME_DURATION;
      traceDecision("IDLE_NEAR",targetId,shootRoll,circleRoll,true);
      return;
    }

    // D2MOD: Too far, approach
    int approachRoll=params.length > 2 ? rollAiPercentage() : -1;
    if (params.length > 2 && approachRoll < params[2]) {
      int size = mSize.has(entityId) ? mSize.get(entityId).size : 1;
      Vector2 destination = NativeRadiusDestination.calculate(mPosition.get(entityId).position,
          targetPos, size, Math.max(0, params[3]), Math.max(0, params[4]));
      boolean moved = walkTo(destination, Engine.INVALID_ENTITY);
      stateMachine.changeState(moved ? State.APPROACH : State.IDLE);
      time = neutralAiDelay();
      traceDecision("APPROACH",targetId,approachRoll,-1,moved);
      return;
    } else {
      stateMachine.changeState(State.IDLE);
      time = 20 * com.riiablo.codec.Animation.FRAME_DURATION;
      traceDecision("IDLE_FAR",targetId,approachRoll,-1,true);
      return;
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
