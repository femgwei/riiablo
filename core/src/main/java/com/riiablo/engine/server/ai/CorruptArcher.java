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
 * CorruptArcher AI implementation matching D2MOD's AITHINK_Fn035_CorruptArcher logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = CORRUPTARCHER_AI_PARAM_APPROACH_CHANCE_PCT (approach chance)
 * - params[1] = CORRUPTARCHER_AI_PARAM_SHOOT_CHANCE_PCT (shoot chance)
 * - params[2] = CORRUPTARCHER_AI_PARAM_STALL_DURATION (idle time)
 * - params[3] = CORRUPTARCHER_AI_PARAM_RUN_CHANCE_PCT (close escape chance)
 * - params[4] = CORRUPTARCHER_AI_PARAM_ALWAYS_RUN_DISTANCE (always run distance)
 * - params[5] = CORRUPTARCHER_AI_PARAM_USE_SKILL_2_CHANCE_PCT (use skill2 chance)
 * - params[6] = CORRUPTARCHER_AI_PARAM_USE_SKILL_3_CHANCE_PCT (use skill3 chance)
 * - params[7] = CORRUPTARCHER_AI_PARAM_WALK_TOW_DISTANCE (walk toward distance)
 * 
 * Special: Ranged attack AI with multiple skills. Can run when far.
 */
public class CorruptArcher extends AI {
  static final int PARAM_APPROACH_CHANCE = 0;
  static final int PARAM_SHOOT_CHANCE = 1;
  static final int PARAM_STALL_DURATION = 2;
  static final int PARAM_RUN_CHANCE = 3;
  static final int PARAM_ALWAYS_RUN_DISTANCE = 4;
  static final int PARAM_USE_SKILL_2_CHANCE = 5;
  static final int PARAM_USE_SKILL_3_CHANCE = 6;
  static final int PARAM_WALK_TOW_DISTANCE = 7;

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

  public CorruptArcher(int entityId) {
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
    stopMovement();
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
   * Check special condition (simplified).
   */
  private boolean checkSpecialCondition(int targetId, float distance) {
    return targetId != Engine.INVALID_ENTITY
        && distance < 6f
        && MathUtils.randomBoolean(params[PARAM_RUN_CHANCE] / 100f);
  }

  private void castSkillOrAttack(int skillIndex, int targetId, Vector2 targetPos) {
    if (useMonsterSkill(skillIndex, targetId, targetPos)) return;
    stateMachine.changeState(State.ATTACK);
    lookAt(targetId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
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
    float rangedRange = missile != null && missile.Range > 0 ? missile.Range - 2f : 15f;
    if (rangedRange <= 0) rangedRange = 15f;
    if (targetId != Engine.INVALID_ENTITY
        && stateMachine.getCurrentState() != State.ATTACK
        && stateMachine.getCurrentState() != State.CAST
        && targetDistance <= rangedRange) {
      stopMovement();
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
            stopMovement();
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
            walkTo(dst, Engine.INVALID_ENTITY);
          }
          break;
        default:
          stateMachine.changeState(State.IDLE);
          break;
      }
      return;
    }

    Vector2 entityPos = mPosition.get(entityId).position;
    Vector2 targetPos = mPosition.get(targetId).position;
    boolean bCombat = isInCombat(targetId);

    // D2MOD: Check special condition
    if (checkSpecialCondition(targetId, targetDistance)) {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 100, 0)
      // D2MOD: sub_6FCD06D0(pGame, pUnit, pTarget, 12, 1)
      Vector2 escapePos = tmpVec2.set(entityPos).sub(targetPos).nor().scl(12f).add(entityPos);
      runTo(escapePos, 100, Engine.INVALID_ENTITY);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: If far, walk toward target
    float walkTowDistance = params[PARAM_WALK_TOW_DISTANCE];
    if (walkTowDistance > 0 && targetDistance > walkTowDistance
        && MathUtils.randomBoolean(params[PARAM_APPROACH_CHANCE] / 100f)) {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 10, 0)
      // D2MOD: AITACTICS_WalkToTargetUnitWithSteps(pGame, pUnit, pTarget, AI_GetParamValue(pGame, pAiTickParam, CORRUPTARCHER_AI_PARAM_WALK_TOW_DISTANCE))
      walkTo(targetPos, 10, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: If very far, always run
    float alwaysRunDistance = params[PARAM_ALWAYS_RUN_DISTANCE];
    if (targetDistance > alwaysRunDistance) {
      // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 100, 0)
      // D2MOD: AITACTICS_RunToTargetUnitWithSteps(pGame, pUnit, pTarget, AI_GetParamValue(pGame, pAiTickParam, CORRUPTARCHER_AI_PARAM_ALWAYS_RUN_DISTANCE))
      runTo(targetPos, 100, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Check shoot chance
    if (MathUtils.randomBoolean(params[PARAM_SHOOT_CHANCE] / 100f)) {
      // D2MOD: Check skill usage
      if (monster.monstats.Skill2 != null && !monster.monstats.Skill2.isEmpty()
          && MathUtils.randomBoolean(params[PARAM_USE_SKILL_2_CHANCE] / 100f)) {
        stateMachine.changeState(State.CAST);
        castSkillOrAttack(1, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }

      if (monster.monstats.Skill3 != null && !monster.monstats.Skill3.isEmpty()
          && MathUtils.randomBoolean(params[PARAM_USE_SKILL_3_CHANCE] / 100f)) {
        stateMachine.changeState(State.CAST);
        castSkillOrAttack(2, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }

      if (monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
        stateMachine.changeState(State.CAST);
        castSkillOrAttack(0, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOD: Normal attack
      stopMovement();
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    stateMachine.changeState(State.IDLE);
    time = params[PARAM_STALL_DURATION] * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
