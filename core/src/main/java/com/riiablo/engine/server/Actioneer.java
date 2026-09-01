package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntSet;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.MonsterModeDamageResolver;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.Engine;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Type;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Leap;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;
import com.riiablo.map.Map;
import com.riiablo.map.DT1;

public class Actioneer extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(Actioneer.class);

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<com.riiablo.engine.server.component.Missile> mMissile;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Leap> mLeap;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Pathfind> mPathfind;

  @com.artemis.annotations.Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  @com.artemis.annotations.Wire(name = "factory")
  protected EntityFactory factory;

  // teleport-specific components
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Box2DBody> mBox2DBody;

  protected EventSystem events;
  protected Pathfinder pathfinder;
  @com.artemis.annotations.Wire(name = "map")
  protected Map map;

  /** Entity IDs for whom the last attack target died; must release before next attack. */
  private final IntSet lastAttackTargetDied = new IntSet();

  public boolean didLastAttackTargetDie(int entityId) {
    return lastAttackTargetDied.contains(entityId);
  }

  public void clearLastAttackTargetDied(int entityId) {
    lastAttackTargetDied.remove(entityId);
  }

  public void moveTo(int entityId, Vector2 targetVec) {
    // Don't allow movement if entity doesn't have Velocity component (e.g., dead player)
    if (!mVelocity.has(entityId)) {
      return;
    }
    pathfinder.findPath(entityId, targetVec, true);
  }

  public void moveTo(int entityId, int targetId) {
    // Don't allow movement if entity doesn't have Velocity component (e.g., dead player)
    if (!mVelocity.has(entityId)) {
      return;
    }
    if (targetId == Engine.INVALID_ENTITY) {
      mTarget.remove(entityId);
      moveTo(entityId, null);
    } else {
      mTarget.create(entityId).target = targetId;
      moveTo(entityId, mPosition.get(targetId).position);
    }
  }

  private boolean canCast(int entityId) {
    // A movement sequence is interruptible by a new player action. An active
    // cast/attack sequence is not, because its keyframe events must finish.
    return !mCasting.has(entityId);
  }

  public boolean canInterrupt(int entityId) {
    return !mCasting.has(entityId);
  }
  
  public boolean hasCasting(int entityId) {
    return mCasting.has(entityId);
  }
  
  public boolean hasSequence(int entityId) {
    return mSequence.has(entityId);
  }

  public void cast(int entityId, int skillId, int targetId, Vector2 targetVec) {
    if (!canCast(entityId)) {
      log.info("[SKILL_CAST] rejected_busy entity={} skill={} casting={} sequence={}",
          entityId, skillId, mCasting.has(entityId), mSequence.has(entityId));
      return;
    }
    if (mAttributesWrapper.has(entityId)) {
      StatRef hp = mAttributesWrapper.get(entityId).attrs.get(Stat.hitpoints, StatRef.obtain());
      if (hp != null && hp.asFixed() <= 0f) {
        log.info("[ATTACK_ANIM] rejected_dead entity={} skill={} target={}", entityId, skillId, targetId);
        return;
      }
    }
    moveTo(entityId, Engine.INVALID_ENTITY);
    final Skills.Entry skill = Riiablo.files.skills.get(skillId);
    log.traceEntry("cast(entityId: {}, skillId: {} ({}), targetId: {}, targetVec: {})",
        entityId, skillId, skill, targetId, targetVec);

    // Check if this is a throwing attack:
    // 1. Skill ID is throw_ (2) or left_hand_throw (4)
    // 2. OR cltdofunc is 3 or 5 (throw missile functions)
    // 3. OR equipped weapon is throwable (javelin, throwing knife, throwing axe)
    boolean isThrowSkill = (skillId == SkillCodes.throw_ || skillId == SkillCodes.left_hand_throw);
    boolean isThrowFunc = (skill != null && (skill.cltdofunc == 3 || skill.cltdofunc == 5));
    
    // A throwable weapon does not make the normal Attack skill a throw. In
    // Diablo II, a javelin/throwing knife/throwing axe can still be used for a
    // point-blank melee Attack; only explicit Throw skills (or throw functions
    // in Skills.txt) create a missile and consume quantity here.
    boolean isThrowAttack = isThrowSkill || isThrowFunc;

    // Keep one authoritative snapshot in the log before the animation starts.
    // This lets us distinguish an equipment/stat aggregation problem from a
    // missile collision or damage-calculation problem without changing combat
    // behaviour.
    if (isThrowAttack && mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR) {
      Item weapon = getThrowableWeapon(entityId);
      int quantity = -1;
      int itemThrowMin = 0;
      int itemThrowMax = 0;
      if (weapon != null && weapon.attrs != null) {
        StatRef qty = weapon.attrs.base().get(Stat.quantity, StatRef.obtain());
        StatRef min = weapon.attrs.base().get(Stat.item_throw_mindamage, StatRef.obtain());
        StatRef max = weapon.attrs.base().get(Stat.item_throw_maxdamage, StatRef.obtain());
        quantity = qty != null ? qty.asInt() : -1;
        itemThrowMin = min != null ? min.asInt() : 0;
        itemThrowMax = max != null ? max.asInt() : 0;
      }
      log.info("[THROW_ATTACK] phase=cast entity={} skill={} target={} skillThrow={} funcThrow={} "
              + "weaponCode={} weaponType={} quantity={} itemThrowMin={} itemThrowMax={}",
          entityId, skillId, targetId, isThrowSkill, isThrowFunc,
          weapon != null ? weapon.code : "none",
          weapon != null && weapon.type != null ? weapon.type : "none",
          quantity, itemThrowMin, itemThrowMax);
    }
    
    // For players, check if equipped weapon is throwable and has quantity
    if (isThrowAttack && mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR) {
      Item weapon = getThrowableWeapon(entityId);
      
      if (weapon != null && weapon.base != null) {
        // Check quantity. The helper already selected a throwable weapon from
        // either active hand, so an unrelated melee RARM item cannot block a
        // valid throwable weapon in LARM.
        StatRef quantity = weapon.attrs.base().get(Stat.quantity);
        if (quantity == null || quantity.asInt() <= 0) {
          log.info("[THROW_ATTACK] phase=reject entity={} skill={} reason=empty_quantity weaponCode={}",
              entityId, skillId, weapon.code);
          return; // Cannot throw, no quantity
        }
      } else {
        log.info("[THROW_ATTACK] phase=reject entity={} skill={} reason=no_throwable_weapon",
            entityId, skillId);
        return; // No weapon equipped
      }
    }

    targetVec = targetVec != null ? targetVec.cpy() : Vector2.Zero;
    final Class.Type type = mClass.get(entityId).type;
    byte mode = (byte) getMode(skill, type);
    log.trace("mode: {}", mode);
    if (mode == Engine.INVALID_MODE) {
      mode = (byte) type.getMode("SC");
      log.trace("mode changed to {} because it was invalid", mode);
    }

    Vector2 entityPos = mPosition.get(entityId).position;
    mAngle.get(entityId).target.set(targetVec).sub(entityPos).nor();
    mSequence.create(entityId).sequence(mode, mMovementModes.get(entityId).NU);
    mCasting.create(entityId).set(skillId, targetId, targetVec);
    SkillCastEvent castEvent = SkillCastEvent.obtain(entityId, skillId, targetId, targetVec);
    events.dispatch(castEvent);
    if (!castEvent.accepted) {
      log.debug("Skill cast rejected by server: entity={}, skill={}, resultCode={}, manaCost={}",
          entityId, skillId, castEvent.resultCode, castEvent.manaCost);
      mCasting.remove(entityId);
      mSequence.remove(entityId);
      return;
    }

    srvstfunc(entityId, skill.srvstfunc, targetId, targetVec);
    events.dispatch(SkillStartEvent.obtain(entityId, skillId, targetId, targetVec, skill.srvstfunc, skill.cltstfunc));
  }

  int getMode(Skills.Entry skill, Class.Type type) {
    switch (type) {
      case MON: return type.getMode(skill.monanim);
      case PLR: return type.getMode(skill.anim);
      default:
        log.error("Unsupported mode translation for class type: " + type);
        return type.getMode(skill.anim);
    }
  }

  /**
   * @param seq specific sequence or {@code -1} to use default sequence
   * @param mode specific mode or {@link Engine#INVALID_MODE} to use default mode
   */
  public void cast(int entityId, int skillId, byte seq, byte mode, int targetId, Vector2 targetVec) {
    cast(entityId, skillId, targetId, targetVec);
  }

  public void attack(int entityId, byte seq, byte mode, int targetId, Vector2 targetVec) {
    cast(entityId, SkillCodes.attack, seq, mode, targetId, targetVec);
  }

  /**
   * D2MOD: UNITS_GetMeleeRange(D2UnitStrc* pUnit)
   * Gets the melee range for an entity based on its type:
   * - Player: Returns RangeAdder from equipped weapon (0 if no weapon)
   * - Monster: Returns MeleeRng from MonStats2
   * - Default: Returns 0
   */
  public int getMeleeRange(int entityId) {
    if (!mClass.has(entityId)) return 0;
    
    Class.Type type = mClass.get(entityId).type;
    switch (type) {
      case PLR:
        // D2MOD: For players, get RangeAdder from equipped weapon
        // TODO: Implement weapon inventory system to get actual RangeAdder
        // For now, return 0 (no weapon equipped)
        return 0;
      case MON:
        // D2MOD: For monsters, get MeleeRng from MonStats2
        if (mMonster.has(entityId)) {
          Monster monster = mMonster.get(entityId);
          if (monster.monstats2 != null) {
            // D2MOD: If MeleeRng == 255, check weapon class (2HT = 2, else 0)
            // For simplicity, we'll just return MeleeRng (assuming it's not 255)
            return monster.monstats2.MeleeRng;
          }
        }
        return 0;
      default:
        return 0;
    }
  }

  /**
   * D2MOD: UNITS_IsInMeleeRange(D2UnitStrc* pUnit1, D2UnitStrc* pUnit2, int nRangeBonus)
   * Checks if attacker is within melee range of target.
   * Formula: UNITS_GetMeleeRange(pUnit1) + nRangeBonus + 1 >= distance
   * 
   * @param attackerId The attacking entity
   * @param targetId The target entity
   * @param rangeBonus Additional range bonus (for players: 3, for monsters: 0)
   * @return true if attacker is within melee range of target
   */
  public boolean isInMeleeRange(int attackerId, int targetId, int rangeBonus) {
    if (attackerId == Engine.INVALID_ENTITY || targetId == Engine.INVALID_ENTITY) {
      return false;
    }
    if (!mPosition.has(attackerId) || !mPosition.has(targetId)) {
      return false;
    }
    
    Vector2 attackerPos = mPosition.get(attackerId).position;
    Vector2 targetPos = mPosition.get(targetId).position;
    float distance = attackerPos.dst(targetPos);
    
    // D2MOD: UNITS_GetMeleeRange(pUnit1) + nRangeBonus + 1 >= nDistance
    int meleeRange = getMeleeRange(attackerId);
    return meleeRange + rangeBonus + 1 >= distance;
  }

  @Subscribe
  public void onAnimDataKeyframe(AnimDataKeyframeEvent event) {
    if (!mCasting.has(event.entityId)) return;
    log.traceEntry("onAnimDataKeyframe(entityId: {}, keyframe: {} ({}))",
        event.entityId, event.keyframe, Engine.getKeyframe(event.keyframe));
    final Casting casting = mCasting.get(event.entityId);
    byte mode = mCofReference.has(event.entityId) ? mCofReference.get(event.entityId).mode : -1;
    int frame = mAnimData.has(event.entityId) ? mAnimData.get(event.entityId).frame : -1;
    log.info("[ATTACK_ANIM] keyframe entity={} skill={} target={} keyframe={} mode={} frame={}",
        event.entityId, casting.skillId, casting.targetId,
        Engine.getKeyframe(event.keyframe), (int) mode, frame);
    
    // D2MOD: Check if target is dead before processing attack keyframe
    // If target is dead, skip damage/events but allow animation to complete
    // This ensures attack animation is shown at least once even if target dies immediately
    boolean targetDead = false;
    if (casting.targetId != Engine.INVALID_ENTITY) {
      if (!mAttributesWrapper.has(casting.targetId)) {
        // Target entity no longer exists (dead), skip damage but continue animation
        targetDead = true;
        } else {
        Attributes targetAttrs = mAttributesWrapper.get(casting.targetId).attrs;
        // 使用 get(stat, dst) 接口避免重用问题
        StatRef targetHp = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
        if (targetHp != null && targetHp.asFixed() <= 0f) {
          // Target is already dead, skip damage but continue animation
          targetDead = true;
        }
      }
    }
    
    // Get skill entry and validate it exists before processing
    final Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
    if (skill == null) {
      log.warn("Skill {} not found for entity {}, cancelling casting", casting.skillId, event.entityId);
      mCasting.remove(event.entityId);
      mSequence.remove(event.entityId);
      return;
    }
    
    // Most skills skip dead targets. Native SrvDo097 Resurrect explicitly
    // requires one, so it must still execute on the animation keyframe.
    if (!targetDead || allowsDeadTarget(skill)) {
      srvdofunc(event.entityId, skill.srvdofunc, casting.targetId, casting.targetVec);
      if (mPlayer.has(event.entityId)) {
        com.badlogic.gdx.Gdx.app.log("Actioneer", String.format(
            "[SKILL_DO] phase=dispatch entity=%d skill=%d target=%d keyframe=%s srvDoFunc=%d cltDoFunc=%d",
            event.entityId, casting.skillId, casting.targetId,
            Engine.getKeyframe(event.keyframe), skill.srvdofunc, skill.cltdofunc));
      }
      events.dispatch(SkillDoEvent.obtain(
          event.entityId, casting.skillId,
          casting.targetId, casting.targetVec,
          skill.srvdofunc, skill.cltdofunc));
    } else {
      log.trace("Target {} is dead, skipping damage but continuing attack animation for {}", casting.targetId, event.entityId);
    }
  }

  @Subscribe
  public void onAnimDataFinished(AnimDataFinishedEvent event) {
    if (!mCasting.has(event.entityId)) return;
    log.traceEntry("onAnimDataFinished(entityId: {})", event.entityId);
    final Casting casting = mCasting.get(event.entityId);
    
    // D2MOD: Check if target is dead after attack animation completes
    boolean targetDead = false;
    if (casting.targetId != Engine.INVALID_ENTITY) {
      if (!mAttributesWrapper.has(casting.targetId)) {
        targetDead = true;
      } else {
        Attributes targetAttrs = mAttributesWrapper.get(casting.targetId).attrs;
        StatRef targetHp = targetAttrs.get(Stat.hitpoints);
        if (targetHp != null && targetHp.asFixed() <= 0f) {
          targetDead = true;
        }
      }
    }
    
    mCasting.remove(event.entityId);
    
    if (targetDead && mSequence.has(event.entityId)) {
      log.trace("Target {} is dead, stopping attack sequence for {}", casting.targetId, event.entityId);
      mSequence.remove(event.entityId);
      if (mTarget.has(event.entityId)) {
        mTarget.remove(event.entityId);
      }
      // D2MOD: Require release before next attack; prevents repeated swinging after target death
      lastAttackTargetDied.add(event.entityId);
    }
  }

  @Subscribe
  public void onDeath(DeathEvent event) {
    // A death can happen during the attack keyframe.  Clear the attack state
    // before the death handler installs MODE_DT -> MODE_DD; otherwise the old
    // attack sequence receives the next AnimDataFinishedEvent and restores NU.
    if (event.victim != Engine.INVALID_ENTITY) {
      if (mCasting.has(event.victim)) mCasting.remove(event.victim);
      if (mSequence.has(event.victim)) mSequence.remove(event.victim);
      if (mTarget.has(event.victim)) mTarget.remove(event.victim);
      log.info("[PLAYER_DEATH] action state cleared entity={} killer={}", event.victim, event.killer);
    }
    if (mTarget.has(event.killer)) {
      Target target = mTarget.get(event.killer);
      if (target.target == event.victim) {
        mTarget.remove(event.killer);
      }
    }
  }

  private void srvstfunc(int entityId, int srvstfunc, int targetId, Vector2 targetVec) {
    log.traceEntry("srvstfunc(entityId: {}, srvstfunc: {}, targetId: {}, targetVec: {})",
        entityId, srvstfunc, targetId, targetVec);
    switch (srvstfunc) {
      case 0:
        break;
      case 1: // attack
        break;
      case 3: // throw
      case 5: // left hand throw
      case 65: // Throw skill (skillId=2)
        break;
      case 42: // native Fire Hit pre-hit setup; resolved authoritatively at the keyframe
        log.info("[MONSTER_SKILL] phase=fire_hit_start entity={} target={} mode=S1",
            entityId, targetId);
        break;
      case 44: // MaggotUp start: native code prepares the unburrow transition
        log.info("[MONSTER_MAGGOT] phase=up_start entity={} target={}", entityId, targetId);
        break;
      case 45: // MaggotDown start: native code enters the burrowed collision state
        log.info("[MONSTER_MAGGOT] phase=down_start entity={} target={}", entityId, targetId);
        break;
      case 48: // Swarm Move: prefer a short toward path, then fall back to A*
        prepareSwarmMove(entityId, targetId, targetVec);
        break;
      case 49: // Nest: native code reserves the spawn point and collision mask
        log.info("[MONSTER_NEST] phase=prepare entity={} target={}", entityId, targetId);
        break;
      case 31: // Charge: reserve the target path; damage is applied at keyframe
        if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
          pathfinder.findPath(entityId, mPosition.get(targetId).position, true, targetId);
        }
        log.info("[MONSTER_CHARGE] phase=start entity={} target={}", entityId, targetId);
        break;
      case 40: // native Leap validates and reserves its landing point on skill start
        log.info("[MONSTER_LEAP] phase=start_check entity={} target={} requested=({}, {})",
            entityId, targetId,
            targetVec != null ? targetVec.x : Float.NaN,
            targetVec != null ? targetVec.y : Float.NaN);
        break;
      default:
        log.warn("Unsupported srvstfunc({}) for {}", srvstfunc, entityId);
        // TODO: default case will log an error when all valid cases are enumerated
        // log.error("Invalid srvdofunc({}) for {}", srvstfunc, entityId);
    }
  }

  private void srvdofunc(int entityId, int srvdofunc, int targetId, Vector2 targetVec) {
    log.traceEntry("srvdofunc(entityId: {}, srvdofunc: {}, targetId: {}, targetVec: {})",
        entityId, srvdofunc, targetId, targetVec);
    switch (srvdofunc) {
      case 0:
        break;
      case 1: // attack
      case 7: // native Jab: same authoritative hit path, skill-specific animation
      case 9: // player Frenzy
      case 109: { // monster Frenzy / BloodLordFrenzy
        if (srvdofunc == 7) {
          log.info("[MONSTER_SKILL] phase=jab entity={} target={} using=melee_hit_pipeline",
              entityId, targetId);
        }
        if (targetId == Engine.INVALID_ENTITY) break;
        if (!mAttributesWrapper.has(targetId)) break;
        // Player components are authoritative for PvP identity.  Some native
        // monster tests intentionally omit the presentation Class component,
        // so using isPlayerEntity() here would misclassify a valid player
        // target and block an otherwise normal monster attack.
        boolean attackerPlayerUnit = mPlayer.has(entityId);
        boolean targetPlayerUnit = mPlayer.has(targetId);
        if ((attackerPlayerUnit && targetPlayerUnit)
            && !PvpCombatRules.canDamage(partyManager, entityId, targetId, true, true)) {
          if (attackerPlayerUnit && targetPlayerUnit) {
            log.info("[PVP] phase=reject source={} target={} reason=not_hostile", entityId, targetId);
          }
          break;
        }
        boolean attackerPlayer = isPlayerEntity(entityId);
        boolean targetPlayer = isPlayerEntity(targetId);
        log.debug("{} attack {}", entityId, targetId);

        if (mCasting.has(entityId)
            && ((srvdofunc == 1 && mCasting.get(entityId).skillId == SkillCodes.attack)
                || srvdofunc == 9 || srvdofunc == 109)) {
          if (isPlayerRangedNormalAttack(entityId)) {
            // ServerSkillSystem creates the Arrow/Bolt at this keyframe.
            break;
          }
          int bonus = isPlayerEntity(entityId) ? 3 : 0;
          // Native monster basic attacks backed by MonStats.MissA1/MissA2
          // are ranged even though they use the shared Attack skill.  The
          // melee-range rejection must not run before their projectile is
          // created at this keyframe.
          boolean rangedMonsterAttack = isMonsterProjectileSkill(entityId)
              || hasMonsterAttackMissile(entityId);
          if (!rangedMonsterAttack && !isInMeleeRange(entityId, targetId, bonus)) {
            log.info("[MELEE_RANGE] phase=reject source={} target={} distance={} range={}",
                entityId, targetId,
                mPosition.get(entityId).position.dst(mPosition.get(targetId).position),
                getMeleeRange(entityId) + bonus + 1);
            break;
          }
        }

        // Native monster attacks with MissA1/MissA2 are projectile attacks,
        // even though their AI enters the shared Attack skill (srvdofunc=1).
        // Resolve them at the same animation keyframe as melee damage so the
        // server remains authoritative for creation, collision and damage.
        if (isMonsterProjectileSkill(entityId)) {
          // ServerSkillSystem consumes the following SkillDoEvent and creates
          // the Skills.txt projectile. Applying melee damage here as well
          // would make one monster spell hit twice.
          break;
        }
        if (hasMonsterAttackMissile(entityId)) {
          if (spawnMonsterAttackMissile(entityId, targetId)) {
            break;
          }
          // A monster row with MissA1/MissA2 is a ranged native attack. Do
          // not fall through to melee damage when the projectile could not be
          // resolved; that would make a failed visual spawn deal an unrelated
          // hit at arbitrary distance.
          log.warn("[MONSTER_MISSILE] attack_blocked entity={} target={} reason=projectile_creation_failed",
              entityId, targetId);
          break;
        }

        // Check if this is a throwing attack and consume quantity
        if (mCasting.has(entityId)) {
          Casting casting = mCasting.get(entityId);
          Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
          boolean isThrowSkill = (casting.skillId == SkillCodes.throw_ || casting.skillId == SkillCodes.left_hand_throw);
          boolean isThrowFunc = (skill != null && (skill.cltdofunc == 3 || skill.cltdofunc == 5));
          
          boolean isThrowAttack = isThrowSkill || isThrowFunc;
          if (isThrowAttack && mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR) {
            Item weapon = getThrowableWeapon(entityId);
            
            if (weapon != null && weapon.base != null) {
              StatRef quantity = weapon.attrs.base().get(Stat.quantity);
              if (quantity != null && quantity.asInt() > 0) {
                // Decrease quantity by 1
                quantity.sub(1);
              }
            }
          }
        }

        Attributes attrs = mAttributesWrapper.get(targetId).attrs;
        // 使用 get(stat, dst) 接口避免重用问题
        StatRef hitpoints = attrs.get(Stat.hitpoints, StatRef.obtain());
        if (hitpoints == null) {
          log.warn("{} has no hitpoints stat", targetId);
          break;
        }
        log.debug("{} {}", targetId, hitpoints.asFixed());

        if (!mAttributesWrapper.has(entityId)) {
          log.debug("{} has no attributes, cannot attack", entityId);
          break;
        }
        Attributes attackerAttrs = mAttributesWrapper.get(entityId).attrs;
        CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateAttack(
            attackerAttrs,
            attrs,
            attackerPlayer,
            targetPlayer,
            false,
            monsterAttackMinDamage(entityId),
            monsterAttackMaxDamage(entityId),
            monsterAttackRating(entityId),
            stateList(entityId), stateList(targetId));
        if (!combat.hit) {
          log.info("[COMBAT_HIT] entity={} target={} result=miss chance={}% attackerLevel={} targetLevel={} ar={} defense={}",
              entityId, targetId, combat.hitChance,
              statInt(attackerAttrs, Stat.level), statInt(attrs, Stat.level),
              statInt(attackerAttrs, Stat.tohit), statInt(attrs, Stat.armorclass));
          break;
        }
        if (combat.blocked) {
          log.debug("{} melee attack blocked by {}", entityId, targetId);
          break;
        }
        log.info("[COMBAT_HIT] entity={} target={} result=hit damage={} chance={}% critical={} deadly={} crushing={}",
            entityId, targetId, combat.totalDamage, combat.hitChance,
            combat.critical, combat.deadlyStrike, combat.crushingBlow);

        float damage = combat.totalDamage;
        if (log.debugEnabled()) {
          log.debug("{} calculated damage: {} on {}", entityId, damage, targetId);
        }
        if (damage <= 0) {
          log.debug("{} melee hit on {} caused no damage", entityId, targetId);
          break;
        }
        float hpBefore = hitpoints.asFixed();
        DamageEvent event = DamageEvent.obtain(entityId, targetId, damage);
        events.dispatch(event);
        float appliedDamage = Math.max(0f, event.damage);
        hitpoints.sub(appliedDamage);
        float hpAfter = hitpoints.asFixed();
        if (hpAfter < 0f) {
          hitpoints.set(0f);
          hpAfter = 0f;
        }
        log.debug("{} hp after {} attack: damage={}, hp: {} -> {}", targetId,
            entityId, appliedDamage, hpBefore, hpAfter);

        applyCombatStates(entityId, targetId, combat);

        // Native Frenzy applies a short-lived stacking state to the attacker
        // at the successful hit frame.  Keep this server authoritative so the
        // speed bonus cannot be faked by a client animation.
        if (mCasting.has(entityId)) {
          Casting casting = mCasting.get(entityId);
          Skills.Entry attackSkill = Riiablo.files.skills.get(casting.skillId);
          if (attackSkill != null && isFrenzySkill(attackSkill)
              && mUnitStates.has(entityId)) {
            UnitStates attackerStates = mUnitStates.get(entityId);
            if (attackerStates.stateList == null) attackerStates.init(entityId);
            int level = Math.max(1, skillLevel(entityId, casting.skillId));
            int stateId = mMonster.has(entityId) ? StateId.MONFRENZY : StateId.FRENZY;
            int duration = SkillFormula.evaluate(attackSkill.auralencalc,
                attackSkill, level);
            if (duration <= 0) duration = 100;
            UnitState existingFrenzy = attackerStates.stateList.getState(stateId);
            UnitState frenzy = attackerStates.stateList.addState(
                stateId, duration, level, entityId);
            if (frenzy != null) {
              frenzy.skillId = casting.skillId;
              frenzy.velocityModifier = Math.min(8,
                  existingFrenzy != null ? existingFrenzy.velocityModifier + 1 : 1);
              frenzy.needsSync = true;
            }
            log.info("[FRENZY] phase=apply source={} target={} state={} level={} stacks={} duration={}",
                entityId, targetId, StateId.getName(stateId), level,
                frenzy != null ? frenzy.velocityModifier : 0, duration);
          }
        }

        if (hitpoints.asFixed() <= 0f) {
          log.debug("{} is dead!", targetId);
          events.dispatch(DeathEvent.obtain(entityId, targetId));
        }
                  break;
      }
      case 83: { // native Fire Hit: S1 physical + mode-matched MonStats elemental profile
        resolveFireHit(entityId, targetId);
        break;
      }
      case 77: { // native Leap: mirrored landing point and collision-free airborne travel
        startLeap(entityId, targetId, targetVec);
        break;
      }
      case 86: { // native MaggotDown: heal current life by calc1 percent
        resolveMaggotDown(entityId);
        break;
      }
      case 87: { // native MaggotLay: spawn the configured egg at a directional offset
        resolveMaggotLay(entityId, targetId);
        break;
      }
      case 90: { // native SwarmMove: advance animation to the configured frame
        resolveSwarmMove(entityId);
        break;
      }
      case 91: { // native Nest: spawn the configured monster at the reserved point
        resolveNest(entityId);
        break;
      }
      case 67: { // native Charge: authoritative melee hit with skill damage bonus
        resolveCharge(entityId, targetId);
        break;
      }
      case 24: // Vampire Firewall projectile; emitted through SkillDoEvent
      case 28: // Vampire Meteor projectile; emitted through SkillDoEvent
        log.info("[MONSTER_VAMPIRE] phase=keyframe entity={} target={} srvdofunc={} delegated=ServerSkillSystem",
            entityId, targetId, srvdofunc);
        break;
      case 96: { // native ZakarumHeal/Bestow: percentage heal on an allied target
        resolveBestow(entityId, targetId);
        break;
      }
      case 30: { // native Necromancer curse dispatcher
        resolveMonsterCurse(entityId, targetId);
        break;
      }
      case 150: { // native monster Smite: A2 physical hit with stun
        resolveSmite(entityId, targetId);
        break;
      }
      case 3: { // throw (srvdofunc for throw attacks)
        // Consume quantity FIRST - this should happen regardless of whether there's a target
        // Check if this is a throwing attack and consume quantity
        if (mCasting.has(entityId)) {
          Casting casting = mCasting.get(entityId);
          boolean isThrowSkill = (casting.skillId == SkillCodes.throw_ || casting.skillId == SkillCodes.left_hand_throw);
          
          // Check weapon type even if not a throw skill (might be equipped throwable weapon)
          if (mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR) {
            Item weapon = getThrowableWeapon(entityId);
            
            if (weapon != null && weapon.base != null) {
              // Consume quantity if a throwable weapon is active
              StatRef quantity = weapon.attrs.base().get(Stat.quantity);
              if (quantity != null && quantity.asInt() > 0) {
                // Decrease quantity by 1
                quantity.sub(1);
              }
            }
          }
        }
        
        // For throw attacks, damage is applied when the missile hits the target
        // (handled by MissileCollisionSystem), not immediately here.
        // We only consume quantity here, and let the missile handle damage on collision.
        break;
      }
      case 27:  // player Teleport
      case 98:  // monster Teleport
      case 129: // imp Teleport
        resolveTeleport(entityId, targetVec);
        break;
      // These server functions are resolved by ServerSkillSystem from the
      // SkillDoEvent emitted immediately below.  Keep the Actioneer stage
      // explicitly no-op so native projectile skills are not reported as
      // unsupported before their authoritative missile is created.
      case 8:  // MultipleShot/Teeth shock wave
      case 22: // Nova/radial missile skill
      case 85: // Fallen Shaman chain missile
      case 95: // Fetish Shaman inferno missile
        break;
      case 97: { // native monster Resurrect
        boolean restored = targetId != Engine.INVALID_ENTITY
            && factory != null
            && factory.resurrectMonster(targetId, entityId);
        log.info("[MONSTER_RAISE] phase=keyframe source={} target={} restored={}",
            entityId, targetId, restored);
        break;
      }
      default:
        log.warn("Unsupported srvdofunc({}) for {}", srvdofunc, entityId);
        // TODO: default case will log an error when all valid cases are enumerated
        //log.error("Invalid srvdofunc({}) for {}", srvdofunc, entityId);
    }
  }

  static boolean allowsDeadTarget(Skills.Entry skill) {
    return skill != null && skill.srvdofunc == 97;
  }

  /** D2MOO SrvDo027: validate the landing subtile, warp, and clear stale movement intent. */
  boolean resolveTeleport(int entityId, Vector2 targetVec) {
    if (!mPosition.has(entityId) || targetVec == null
        || !Float.isFinite(targetVec.x) || !Float.isFinite(targetVec.y)) {
      log.warn("[TELEPORT] phase=reject entity={} target={} reason=invalid_target",
          entityId, targetVec);
      return false;
    }
    int flags = map != null ? map.flags(targetVec) : 0xFF;
    if ((flags & DT1.Tile.FLAG_BLOCK_WALK) != 0) {
      log.info("[TELEPORT] phase=reject entity={} target=({}, {}) flags={} reason=blocked",
          entityId, targetVec.x, targetVec.y, flags);
      return false;
    }
    Vector2 from = new Vector2(mPosition.get(entityId).position);
    mPosition.get(entityId).position.set(targetVec);
    if (mPathfind.has(entityId)) mPathfind.remove(entityId);
    if (mTarget.has(entityId)) mTarget.remove(entityId);
    if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
    Box2DBody box2dWrapper = mBox2DBody.get(entityId);
    if (box2dWrapper != null && box2dWrapper.body != null) {
      box2dWrapper.body.setTransform(targetVec, 0);
    }
    if (mUnitStates.has(entityId)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states.stateList == null) states.init(entityId);
      states.stateList.addState(StateId.SYNC_WARPED, 2, 1, entityId);
    }
    log.info("[TELEPORT] phase=warp entity={} from=({}, {}) to=({}, {}) flags={} status=PASS",
        entityId, from.x, from.y, targetVec.x, targetVec.y, flags);
    return true;
  }

  /** Applies the target state selected by the Skills.txt curse row. */
  private void resolveMonsterCurse(int entityId, int targetId) {
    if (targetId == Engine.INVALID_ENTITY || !mUnitStates.has(targetId)
        || !mCasting.has(entityId)) return;
    Casting casting = mCasting.get(entityId);
    Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
    int stateId = curseStateId(skill != null ? skill.skill : null);
    if (stateId == StateId.NONE) {
      log.warn("[MONSTER_CURSE] phase=reject source={} target={} skill={} reason=unknown_state",
          entityId, targetId, casting.skillId);
      return;
    }
    int level = Math.max(1, skillLevel(entityId, casting.skillId));
    int duration = SkillFormula.evaluate(skill != null ? skill.auralencalc : null, skill, level);
    if (duration <= 0) duration = 150;
    UnitStates states = mUnitStates.get(targetId);
    if (states.stateList == null) states.init(targetId);
    UnitState state = states.stateList.addState(stateId, duration, level, entityId);
    if (state != null) {
      state.skillId = casting.skillId;
      state.needsSync = true;
    }
    log.info("[MONSTER_CURSE] phase=apply source={} target={} skill={} state={} level={} duration={}",
        entityId, targetId, casting.skillId, StateId.getName(stateId), level, duration);
  }

  private int skillLevel(int entityId, int skillId) {
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      return Math.max(1, mPlayer.get(entityId).data.getSkill(skillId));
    }
    if (mMonster.has(entityId) && mMonster.get(entityId).monstats != null) {
      MonStats.Entry row = mMonster.get(entityId).monstats;
      String name = Riiablo.files.skills.get(skillId) != null
          ? Riiablo.files.skills.get(skillId).skill : null;
      String[] names = {row.Skill1, row.Skill2, row.Skill3, row.Skill4,
          row.Skill5, row.Skill6, row.Skill7, row.Skill8};
      int[] levels = {row.Sk1lvl, row.Sk2lvl, row.Sk3lvl, row.Sk4lvl,
          row.Sk5lvl, row.Sk6lvl, row.Sk7lvl, row.Sk8lvl};
      for (int i = 0; i < names.length; i++) if (name != null && name.equals(names[i])) {
        return Math.max(1, levels[i]);
      }
    }
    return 1;
  }

  private static int curseStateId(String skillName) {
    if (skillName == null) return StateId.NONE;
    String name = skillName.toLowerCase(java.util.Locale.ROOT);
    if (name.contains("amplify")) return StateId.AMPLIFYDAMAGE;
    if (name.contains("weaken")) return StateId.WEAKEN;
    if (name.contains("defense curse")) return StateId.DEFENSE_CURSE;
    if (name.contains("blood mana")) return StateId.BLOOD_MANA;
    if (name.contains("decrepify")) return StateId.DECREPIFY;
    if (name.contains("lower resist")) return StateId.LOWERRESIST;
    if (name.contains("dim vision")) return StateId.DIMVISION;
    if (name.contains("terror")) return StateId.TERROR;
    if (name.contains("attract")) return StateId.ATTRACT;
    return StateId.NONE;
  }

  private static boolean isFrenzySkill(Skills.Entry skill) {
    return skill != null && skill.skill != null
        && skill.skill.toLowerCase(java.util.Locale.ROOT).contains("frenzy");
  }

  private boolean isPlayerEntity(int entityId) {
    return mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR;
  }

  private Item getThrowableWeapon(int entityId) {
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      return mPlayer.get(entityId).data.getItems().getEquippedThrowableWeapon();
    }
    // Legacy local tests may construct a player without the Player component.
    return Riiablo.charData == null ? null
        : Riiablo.charData.getItems().getEquippedThrowableWeapon();
  }

  private boolean isPlayerRangedNormalAttack(int entityId) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return false;
    Item weapon = mPlayer.get(entityId).data.getItems().getEquipped(BodyLoc.RARM);
    if (weapon == null) weapon = mPlayer.get(entityId).data.getItems().getEquipped(BodyLoc.LARM);
    return weapon != null && weapon.type != null
        && (weapon.type.is(Type.BOW) || weapon.type.is(Type.XBOW));
  }

  private static int statInt(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  /** Returns the authoritative runtime states for combat modifiers. */
  private com.riiablo.engine.server.state.StateList stateList(int entityId) {
    if (!mUnitStates.has(entityId)) return null;
    UnitStates states = mUnitStates.get(entityId);
    return states != null ? states.stateList : null;
  }

  private void applyCombatStates(int attackerId, int targetId,
      CombatSystem.CombatResult combat) {
    if (!mUnitStates.has(targetId)) return;
    if (combat.poisonDuration > 0
        && combat.elementalDamage[CombatSystem.DAMAGE_POISON] > 0) {
      StatusEffectApplier.INSTANCE.applyPoison(targetId,
          combat.elementalDamage[CombatSystem.DAMAGE_POISON],
          combat.poisonDuration, attackerId);
    }
    if (combat.coldDuration > 0
        && combat.elementalDamage[CombatSystem.DAMAGE_COLD] > 0) {
      StatusEffectApplier.INSTANCE.applyCold(targetId, combat.coldDuration, attackerId);
    }
  }

  private void resolveFireHit(int entityId, int targetId) {
    if (targetId == Engine.INVALID_ENTITY || !mMonster.has(entityId)
        || !mAttributesWrapper.has(entityId) || !mAttributesWrapper.has(targetId)) {
      log.info("[MONSTER_SKILL] phase=fire_hit_skipped entity={} target={} reason=missing_entity_data",
          entityId, targetId);
      return;
    }
    Monster monster = mMonster.get(entityId);
    if (monster.monstats == null) return;
    Attributes attacker = mAttributesWrapper.get(entityId).attrs;
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    int level = Math.max(1, statInt(attacker, Stat.level));
    MonsterModeDamageResolver.Profile profile = MonsterModeDamageResolver.resolve(
        monster.monstats, level, 0, Engine.Monster.MODE_S1);
    log.info("[MONSTER_SKILL] phase=fire_hit_profile entity={} monster={} target={} "
            + "source=monstats_s1 physical={}..{} ar={} fire={}..{} lightning={}..{} "
            + "cold={}..{} poison={}..{} magic={}..{} elements={}",
        entityId, monster.monstats.Id, targetId,
        profile.minDamage, profile.maxDamage, profile.attackRating,
        profile.elementalMin[CombatSystem.DAMAGE_FIRE],
        profile.elementalMax[CombatSystem.DAMAGE_FIRE],
        profile.elementalMin[CombatSystem.DAMAGE_LIGHTNING],
        profile.elementalMax[CombatSystem.DAMAGE_LIGHTNING],
        profile.elementalMin[CombatSystem.DAMAGE_COLD],
        profile.elementalMax[CombatSystem.DAMAGE_COLD],
        profile.elementalMin[CombatSystem.DAMAGE_POISON],
        profile.elementalMax[CombatSystem.DAMAGE_POISON],
        profile.elementalMin[CombatSystem.DAMAGE_MAGIC],
        profile.elementalMax[CombatSystem.DAMAGE_MAGIC],
        profile.matchedElementProfiles);

    CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, false, isPlayerEntity(targetId), false,
        profile.minDamage, profile.maxDamage, profile.attackRating, false,
        profile.elementalMin, profile.elementalMax,
        profile.coldLength, profile.poisonLength,
        stateList(entityId), stateList(targetId));
    if (!combat.hit) {
      log.info("[MONSTER_SKILL] phase=fire_hit_result entity={} target={} result=miss chance={}",
          entityId, targetId, combat.hitChance);
      return;
    }
    if (combat.blocked) {
      log.info("[MONSTER_SKILL] phase=fire_hit_result entity={} target={} result=blocked chance={}",
          entityId, targetId, combat.hitChance);
      return;
    }
    float damage = combat.totalDamage;
    log.info("[MONSTER_SKILL] phase=fire_hit_result entity={} target={} result=hit chance={} "
            + "physical={} fire={} total={}",
        entityId, targetId, combat.hitChance, combat.physicalDamage,
        combat.elementalDamage[CombatSystem.DAMAGE_FIRE], damage);
    if (damage <= 0) return;

    StatRef hitpoints = defender.get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints == null || hitpoints.asFixed() <= 0f) return;
    DamageEvent event = DamageEvent.obtain(entityId, targetId, damage);
    events.dispatch(event);
    hitpoints.sub(Math.max(0f, event.damage));
    if (hitpoints.asFixed() < 0f) hitpoints.set(0f);
    applyCombatStates(entityId, targetId, combat);
    if (hitpoints.asFixed() <= 0f) events.dispatch(DeathEvent.obtain(entityId, targetId));
  }

  /**
   * D2MOO SKILLS_SrvDo096_ZakarumHeal_Bestow.  Bestow is intentionally not
   * modelled as a state/buff: it immediately restores a random percentage of
   * the target's maximum life at the animation keyframe.  The native function
   * uses calc1/calc2 as a half-open percentage range (max is exclusive); the hidden monster
   * row has those columns empty in some data exports, so fall back to the
   * shared ZakarumHeal formulas (15 + 5 * level .. 50) used by the original
   * binary table.
   */
  private void resolveBestow(int entityId, int targetId) {
    if (targetId == Engine.INVALID_ENTITY || !mMonster.has(entityId)
        || !mAttributesWrapper.has(targetId)) {
      log.info("[MONSTER_BESTOW] phase=skipped source={} target={} reason=missing_entity_data",
          entityId, targetId);
      return;
    }
    Attributes target = mAttributesWrapper.get(targetId).attrs;
    if (target == null) {
      log.info("[MONSTER_BESTOW] phase=skipped source={} target={} reason=missing_attributes",
          entityId, targetId);
      return;
    }
    StatRef currentRef = target.get(Stat.hitpoints, StatRef.obtain());
    StatRef maxRef = target.get(Stat.maxhp, StatRef.obtain());
    if (currentRef == null || maxRef == null || maxRef.asFixed() <= 0f) {
      log.info("[MONSTER_BESTOW] phase=skipped source={} target={} reason=missing_life_stats",
          entityId, targetId);
      return;
    }
    float current = Math.max(0f, currentRef.asFixed());
    float max = Math.max(0f, maxRef.asFixed());
    if (current <= 0f || current >= max) {
      log.info("[MONSTER_BESTOW] phase=skipped source={} target={} reason=target_not_wounded hp={} maxHp={}",
          entityId, targetId, current, max);
      return;
    }

    Skills.Entry skill = mCasting.has(entityId)
        ? Riiablo.files.skills.get(mCasting.get(entityId).skillId) : null;
    int level = monsterSkillLevelFor(entityId, skill);
    int[] range = resolveBestowPercentRange(skill, level);
    int minPercent = range[0];
    int maxPercent = range[1];
    boolean fallback = range[2] != 0;
    minPercent = Math.max(0, Math.min(100, minPercent));
    maxPercent = Math.max(minPercent, Math.min(100, maxPercent));
    // ITEMS_RollLimitedRandomNumber(seed, max-min) returns [0, max-min), so
    // preserve the native half-open upper bound rather than using LibGDX's
    // inclusive random(min,max) overload.
    int percent = maxPercent > minPercent
        ? MathUtils.random(minPercent, maxPercent - 1) : minPercent;
    float before = current;
    float restored = max * percent / 100f;
    float after = Math.min(max, current + restored);
    currentRef.set(after);
    log.info("[MONSTER_BESTOW] phase=result source={} target={} level={} percent={} range={}..{} "
            + "fallbackZakarumHeal={} restored={} hp={} -> {} maxHp={}",
        entityId, targetId, level, percent, minPercent, maxPercent, fallback,
        after - before, before, after, max);
  }

  /** D2MOO SKILLS_SrvDo150_Smite monster branch. */
  private void resolveSmite(int entityId, int targetId) {
    if (targetId == Engine.INVALID_ENTITY || !mMonster.has(entityId)
        || !mAttributesWrapper.has(entityId) || !mAttributesWrapper.has(targetId)
        || !mPosition.has(entityId) || !mPosition.has(targetId)) {
      log.info("[MONSTER_SMITE] phase=skipped source={} target={} reason=missing_entity_data",
          entityId, targetId);
      return;
    }
    Monster monster = mMonster.get(entityId);
    float distance = mPosition.get(entityId).position.dst(mPosition.get(targetId).position);
    float meleeRange = 1f + (monster.monstats2 != null ? monster.monstats2.MeleeRng : 0);
    if (distance > meleeRange) {
      log.info("[MONSTER_SMITE] phase=skipped source={} target={} reason=out_of_melee_range distance={} range={}",
          entityId, targetId, distance, meleeRange);
      return;
    }
    Attributes attacker = mAttributesWrapper.get(entityId).attrs;
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    if (attacker == null || defender == null) return;
    int minDamage = monster.attack2MinDamage;
    int maxDamage = monster.attack2MaxDamage;
    int attackRating = monster.attack2ToHit;
    if (maxDamage <= 0) {
      minDamage = statInt(attacker, Stat.mindamage);
      maxDamage = statInt(attacker, Stat.maxdamage);
      attackRating = statInt(attacker, Stat.tohit);
    }
    CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, false, isPlayerEntity(targetId), false,
        minDamage, maxDamage, attackRating,
        stateList(entityId), stateList(targetId));
    if (!combat.hit) {
      log.info("[MONSTER_SMITE] phase=result source={} target={} result=miss chance={} distance={}",
          entityId, targetId, combat.hitChance, distance);
      return;
    }
    if (combat.blocked) {
      log.info("[MONSTER_SMITE] phase=result source={} target={} result=blocked chance={}",
          entityId, targetId, combat.hitChance);
      return;
    }
    StatRef hitpoints = defender.get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints == null || hitpoints.asFixed() <= 0f) return;
    float before = hitpoints.asFixed();
    DamageEvent event = DamageEvent.obtain(entityId, targetId, Math.max(0f, combat.totalDamage));
    events.dispatch(event);
    float damage = Math.max(0f, event.damage);
    hitpoints.sub(damage);
    if (hitpoints.asFixed() < 0f) hitpoints.set(0f);

    Skills.Entry skill = mCasting.has(entityId)
        ? Riiablo.files.skills.get(mCasting.get(entityId).skillId) : null;
    int stunFrames = SkillFormula.evaluate(skill != null ? skill.calc2 : null, skill,
        monsterSkillLevelFor(entityId, skill));
    if (stunFrames > 0) StatusEffectApplier.INSTANCE.applyStun(targetId, stunFrames);
    log.info("[MONSTER_SMITE] phase=result source={} target={} result=hit damage={} hp={} -> {} "
            + "chance={} stunFrames={} a2Profile={}..{} ar={}",
        entityId, targetId, damage, before, hitpoints.asFixed(), combat.hitChance,
        stunFrames, minDamage, maxDamage, attackRating);
    if (hitpoints.asFixed() <= 0f) events.dispatch(DeathEvent.obtain(entityId, targetId));
  }

  /** D2MOO SKILLS_SrvDo086_MaggotDown. */
  private void resolveMaggotDown(int entityId) {
    if (!mMonster.has(entityId) || !mAttributesWrapper.has(entityId)) {
      log.info("[MONSTER_MAGGOT] phase=down_heal source={} result=skipped reason=missing_components",
          entityId);
      return;
    }
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    StatRef maxHp = attrs != null ? attrs.get(Stat.maxhp, StatRef.obtain()) : null;
    if (hp == null || maxHp == null || maxHp.asFixed() <= 0f || hp.asFixed() <= 0f) {
      log.info("[MONSTER_MAGGOT] phase=down_heal source={} result=skipped reason=invalid_life hp={} maxHp={}",
          entityId, hp != null ? hp.asFixed() : -1f, maxHp != null ? maxHp.asFixed() : -1f);
      return;
    }
    Skills.Entry skill = mCasting.has(entityId)
        ? Riiablo.files.skills.get(mCasting.get(entityId).skillId) : null;
    int level = monsterSkillLevelFor(entityId, skill);
    int percent = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, level);
    percent = Math.max(0, percent);
    float before = hp.asFixed();
    float after = calculateMaggotHeal(before, maxHp.asFixed(), percent);
    hp.set(after);
    log.info("[MONSTER_MAGGOT] phase=down_heal source={} result=healed level={} percent={} hp={} -> {} maxHp={}",
        entityId, level, percent, before, after, maxHp.asFixed());
  }

  /** D2MOO SKILLS_SrvDo087_MaggotLay. */
  private void resolveMaggotLay(int entityId, int targetId) {
    if (!mMonster.has(entityId) || factory == null || !mPosition.has(entityId)
        || targetId == Engine.INVALID_ENTITY || !mPosition.has(targetId)) {
      log.info("[MONSTER_MAGGOT] phase=lay_rejected source={} target={} reason=missing_components",
          entityId, targetId);
      return;
    }
    Monster source = mMonster.get(entityId);
    String spawnId = source.monstats != null ? source.monstats.spawn : null;
    // D2GAME_GetSummonIdFromSkill falls back to the monster minion chain when
    // the row has no explicit spawn column (common for crow nests).
    if (!hasText(spawnId) && source.monstats != null) {
      spawnId = hasText(source.monstats.minion1) ? source.monstats.minion1
          : source.monstats.minion2;
    }
    // D2GAME_GetSummonIdFromSkill falls back to the monster minion chain when
    // the row has no explicit spawn column (common for crow nests).
    if (!hasText(spawnId) && source.monstats != null) {
      spawnId = hasText(source.monstats.minion1) ? source.monstats.minion1
          : source.monstats.minion2;
    }
    if (spawnId == null || spawnId.isEmpty()) {
      log.info("[MONSTER_MAGGOT] phase=lay_rejected source={} target={} reason=missing_spawn_row",
          entityId, targetId);
      return;
    }
    MonStats.Entry egg = Riiablo.files.monstats.get(spawnId);
    if (egg == null) {
      log.warn("[MONSTER_MAGGOT] phase=lay_rejected source={} target={} reason=spawn_lookup_failed spawn={}",
          entityId, targetId, spawnId);
      return;
    }
    Vector2 origin = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    int direction = maggotDirectionIndex(target.x - origin.x, target.y - origin.y);
    int offsetX = MAGGOT_OFFSET_X[direction];
    int offsetY = MAGGOT_OFFSET_Y[direction];
    float spawnX = origin.x + offsetX;
    float spawnY = origin.y + offsetY;
    int spawned;
    try {
      spawned = factory.createMonster(egg, spawnX, spawnY);
    } catch (RuntimeException ex) {
      log.warn("[MONSTER_MAGGOT] phase=lay_rejected source={} target={} reason=spawn_exception spawn={} error={}",
          entityId, targetId, spawnId, ex.toString());
      return;
    }
    if (spawned == Engine.INVALID_ENTITY) {
      log.info("[MONSTER_MAGGOT] phase=lay_rejected source={} target={} reason=spawn_failed spawn={} position=({}, {})",
          entityId, targetId, spawnId, spawnX, spawnY);
      return;
    }
    log.info("[MONSTER_MAGGOT] phase=lay_spawn source={} target={} spawn={} entity={} direction={} offset=({}, {}) position=({}, {})",
        entityId, targetId, spawnId, spawned, direction, offsetX, offsetY, spawnX, spawnY);
  }

  /** D2MOO SKILLS_SrvSt48_SwarmMove. */
  private void prepareSwarmMove(int entityId, int targetId, Vector2 targetVec) {
    if (targetId == Engine.INVALID_ENTITY || !mPosition.has(entityId)
        || !mPosition.has(targetId)) {
      log.info("[MONSTER_SWARM] phase=prepare_rejected source={} target={} reason=missing_target",
          entityId, targetId);
      return;
    }
    Vector2 target = targetVec != null ? targetVec : mPosition.get(targetId).position;
    boolean found = pathfinder.findPath(entityId, target, false, targetId);
    log.info("[MONSTER_SWARM] phase=prepare source={} target={} pathFound={} targetPos=({}, {})",
        entityId, targetId, found, target.x, target.y);
  }

  /** D2MOO SKILLS_SrvDo090_SwarmMove. */
  private void resolveSwarmMove(int entityId) {
    if (!mAnimData.has(entityId) || !mCasting.has(entityId)) return;
    Casting casting = mCasting.get(entityId);
    Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
    if (skill == null) return;
    int level = monsterSkillLevelFor(entityId, skill);
    int frame = SkillFormula.evaluate(skill.calc1, skill, level);
    if (frame <= 0) frame = SkillFormula.evaluate(skill.calc2, skill, level);
    if (frame > 0) {
      AnimData anim = mAnimData.get(entityId);
      anim.frame = Math.min(anim.numFrames > 0 ? anim.numFrames - 1 : frame << 8, frame << 8);
      log.info("[MONSTER_SWARM] phase=frame source={} skill={} level={} frame={}",
          entityId, skill.skill, level, frame);
    }
  }

  /** D2MOO SKILLS_SrvDo091_Nest_EvilHutSpawner. */
  private void resolveNest(int entityId) {
    if (!mMonster.has(entityId) || factory == null || !mPosition.has(entityId)) {
      log.info("[MONSTER_NEST] phase=spawn_rejected source={} reason=missing_components", entityId);
      return;
    }
    Monster source = mMonster.get(entityId);
    String spawnId = source.monstats != null ? source.monstats.spawn : null;
    // Native MONSTERS_GetSpawnMode_XY falls back to the minion chain when a
    // nest row has no explicit spawn field.
    if (!hasText(spawnId) && source.monstats != null) {
      spawnId = hasText(source.monstats.minion1) ? source.monstats.minion1
          : source.monstats.minion2;
    }
    if (spawnId == null || spawnId.isEmpty()) {
      log.info("[MONSTER_NEST] phase=spawn_rejected source={} reason=missing_spawn_row", entityId);
      return;
    }
    MonStats.Entry spawn = Riiablo.files.monstats.get(spawnId);
    if (spawn == null) {
      log.warn("[MONSTER_NEST] phase=spawn_rejected source={} reason=spawn_lookup_failed spawn={}",
          entityId, spawnId);
      return;
    }
    Vector2 sourcePosition = mPosition.get(entityId).position;
    float preferredX = sourcePosition.x + source.monstats.spawnx;
    float preferredY = sourcePosition.y + source.monstats.spawny;
    Vector2 position = map != null
        ? findNestSpawnPosition(map, preferredX, preferredY, new Vector2())
        // Detached/headless worlds may not register a Map; preserve the
        // native spawn request while making the degraded collision check
        // explicit in the log.
        : new Vector2(preferredX, preferredY);
    if (map == null) {
      log.warn("[MONSTER_NEST] phase=collision_check_skipped source={} reason=map_missing", entityId);
    }
    if (position == null) {
      log.info("[MONSTER_NEST] phase=spawn_rejected source={} reason=no_free_position "
              + "spawn={} preferred=({}, {}) offset=({}, {})",
          entityId, spawnId, preferredX, preferredY,
          source.monstats.spawnx, source.monstats.spawny);
      return;
    }
    int spawned;
    try {
      spawned = factory.createMonster(spawn, position.x, position.y);
    } catch (RuntimeException ex) {
      log.warn("[MONSTER_NEST] phase=spawn_rejected source={} reason=spawn_exception spawn={} error={}",
          entityId, spawnId, ex.toString());
      return;
    }
    if (spawned == Engine.INVALID_ENTITY) {
      log.info("[MONSTER_NEST] phase=spawn_rejected source={} reason=spawn_failed spawn={}",
          entityId, spawnId);
      return;
    }
    log.info("[MONSTER_NEST] phase=spawn source={} spawn={} entity={} position=({}, {})",
        entityId, spawnId, spawned, position.x, position.y);
  }

  static Vector2 findNestSpawnPosition(Map map, float preferredX, float preferredY, Vector2 out) {
    if (map == null) return null;
    return findNestSpawnPosition(preferredX, preferredY, out,
        (x, y) -> (map.flags(x, y) & DT1.Tile.FLAG_BLOCK_WALK) == 0);
  }

  interface NestWalkability {
    boolean isWalkable(int x, int y);
  }

  static Vector2 findNestSpawnPosition(float preferredX, float preferredY, Vector2 out,
      NestWalkability walkability) {
    if (out == null || walkability == null) return null;
    int centerX = MathUtils.round(preferredX);
    int centerY = MathUtils.round(preferredY);
    for (int radius = 0; radius <= 3; radius++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (radius > 0 && Math.abs(dx) != radius && Math.abs(dy) != radius) continue;
          int x = centerX + dx;
          int y = centerY + dy;
          if (walkability.isWalkable(x, y)) {
            return out.set(x, y);
          }
        }
      }
    }
    return null;
  }

  /** D2MOO SKILLS_SrvDo067_Charge (monster branch). */
  private void resolveCharge(int entityId, int targetId) {
    if (targetId == Engine.INVALID_ENTITY || !mMonster.has(entityId)
        || !mAttributesWrapper.has(entityId) || !mAttributesWrapper.has(targetId)) {
      log.info("[MONSTER_CHARGE] phase=hit_rejected source={} target={} reason=missing_components",
          entityId, targetId);
      return;
    }
    Attributes attacker = mAttributesWrapper.get(entityId).attrs;
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateAttack(
        attacker, defender, false, isPlayerEntity(targetId), false,
        statInt(attacker, Stat.mindamage), statInt(attacker, Stat.maxdamage),
        statInt(attacker, Stat.tohit),
        stateList(entityId), stateList(targetId));
    if (!combat.hit || combat.blocked) {
      log.info("[MONSTER_CHARGE] phase=hit_result source={} target={} result={} chance={}",
          entityId, targetId, combat.blocked ? "blocked" : "miss", combat.hitChance);
      return;
    }
    Skills.Entry skill = mCasting.has(entityId)
        ? Riiablo.files.skills.get(mCasting.get(entityId).skillId) : null;
    int level = monsterSkillLevelFor(entityId, skill);
    int bonusPercent = Math.max(0, SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, level));
    float damage = combat.totalDamage * (100f + bonusPercent) / 100f;
    StatRef hp = defender.get(Stat.hitpoints, StatRef.obtain());
    if (hp == null || hp.asFixed() <= 0f) return;
    float before = hp.asFixed();
    DamageEvent event = DamageEvent.obtain(entityId, targetId, damage);
    events.dispatch(event);
    hp.sub(Math.max(0f, event.damage));
    if (hp.asFixed() < 0f) hp.set(0f);
    log.info("[MONSTER_CHARGE] phase=hit_result source={} target={} result=hit baseDamage={} bonusPct={} damage={} hp={} -> {}",
        entityId, targetId, combat.totalDamage, bonusPercent, event.damage, before, hp.asFixed());
    if (hp.asFixed() <= 0f) events.dispatch(DeathEvent.obtain(entityId, targetId));
  }

  private static final int[] MAGGOT_OFFSET_X = {
      0, -1, -1, -1, 0, 1, 1, 1,
      0, -1, -2, -2, -2, -2, -2, -1,
      0, 1, 2, 2, 2, 2, 2, 1,
      0, -3, -3, -3, 0, 3, 3, 3};
  private static final int[] MAGGOT_OFFSET_Y = {
      -1, -1, 0, 1, 1, 1, 0, -1,
      -2, -2, -2, -1, 0, 1, 2, 2,
      2, 2, 2, 1, 0, -1, -2, -2,
      -3, -3, 0, 3, 3, 3, 0, -3};

  static float calculateMaggotHeal(float currentHp, float maxHp, int percent) {
    if (currentHp <= 0f || maxHp <= 0f) return Math.max(0f, currentHp);
    float healed = currentHp + currentHp * Math.max(0, percent) / 100f;
    return Math.min(maxHp, healed);
  }

  /** Maps world direction to the D2Common_11055 lookup index used by MaggotLay. */
  static int maggotDirectionIndex(float dx, float dy) {
    if (Math.abs(dx) < 0.0001f && Math.abs(dy) < 0.0001f) return 0;
    // D2's direction 0 points north and direction numbers advance clockwise;
    // world Y therefore uses the opposite sign from the usual atan2 X axis.
    double angle = Math.atan2(-dx, -dy);
    int direction = ((int) Math.round(angle / (Math.PI / 4.0))) & 7;
    // D2MOO byte_6FD296C8: direction -> D2Common_11055 argument.
    return new int[] {10, 8, 22, 20, 18, 16, 14, 12}[direction];
  }

  /** Returns {minPercent, maxPercent, fallbackToZakarumHeal(0/1)}. */
  static int[] resolveBestowPercentRange(Skills.Entry skill, int level) {
    int min = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, level);
    int max = SkillFormula.evaluate(skill != null ? skill.calc2 : null, skill, level);
    int fallback = 0;
    if (min <= 0 && max <= 0) {
      Skills.Entry heal = Riiablo.files.skills.get("ZakarumHeal");
      min = SkillFormula.evaluate(heal != null ? heal.calc1 : "15+5*lvl", heal, level);
      max = SkillFormula.evaluate(heal != null ? heal.calc2 : "50", heal, level);
      fallback = 1;
    }
    min = Math.max(0, Math.min(100, min));
    max = Math.max(min, Math.min(100, max));
    return new int[] {min, max, fallback};
  }

  private int monsterSkillLevelFor(int entityId, Skills.Entry skill) {
    if (!mMonster.has(entityId) || skill == null || mMonster.get(entityId).monstats == null) {
      return 1;
    }
    Monster row = mMonster.get(entityId);
    String name = skill.skill;
    String[] skills = {row.monstats.Skill1, row.monstats.Skill2, row.monstats.Skill3,
        row.monstats.Skill4, row.monstats.Skill5, row.monstats.Skill6,
        row.monstats.Skill7, row.monstats.Skill8};
    int[] levels = {row.monstats.Sk1lvl, row.monstats.Sk2lvl, row.monstats.Sk3lvl,
        row.monstats.Sk4lvl, row.monstats.Sk5lvl, row.monstats.Sk6lvl,
        row.monstats.Sk7lvl, row.monstats.Sk8lvl};
    for (int i = 0; i < skills.length; i++) {
      if (name != null && name.equals(skills[i])) return Math.max(1, levels[i]);
    }
    return 1;
  }

  private void startLeap(int entityId, int targetId, Vector2 requestedTarget) {
    if (!mPosition.has(entityId) || mLeap.has(entityId)) {
      log.info("[MONSTER_LEAP] phase=rejected entity={} target={} reason={}",
          entityId, targetId,
          mLeap.has(entityId) ? "already_airborne" : "missing_position");
      return;
    }
    Vector2 start = mPosition.get(entityId).position;
    Vector2 target = null;
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
      target = mPosition.get(targetId).position;
    } else if (requestedTarget != null) {
      target = requestedTarget;
    }
    if (target == null) {
      log.info("[MONSTER_LEAP] phase=rejected entity={} target={} reason=missing_target",
          entityId, targetId);
      return;
    }

    // D2MOO SrvSt40 uses 2 * target - unit for monsters, making a sand
    // leaper vault across the target instead of landing on top of it.
    Vector2 desired = new Vector2(target).scl(2f).sub(start);
    int size = mSize.has(entityId) ? mSize.get(entityId).size : 0;
    Vector2 landing = new Vector2();
    if (!LeapSystem.findLanding(map, desired, size, landing)) {
      log.info("[MONSTER_LEAP] phase=rejected entity={} target={} reason=no_free_landing "
              + "desired=({}, {}) size={}",
          entityId, targetId, desired.x, desired.y, size);
      return;
    }

    pathfinder.findPath(entityId, null);
    if (mAngle.has(entityId)) mAngle.get(entityId).target.set(landing).sub(start).nor();
    float distance = start.dst(landing);
    float nativeRun = 0f;
    String monsterId = "unknown";
    if (mMonster.has(entityId) && mMonster.get(entityId).monstats != null) {
      nativeRun = mMonster.get(entityId).monstats.Run;
      monsterId = mMonster.get(entityId).monstats.Id;
    }
    if (nativeRun <= 0f && mVelocity.has(entityId)) {
      nativeRun = Math.max(mVelocity.get(entityId).runSpeed, mVelocity.get(entityId).walkSpeed);
    }
    float duration = MathUtils.clamp(distance / Math.max(8f, nativeRun), 0.18f, 0.65f);
    mLeap.create(entityId).set(start, landing, duration, targetId);
    log.info("[MONSTER_LEAP] phase=takeoff entity={} monster={} target={} start=({}, {}) "
            + "targetPos=({}, {}) desired=({}, {}) landing=({}, {}) distance={} "
            + "nativeRun={} duration={} size={}",
        entityId, monsterId, targetId, start.x, start.y, target.x, target.y,
        desired.x, desired.y, landing.x, landing.y, distance, nativeRun, duration, size);
  }

  private boolean spawnMonsterAttackMissile(int entityId, int targetId) {
    if (!mMonster.has(entityId) || factory == null || !mPosition.has(entityId)
        || !mPosition.has(targetId)) {
      return false;
    }
    if (mCasting.has(entityId) && mCasting.get(entityId).skillId != SkillCodes.attack) {
      // Skills.txt projectiles are created by ServerSkillSystem from the
      // SkillDoEvent. MissA1/MissA2 belong only to the native basic attack.
      return false;
    }

    Monster monster = mMonster.get(entityId);
    if (monster.monstats == null) return false;
    boolean attack2 = currentMonsterAttackMode(entityId) == Engine.Monster.MODE_A2;
    String missileName = attack2 ? monster.monstats.MissA2 : monster.monstats.MissA1;
    if (missileName == null || missileName.isEmpty()) {
      missileName = attack2 ? monster.monstats.MissA1 : monster.monstats.MissA2;
    }
    if (missileName == null || missileName.isEmpty()) return false;

    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      log.warn("[MONSTER_MISSILE] lookup_failed entity={} target={} missile={}",
          entityId, targetId, missileName);
      return false;
    }

    Vector2 start = mPosition.get(entityId).position;
    Vector2 direction = new Vector2(mPosition.get(targetId).position).sub(start);
    if (direction.isZero(0.0001f)) direction.set(1f, 0f);
    direction.nor();
    int missileId = factory.createMissile(missile, direction, start, entityId);
    if (missileId < 0) {
      log.warn("[MONSTER_MISSILE] create_failed entity={} target={} missile={}",
          entityId, targetId, missileName);
      return false;
    }
    if (attack2 && mMissile.has(missileId)) {
      // Keep the A2 profile on this projectile; the shared monster attributes
      // represent A1 and must remain unchanged for overlapping attacks.
      com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
      MissileDamageResolver.applySourceAttackProfile(projectile,
          monster.attack2MinDamage, monster.attack2MaxDamage, monster.attack2ToHit);
    }
    log.info("[MONSTER_MISSILE] created entity={} target={} mode={} missileId={} missile={} "
            + "speed={} range={} damage={}..{} ar={} start=({}, {}) direction=({}, {})",
        entityId, targetId, attack2 ? "A2" : "A1", missileId, missile.Missile,
        missile.Vel, missile.Range, monsterAttackMinDamage(entityId),
        monsterAttackMaxDamage(entityId), monsterAttackRating(entityId),
        start.x, start.y, direction.x, direction.y);
    return true;
  }

  private boolean hasMonsterAttackMissile(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    if (monster.monstats == null) return false;
    return hasText(monster.monstats.MissA1) || hasText(monster.monstats.MissA2);
  }

  private boolean isMonsterProjectileSkill(int entityId) {
    if (!mMonster.has(entityId) || !mCasting.has(entityId)) return false;
    int skillId = mCasting.get(entityId).skillId;
    if (skillId == SkillCodes.attack) return false;
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    return skill != null && (hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
        || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
        || hasText(skill.cltmissilec) || hasText(skill.cltmissiled));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  private byte currentMonsterAttackMode(int entityId) {
    return mCofReference.has(entityId)
        ? mCofReference.get(entityId).mode : Engine.Monster.MODE_A1;
  }

  private int monsterAttackMinDamage(int entityId) {
    Monster monster = mMonster.has(entityId) ? mMonster.get(entityId) : null;
    return currentMonsterAttackMode(entityId) == Engine.Monster.MODE_A2
        && monster != null ? monster.attack2MinDamage : 0;
  }

  private int monsterAttackMaxDamage(int entityId) {
    Monster monster = mMonster.has(entityId) ? mMonster.get(entityId) : null;
    return currentMonsterAttackMode(entityId) == Engine.Monster.MODE_A2
        && monster != null ? monster.attack2MaxDamage : 0;
  }

  private int monsterAttackRating(int entityId) {
    Monster monster = mMonster.has(entityId) ? mMonster.get(entityId) : null;
    return currentMonsterAttackMode(entityId) == Engine.Monster.MODE_A2
        && monster != null ? monster.attack2ToHit : 0;
  }
}
