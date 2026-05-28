package com.riiablo.engine.server.ai;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;

/**
 * QuillRat AI implementation matching D2MOD's AITHINK_Fn014_QuillRat logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = QUILLRAT_AI_PARAM_ACTIVATE_DISTANCE (activate distance)
 * - params[1] = QUILLRAT_AI_PARAM_SHOOT_CHANCE_PCT (shoot chance)
 * - params[2] = QUILLRAT_AI_PARAM_UNUSED (unused)
 * - params[3] = QUILLRAT_AI_PARAM_WALK_DISTANCE (walk distance)
 * 
 * Special: Ranged attack AI. When target is far, walks randomly around target.
 */
public class QuillRat extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    DEAD;

    @Override public void enter(Integer entity) {}
    @Override public void update(Integer entity) {}
    @Override public void exit(Integer entity) {}
    @Override
    public boolean onMessage(Integer entity, Telegram telegram) {
      return false;
    }
  }

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;
  Missiles.Entry missile;

  public QuillRat(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    monsound = "spikefiend";
    missile = Riiablo.files.Missiles.get(monster.monstats.MissA2);
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
   * 射程内可攻击（用于远程怪即时反应）
   * 使用 activate distance 作为射程，与 D2MOD 一致。
   */
  private boolean isInRangedRange(float distance) {
    float activateDistance = params.length > 0 ? params[0] : 15f;
    return distance <= activateDistance;
  }

  /**
   * Check special AI state (simplified - AI state == 3 or 19).
   */
  private boolean checkSpecialAiState() {
    // TODO: Implement proper AI state check (AI state == 3 or 19)
    return false;
  }

  /**
   * Walk close to target unit (random walk around self, NOT target).
   * D2MOD: AITACTICS_WalkCloseToUnit(pGame, pUnit, nWalkDist)
   * 
   * IMPORTANT: This function walks to a random position around the MONSTER itself,
   * NOT around the target! This is why QuillRat doesn't chase players.
   */
  private void walkCloseToUnit(int targetId, float walkDist) {
    Vector2 entityPos = mPosition.get(entityId).position;
    
    // D2MOD: Calculate random offset from monster's own position
    // nOffsetX and nOffsetY are randomly chosen, one is nMaxDistance, the other is random(0, nMaxDistance)
    float offsetX, offsetY;
    if (MathUtils.randomBoolean()) {
      offsetX = walkDist;
      offsetY = MathUtils.random(0f, walkDist);
    } else {
      offsetX = MathUtils.random(0f, walkDist);
      offsetY = walkDist;
    }
    
    // Randomly negate offsets
    if (MathUtils.randomBoolean()) {
      offsetX = -offsetX;
    }
    if (MathUtils.randomBoolean()) {
      offsetY = -offsetY;
    }
    
    Vector2 walkPos = tmpVec2.set(entityPos);
    walkPos.add(offsetX, offsetY);
    
    pathfinder.findPath(entityId, walkPos, false, Engine.INVALID_ENTITY);
    stateMachine.changeState(State.APPROACH);
  }

  /**
   * Try to escape from target.
   * D2MOD: D2GAME_AICORE_Escape_6FCD0560(pGame, pUnit, pTarget, nWalkDist, 1)
   */
  private boolean tryEscape(int targetId, float walkDist) {
    if (targetId == Engine.INVALID_ENTITY || !mPosition.has(targetId)) return false;
    
    Vector2 targetPos = mPosition.get(targetId).position;
    Vector2 entityPos = mPosition.get(entityId).position;
    
    // Calculate escape direction (away from target)
    Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
    Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(walkDist));
    
    pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
    if (mPathfind.has(entityId)) {
      stateMachine.changeState(State.APPROACH);
      return true;
    }
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

    // 远程怪即时反应：每帧检查玩家是否进入射程，不等到 time 结束
    // 避免 APPROACH 寻路期间玩家靠近却要等走完才攻击
    float[] outDist = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(outDist);
    float targetDistance = outDist[0];
    if (targetId != Engine.INVALID_ENTITY
        && stateMachine.getCurrentState() != State.ATTACK
        && isInRangedRange(targetDistance)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      if (isInCombat(targetId)) {
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, mPosition.get(targetId).position);
        Riiablo.audio.play(monsound + "_attack_1", true);
      } else {
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, mPosition.get(targetId).position);
        Riiablo.audio.play(monsound + "_shoot_1", true);
        fire(missile);
      }
      time = MathUtils.random(1f, 2f);
      return;
    }

    if (time > 0) {
      return;
    }

    time = SLEEP;
    // targetId / targetDistance 已由上方即时反应前的 findNearestTargetWithAidist 得到，主逻辑复用

    // D2MOD: If in combat, use ATTACK1
    if (targetId != Engine.INVALID_ENTITY && isInCombat(targetId)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, mPosition.get(targetId).position);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Check special AI state (sub_6FCF2E70)
    if (targetId != Engine.INVALID_ENTITY && checkSpecialAiState()) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, mPosition.get(targetId).position);
      Riiablo.audio.play(monsound + "_shoot_1", true);
      time = MathUtils.random(1f, 2);
      fire(missile);
      return;
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
    float activateDistance = params.length > 0 ? params[0] : 15f;
    float walkDistance = params.length > 3 ? params[3] : 3f;
    if (walkDistance < 3) walkDistance = 3f;

    // D2MOD: If target distance >= ACTIVATE_DISTANCE, walk close to unit (random walk)
    if (targetDistance >= activateDistance) {
      walkCloseToUnit(targetId, walkDistance);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Target distance < ACTIVATE_DISTANCE
    // Check shoot chance
    if (params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_shoot_1", true);
      time = MathUtils.random(1f, 2);
      fire(missile);
      return;
    }

    // D2MOD: Try to escape
    if (!tryEscape(targetId, walkDistance)) {
      // Can't escape
      if (targetDistance < 4) {
        // D2MOD: If very close, shoot
        pathfinder.findPath(entityId, null);
        lookAt(targetId);
        stateMachine.changeState(State.ATTACK);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        Riiablo.audio.play(monsound + "_shoot_1", true);
        time = MathUtils.random(1f, 2);
        fire(missile);
        return;
      }

      // D2MOD: Walk close to unit (random walk)
      walkCloseToUnit(targetId, walkDistance);
      time = MathUtils.random(1f, 2);
      return;
    }

    // Escaping
    time = MathUtils.random(1f, 2);
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
