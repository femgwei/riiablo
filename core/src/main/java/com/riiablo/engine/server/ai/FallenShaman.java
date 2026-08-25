package com.riiablo.engine.server.ai;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * FallenShaman AI implementation matching D2MOD's AITHINK_Fn013_FallenShaman logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = FALLENSHAMAN_AI_PARAM_RESURRECT_AND_COMMAND_CHANCE_PCT
 * - params[1] = FALLENSHAMAN_AI_PARAM_SHOOT_CHANCE_PCT
 * - params[2] = FALLENSHAMAN_AI_PARAM_MELEE_AND_CIRCLE_CHANCE_PCT
 * - params[3] = FALLENSHAMAN_AI_PARAM_RESURRECT_DISTANCE
 * - params[4] = FALLENSHAMAN_AI_PARAM_SHOOT_DISTANCE
 */
public class FallenShaman extends AI {
  private static final Logger log = LogManager.getLogger(FallenShaman.class);

  static final int PARAM_RESURRECT_AND_COMMAND_CHANCE = 0;
  static final int PARAM_SHOOT_CHANCE = 1;
  static final int PARAM_MELEE_AND_CIRCLE_CHANCE = 2;
  static final int PARAM_RESURRECT_DISTANCE = 3;
  static final int PARAM_SHOOT_DISTANCE = 4;

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

  final Vector2 tmpVec2 = new Vector2();
  final float[] targetDistance = { Float.MAX_VALUE };

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public FallenShaman(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
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

  static boolean withinShootDistance(float distance, int shootDistance) {
    return shootDistance > 0 && distance < shootDistance;
  }

  private boolean castFireball(int targetId, Vector2 targetPos) {
    stateMachine.changeState(State.CAST);
    if (useMonsterSkill(1, targetId, targetPos)) {
      time = MathUtils.random(1f, 2f);
      return true;
    }

    Monster current = mMonster.get(entityId);
    String configuredSkill = current.monstats != null ? current.monstats.Skill2 : null;
    log.warn(
        "[MONSTER_SKILL] phase=shoot_failed entity={} monster={} slot=2 skill={} target={}",
        entityId,
        current.monstats != null ? current.monstats.Id : "unknown",
        configuredSkill,
        targetId);
    stateMachine.changeState(State.IDLE);
    return false;
  }

  /** Approximation of native sub_6FCD0E80: pick a short lateral step around the target. */
  private void circleTarget(int targetId) {
    Vector2 entityPos = mPosition.get(entityId).position;
    Vector2 targetPos = mPosition.get(targetId).position;
    tmpVec2.set(entityPos).sub(targetPos);
    if (tmpVec2.isZero(0.0001f)) tmpVec2.set(1f, 0f);
    tmpVec2.setLength(3f).rotate90(MathUtils.randomBoolean() ? 1 : -1).add(targetPos);
    walkTo(tmpVec2, Engine.INVALID_ENTITY);
    stateMachine.changeState(State.APPROACH);
    time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;
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

    targetDistance[0] = Float.MAX_VALUE;
    int targetId = findNearestTargetWithAidist(targetDistance);

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

    // Native checks melee first, but a failed melee roll continues into
    // resurrection and shooting instead of forcing an idle action.
    if (bCombat && MathUtils.randomBoolean(params[PARAM_MELEE_AND_CIRCLE_CHANCE] / 100f)) {
      stopMovement();
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2f);
      return;
    }

    int corpseId = findNearbyCorpse(params[PARAM_RESURRECT_DISTANCE]);
    if (corpseId != Engine.INVALID_ENTITY
        && MathUtils.randomBoolean(params[PARAM_RESURRECT_AND_COMMAND_CHANCE] / 100f)) {
      // The corpse search and resurrection effect are implemented in the next
      // stage. Keeping this branch after melee preserves the native ordering.
      if (useMonsterSkill(0, corpseId, mPosition.get(corpseId).position)) {
        stateMachine.changeState(State.CAST);
        time = MathUtils.random(1f, 2f);
        return;
      }
    }

    if (withinShootDistance(targetDistance[0], params[PARAM_SHOOT_DISTANCE])
        && MathUtils.randomBoolean(params[PARAM_SHOOT_CHANCE] / 100f)
        && castFireball(targetId, targetPos)) {
      return;
    }

    if (MathUtils.randomBoolean(params[PARAM_MELEE_AND_CIRCLE_CHANCE] / 100f)) {
      circleTarget(targetId);
      return;
    }

    stopMovement();
    stateMachine.changeState(State.IDLE);
    time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
