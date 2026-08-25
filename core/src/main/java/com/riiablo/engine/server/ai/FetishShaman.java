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
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * FetishShaman AI implementation matching D2MOD's AITHINK_Fn065_FetishShaman logic.
 * 
 * D2MOD AI Parameters:
 * - params[0] = FETISHSHAMAN_AI_PARAM_HEAL_CHANCE_PCT (heal chance)
 * - params[1] = FETISHSHAMAN_AI_PARAM_HEAL_CAPABILITY (heal capability)
 * - params[2] = FETISHSHAMAN_AI_PARAM_HEAL_RANGE (heal range)
 * - params[3] = FETISHSHAMAN_AI_PARAM_CIRCLE_CHANCE_PCT (circle chance)
 * - params[4] = FETISHSHAMAN_AI_PARAM_HEAL_SEARCH_RANGE (heal search range)
 * 
 * Special: Can heal dead allies (raise dead) and has inferno skill.
 */
public class FetishShaman extends AI {
  private static final Logger log = LogManager.getLogger(FetishShaman.class);
  enum State implements com.badlogic.gdx.ai.fsm.State<Integer> {
    IDLE,
    WANDER,
    APPROACH,
    ATTACK,
    CAST,
    HEAL,
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
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<com.riiablo.engine.server.component.Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;

  private static EntitySubscription enemyEntities;
  private EntitySubscription corpseEntities;

  final Vector2 tmpVec2 = new Vector2();

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public FetishShaman(int entityId) {
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
    corpseEntities = Riiablo.engine.getAspectSubscriptionManager().get(Aspect
        .all(Monster.class, Corpse.class, Position.class, AttributesWrapper.class));
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

  /** Native FetishShaman corpse eligibility used by the raise-dead branch. */
  static boolean isResurrectableAlly(
      Monster source, Monster candidate, Corpse corpse, float hitpoints) {
    if (source == null || source.monstats == null || candidate == null
        || candidate.monstats == null || candidate.monstats2 == null
        || corpse == null || !corpse.usable || corpse.fading || hitpoints > 0f) {
      return false;
    }
    if (!candidate.monstats2.revive || candidate.monstats.boss
        || candidate.monstats.SetBoss || candidate.monstats.primeevil) {
      return false;
    }
    // D2's raise-dead helper only accepts an allied alignment.  Zero is used
    // by synthetic/headless fixtures, so treat equal zero as allied too.
    return source.monstats.Align == candidate.monstats.Align;
  }

  private int findNearbyDeadAlly(float maxRange) {
    if (corpseEntities == null || maxRange <= 0f || !mPosition.has(entityId)) {
      return Engine.INVALID_ENTITY;
    }
    Vector2 source = mPosition.get(entityId).position;
    float bestDistance2 = maxRange * maxRange;
    int best = Engine.INVALID_ENTITY;
    IntBag entities = corpseEntities.getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int candidateId = entities.get(i);
      if (candidateId == entityId || !mPosition.has(candidateId)
          || !mMonster.has(candidateId) || !mCorpse.has(candidateId)
          || !mAttributesWrapper.has(candidateId)) continue;
      AttributesWrapper wrapper = mAttributesWrapper.get(candidateId);
      com.riiablo.attributes.StatRef hp = wrapper != null && wrapper.attrs != null
          ? wrapper.attrs.get(com.riiablo.attributes.Stat.hitpoints,
              com.riiablo.attributes.StatRef.obtain()) : null;
      float hitpoints = hp == null ? Float.MAX_VALUE : hp.asFixed();
      if (!isResurrectableAlly(monster, mMonster.get(candidateId),
          mCorpse.get(candidateId), hitpoints)) continue;
      float distance2 = source.dst2(mPosition.get(candidateId).position);
      if (distance2 > bestDistance2) continue;
      bestDistance2 = distance2;
      best = candidateId;
    }
    return best;
  }

  /** Native AI compares target distance with the monster's Skill1 level. */
  private float infernoRange() {
    if (monster == null || monster.monstats == null
        || monster.monstats.Skill1 == null || monster.monstats.Skill1.isEmpty()) return 0f;
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(monster.monstats.Skill1);
    return hasProjectileMissile(skill) ? Math.max(1, monster.monstats.Sk1lvl) : 0f;
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

    // Keep the cast/animation transaction intact until Actioneer publishes
    // its finished event; otherwise the next AI tick can replace it before
    // the inferno keyframe creates the authoritative missiles.
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;

    time = SLEEP;

    // Find target
    int targetId = Engine.INVALID_ENTITY;
    float targetDistance = Float.MAX_VALUE;
    Vector2 entityPos = mPosition.get(entityId).position;

    // Native Resurrect2 is a real server skill. Select an eligible allied
    // corpse before attacking so Actioneer can execute srvDoFunc=97 at the
    // configured animation keyframe instead of only changing to a fake attack
    // animation (the previous port's behavior).
    float raiseSearchRange = params.length > 4 ? params[4] : 15f;
    int deadAllyId = findNearbyDeadAlly(raiseSearchRange);
    if (deadAllyId != Engine.INVALID_ENTITY && monster.monstats.Skill3 != null
        && !monster.monstats.Skill3.isEmpty() && params.length > 0
        && MathUtils.randomBoolean(params[0] / 100f)) {
      Vector2 deadPos = mPosition.get(deadAllyId).position;
      float deadDistance = entityPos.dst(deadPos);
      float healRange = params.length > 2 ? params[2] : 5f;
      if (deadDistance <= healRange) {
        lookAt(deadAllyId);
        stateMachine.changeState(State.HEAL);
        if (useMonsterSkill(2, deadAllyId, deadPos)) {
          time = MathUtils.random(1f, 2f);
          log.info("[MONSTER_RAISE] phase=cast source={} target={} monster={} skill={}",
              entityId, deadAllyId, monster.monstats.Id, monster.monstats.Skill3);
          return;
        }
        stateMachine.changeState(State.IDLE);
      } else {
        pathfinder.findPath(entityId, deadPos, false, deadAllyId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2f);
        return;
      }
    }
    
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

    // FetishInferno is a real server missile skill (not a weapon throw).  The
    // previous port compared distance with a hard-coded level of one and then
    // scheduled SkillCodes.attack, so the animation never emitted inferno
    // missiles.  Resolve the MonStats Skill1 row and let the normal
    // Actioneer -> SkillDoEvent -> ServerSkillSystem path execute it.
    float infernoRange = infernoRange();
    if (targetId != Engine.INVALID_ENTITY && infernoRange > 0f
        && targetDistance <= infernoRange) {
      Vector2 targetPos = mPosition.get(targetId).position;
      lookAt(targetId);
      stateMachine.changeState(State.CAST);
      if (useMonsterSkill(0, targetId, targetPos)) {
        time = MathUtils.random(1f, 2f);
        return;
      }
      stateMachine.changeState(State.IDLE);
    }

    // D2MOD: Circle or idle
    if (params.length > 3 && !MathUtils.randomBoolean(params[3] / 100f)) {
      stateMachine.changeState(State.IDLE);
      time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;
      return;
    } else {
      // D2MOD: sub_6FCD0E80(pGame, pUnit, pAiTickParam->pTarget, 4u, 0)
      if (targetId != Engine.INVALID_ENTITY) {
        Vector2 targetPos = mPosition.get(targetId).position;
        pathfinder.findPath(entityId, targetPos, false, targetId);
        stateMachine.changeState(State.APPROACH);
        time = MathUtils.random(1f, 2);
        return;
      }
    }

    stateMachine.changeState(State.IDLE);
    time = 10f * com.riiablo.codec.Animation.FRAME_DURATION;
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
