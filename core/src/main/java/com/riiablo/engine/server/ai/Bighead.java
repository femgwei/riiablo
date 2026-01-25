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
import com.riiablo.engine.server.component.AttributesWrapper;

/**
 * Bighead AI implementation matching D2MOO's AITHINK_Fn004_Bighead logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = BIGHEAD_AI_PARAM_HURT_PCT (hurt percentage threshold)
 * - params[1] = BIGHEAD_AI_PARAM_FIRE_WHILE_HEALTHY_CHANCE_PCT (fire while healthy chance)
 * - params[2] = BIGHEAD_AI_PARAM_FIRE_WHILE_HURT_CHANCE_PCT (fire while hurt chance)
 * - params[3] = BIGHEAD_AI_PARAM_CIRCLE_CHANCE_PCT (circle chance)
 * 
 * Special: Has different behavior when healthy vs hurt. Can escape when hurt.
 */
public class Bighead extends AI {
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
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public Bighead(int entityId) {
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

    // 远程怪即时反应：玩家进入射程（15 内喷吐/近战）立即攻击，不等到 time 结束
    float[] outDist = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(outDist);
    float targetDistance = outDist[0];
    if (targetId != Engine.INVALID_ENTITY
        && stateMachine.getCurrentState() != State.ATTACK
        && stateMachine.getCurrentState() != State.ESCAPE
        && targetDistance < 15f) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      Vector2 targetPos = mPosition.get(targetId).position;
      boolean bCombat = isInCombat(targetId);
      if (bCombat) {
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      } else {
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      }
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
    boolean bCombat = isInCombat(targetId);
    float lifePercent = getLifePercentage();
    float hurtPct = params.length > 0 ? params[0] : 50f;

    // D2MOO: Check special AI state first
    if (!bCombat && checkSpecialAiState()) {
      // D2MOO: AITACTICS_ChangeModeAndTargetUnit(pGame, pUnit, MONMODE_ATTACK2, pAiTickParam->pTarget)
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: Healthy behavior
    if (lifePercent >= hurtPct) {
      if (bCombat) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        Riiablo.audio.play(monsound + "_attack_1", true);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOO: Check if should fire while healthy (distance < 15)
      if (targetDistance < 15 && params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOO: Walk to target
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: Hurt behavior
    if (targetDistance >= 3) {
      if (targetDistance > 15) {
        // D2MOO: AITACTICS_WalkToTargetUnitWithSteps(pGame, pUnit, pAiTickParam->pTarget, 6u)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOO: Check if should fire while hurt
      if (params.length > 2 && MathUtils.randomBoolean(params[2] / 100f)) {
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOO: Circle or idle
      if (params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
        // D2MOO: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 3u, 0)
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      } else {
        stateMachine.changeState(State.IDLE);
        time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;
        return;
      }
    } else {
      // D2MOO: Very close, escape
      // D2MOO: AITACTICS_SetVelocity(pUnit, 0, 50, 0)
      // D2MOO: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pAiTickParam->pTarget, 5u, 1)
      stateMachine.changeState(State.ESCAPE);
      // Try to escape, if can't escape, attack
      Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
      Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(5f));
      pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
      if (!mPathfind.has(entityId)) {
        // Can't escape, attack
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      }
      time = MathUtils.random(1f, 2);
      return;
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
