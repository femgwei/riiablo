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
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.codec.excel.Skills;

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

  /** All frames of native MonSeq {@code seq_shamanresurrect} use A2. */
  static final byte SHAMAN_SEQUENCE_MODE = Engine.Monster.MODE_A2;

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

  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  private EntitySubscription corpseEntities;

  final StateMachine<Integer, State> stateMachine;
  float nextAction;
  float time;

  public FallenShaman(int entityId) {
    super(entityId);
    stateMachine = new DefaultStateMachine<>(entityId, State.IDLE);
  }

  @Override
  public void initialize() {
    super.initialize();
    corpseEntities = Riiablo.engine.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, Corpse.class, Position.class, AttributesWrapper.class));
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

  /** Native AITHINK_TargetCallback_FallenShaman corpse search. */
  private int findNearbyCorpse(float maxRange) {
    if (corpseEntities == null || maxRange <= 0f) {
      log.debug("[MONSTER_RAISE] phase=search source={} range={} candidates=0 reason=subscription_unavailable",
          entityId, maxRange);
      return Engine.INVALID_ENTITY;
    }
    Vector2 source = mPosition.get(entityId).position;
    float bestDistance2 = maxRange * maxRange;
    int best = Engine.INVALID_ENTITY;
    int eligible = 0;
    IntBag entities = corpseEntities.getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int candidateId = entities.get(i);
      if (candidateId == entityId) continue;

      Attributes attrs = mAttributesWrapper.get(candidateId).attrs;
      StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
      float hpValue = hp != null ? hp.asFixed() : Float.MAX_VALUE;
      Monster candidate = mMonster.get(candidateId);
      Corpse corpse = mCorpse.get(candidateId);
      float distance2 = source.dst2(mPosition.get(candidateId).position);
      boolean resurrectable = isResurrectableFallen(candidate, corpse, hpValue);
      if (!resurrectable) {
        log.debug("[MONSTER_RAISE] phase=candidate_rejected source={} candidate={} monster={} "
                + "hp={} usable={} fading={} distance={} align={} revive={}",
            entityId, candidateId,
            candidate != null && candidate.monstats != null ? candidate.monstats.Id : "unknown",
            hpValue, corpse != null && corpse.usable, corpse != null && corpse.fading,
            (float) Math.sqrt(distance2),
            candidate != null && candidate.monstats != null ? candidate.monstats.Align : -1,
            candidate != null && candidate.monstats2 != null && candidate.monstats2.revive);
        continue;
      }
      if (distance2 > bestDistance2) continue;
      eligible++;
      bestDistance2 = distance2;
      best = candidateId;
    }
    log.debug("[MONSTER_RAISE] phase=search source={} range={} subscribed={} eligible={} selected={}",
        entityId, maxRange, entities.size(), eligible, best);
    return best;
  }

  /** Finds the native resurrection skill instead of assuming it is Skill1. */
  private int findResurrectionSkillIndex() {
    if (monster == null || monster.monstats == null || Riiablo.files == null
        || Riiablo.files.skills == null) return -1;
    String[] names = {
        monster.monstats.Skill1, monster.monstats.Skill2, monster.monstats.Skill3,
        monster.monstats.Skill4, monster.monstats.Skill5, monster.monstats.Skill6,
        monster.monstats.Skill7, monster.monstats.Skill8 };
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      if (name == null || name.isEmpty()) continue;
      Skills.Entry skill = Riiablo.files.skills.get(name);
      if (skill != null && skill.srvdofunc == 97) return i;
      if (name.toLowerCase().contains("resurrect") || name.toLowerCase().contains("raise")) return i;
    }
    return -1;
  }

  public static boolean isResurrectableFallen(Monster monster, Corpse corpse, float hitpoints) {
    if (monster == null || monster.monstats == null || monster.monstats2 == null
        || corpse == null || !corpse.usable || corpse.fading || hitpoints > 0f) {
      return false;
    }
    // D2MOO's AITHINK_TargetCallback_FallenShaman does not consult the
    // MonStats2.revive column.  Fallen resurrection is determined by the
    // base id/alignment/dead-mode/targetability checks; requiring revive here
    // incorrectly filters normal Fallen rows in several data sets.
    if (monster.monstats.Align != 1
        || monster.monstats.boss || monster.monstats.SetBoss || monster.monstats.primeevil) {
      return false;
    }

    String baseId = monster.monstats.BaseId;
    if (baseId == null || baseId.isEmpty()) baseId = monster.monstats.Id;
    return "fallen1".equalsIgnoreCase(baseId)
        || "fallenshaman1".equalsIgnoreCase(baseId);
  }

  static boolean withinShootDistance(float distance, int shootDistance) {
    return shootDistance > 0 && distance < shootDistance;
  }

  private boolean castFireball(int targetId, Vector2 targetPos) {
    stateMachine.changeState(State.CAST);
    if (useMonsterSkill(1, targetId, targetPos, SHAMAN_SEQUENCE_MODE)) {
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
    int resurrectSkill = findResurrectionSkillIndex();
    boolean resurrectionRoll = corpseId != Engine.INVALID_ENTITY
        && MathUtils.randomBoolean(params[PARAM_RESURRECT_AND_COMMAND_CHANCE] / 100f);
    if (corpseId != Engine.INVALID_ENTITY && resurrectSkill < 0) {
      log.warn("[MONSTER_RAISE] phase=skill_missing source={} target={} monster={} "
              + "skills=[{}, {}, {}, {}, {}, {}, {}, {}]",
          entityId, corpseId, monster.monstats.Id,
          monster.monstats.Skill1, monster.monstats.Skill2, monster.monstats.Skill3,
          monster.monstats.Skill4, monster.monstats.Skill5, monster.monstats.Skill6,
          monster.monstats.Skill7, monster.monstats.Skill8);
    }
    log.debug("[MONSTER_RAISE] phase=decision source={} target={} skillSlot={} roll={}",
        entityId, corpseId, resurrectSkill + 1, resurrectionRoll);
    if (corpseId != Engine.INVALID_ENTITY && resurrectSkill >= 0 && resurrectionRoll) {
      if (useMonsterSkill(
          resurrectSkill, corpseId, mPosition.get(corpseId).position, SHAMAN_SEQUENCE_MODE)) {
        stateMachine.changeState(State.CAST);
        time = MathUtils.random(1f, 2f);
        // Some headless/server animation paths do not emit the native
        // AnimDataFinished keyframe for seq_shamanresurrect.  Apply the
        // authoritative state transition immediately as a safe fallback;
        // Actioneer's srvdofunc=97 remains idempotent and will simply reject
        // a second restore after the corpse has been removed.
        boolean restored = factory != null && factory.resurrectMonster(corpseId, entityId);
        log.info("[MONSTER_RAISE] phase=immediate_fallback source={} target={} restored={}",
            entityId, corpseId, restored);
        log.info("[MONSTER_RAISE] phase=cast source={} target={} monster={} distance={} skill={}",
            entityId, corpseId, mMonster.get(corpseId).monstats.Id,
            mPosition.get(entityId).position.dst(mPosition.get(corpseId).position),
            resurrectionSkillName(resurrectSkill));
        return;
      }
      log.warn("[MONSTER_RAISE] phase=skill_rejected source={} target={} skillSlot={} skill={}",
          entityId, corpseId, resurrectSkill + 1,
          resurrectionSkillName(resurrectSkill));
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

  private String resurrectionSkillName(int index) {
    if (monster == null || monster.monstats == null) return "unknown";
    switch (index) {
      case 0: return monster.monstats.Skill1;
      case 1: return monster.monstats.Skill2;
      case 2: return monster.monstats.Skill3;
      case 3: return monster.monstats.Skill4;
      case 4: return monster.monstats.Skill5;
      case 5: return monster.monstats.Skill6;
      case 6: return monster.monstats.Skill7;
      case 7: return monster.monstats.Skill8;
      default: return "unknown";
    }
  }

  @Override
  public String getState() {
    return stateMachine.getCurrentState().name();
  }
}
