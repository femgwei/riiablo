package com.riiablo.engine.server.ai;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.engine.server.component.Sequence;

/**
 * GreaterMummy AI implementation matching D2MOD's AITHINK_Fn022_GreaterMummy logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = GREATMUMMY_AI_PARAM_MELEE_BREATHE_CHANCE_PCT (melee breathe chance)
 * - params[1] = GREATMUMMY_AI_PARAM_RAISE_CHANCE_PCT (raise dead chance)
 * - params[2] = GREATMUMMY_AI_PARAM_HEAL_CHANCE_PCT (heal chance)
 * - params[3] = GREATMUMMY_AI_PARAM_SHOOT_CHANCE_PCT (shoot chance)
 * - params[4] = GREATMUMMY_AI_PARAM_RAISE_RANGE (raise range)
 * 
 * Special: Can raise dead, heal allies, and shoot projectiles.
 */
public class GreaterMummy extends AI {
  private static final Logger log = LogManager.getLogger(GreaterMummy.class);
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    CAST,
    RAISE,
    HEAL,
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

  private EntitySubscription allyEntities;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public GreaterMummy(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    allyEntities = Riiablo.engine.getAspectSubscriptionManager().get(Aspect.all(
        Monster.class, Position.class, AttributesWrapper.class));
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
   * Find nearby corpse to raise (simplified).
   */
  private int findNearbyCorpse(float maxRange) {
    // TODO: Implement corpse finding logic
    return Engine.INVALID_ENTITY;
  }

  /** Native Greater Mummy callback: choose the nearest wounded allied undead. */
  private int findNearbyAlly(float maxRange) {
    if (allyEntities == null || !mPosition.has(entityId) || maxRange <= 0f) {
      return Engine.INVALID_ENTITY;
    }
    Vector2 source = mPosition.get(entityId).position;
    float bestDistance2 = maxRange * maxRange;
    int best = Engine.INVALID_ENTITY;
    IntBag entities = allyEntities.getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int candidateId = entities.get(i);
      if (candidateId == entityId || !mMonster.has(candidateId)
          || !mAttributesWrapper.has(candidateId)) continue;
      Monster candidate = mMonster.get(candidateId);
      if (!isBestowEligible(candidate, mAttributesWrapper.get(candidateId).attrs)) continue;
      float distance2 = source.dst2(mPosition.get(candidateId).position);
      if (distance2 > bestDistance2) continue;
      bestDistance2 = distance2;
      best = candidateId;
    }
    return best;
  }

  public static boolean isBestowEligible(Monster candidate, com.riiablo.attributes.Attributes attrs) {
    if (candidate == null || candidate.monstats == null || attrs == null) return false;
    // D2MOO's callback filters to undead unless the special Radament branch is
    // active. Greater Mummy itself is always the undead healer in this AI.
    if (!candidate.monstats.lUndead && !candidate.monstats.hUndead) return false;
    com.riiablo.attributes.StatRef hp = attrs.get(com.riiablo.attributes.Stat.hitpoints,
        com.riiablo.attributes.StatRef.obtain());
    com.riiablo.attributes.StatRef max = attrs.get(com.riiablo.attributes.Stat.maxhp,
        com.riiablo.attributes.StatRef.obtain());
    return hp != null && max != null && hp.asFixed() > 0f && hp.asFixed() < max.asFixed();
  }

  @Override
  public void update(float delta) {
    stateMachine.update();
    if (stateMachine.getCurrentState() == State.DEAD) {
      return;
    }

    nextAction -= delta;
    time -= delta;

    // 远程怪即时反应：玩家进入射程（<5 喷吐）立即攻击，不等到 time 结束
    float[] outDist = { Float.MAX_VALUE };
    int targetId = findNearestTargetWithAidist(outDist);
    float targetDistance = outDist[0];
    if (targetId != Engine.INVALID_ENTITY
        && stateMachine.getCurrentState() != State.ATTACK
        && stateMachine.getCurrentState() != State.CAST
        && stateMachine.getCurrentState() != State.RAISE
        && stateMachine.getCurrentState() != State.HEAL
        && targetDistance < 5f) {
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

    // D2MOD: If in combat, melee breathe attack
    if (bCombat && params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: If close (< 5), breathe attack
    if (targetDistance < 5 && params.length > 0 && MathUtils.randomBoolean(params[0] / 100f)) {
      pathfinder.findPath(entityId, null);
      lookAt(targetId);
      stateMachine.changeState(State.ATTACK);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      Riiablo.audio.play(monsound + "_attack_1", true);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Check heal chance
    if (monster.monstats.Skill2 != null && !monster.monstats.Skill2.isEmpty()) {
      float healRange = params.length > 4 && params[4] > 0 ? params[4] : 20f;
      int healTargetId = findNearbyAlly(healRange);
      if (healTargetId != Engine.INVALID_ENTITY && params.length > 2 && MathUtils.randomBoolean(params[2] / 100f)) {
        // D2MOO AITACTICS_UseSkill(nSkill[1]) -> srvDoFunc=96 Bestow.
        stateMachine.changeState(State.HEAL);
        lookAt(healTargetId);
        if (!useMonsterSkill(1, healTargetId, mPosition.get(healTargetId).position,
            Engine.Monster.MODE_A1)) {
          stateMachine.changeState(State.IDLE);
          return;
        }
        log.info("[MONSTER_BESTOW] phase=cast source={} target={} monster={} distance={} skill={}",
            entityId, healTargetId, monster.monstats.Id,
            mPosition.get(entityId).position.dst(mPosition.get(healTargetId).position),
            monster.monstats.Skill2);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    // D2MOD: Check raise dead chance
    float raiseRange = params.length > 4 ? params[4] : 10f;
    int corpseId = findNearbyCorpse(raiseRange);
    if (corpseId != Engine.INVALID_ENTITY && monster.monstats.Skill1 != null && !monster.monstats.Skill1.isEmpty()
        && params.length > 1 && MathUtils.randomBoolean(params[1] / 100f)) {
      // D2MOD: Use raise dead skill (nSkill[0])
      // TODO: Implement skill casting
      stateMachine.changeState(State.RAISE);
      lookAt(corpseId);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, corpseId, mPosition.get(corpseId).position);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Check shoot chance
    if (monster.monstats.Skill3 != null && !monster.monstats.Skill3.isEmpty()
        && params.length > 3 && MathUtils.randomBoolean(params[3] / 100f)) {
      // D2MOD: Use shoot skill (nSkill[2])
      // TODO: Implement skill casting
      stateMachine.changeState(State.CAST);
      lookAt(targetId);
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, targetPos);
      time = MathUtils.random(1f, 2);
      return;
    }

    // D2MOD: Walk to target or circle
    // D2MOD: AITACTICS_SetVelocity(pUnit, 0, 50, 0)
    // D2MOD: AITACTICS_WalkToTargetUnitWithSteps(pGame, pUnit, pAiTickParam->pTarget, 3u)
    if (MathUtils.randomBoolean(0.5f)) {
      walkTo(targetPos, 50, targetId);
      stateMachine.changeState(State.APPROACH);
      time = MathUtils.random(1f, 2);
      return;
    } else {
      // D2MOD: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 3u, 0)
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
