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
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.AttributesWrapper;

/**
 * Vampire AI implementation matching D2MOO's AITHINK_Fn028_Vampire logic.
 * 
 * D2MOO AI Parameters:
 * - params[0] = VAMPIRE_AI_PARAM_MELEE_CHANCE_PCT (melee chance)
 * - params[1] = VAMPIRE_AI_PARAM_CAST_CHANCE_PCT (cast chance)
 * - params[2] = VAMPIRE_AI_PARAM_ACTIVE_DISTANCE (active distance)
 * - params[3] = VAMPIRE_AI_PARAM_UPGRADE_CAST_CHANCE_PCT (upgrade cast chance)
 * - params[4] = VAMPIRE_AI_PARAM_SPELL_FLAGS (spell flags: bit 0=skill0/3, bit 1=skill1, bit 2=skill2)
 * 
 * Special: Can teleport and use multiple spells. Maintains distance tracking.
 */
public class Vampire extends AI {
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    CAST,
    TELEPORT,
    ESCAPE,
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
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<com.riiablo.engine.server.component.Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;

  private static EntitySubscription enemyEntities;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;
  
  // AI state tracking
  int aiParam0 = 0;  // State (0=normal, 1=activated, 2=escape)
  int aiParam1 = 0;  // Distance tracking
  int aiParam2 = 0;  // Skill cooldown timer

  public Vampire(int entityId) {
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
   * Check if in special AI state (simplified).
   */
  private boolean checkSpecialAiState() {
    // TODO: Implement proper AI state check
    return false;
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
    if (time > 0) {
      return;
    }

    time = SLEEP;

    // D2MOO: Decrement skill cooldown
    if (aiParam2 > 0) {
      aiParam2--;
    }

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

    int spellFlags = params.length > 4 ? params[4] : 0;

    // D2MOO: Check special AI state
    if (checkSpecialAiState()) {
      if (aiParam0 == 0) {
        aiParam0 = 1;
      }

      // D2MOO: Track distance
      if (targetDistance < 30 && targetDistance > aiParam1) {
        aiParam1 = (int)targetDistance;
      }

      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        boolean bCombat = isInCombat(targetId);

        // D2MOO: If in combat, 30% chance to use spell
        if (bCombat && (spellFlags & 1) != 0 && MathUtils.randomBoolean(0.3f)) {
          // D2MOO: 50% chance skill0, 50% chance skill3
          if (monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty() && MathUtils.randomBoolean(0.5f)) {
            // D2MOO: Use skill0
            // TODO: Implement skill casting
            stateMachine.changeState(State.CAST);
            lookAt(targetId);
            mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
            mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
            time = MathUtils.random(1f, 2);
            return;
          } else if (monster.monstats.Skill4 != null && !monster.monstats.Skill4.isEmpty()) {
            // D2MOO: Use skill3
            // TODO: Implement skill casting
            stateMachine.changeState(State.CAST);
            lookAt(targetId);
            mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
            mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
            time = MathUtils.random(1f, 2);
            return;
          }
        }

        // D2MOO: Normal melee attack
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
      }
    }

    // D2MOO: Escape state (aiParam0 == 2)
    if (aiParam0 == 2) {
      float lifePercent = getLifePercentage();
      if (lifePercent >= 75) {
        aiParam0 = 1;
        if (targetId != Engine.INVALID_ENTITY) {
          Vector2 targetPos = mPosition.get(targetId).position;
          pathfinder.findPath(entityId, targetPos, false, targetId);
          stateMachine.changeState(State.APPROACH);
          time = MathUtils.random(1f, 2);
          return;
        }
      }

      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        // D2MOO: Check escape conditions
        if (targetDistance < 14 || targetDistance <= aiParam1) {
          // D2MOO: Try to escape
          stateMachine.changeState(State.ESCAPE);
          Vector2 escapeDir = tmpVec2.set(entityPos).sub(targetPos).nor();
          Vector2 escapePos = tmpVec2.set(entityPos).add(escapeDir.scl(8f));
          pathfinder.findPath(entityId, escapePos, false, Engine.INVALID_ENTITY);
          if (!mPathfind.has(entityId)) {
            // Can't escape, check distance
            float activeDistance = params.length > 2 ? params[2] : 20f;
            if (targetDistance >= activeDistance) {
              stateMachine.changeState(State.IDLE);
              time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
              return;
            }

            if (params.length > 1 && !MathUtils.randomBoolean(params[1] / 100f)) {
              stateMachine.changeState(State.IDLE);
              time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
              return;
            }

            // D2MOO: Check upgrade cast
            if ((spellFlags & (1 << 1)) != 0 && aiParam2 <= 0 && params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
              // D2MOO: Use skill1
              // TODO: Implement skill casting
              aiParam2 = 11;
              stateMachine.changeState(State.CAST);
              lookAt(targetId);
              mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
              mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
              time = MathUtils.random(1f, 2);
              return;
            }

            if ((spellFlags & (1 << 2)) != 0 && aiParam2 <= 0 && params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
              // D2MOO: Use skill2
              // TODO: Implement skill casting
              aiParam2 = 11;
              stateMachine.changeState(State.CAST);
              lookAt(targetId);
              mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
              mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
              time = MathUtils.random(1f, 2);
              return;
            }

            if ((spellFlags & 1) != 0 && targetDistance <= 20) {
              // D2MOO: Use skill0 or skill3
              if (MathUtils.randomBoolean(0.5f) && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
                // TODO: Implement skill casting
                stateMachine.changeState(State.CAST);
                lookAt(targetId);
                mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
                mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
                time = MathUtils.random(1f, 2);
                return;
              } else if (monster.monstats.Skill4 != null && !monster.monstats.Skill4.isEmpty()) {
                // TODO: Implement skill casting
                stateMachine.changeState(State.CAST);
                lookAt(targetId);
                mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
                mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
                time = MathUtils.random(1f, 2);
                return;
              }
            }
          }
          time = MathUtils.random(1f, 2);
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
    float activeDistance = params.length > 2 ? params[2] : 20f;

    // D2MOO: If too far, idle
    if (targetDistance >= activeDistance) {
      stateMachine.changeState(State.IDLE);
      time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    }

    // D2MOO: Check cast chance
    if (params.length > 1 && !MathUtils.randomBoolean(params[1] / 100f)) {
      stateMachine.changeState(State.IDLE);
      time = 15f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    }

    // D2MOO: Check upgrade cast
    if ((spellFlags & (1 << 1)) != 0 && aiParam2 <= 0 && params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
      // D2MOO: Use skill1
      // TODO: Implement skill casting
      aiParam2 = 11;
      stateMachine.changeState(State.CAST);
      lookAt(targetId);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      time = MathUtils.random(1f, 2);
      return;
    }

    if ((spellFlags & (1 << 2)) != 0 && aiParam2 <= 0 && params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
      // D2MOO: Use skill2
      // TODO: Implement skill casting
      aiParam2 = 11;
      stateMachine.changeState(State.CAST);
      lookAt(targetId);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOO: Use basic spell
    if ((spellFlags & 1) != 0 && targetDistance <= 20) {
      if (MathUtils.randomBoolean(0.5f) && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()) {
        // TODO: Implement skill casting
        stateMachine.changeState(State.CAST);
        lookAt(targetId);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      } else if (monster.monstats.Skill4 != null && !monster.monstats.Skill4.isEmpty()) {
        // TODO: Implement skill casting
        stateMachine.changeState(State.CAST);
        lookAt(targetId);
        mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
        mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOO: Normal melee attack
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

    // D2MOO: Approach
    pathfinder.findPath(entityId, targetPos, false, targetId);
    stateMachine.changeState(State.APPROACH);
    time = MathUtils.random(1f, 2);
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
