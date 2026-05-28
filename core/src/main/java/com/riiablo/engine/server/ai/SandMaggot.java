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
 * SandMaggot AI implementation matching D2MOD's AITHINK_Fn015_SandMaggot logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = SANDMAGGOT_AI_PARAM_LAY_CHANCE_PCT (lay egg chance)
 * - params[1] = SANDMAGGOT_AI_PARAM_SPIT_CHANCE_PCT (spit chance)
 * - params[2] = SANDMAGGOT_AI_PARAM_NUMBER_OF_EGGS (max number of eggs)
 * - params[3] = SANDMAGGOT_AI_PARAM_MELEE_CHANCE_PCT (melee attack chance)
 * - params[4] = SANDMAGGOT_AI_PARAM_MIN_UP_DOWN_TIME (min up/down time)
 * 
 * Special: Can lay eggs (spawn minions) and has burrow/unburrow mechanism.
 */
public class SandMaggot extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    BURROW,
    UNBURROW,
    LAY_EGG,
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
  
  // AI state tracking
  int aiParam0 = 0;  // Burrow state (0=normal, 1=preparing, 2=ready to lay, 3=burrowed)
  int aiParam1 = 0;  // Frame counter for burrow/unburrow timing
  int aiParam2 = 0;  // Number of eggs laid

  public SandMaggot(int entityId) {
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

  @Override
  public void update(float delta) {
    stateMachine.update();
    if (stateMachine.getCurrentState() == State.DEAD) {
      return;
    }

    nextAction -= delta;
    time -= delta;

    // 远程怪即时反应：玩家进入射程（<15 喷吐）立即攻击，不等到 time 结束
    float[] outDist = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(outDist);
    float targetDistance = outDist[0];
    if (targetId != Engine.INVALID_ENTITY
        && stateMachine.getCurrentState() != State.ATTACK
        && stateMachine.getCurrentState() != State.BURROW
        && stateMachine.getCurrentState() != State.UNBURROW
        && stateMachine.getCurrentState() != State.LAY_EGG
        && targetDistance < 15f) {
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

    // D2MOD: Burrow state handling (nParam >= 3)
    if (aiParam0 >= 3) {
      if (targetId == Engine.INVALID_ENTITY || targetDistance >= 16) {
        if (aiParam0 == 3) {
          // D2MOD: sub_6FCD0150(pGame, pUnit, 20)
          stateMachine.changeState(State.BURROW);
          time = 20f * com.riiablo.codec.Animation.FRAME_DURATION;
          return;
        }
      }

      if (aiParam0 == 3) {
        // D2MOD: Check if can unburrow and use skill
        // TODO: Check game frame vs aiParam1
        if (monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
          // D2MOD: Use skill (nSkill[0]) - unburrow and attack
          // TODO: Implement skill casting
          stateMachine.changeState(State.UNBURROW);
          aiParam0 = 1;
          int minUpDownTime = params.length > 4 ? params[4] : 30;
          aiParam1 = minUpDownTime; // Set frame counter
          time = 25f * com.riiablo.codec.Animation.FRAME_DURATION;
          return;
        }

        stateMachine.changeState(State.BURROW);
        time = 20f * com.riiablo.codec.Animation.FRAME_DURATION;
        return;
      }
    } else {
      // D2MOD: Normal state (nParam < 3)
      if (targetId == Engine.INVALID_ENTITY || targetDistance > 10) {
        // D2MOD: Check if should burrow (use skill[1])
        // TODO: Check game frame vs aiParam1
        if (monster.monstats.Skill2 != null && !monster.monstats.Skill2.isEmpty()) {
          // D2MOD: Use burrow skill (nSkill[1])
          // TODO: Implement skill casting
          stateMachine.changeState(State.BURROW);
          aiParam0 = 3;
          int minUpDownTime = params.length > 4 ? params[4] : 30;
          aiParam1 = minUpDownTime; // Set frame counter
          time = 30f * com.riiablo.codec.Animation.FRAME_DURATION;
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
    float lifePercent = getLifePercentage();

    // D2MOD: If hurt (< 25%) and close (< 7), try to burrow
    if (lifePercent < 25 && targetDistance < 7 && monster.monstats.Skill2 != null && !monster.monstats.Skill2.isEmpty()
        && MathUtils.randomBoolean(0.2f)) {
      // D2MOD: Use burrow skill (nSkill[1])
      // TODO: Implement skill casting
      stateMachine.changeState(State.BURROW);
      aiParam0 = 3;
      int minUpDownTime = params.length > 4 ? params[4] : 30;
      aiParam1 = minUpDownTime;
      time = 30f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    }

    // D2MOD: If in combat, melee attack
    if (bCombat && params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: If close (< 15), spit attack
    if (targetDistance < 15 && params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: 20% chance to circle
    if (MathUtils.randomBoolean(0.2f)) {
      // D2MOD: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 6u, 0)
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Check if should lay egg
    int maxEggs = params.length > 2 ? params[2] : 3;
    if (aiParam2 >= maxEggs || (params.length > 0 && !MathUtils.randomBoolean(params[0] / 100f))) {
      // Can't lay more eggs or no lay chance
      stateMachine.changeState(State.IDLE);
      time = 12f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    }

    // D2MOD: Prepare to lay egg
    if (aiParam0 == 2 && monster.monstats.Skill3 != null && !monster.monstats.Skill3.isEmpty()) {
      // D2MOD: Use lay egg skill (nSkill[2])
      // TODO: Implement skill casting
      aiParam0 = 1;
      aiParam2++;
      stateMachine.changeState(State.LAY_EGG);
      time = 20f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    }

    // D2MOD: Start preparing to lay egg
    // D2MOD: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 6u, 0)
    pathfinder.findPath(entityId, targetPos, false, targetId);
    aiParam0 = 2;
    stateMachine.changeState(State.APPROACH);
    time = MathUtils.random(1f, 2);
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
