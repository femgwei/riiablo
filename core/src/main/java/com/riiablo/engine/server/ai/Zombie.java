package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Player;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Zombie AI implementation matching D2MOD's AITHINK_Fn003_Zombie logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = ZOMBIE_AI_PARAM_APPROACH_CHANCE_PCT (approach chance)
 * - params[1] = ZOMBIE_AI_PARAM_AWARE_DISTANCE (aware distance)
 * - params[3] = ZOMBIE_AI_PARAM_ATTACK_1_OR_2_CHANCE_PCT (A1 vs A2 chance)
 */
public class Zombie extends AI {
  private static final Logger log = LogManager.getLogger(Zombie.class);

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

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<CofReference> mCofReference;

  private static EntitySubscription enemyEntities;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public Zombie(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    if (enemyEntities == null) {
      enemyEntities = Riiablo.engine.getAspectSubscriptionManager().get(Aspect
              .all(Class.class)
              .one(Player.class));
    }
  }

  @Override
  public void kill() {
    if (stateMachine.getCurrentState() == State.DEAD) return;
    pathfinder.findPath(entityId, null);
    stateMachine.changeState(State.DEAD);
    // Create death sequence: MODE_DT (death animation) -> MODE_DD (corpse)
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    log.debug("Zombie killed: entityId={}, starting death sequence: MODE_DT -> MODE_DD", entityId);
    Riiablo.audio.play(monsound + "_death_1", true);
  }

  /**
   * Check if monster is in special AI state (sub_6FCF2E70).
   * D2MOD: Returns true if AI state == 3 || AI state == 19.
   * For now, we'll use a simplified check.
   */
  private boolean checkSpecialAiState() {
    // TODO: Implement proper AI state check
    // D2MOD checks MONSTER_GetAiState(pUnit) == 3 || == 19
    return false;
  }

  /**
   * Check if current level is Burial Grounds.
   * D2MOD: DUNGEON_GetLevelIdFromRoom(UNITS_GetRoom(pUnit)) == LEVEL_BURIALGROUNDS
   */
  private boolean isInBurialGrounds() {
    // TODO: Implement level ID check
    // For now, return false
    return false;
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

    // Find target
    int targetId = Engine.INVALID_ENTITY;
    float targetDistance = Float.MAX_VALUE;
    Vector2 entityPos = mPosition.get(entityId).position;
    
    IntBag entities = enemyEntities.getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int ent = entities.get(i);
      if (mClass.get(ent).type == Class.Type.PLR) {
        Vector2 targetPos = mPosition.get(ent).position;
        float dst = entityPos.dst(targetPos);
        if (dst < targetDistance) {
          targetDistance = dst;
          targetId = ent;
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
    float meleeRng = 1f + monster.monstats2.MeleeRng;
    
    // Calculate ranged attack range if monster has ranged attack capability
    float rangedRng = 0f;
    if ((monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty()) ||
        (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty())) {
      String missileName = null;
      if (monster.monstats.MissA1 != null && !monster.monstats.MissA1.isEmpty()) {
        missileName = monster.monstats.MissA1;
      } else if (monster.monstats.MissA2 != null && !monster.monstats.MissA2.isEmpty()) {
        missileName = monster.monstats.MissA2;
      }
      if (missileName != null) {
        com.riiablo.codec.excel.Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
        if (missile != null) {
          rangedRng = missile.Range - 2f;
          if (rangedRng < meleeRng) {
            rangedRng = meleeRng + 5f;
          }
        }
      }
    }

    // D2MOD: If in combat, attack
    if (bCombat) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      byte attackMode = MathUtils.randomBoolean(params[3] / 100f) ? Engine.Monster.MODE_A2 : Engine.Monster.MODE_A1;
      mSequence.create(entityId).sequence(attackMode, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Not in combat
    // Check conditions for running to target:
    // 1. checkSpecialAiState() OR
    // 2. (distance < AWARE_DISTANCE AND random chance) OR
    // 3. isInBurialGrounds()
    boolean shouldRun = checkSpecialAiState() 
        || (targetDistance < params[1] && MathUtils.randomBoolean(params[0] / 100f))
        || isInBurialGrounds();

    if (shouldRun) {
      // Run to target (set velocity to 100 like D2MOD)
      if (mVelocity.has(entityId)) {
        mVelocity.get(entityId).velocity.set(targetPos).sub(entityPos).nor().scl(100f);
      }
      pathfinder.findPath(entityId, targetPos, false, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    } else {
      // Walk close to unit (distance 3)
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
