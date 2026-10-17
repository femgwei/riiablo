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
import com.riiablo.engine.server.component.AttributesWrapper;

/**
 * Baboon AI implementation matching D2MOD's AITHINK_Fn011_Baboon logic.
 * 
 * D2MOD AI Parameters:
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

  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<com.riiablo.engine.server.component.Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  protected ComponentMapper<com.riiablo.engine.server.component.NativeAiState> mNativeAiState;

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
   * Get current life percentage (0-100).
   */
  private float getLifePercentage() {
    if (!mAttributesWrapper.has(entityId)) return 100f;
    com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return 100f;
    int hitpoints = attrs.getValue(com.riiablo.attributes.Stat.hitpoints, 0);
    int maxhp = attrs.getValue(com.riiablo.attributes.Stat.maxhp, 0);
    return maxhp > 0 ? (100L * hitpoints) / maxhp : 100f;
  }

  /**
   * D2Game sub_6FCF2E70: MonsterData mode memory is 3 or 19.
   */
  private boolean checkSpecialAiState() {
    com.riiablo.engine.server.component.NativeAiState memory = mNativeAiState.get(entityId);
    return memory != null && memory.isUncontrolled();
  }

  /** AITACTICS_WalkCloseToUnit: four unit-seed draws around this monster. */
  private boolean walkCloseToSelf(int radius, int velocityBonus) {
    boolean axis = (rollAiRandomNumber() & 1L) != 0;
    int random = (int) (rollAiRandomNumber() % radius);
    int x = axis ? radius : random;
    int y = axis ? random : radius;
    if ((rollAiRandomNumber() & 1L) != 0) x = -x;
    if ((rollAiRandomNumber() & 1L) != 0) y = -y;
    Vector2 source = mPosition.get(entityId).position;
    return walkTo(new Vector2((int) source.x + x, (int) source.y + y),
        velocityBonus, Engine.INVALID_ENTITY);
  }

  private void changeRegen(int delta) {
    if (!mAttributesWrapper.has(entityId)) return;
    com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return;
    short stat = com.riiablo.attributes.Stat.hpregen;
    int base = attrs.base().getValue(stat, 0);
    int aggregate = attrs.getValue(stat, 0);
    attrs.base().put(stat, base + delta);
    attrs.aggregate().put(stat, aggregate + delta);
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

    // D2MOD: Calculate velocity modifier based on run/velocity ratio
    int nVel = 0;
    if (monster.monstats.Velocity > 0) {
      int ratio = 100 * monster.monstats.Run / monster.monstats.Velocity;
      if (ratio >= 100) nVel = Math.min(ratio - 100, 120);
    }

    // D2MOD: If in regen state
    if (aiParam0 > 0) {
      aiParam1 = 0;
      aiParam0--;

      // D2MOD: Remove regen bonus if timer expired or life > 75%
      if (aiParam0 == 0 || lifePercent > 75) {
        // D2MOD: STATLIST_SetUnitStat(pUnit, STAT_HPREGEN, STATLIST_UnitGetStatValue(pUnit, STAT_HPREGEN, 0) - pAiTickParam->pAiControl->dwAiParam[2], 0)
        changeRegen(-aiParam2);
      }

      float[] targetDistances = {Float.MAX_VALUE};
      int targetId = findNearestTargetWithAidist(targetDistances);
      float targetDistance = targetDistances[0];
      Vector2 entityPos = mPosition.get(entityId).position;

      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        boolean bCombat = isInCombat(targetId);

        // D2MOD: If in combat, 33% chance to attack
        if (bCombat && rollAiPercentage() < 33) {
          pathfinder.findPath(entityId, null);
          lookAt(targetId);
          stateMachine.changeState(State.ATTACK);
          byte attackMode = rollAiChance(params[3]) ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2;
          mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
          mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
          time = MathUtils.random(1f, 2);
          return;
        }

        // Fn011 only leaves regeneration through this branch outside melee.
        if (!bCombat && lifePercent > 75) {
          aiParam0 = 0;
          // D2MOD: AITACTICS_SetVelocity(pUnit, 13, nVel, 0)
          // D2MOD: sub_6FCD0410(pGame, pUnit, pAiTickParam->pTarget, 7)
          walkTo(targetPos, nVel, targetId);
          stateMachine.changeState(State.APPROACH);
          time = MathUtils.random(1f, 2);
          return;
        }

        // D2MOD: If far (distance >= 24) and not in special state
        if (targetDistance >= 24 && !checkSpecialAiState()) {
          if (rollAiPercentage() < 33) {
            pathfinder.findNativeOrbit(entityId, targetId, 4, (rollAiRandomNumber() & 255L) < 128L);
          }
          stopMovement();
          stateMachine.changeState(State.IDLE);
          time = 20f * com.riiablo.codec.Animation.FRAME_DURATION;
          return;
        }

        // D2MOD: Try to escape
        // D2MOD: AITACTICS_SetVelocity(pUnit, 2, nVel, 0)
        // D2MOD: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pAiTickParam->pTarget, 15, 1)
        stateMachine.changeState(State.ESCAPE);
        Vector2 escapePos = tmpVec2.set(entityPos).sub(targetPos).nor().scl(15f).add(entityPos);
        boolean escaped = walkTo(escapePos, nVel, Engine.INVALID_ENTITY);
        if (!escaped) {
          // Can't escape
          if (!bCombat) {
            // D2MOD: AITACTICS_WalkCloseToUnit(pGame, pUnit, 5u)
            stateMachine.changeState(walkCloseToSelf(5, nVel) ? State.APPROACH : State.IDLE);
            return;
          } else {
            // Attack
            pathfinder.findPath(entityId, null);
            lookAt(targetId);
            stateMachine.changeState(State.ATTACK);
            byte attackMode = rollAiChance(params[3]) ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2;
            mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
            mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
          }
        }
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    float[] targetDistances = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(targetDistances);
    float targetDistance = targetDistances[0];
    Vector2 entityPos = mPosition.get(entityId).position;

    if (targetId == Engine.INVALID_ENTITY) {
      stopMovement();
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

    // D2MOD: If not in combat, approach
    if (!bCombat) {
      // D2MOD: sub_6FCD0410(pGame, pUnit, pAiTickParam->pTarget, 7)
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: In combat
    if (checkSpecialAiState()) {
      // D2MOD: If hurt, start regen
      if (lifePercent < hurtPct && rollAiPercentage() < 50) {
        aiParam0 = (int) (rollAiRandomNumber() % 5) + 2;
        
        int regen = mAttributesWrapper.has(entityId)
            ? mAttributesWrapper.get(entityId).attrs.getValue(com.riiablo.attributes.Stat.hpregen, 0) : 0;
        aiParam2 = regen * params[4] / 8;
        changeRegen(aiParam2);

        // D2MOD: AITACTICS_SetVelocity(pUnit, 2, nVel, 0)
        // D2MOD: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pAiTickParam->pTarget, 0xFu, 0)
        stateMachine.changeState(State.ESCAPE);
        Vector2 escapePos = tmpVec2.set(entityPos).sub(targetPos).nor().scl(15f).add(entityPos);
        walkTo(escapePos, nVel, Engine.INVALID_ENTITY);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOD: If not attacked yet, attack
      if (aiParam1 == 0) {
        aiParam1 = 1;
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        byte attackMode = rollAiChance(params[3]) ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2;
        mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }

      // D2MOD: 20% chance to circle
      if (rollAiPercentage() < 20) {
        boolean orbit = pathfinder.findNativeOrbit(
            entityId, targetId, 3, (rollAiRandomNumber() & 255L) < 128L);
        stateMachine.changeState(orbit ? State.APPROACH : State.IDLE);
        aiParam1 = 0;
        return;
      }
    }

    // D2MOD: Normal attack logic
    if (aiParam1 == 0 || (rollAiChance(params[2]))) {
      aiParam1 = 1;
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      byte attackMode = rollAiChance(params[3]) ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2;
      mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // Fn011 deliberately enters neutral for 15 frames even after requesting orbit.
    if (rollAiChance(params[1])) {
      pathfinder.findNativeOrbit(entityId, targetId, 3, (rollAiRandomNumber() & 255L) < 128L);
      aiParam1 = 0;
    }
    stopMovement();
    stateMachine.changeState(State.IDLE);
    time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}