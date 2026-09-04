package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.IntIntMap;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.MonsterModeDamageResolver;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.item.ItemDurabilityManager;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.Engine;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Type;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.FrenzyRuntime;
import com.riiablo.engine.server.component.WhirlwindRuntime;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Leap;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.monster.MonsterRank;
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
  protected ComponentMapper<FrenzyRuntime> mFrenzyRuntime;
  protected ComponentMapper<WhirlwindRuntime> mWhirlwindRuntime;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<com.riiablo.engine.server.component.Velocity> mVelocity;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
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
  /** Advancing per-unit stream used by native progressive field placement. */
  private final IntIntMap assassinProgressiveSeeds = new IntIntMap();

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
    castInternal(entityId, skillId, targetId, targetVec, (byte) Engine.INVALID_MODE);
  }

  /** Casts with an explicit native animation mode (used by hireling AI). */
  public void castWithMode(int entityId, int skillId, byte mode, int targetId, Vector2 targetVec) {
    castInternal(entityId, skillId, targetId, targetVec, mode);
  }

  private void castInternal(int entityId, int skillId, int targetId, Vector2 targetVec,
      byte requestedMode) {
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

    if (mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR
        && mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      com.riiablo.save.ItemData items = mPlayer.get(entityId).data.getItems();
      Item rangedWeapon = items.getEquippedRangedWeapon();
      if (ServerSkillSystem.isAmazonBowSkill(skill) && rangedWeapon == null) {
        log.info("[RANGED_AMMO] phase=cast_reject entity={} skill={} reason=no_ranged_weapon",
            entityId, skillId);
        return;
      }
      if (ServerSkillSystem.requiresRangedAmmo(skill, rangedWeapon)) {
        Item ammo = items.getEquippedAmmo(rangedWeapon);
        if (!ServerSkillSystem.hasQuantity(ammo)) {
          log.info("[RANGED_AMMO] phase=cast_reject entity={} skill={} weapon={} ammo={} reason={}",
              entityId, skillId, rangedWeapon.code, ammo != null ? ammo.code : "none",
              ammo == null ? "missing" : "empty");
          return;
        }
      }
    }

    if (skill != null && skill.srvdofunc == 9 && !hasTwoFrenzyWeapons(entityId)) {
      log.info("[FRENZY] phase=cast_reject entity={} skill={} reason=requires_two_melee_weapons",
          entityId, skillId);
      return;
    }

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
    byte mode = requestedMode != Engine.INVALID_MODE
        ? requestedMode : (byte) getMode(skill, type);
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

    events.dispatch(SkillStartEvent.obtain(entityId, skillId, targetId, targetVec, skill.srvstfunc, skill.cltstfunc));
  }

  /** Runs Skills.txt server-start functions for both player and AI cast entry points. */
  @Subscribe
  public void onSkillStart(SkillStartEvent event) {
    if (event == null || event.entityId == Engine.INVALID_ENTITY) return;
    srvstfunc(event.entityId, event.srvstfunc, event.targetId, event.targetVec);
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
        NativeUnitFlags targetFlags = mNativeUnitFlags.get(casting.targetId);
        if (targetFlags != null
            && !targetFlags.has(NativeUnitFlags.CAN_BE_ATTACKED)) {
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
    boolean frenzyRetarget = BarbarianSkills.isFrenzy(skill)
        && casting.frenzyInitialized && (casting.frenzyStrikeIndex & 1) != 0;
    if (!targetDead || allowsDeadTarget(skill) || frenzyRetarget) {
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
    final int completedTargetId = casting.targetId;
    Skills.Entry completedSkill = Riiablo.files.skills.get(casting.skillId);
    
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
    
    if (completedSkill != null && completedSkill.srvstfunc == 61 && factory != null) {
      factory.finishSelfResurrection(event.entityId);
    }
    if (casting.dragonTalonInitialized
        && casting.dragonTalonRemainingKicks > 0
        && casting.dragonTalonKickProcessed
        && !targetDead) {
      log.info("[ASSASSIN_DRAGON_TALON] phase=continue entity={} target={} remaining={} successes={}",
          event.entityId, completedTargetId, casting.dragonTalonRemainingKicks,
          casting.dragonTalonSuccessfulKicks);
      return;
    }
    if (casting.dragonClawInitialized
        && casting.dragonClawRemainingStrikes > 0
        && casting.dragonClawStrikeProcessed
        && !targetDead) {
      log.info("[ASSASSIN_DRAGON_CLAW] phase=continue entity={} target={} remaining={} nextStrike={}",
          event.entityId, completedTargetId, casting.dragonClawRemainingStrikes,
          casting.dragonClawStrikeIndex + 1);
      return;
    }
    if (casting.dragonFlightInitialized
        && casting.dragonFlightWarped
        && !casting.dragonFlightKickProcessed
        && !targetDead) {
      log.info("[ASSASSIN_DRAGON_FLIGHT] phase=continue entity={} target={} next=kick",
          event.entityId, completedTargetId);
      return;
    }
    if (mWhirlwindRuntime.has(event.entityId)) {
      log.debug("[WHIRLWIND] phase=repeat_animation entity={} skill={}",
          event.entityId, casting.skillId);
      return;
    }
    mCasting.remove(event.entityId);
    
    if (targetDead && mSequence.has(event.entityId)) {
      log.trace("Target {} is dead, stopping attack sequence for {}", completedTargetId, event.entityId);
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
      if (mFrenzyRuntime.has(event.victim)) mFrenzyRuntime.remove(event.victim);
      if (mWhirlwindRuntime.has(event.victim)) mWhirlwindRuntime.remove(event.victim);
      if (mPathfind.has(event.victim)) mPathfind.remove(event.victim);
      if (mVelocity.has(event.victim)) mVelocity.get(event.victim).velocity.setZero();
      if (mUnitStates.has(event.victim)) {
        UnitStates states = mUnitStates.get(event.victim);
        if (states != null && states.stateList != null) {
          states.stateList.removeState(StateId.WHIRLWIND);
          states.stateList.removeState(StateId.BERSERK);
        }
      }
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
      case 6: // Amazon Power/Charged Strike; combat resolves at the keyframe
      case 10: // Amazon Lightning Strike; chain resolves at the keyframe
        log.debug("[AMAZON_SKILL] phase=start entity={} target={} srvStFunc={} delegated=keyframe",
            entityId, targetId, srvstfunc);
        break;
      case 12: { // SKILLS_SrvSt12_Telekinesis_DragonFlight
        Casting casting = mCasting.get(entityId);
        Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
        // Telekinesis shares SrvSt12 but does not use the Dragon Flight
        // sequence or melee target rules.
        if (skill == null || skill.srvdofunc != 52) break;
        String reject = dragonFlightStartRejection(entityId, targetId, skill,
            Math.max(1, skillLevel(entityId, casting.skillId)));
        if (reject != null) {
          log.info("[ASSASSIN_DRAGON_FLIGHT] phase=start_reject entity={} target={} reason={}",
              entityId, targetId, reject);
          mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        casting.dragonFlightInitialized = true;
        casting.dragonFlightWarped = false;
        casting.dragonFlightKickProcessed = false;
        int level = Math.max(1, skillLevel(entityId, casting.skillId));
        int range = Math.max(0,
            SkillFormula.evaluate(skill.aurarangecalc, skill, level));
        log.info("[ASSASSIN_DRAGON_FLIGHT] phase=start entity={} target={} skillLevel={} "
                + "range={} damagePercent={} attackRating={}",
            entityId, targetId, level, range,
            AssassinSkills.calculateDragonFlightDamageBonus(skill, level),
            AssassinSkills.dragonFlightAttackRating(
                skill, level, mAttributesWrapper.get(entityId).attrs));
        break;
      }
      case 24: { // SKILLS_SrvSt24_DragonTalon
        Casting casting = mCasting.get(entityId);
        Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
        if (casting == null || skill == null || targetId == Engine.INVALID_ENTITY
            || !isInMeleeRange(entityId, targetId, 0)) {
          log.info("[ASSASSIN_DRAGON_TALON] phase=start_reject entity={} target={} reason=range_or_target",
              entityId, targetId);
          if (mCasting.has(entityId)) mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        int level = Math.max(1, skillLevel(entityId, casting.skillId));
        casting.dragonTalonRemainingKicks =
            AssassinSkills.getDragonTalonKickCount(skill, level);
        casting.dragonTalonSuccessfulKicks = 0;
        casting.dragonTalonInitialized = true;
        casting.dragonTalonProgressiveReleased = false;
        casting.dragonTalonKickProcessed = false;
        log.info("[ASSASSIN_DRAGON_TALON] phase=start entity={} target={} skillLevel={} kicks={}",
            entityId, targetId, level, casting.dragonTalonRemainingKicks);
        break;
      }
      case 25: { // SKILLS_SrvSt25_64_DragonClaw_MonFrenzy (Dragon Claw table entry)
        Casting casting = mCasting.get(entityId);
        Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
        Item right = equippedClaw(entityId, BodyLoc.RARM);
        Item left = equippedClaw(entityId, BodyLoc.LARM);
        if (casting == null || skill == null || targetId == Engine.INVALID_ENTITY) {
          log.info("[ASSASSIN_DRAGON_CLAW] phase=start_reject entity={} target={} reason=target",
              entityId, targetId);
          if (mCasting.has(entityId)) mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        casting.dragonClawRemainingStrikes = right != null && left != null ? 2 : 1;
        casting.dragonClawStrikeIndex = 0;
        casting.dragonClawInitialized = true;
        casting.dragonClawProgressiveReleased = false;
        casting.dragonClawStrikeProcessed = false;
        if (mSequence.has(entityId)) {
          mSequence.get(entityId).sequence(
              Engine.Player.MODE_A2, mMovementModes.get(entityId).NU);
        }
        log.info("[ASSASSIN_DRAGON_CLAW] phase=start entity={} target={} strikes={} right={} left={}",
            entityId, targetId, casting.dragonClawRemainingStrikes,
            right != null ? right.code : "none", left != null ? left.code : "none");
        break;
      }
      case 64: // SKILLS_SrvSt25_64_DragonClaw_MonFrenzy (MonFrenzy table entry)
        // The native shared start function only requires a live target.
        // MonFrenzy owns its alternating sequence state in SrvDo109 and must
        // never inherit Dragon Claw's player inventory/hand requirements.
        if (targetId == Engine.INVALID_ENTITY) {
          if (mCasting.has(entityId)) mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
        }
        break;
      case 27: { // SKILLS_SrvSt27_DragonTail
        Casting casting = mCasting.get(entityId);
        Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
        if (casting == null || skill == null || targetId == Engine.INVALID_ENTITY
            || !mPosition.has(entityId) || !mPosition.has(targetId)
            || !mAttributesWrapper.has(entityId) || !mAttributesWrapper.has(targetId)
            || !isAlive(entityId) || !isAlive(targetId)
            || !isInMeleeRange(entityId, targetId, 0)) {
          log.info("[ASSASSIN_DRAGON_TAIL] phase=start_reject entity={} target={} reason=range_or_target",
              entityId, targetId);
          if (mCasting.has(entityId)) mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        boolean sourcePlayerAligned = mPlayer.has(entityId) || mMercenary.has(entityId)
            || mSummonedPet.has(entityId);
        boolean targetPlayerAligned = mPlayer.has(targetId) || mMercenary.has(targetId)
            || mSummonedPet.has(targetId);
        if (!PvpCombatRules.canDamage(
            partyManager, entityId, targetId, sourcePlayerAligned, targetPlayerAligned)) {
          log.info("[ASSASSIN_DRAGON_TAIL] phase=start_reject entity={} target={} reason=relation",
              entityId, targetId);
          mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        int level = Math.max(1, skillLevel(entityId, casting.skillId));
        Attributes attacker = mAttributesWrapper.get(entityId).attrs;
        Attributes defender = mAttributesWrapper.get(targetId).attrs;
        int[] kickDamage = AssassinSkills.calculateDragonTailKickDamage(
            skill, level, attacker, equippedBoots(entityId));
        int attackRating = AssassinSkills.dragonTailAttackRating(skill, level, attacker);
        CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
            attacker, defender, isPlayerEntity(entityId), isPlayerEntity(targetId),
            kickDamage[0], kickDamage[1], attackRating,
            stateList(entityId), stateList(targetId), isEntityMoving(targetId));
        if (!combat.hit || combat.blocked) {
          log.info("[ASSASSIN_DRAGON_TAIL] phase=start_reject entity={} target={} reason={} chance={} ",
              entityId, targetId, combat.blocked ? "blocked" : "miss", combat.hitChance);
          mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        casting.dragonTailCombat = combat;
        casting.dragonTailTargetId = targetId;
        casting.dragonTailPrepared = true;
        log.info("[ASSASSIN_DRAGON_TAIL] phase=start entity={} target={} skillLevel={} "
                + "damageRange={}..{} rolledPhysical={} attackRating={} firePercent={} radius={}",
            entityId, targetId, level, kickDamage[0], kickDamage[1], combat.physicalDamage,
            attackRating, AssassinSkills.getDragonTailFirePercent(skill, level),
            AssassinSkills.getDragonTailRadius(skill, level));
        break;
      }
      case 28: { // SKILLS_SrvSt28_BladeShield
        Casting casting = mCasting.get(entityId);
        Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
        if (casting == null || skill == null || !mUnitStates.has(entityId)) {
          log.info("[ASSASSIN_BLADE_SHIELD] phase=start_reject entity={} reason=missing_state_or_skill",
              entityId);
          if (mCasting.has(entityId)) mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        UnitStates states = mUnitStates.get(entityId);
        if (states.stateList == null) states.init(entityId);
        int level = Math.max(1, skillLevel(entityId, casting.skillId));
        UnitState bladeShield = AssassinSkills.applyBladeShieldState(
            states.stateList, skill, level, entityId);
        if (bladeShield == null) {
          log.info("[ASSASSIN_BLADE_SHIELD] phase=start_reject entity={} skill={} reason=native_validation",
              entityId, casting.skillId);
          mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        int[] damage = AssassinSkills.bladeShieldDamageRange(skill, level);
        log.info("[ASSASSIN_BLADE_SHIELD] phase=start entity={} skill={} level={} duration={} "
                + "delay={} range={} damage={}..{} srcDam={}",
            entityId, skill.Id, level, bladeShield.duration,
            bladeShield.periodicDelayFrames, AssassinSkills.bladeShieldRange(skill, level),
            damage[0], damage[1], skill.SrcDam);
        break;
      }
      case 33: // Find Potion / Grim Ward corpse eligibility is authoritative in ServerSkillSystem
      case 34: // Find Item corpse eligibility is authoritative in ServerSkillSystem
        log.debug("[BARBARIAN_CORPSE] phase=start entity={} target={} srvStFunc={}",
            entityId, targetId, srvstfunc);
        break;
      case 42: // native Fire Hit pre-hit setup; resolved authoritatively at the keyframe
        log.info("[MONSTER_SKILL] phase=fire_hit_start entity={} target={} mode=S1",
            entityId, targetId);
        break;
      case 56: { // SKILLS_SrvSt56_FeralRage_Maul
        prepareFeralMaul(entityId, targetId);
        break;
      }
      case 57: { // SKILLS_SrvSt57_Rabies
        prepareDruidElementalMelee(entityId, targetId, true);
        break;
      }
      case 58: { // SKILLS_SrvSt58_FireClaws
        prepareDruidElementalMelee(entityId, targetId, false);
        break;
      }
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
      case 61: { // SKILLS_SrvSt61_SelfResurrect
        boolean restored = mMonster.has(entityId) && factory != null
            && factory.selfResurrectMonster(entityId);
        log.info("[MONSTER_SELF_RESURRECT] phase=skill_start entity={} restored={}",
            entityId, restored);
        break;
      }
      case 51: { // SKILLS_SrvSt51_Submerge
        boolean submerged = mMonster.has(entityId) && factory != null
            && factory.submergeMonster(entityId);
        log.info("[MONSTER_SUBMERGE] phase=skill_start entity={} applied={}",
            entityId, submerged);
        break;
      }
      case 52: { // SKILLS_SrvSt52_Emerge
        boolean emerged = mMonster.has(entityId) && factory != null
            && factory.emergeMonster(entityId);
        log.info("[MONSTER_EMERGE] phase=skill_start entity={} applied={}",
            entityId, emerged);
        break;
      }
      case 31: // Charge: reserve the target path; damage is applied at keyframe
        if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)) {
          pathfinder.findPath(entityId, mPosition.get(targetId).position, true, targetId);
        }
        log.info("[MONSTER_CHARGE] phase=start entity={} target={}", entityId, targetId);
        break;
      case 37: { // Zeal/Fury/BloodLordFrenzy shared start function
        Casting casting = mCasting.get(entityId);
        Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
        if (skill == null || skill.srvdofunc != 109) break;
        int resolvedTarget = targetId != Engine.INVALID_ENTITY
            ? targetId : findNextFrenzyTarget(entityId, Engine.INVALID_ENTITY);
        if (resolvedTarget == Engine.INVALID_ENTITY) {
          log.info("[FRENZY] phase=start_reject entity={} skill={} reason=no_target",
              entityId, casting.skillId);
          mCasting.remove(entityId);
          if (mSequence.has(entityId)) mSequence.remove(entityId);
          break;
        }
        casting.targetId = resolvedTarget;
        if (mPosition.has(resolvedTarget)) {
          casting.targetVec.set(mPosition.get(resolvedTarget).position);
        }
        log.info("[FRENZY] phase=bloodlord_start entity={} skill={} target={}",
            entityId, casting.skillId, resolvedTarget);
        break;
      }
      case 38: { // SKILLS_SrvSt38_Whirlwind
        // A network client renders the D2GS-owned runtime and must not start
        // a second movement or damage loop from its local cast prediction.
        if (world.getSystem(WhirlwindSystem.class) == null) break;
        startWhirlwind(entityId, targetId, targetVec);
        break;
      }
      case 39: { // SKILLS_SrvSt39_Berserk
        startBerserk(entityId, targetId);
        break;
      }
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
      case 120: { // SKILLS_SrvDo120_FeralRage_Maul
        resolveFeralMaul(entityId, targetId);
        break;
      }
      case 121: { // SKILLS_SrvDo121_Rabies
        resolveRabies(entityId, targetId);
        break;
      }
      case 1: // attack
      case 7: // native Jab: same authoritative hit path, skill-specific animation
      case 11: // native Charged Strike: melee hit plus bolts from ServerSkillSystem
      case 14: // native Lightning Strike: melee hit plus chain from ServerSkillSystem
      case 34: // Assassin physical/leech charge-up strikes
      case 35: // Assassin elemental charge-up strikes
      case 42: // Dragon Talon finisher
      case 46: // Dragon Claw finisher
      case 50: // Dragon Tail finisher
      case 52: // Dragon Flight finisher hit
      case 2: // Berserk and other native SrvDo002 melee skills
      case 9: // player Frenzy
      case 109: { // monster Frenzy / BloodLordFrenzy
        if (srvdofunc == 7) {
          log.info("[MONSTER_SKILL] phase=jab entity={} target={} using=melee_hit_pipeline",
              entityId, targetId);
        }
        Casting activeCasting = mCasting.get(entityId);
        Skills.Entry activeSkill = activeCasting != null
            ? Riiablo.files.skills.get(activeCasting.skillId) : null;
        int activeSkillLevel = activeCasting != null
            ? Math.max(1, skillLevel(entityId, activeCasting.skillId)) : 1;
        boolean frenzyAttack = srvdofunc == 9 || srvdofunc == 109;
        int frenzyStrike = -1;
        Item frenzyWeapon = null;
        boolean dragonTalon = srvdofunc == 42;
        boolean dragonTalonLastKick = false;
        boolean dragonClaw = srvdofunc == 46;
        int dragonClawStrike = -1;
        Item dragonClawWeapon = null;
        boolean dragonTail = srvdofunc == 50;
        boolean dragonFlight = srvdofunc == 52;
        boolean berserk = activeSkill != null && activeSkill.srvstfunc == 39
            && activeSkill.srvdofunc == 2;
        boolean fireClaws = activeSkill != null && DruidSkills.isFireClaws(activeSkill);
        Item berserkWeapon = null;
        CombatSystem.CombatResult dragonTailCombat = dragonTail && activeCasting != null
            && activeCasting.dragonTailPrepared
            && activeCasting.dragonTailTargetId == targetId
            ? activeCasting.dragonTailCombat : null;
        if (frenzyAttack) {
          if (activeCasting == null || activeSkill == null) break;
          if (!activeCasting.frenzyInitialized) {
            if (srvdofunc == 9 && !hasTwoFrenzyWeapons(entityId)) {
              log.info("[FRENZY] phase=reject entity={} skill={} reason=requires_two_melee_weapons",
                  entityId, activeCasting.skillId);
              mCasting.remove(entityId);
              if (mSequence.has(entityId)) mSequence.remove(entityId);
              break;
            }
            activeCasting.frenzyInitialized = true;
            activeCasting.frenzyStrikeIndex = 0;
            activeCasting.frenzyOriginalTargetId = targetId;
          }
          FrenzyRuntime runtime = mFrenzyRuntime.create(entityId);
          if (runtime.skillId != activeCasting.skillId) {
            runtime.set(activeCasting.skillId, false);
          }
          if (runtime.previousStrikeHit && mUnitStates.has(entityId)) {
            UnitStates states = mUnitStates.get(entityId);
            if (states.stateList == null) states.init(entityId);
            UnitState state = BarbarianSkills.applyFrenzyState(
                states.stateList, activeSkill, activeSkillLevel, entityId);
            log.info("[FRENZY] phase=apply_previous source={} skill={} state={} level={} "
                    + "stacks={} velocityPercent={} animationRatePercent={} duration={}",
                entityId, activeCasting.skillId,
                state != null ? StateId.getName(state.stateId) : "none", activeSkillLevel,
                state != null ? state.runtimeValue : 0,
                state != null ? state.velocityModifier : 0,
                state != null ? state.animationRateModifier : 0,
                state != null ? state.duration : 0);
          }
          frenzyStrike = activeCasting.frenzyStrikeIndex++;
          if ((frenzyStrike & 1) != 0) {
            targetId = findNextFrenzyTarget(
                entityId, activeCasting.frenzyOriginalTargetId);
          } else {
            targetId = activeCasting.frenzyOriginalTargetId;
          }
          frenzyWeapon = frenzyWeapon(entityId, frenzyStrike);
          runtime.previousStrikeHit = false;
          log.info("[FRENZY] phase=strike_start source={} skill={} index={} hand={} "
                  + "originalTarget={} resolvedTarget={} weapon={}",
              entityId, activeCasting.skillId, frenzyStrike + 1,
              (frenzyStrike & 1) == 0 ? "right" : "left",
              activeCasting.frenzyOriginalTargetId, targetId,
              frenzyWeapon != null ? frenzyWeapon.code : "monster_profile");
        }
        if (dragonFlight) {
          if (activeCasting == null || activeSkill == null
              || !activeCasting.dragonFlightInitialized) {
            log.info("[ASSASSIN_DRAGON_FLIGHT] phase=reject entity={} target={} reason=not_initialized",
                entityId, targetId);
            break;
          }
          if (!activeCasting.dragonFlightWarped) {
            if (resolveDragonFlightWarp(entityId, targetId)) {
              activeCasting.dragonFlightWarped = true;
            } else {
              log.info("[ASSASSIN_DRAGON_FLIGHT] phase=warp_reject entity={} target={}",
                  entityId, targetId);
              mCasting.remove(entityId);
              if (mSequence.has(entityId)) mSequence.remove(entityId);
            }
            break;
          }
          if (activeCasting.dragonFlightKickProcessed) break;
          activeCasting.dragonFlightKickProcessed = true;
        }
        if (dragonTalon) {
          if (activeCasting == null || activeSkill == null) break;
          if (!activeCasting.dragonTalonInitialized) {
            activeCasting.dragonTalonRemainingKicks =
                AssassinSkills.getDragonTalonKickCount(activeSkill, activeSkillLevel);
            activeCasting.dragonTalonInitialized = true;
          }
          if (activeCasting.dragonTalonRemainingKicks <= 0) break;
          activeCasting.dragonTalonKickProcessed = true;
          activeCasting.dragonTalonRemainingKicks--;
          dragonTalonLastKick = activeCasting.dragonTalonRemainingKicks == 0;
        }
        if (dragonClaw) {
          if (activeCasting == null || activeSkill == null) break;
          if (!activeCasting.dragonClawInitialized) {
            // Legacy/synthetic callers may dispatch the keyframe without
            // SrvSt25 or inventory data. Preserve one generic finisher hit.
            activeCasting.dragonClawRemainingStrikes = 1;
            activeCasting.dragonClawStrikeIndex = 0;
            activeCasting.dragonClawInitialized = true;
          }
          if (activeCasting.dragonClawRemainingStrikes <= 0) break;
          dragonClawStrike = activeCasting.dragonClawStrikeIndex++;
          activeCasting.dragonClawStrikeProcessed = true;
          activeCasting.dragonClawRemainingStrikes--;
          dragonClawWeapon = dragonClawWeapon(entityId, dragonClawStrike);
        }
        if (fireClaws) {
          resolveFireClaws(entityId, targetId);
          break;
        }
        if (targetId == Engine.INVALID_ENTITY) break;
        if (!mAttributesWrapper.has(targetId)) break;
        // Player components are authoritative for PvP identity.  Some native
        // monster tests intentionally omit the presentation Class component,
        // so using isPlayerEntity() here would misclassify a valid player
        // target and block an otherwise normal monster attack.
        boolean attackerPlayerUnit = mPlayer.has(entityId) || mMercenary.has(entityId)
            || mSummonedPet.has(entityId);
        boolean targetPlayerUnit = mPlayer.has(targetId) || mMercenary.has(targetId)
            || mSummonedPet.has(targetId);
        if (!PvpCombatRules.canDamage(
            partyManager, entityId, targetId, attackerPlayerUnit, targetPlayerUnit)) {
          log.info("[COMBAT_RELATION] phase=reject source={} target={} "
                  + "sourcePlayerAligned={} targetPlayerAligned={}",
              entityId, targetId, attackerPlayerUnit, targetPlayerUnit);
          break;
        }
        boolean attackerPlayer = isPlayerEntity(entityId);
        boolean targetPlayer = isPlayerEntity(targetId);
        log.debug("{} attack {}", entityId, targetId);

        if (mCasting.has(entityId)
            && ((srvdofunc == 1 && mCasting.get(entityId).skillId == SkillCodes.attack)
                || srvdofunc == 2 || srvdofunc == 9 || srvdofunc == 11 || srvdofunc == 14
                || srvdofunc == 34 || srvdofunc == 35
                || AssassinSkills.isFinishingMove(srvdofunc)
                || srvdofunc == 109)) {
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
          if (!rangedMonsterAttack && dragonTailCombat == null && !dragonFlight
              && !isInMeleeRange(entityId, targetId, bonus)) {
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
        CombatSystem.CombatResult combat;
        if (berserk) {
          berserkWeapon = whirlwindPrimaryWeapon(entityId);
          int[] weaponDamage = BarbarianSkills.calculateBerserkWeaponDamage(
              activeSkill, activeSkillLevel, attackerAttrs, berserkWeapon,
              name -> baseSkillLevel(entityId, name), stateList(entityId));
          int attackRating = BarbarianSkills.getWeaponMasteryAttackRating(
              activeSkill, activeSkillLevel, attackerAttrs, attackerPlayer,
              berserkWeapon, stateList(entityId));
          int conversion = BarbarianSkills.getBerserkMagicConversion(
              activeSkill, activeSkillLevel, name -> baseSkillLevel(entityId, name));
          combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
              attackerAttrs, attrs, attackerPlayer, targetPlayer,
              weaponDamage[0], weaponDamage[1], attackRating,
              conversion, CombatSystem.DAMAGE_MAGIC,
              stateList(entityId), stateList(targetId), isEntityMoving(targetId),
              weaponMastery(entityId, berserkWeapon, false));
          log.info("[BERSERK] phase=roll source={} target={} skill={} weapon={} "
                  + "damageRange={}..{} enhancedPercent={} conversion={} chance={}",
              entityId, targetId, activeCasting != null ? activeCasting.skillId : -1,
              berserkWeapon != null ? berserkWeapon.code : "unarmed", weaponDamage[0], weaponDamage[1],
              BarbarianSkills.calculateBerserkDamageBonus(
                  activeSkill, activeSkillLevel, name -> baseSkillLevel(entityId, name)),
              conversion, combat.hitChance);
        } else if (frenzyAttack && srvdofunc == 9 && frenzyWeapon != null) {
          int[] weaponDamage = BarbarianSkills.calculateFrenzyWeaponDamage(
              activeSkill, activeSkillLevel, attackerAttrs, frenzyWeapon,
              name -> baseSkillLevel(entityId, name), stateList(entityId));
          int attackRating = BarbarianSkills.getWeaponMasteryAttackRating(
              activeSkill, activeSkillLevel, attackerAttrs, true,
              frenzyWeapon, stateList(entityId));
          int conversion = BarbarianSkills.getFrenzyMagicConversion(
              activeSkill, activeSkillLevel, name -> baseSkillLevel(entityId, name));
          combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
              attackerAttrs, attrs, attackerPlayer, targetPlayer,
              weaponDamage[0], weaponDamage[1], attackRating,
              conversion, CombatSystem.DAMAGE_MAGIC,
              stateList(entityId), stateList(targetId), isEntityMoving(targetId),
              weaponMastery(entityId, frenzyWeapon, false));
          log.info("[FRENZY] phase=roll source={} target={} index={} weapon={} "
                  + "damageRange={}..{} enhancedPercent={} attackRating={} conversion={}",
              entityId, targetId, frenzyStrike + 1, frenzyWeapon.code,
              weaponDamage[0], weaponDamage[1],
              BarbarianSkills.calculateFrenzyDamageBonus(
                  activeSkill, activeSkillLevel, name -> baseSkillLevel(entityId, name)),
              attackRating,
              conversion);
        } else if (frenzyAttack) {
          int attackRating = BarbarianSkills.getFrenzyAttackRating(
              activeSkill, activeSkillLevel, attackerAttrs, attackerPlayer);
          combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
              attackerAttrs, attrs, attackerPlayer, targetPlayer,
              monsterAttackMinDamage(entityId), monsterAttackMaxDamage(entityId), attackRating,
              stateList(entityId), stateList(targetId), isEntityMoving(targetId));
        } else if (dragonTail) {
          if (activeCasting == null || activeSkill == null) break;
          if (dragonTailCombat == null) {
            int[] kickDamage = AssassinSkills.calculateDragonTailKickDamage(
                activeSkill, activeSkillLevel, attackerAttrs, equippedBoots(entityId));
            int attackRating = AssassinSkills.dragonTailAttackRating(
                activeSkill, activeSkillLevel, attackerAttrs);
            dragonTailCombat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
                attackerAttrs, attrs, attackerPlayer, targetPlayer,
                kickDamage[0], kickDamage[1], attackRating,
                stateList(entityId), stateList(targetId), isEntityMoving(targetId));
          }
          combat = dragonTailCombat;
          activeCasting.dragonTailCombat = null;
          activeCasting.dragonTailTargetId = Engine.INVALID_ENTITY;
          activeCasting.dragonTailPrepared = false;
          log.info("[ASSASSIN_DRAGON_TAIL] phase=primary entity={} target={} physical={} "
                  + "total={} attackRating={} chance={}",
              entityId, targetId, combat.physicalDamage, combat.totalDamage,
              AssassinSkills.dragonTailAttackRating(activeSkill, activeSkillLevel, attackerAttrs),
              combat.hitChance);
        } else if (dragonTalon) {
          int[] kickDamage = AssassinSkills.calculateDragonTalonKickDamage(
              activeSkill, activeSkillLevel, attackerAttrs, equippedBoots(entityId));
          int attackRating = AssassinSkills.dragonTalonAttackRating(
              activeSkill, activeSkillLevel, attackerAttrs);
          combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
              attackerAttrs, attrs, attackerPlayer, targetPlayer,
              kickDamage[0], kickDamage[1], attackRating,
              stateList(entityId), stateList(targetId), isEntityMoving(targetId));
          log.info("[ASSASSIN_DRAGON_TALON] phase=kick entity={} target={} index={} remaining={} "
                  + "damageRange={}..{} attackRating={} last={}",
              entityId, targetId, activeCasting.dragonTalonSuccessfulKicks + 1,
              activeCasting.dragonTalonRemainingKicks,
              kickDamage[0], kickDamage[1], attackRating, dragonTalonLastKick);
        } else if (dragonFlight) {
          int[] kickDamage = AssassinSkills.calculateDragonFlightKickDamage(
              activeSkill, activeSkillLevel, attackerAttrs, equippedBoots(entityId));
          int attackRating = AssassinSkills.dragonFlightAttackRating(
              activeSkill, activeSkillLevel, attackerAttrs);
          combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
              attackerAttrs, attrs, attackerPlayer, targetPlayer,
              kickDamage[0], kickDamage[1], attackRating,
              stateList(entityId), stateList(targetId), isEntityMoving(targetId));
          log.info("[ASSASSIN_DRAGON_FLIGHT] phase=kick entity={} target={} "
                  + "damageRange={}..{} attackRating={} chance={}",
              entityId, targetId, kickDamage[0], kickDamage[1], attackRating,
              combat.hitChance);
        } else if (dragonClaw && dragonClawWeapon != null) {
          int[] clawDamage = AssassinSkills.calculateDragonClawDamage(
              activeSkill, activeSkillLevel, attackerAttrs, dragonClawWeapon);
          int attackRating = AssassinSkills.dragonClawAttackRating(
              activeSkill, activeSkillLevel, attackerAttrs);
          combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
              attackerAttrs, attrs, attackerPlayer, targetPlayer,
              clawDamage[0], clawDamage[1], attackRating,
              stateList(entityId), stateList(targetId), isEntityMoving(targetId));
          log.info("[ASSASSIN_DRAGON_CLAW] phase=strike entity={} target={} index={} hand={} "
                  + "weapon={} damageRange={}..{} attackRating={} remaining={}",
              entityId, targetId, dragonClawStrike + 1,
              dragonClawStrike == 0 ? "right" : "left", dragonClawWeapon.code,
              clawDamage[0], clawDamage[1], attackRating,
              activeCasting.dragonClawRemainingStrikes);
        } else {
          Item attackWeapon = activeAttackWeapon(entityId);
          combat = CombatSystem.INSTANCE.calculateAttack(
              attackerAttrs,
              attrs,
              attackerPlayer,
              targetPlayer,
              false,
              monsterAttackMinDamage(entityId),
              monsterAttackMaxDamage(entityId),
              monsterAttackRating(entityId),
              stateList(entityId), stateList(targetId), isEntityMoving(targetId),
              weaponMastery(entityId, attackWeapon, false));
        }
        if (!combat.hit) {
          log.info("[COMBAT_HIT] entity={} target={} result=miss chance={}% attackerLevel={} targetLevel={} ar={} defense={}",
              entityId, targetId, combat.hitChance,
              statInt(attackerAttrs, Stat.level), statInt(attrs, Stat.level),
              statInt(attackerAttrs, Stat.tohit), statInt(attrs, Stat.armorclass));
          break;
        }
        if (combat.blocked) {
          log.debug("{} melee attack blocked by {}", entityId, targetId);
          queueHitReaction(targetId, true);
          break;
        }

        if (frenzyAttack) {
          mFrenzyRuntime.create(entityId).set(activeCasting.skillId, true);
          if (frenzyWeapon != null) drainFrenzyDurability(frenzyWeapon, targetId);
        }
        if (berserk && berserkWeapon != null) {
          drainFrenzyDurability(berserkWeapon, targetId);
        }

        AssassinSkills.ProgressiveRelease progressiveRelease = null;
        if (AssassinSkills.isFinishingMove(srvdofunc) && mUnitStates.has(entityId)
            && (!dragonTalon || !activeCasting.dragonTalonProgressiveReleased)
            && (!dragonClaw || !activeCasting.dragonClawProgressiveReleased)) {
          UnitStates unitStates = mUnitStates.get(entityId);
          if (unitStates.stateList != null) {
            progressiveRelease = AssassinSkills.resolveProgressiveRelease(
                unitStates.stateList, id -> Riiablo.files.skills.get(id),
                id -> skillLevel(entityId, id));
            if (progressiveRelease.hasEffects()) {
              applyAssassinProgressiveDamage(progressiveRelease, combat, attrs,
                  stateList(targetId));
              int consumed = AssassinSkills.consumeProgressiveCharges(unitStates.stateList);
              log.info("[ASSASSIN_FINISHER] phase=release source={} target={} srvDoFunc={} "
                      + "charges={} consumed={} tigerPct={} lifeLeechPct={} manaLeechPct={} "
                      + "fire={} totalDamage={}",
                  entityId, targetId, srvdofunc, progressiveRelease.totalCharges, consumed,
                  progressiveRelease.tigerDamagePercent,
                  progressiveRelease.lifeLeechPercent,
                  progressiveRelease.manaLeechPercent,
                  combat.elementalDamage[CombatSystem.DAMAGE_FIRE], combat.totalDamage);
            }
          }
        }
        if (dragonTalon) activeCasting.dragonTalonProgressiveReleased = true;
        if (dragonClaw) activeCasting.dragonClawProgressiveReleased = true;
        log.info("[COMBAT_HIT] entity={} target={} result=hit damage={} chance={}% critical={} deadly={} crushing={}",
            entityId, targetId, combat.totalDamage, combat.hitChance,
            combat.critical, combat.deadlyStrike, combat.crushingBlow);
        if (dragonTalon) activeCasting.dragonTalonSuccessfulKicks++;
        if (dragonTalon) drainDragonTalonDurability(entityId, targetId);
        if (dragonFlight) drainDragonTalonDurability(entityId, targetId);
        if (dragonClaw) drainDragonClawDurability(dragonClawWeapon, targetId);
        if (dragonTail) drainDragonTalonDurability(entityId, targetId);

        // D2MOO SrvDo034/SrvDo035 adds a progressive state only after the
        // shared combat record reports a successful, unblocked hit. The
        // state stores its originating skill and caps the charge stat at 3.
        if (AssassinSkills.isProgressiveStrike(srvdofunc) && mCasting.has(entityId)
            && mUnitStates.has(entityId)) {
          Casting casting = mCasting.get(entityId);
          Skills.Entry chargeSkill = Riiablo.files.skills.get(casting.skillId);
          UnitStates unitStates = mUnitStates.get(entityId);
          if (unitStates.stateList == null) unitStates.init(entityId);
          UnitState charge = AssassinSkills.addProgressiveCharge(unitStates.stateList,
              chargeSkill, Math.max(1, skillLevel(entityId, casting.skillId)), entityId);
          if (charge == null) {
            log.warn("[ASSASSIN_CHARGE] phase=reject source={} target={} skill={} reason=unresolved_state",
                entityId, targetId, casting.skillId);
          } else {
            log.info("[ASSASSIN_CHARGE] phase=hit source={} target={} skill={} state={} charges={}",
                entityId, targetId, casting.skillId, StateId.getName(charge.stateId),
                AssassinSkills.progressiveCharges(charge));
          }
        }

        float damage = combat.totalDamage;
        if (log.debugEnabled()) {
          log.debug("{} calculated damage: {} on {}", entityId, damage, targetId);
        }
        if (damage <= 0) {
          log.debug("{} melee hit on {} caused no damage", entityId, targetId);
          if (progressiveRelease != null && progressiveRelease.hasEffects()) {
            applyAssassinProgressiveStageEffects(
                entityId, targetId, progressiveRelease, attackerAttrs);
          }
          // SrvDo050 still emits the Dragon Tail explosion presentation when
          // the primary target nullifies all physical damage; its fire amount
          // is simply zero in that case.
          if (dragonTail && isAlive(entityId)) {
            applyDragonTailExplosion(
                entityId, targetId, activeSkill, activeSkillLevel,
                attackerAttrs, combat.physicalDamage);
          }
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
        if (hpAfter > 0f) queueHitReaction(targetId, false);

        if (progressiveRelease != null && progressiveRelease.hasEffects()) {
          applyAssassinProgressiveLeech(entityId, progressiveRelease, combat, appliedDamage);
          applyAssassinProgressiveStageEffects(
              entityId, targetId, progressiveRelease, attackerAttrs);
        }

        if (dragonTail && isAlive(entityId)) {
          applyDragonTailExplosion(
              entityId, targetId, activeSkill, activeSkillLevel,
              attackerAttrs, combat.physicalDamage);
        }

        applyCombatStates(entityId, targetId, combat);
        if (progressiveRelease != null
            && progressiveRelease.coldFreezeDuration > 0
            && combat.elementalDamage[CombatSystem.DAMAGE_COLD] > 0
            && mUnitStates.has(targetId)) {
          StatusEffectApplier.INSTANCE.applyFreeze(
              targetId, progressiveRelease.coldFreezeDuration, entityId);
          log.info("[ASSASSIN_BLADES] phase=primary_freeze source={} target={} duration={}",
              entityId, targetId, progressiveRelease.coldFreezeDuration);
        }

        if (hitpoints.asFixed() <= 0f) {
          log.debug("{} is dead!", targetId);
          events.dispatch(DeathEvent.obtain(entityId, targetId));
        } else if (dragonTalonLastKick
            && shouldDragonTalonKnockback(activeSkill, activeSkillLevel, targetId)) {
          applyDragonTalonKnockback(entityId, targetId);
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
      case 18: // DefensiveBuff; Venom state is applied by ServerSkillSystem
      case 22: // Nova/radial missile skill
      case 54: // Blade Shield periodic pulse is applied by StateUpdater
      case 76: // Whirlwind periodic hits are applied by WhirlwindSystem
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
      case 116: { // SKILLS_SrvDo116_Wearwolf_Wearbear
        Casting casting = mCasting.get(entityId);
        Skills.Entry shapeSkill = casting != null
            ? Riiablo.files.skills.get(casting.skillId) : null;
        if (shapeSkill == null || !mUnitStates.has(entityId)) {
          log.info("[DRUID_SHAPE] phase=reject entity={} skill={} reason=missing_skill_or_state",
              entityId, casting != null ? casting.skillId : -1);
          break;
        }
        UnitStates unitStates = mUnitStates.get(entityId);
        if (unitStates.stateList == null) unitStates.init(entityId);
        int level = Math.max(1, skillLevel(entityId, shapeSkill.Id));
        DruidSkills.ShapeShiftResult result = DruidSkills.applyShapeShiftState(
            unitStates.stateList, shapeSkill, level, entityId,
            name -> baseSkillLevel(entityId, name),
            name -> Riiablo.files.skills.get(name));
        UnitState state = result.appliedState;
        log.info("[DRUID_SHAPE] phase={} entity={} skill={} level={} state={} removed={} "
                + "duration={} damage={} defense={} attackRating={} animationRate={} "
                + "maxLife={} maxStamina={}",
            result.transformed() ? "apply" : result.removedStateId != StateId.NONE
                ? "remove" : "reject",
            entityId, shapeSkill.skill, level,
            state != null ? StateId.getName(state.stateId) : "none",
            StateId.getName(result.removedStateId), state != null ? state.duration : 0,
            state != null ? state.damageModifier : 0,
            state != null ? state.defenseModifier : 0,
            state != null ? state.attackModifier : 0,
            state != null ? state.animationRateModifier : 0,
            state != null ? state.maxLifeModifier : 0,
            state != null ? state.maxStaminaModifier : 0);
        break;
      }
      default:
        log.warn("Unsupported srvdofunc({}) for {}", srvdofunc, entityId);
        // TODO: default case will log an error when all valid cases are enumerated
        //log.error("Invalid srvdofunc({}) for {}", srvdofunc, entityId);
    }
  }

  /** Native SrvSt56: resolve hit once and retain the combat record for SrvDo120. */
  private void prepareFeralMaul(int entityId, int targetId) {
    Casting casting = mCasting.get(entityId);
    Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
    if (casting == null || !DruidSkills.isFeralRageOrMaul(skill)
        || !mUnitStates.has(entityId) || targetId == Engine.INVALID_ENTITY
        || !mAttributesWrapper.has(entityId) || !mAttributesWrapper.has(targetId)
        || !isAlive(entityId) || !isAlive(targetId)) {
      log.info("[DRUID_FERAL_MAUL] phase=start_reject source={} target={} reason=invalid_context",
          entityId, targetId);
      return;
    }
    UnitStates states = mUnitStates.get(entityId);
    if (states.stateList == null) states.init(entityId);
    if (!DruidSkills.isSkillAllowedInCurrentShape(skill, states.stateList)) {
      log.info("[DRUID_FERAL_MAUL] phase=start_reject source={} skill={} reason=shape_restriction",
          entityId, skill.skill);
      return;
    }
    if (!isInMeleeRange(entityId, targetId, isPlayerEntity(entityId) ? 3 : 0)) {
      log.info("[DRUID_FERAL_MAUL] phase=start_reject source={} target={} reason=out_of_range",
          entityId, targetId);
      return;
    }
    boolean attackerPlayer = isPlayerEntity(entityId);
    boolean targetPlayer = isPlayerEntity(targetId);
    boolean sourceAligned = mPlayer.has(entityId) || mMercenary.has(entityId)
        || mSummonedPet.has(entityId);
    boolean targetAligned = mPlayer.has(targetId) || mMercenary.has(targetId)
        || mSummonedPet.has(targetId);
    if (!PvpCombatRules.canDamage(partyManager, entityId, targetId, sourceAligned, targetAligned)) {
      log.info("[DRUID_FERAL_MAUL] phase=start_reject source={} target={} reason=relation",
          entityId, targetId);
      return;
    }
    Attributes attacker = mAttributesWrapper.get(entityId).attrs;
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    int level = Math.max(1, skillLevel(entityId, skill.Id));
    Item weapon = activeAttackWeapon(entityId);
    int[] damage = DruidSkills.calculateFeralMaulWeaponDamage(
        skill, level, attacker, weapon, states.stateList);
    int attackRating = DruidSkills.getFeralMaulAttackRating(
        skill, level, attacker, attackerPlayer);
    casting.feralMaulCombat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
        attacker, defender, attackerPlayer, targetPlayer,
        damage[0], damage[1], attackRating,
        states.stateList, stateList(targetId), isEntityMoving(targetId),
        weaponMastery(entityId, weapon, false));
    casting.feralMaulTargetId = targetId;
    // The SrvSt56 combat record sees the old Maul stat-list. The stack gained
    // by SrvDo120 must not stun or enhance the strike that created it.
    casting.feralMaulStunFrames = states.stateList.getTotalStunLength();
    casting.feralMaulPrepared = true;
    log.info("[DRUID_FERAL_MAUL] phase=start source={} target={} skill={} level={} weapon={} "
            + "damage={}..{} attackRating={} chance={} hit={} blocked={}",
        entityId, targetId, skill.skill, level, weapon != null ? weapon.code : "unarmed",
        damage[0], damage[1], attackRating, casting.feralMaulCombat.hitChance,
        casting.feralMaulCombat.hit, casting.feralMaulCombat.blocked);
  }

  /** Native SrvDo120: consume the SrvSt56 result, then build/refresh stacks. */
  private void resolveFeralMaul(int entityId, int targetId) {
    Casting casting = mCasting.get(entityId);
    if (casting == null || !casting.feralMaulPrepared
        || casting.feralMaulTargetId != targetId
        || casting.feralMaulCombat == null) {
      log.info("[DRUID_FERAL_MAUL] phase=keyframe_reject source={} target={} reason=not_prepared",
          entityId, targetId);
      return;
    }
    CombatSystem.CombatResult combat = casting.feralMaulCombat;
    int stunFrames = casting.feralMaulStunFrames;
    casting.feralMaulPrepared = false;
    casting.feralMaulCombat = null;
    casting.feralMaulTargetId = Engine.INVALID_ENTITY;
    casting.feralMaulStunFrames = 0;
    // Native SrvDo120 calls SUNITDMG_DrainItemDurability before Param1.
    Item weapon = activeAttackWeapon(entityId);
    if (weapon != null) drainFrenzyDurability(weapon, targetId);
    if (!combat.hit || combat.blocked || !mAttributesWrapper.has(targetId)) {
      log.info("[DRUID_FERAL_MAUL] phase=keyframe source={} target={} result={} blocked={}",
          entityId, targetId, combat.hit ? "blocked" : "miss", combat.blocked);
      if (combat.blocked) queueHitReaction(targetId, true);
      return;
    }
    UnitStates states = mUnitStates.get(entityId);
    if (states == null) return;
    if (states.stateList == null) states.init(entityId);
    Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
    int level = Math.max(1, skillLevel(entityId, casting.skillId));
    UnitState feralState = DruidSkills.applyFeralMaulState(
        states.stateList, skill, level, entityId);
    if (feralState == null) {
      log.warn("[DRUID_FERAL_MAUL] phase=state_reject source={} skill={} reason=invalid_shape_or_formula",
          entityId, skill != null ? skill.skill : "none");
    }
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    StatRef hitpoints = defender.get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints == null || hitpoints.asFixed() <= 0f) return;
    float before = hitpoints.asFixed();
    DamageEvent event = DamageEvent.obtain(entityId, targetId, Math.max(0, combat.totalDamage));
    events.dispatch(event);
    float applied = Math.max(0f, event.damage);
    hitpoints.sub(applied);
    if (hitpoints.asFixed() < 0f) hitpoints.set(0f);
    float lifeStolen = combat.totalDamage > 0
        ? combat.lifeStolen * applied / combat.totalDamage : 0f;
    if (lifeStolen > 0 && mAttributesWrapper.has(entityId)) {
      restoreUpToMaximum(mAttributesWrapper.get(entityId).attrs, Stat.hitpoints,
          Stat.maxhp, lifeStolen);
    }
    if (stunFrames > 0) {
      StatusEffectApplier.INSTANCE.applyStun(targetId, stunFrames);
    }
    applyCombatStates(entityId, targetId, combat);
    if (hitpoints.asFixed() > 0f) queueHitReaction(targetId, false);
    if (hitpoints.asFixed() <= 0f) events.dispatch(DeathEvent.obtain(entityId, targetId));
    log.info("[DRUID_FERAL_MAUL] phase=keyframe source={} target={} skill={} "
            + "result=hit damage={} hp={} -> {} stacks={} state={} leech={} stun={}",
        entityId, targetId, skill != null ? skill.skill : "none", applied, before,
        hitpoints.asFixed(), feralState != null ? feralState.runtimeValue : 0,
        feralState != null ? StateId.getName(feralState.stateId) : "none",
        lifeStolen, stunFrames);
  }

  /** Native SrvSt57/SrvSt58: validate shape/range and retain one hit roll. */
  private void prepareDruidElementalMelee(int entityId, int targetId, boolean rabies) {
    Casting casting = mCasting.get(entityId);
    Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
    boolean matching = rabies ? DruidSkills.isRabies(skill) : DruidSkills.isFireClaws(skill);
    if (casting == null || !matching || targetId == Engine.INVALID_ENTITY
        || !mAttributesWrapper.has(entityId) || !mAttributesWrapper.has(targetId)
        || !mUnitStates.has(entityId) || !isAlive(entityId) || !isAlive(targetId)
        || !isInMeleeRange(entityId, targetId, isPlayerEntity(entityId) ? 3 : 0)) {
      log.info("[DRUID_{}] phase=start_reject source={} target={} reason=invalid_context",
          rabies ? "RABIES" : "FIRE_CLAWS", entityId, targetId);
      return;
    }
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) unitStates.init(entityId);
    if (!DruidSkills.isSkillAllowedInCurrentShape(skill, unitStates.stateList)) {
      log.info("[DRUID_{}] phase=start_reject source={} target={} reason=shape_restriction",
          rabies ? "RABIES" : "FIRE_CLAWS", entityId, targetId);
      return;
    }
    boolean sourceAligned = mPlayer.has(entityId) || mMercenary.has(entityId)
        || mSummonedPet.has(entityId);
    boolean targetAligned = mPlayer.has(targetId) || mMercenary.has(targetId)
        || mSummonedPet.has(targetId);
    if (!PvpCombatRules.canDamage(partyManager, entityId, targetId,
        sourceAligned, targetAligned)) return;

    int level = Math.max(1, skillLevel(entityId, skill.Id));
    Attributes attacker = mAttributesWrapper.get(entityId).attrs;
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    Item weapon = activeAttackWeapon(entityId);
    int[] physical = DruidSkills.calculateShapeWeaponDamage(
        skill, level, attacker, weapon, unitStates.stateList);
    int[] elemental = rabies
        ? DruidSkills.getRabiesPoisonDamage(
            skill, level, name -> baseSkillLevel(entityId, name))
        : DruidSkills.getFireClawsFireDamage(
            skill, level, name -> baseSkillLevel(entityId, name));
    int[] elementalMin = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int[] elementalMax = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    int type = rabies ? CombatSystem.DAMAGE_POISON : CombatSystem.DAMAGE_FIRE;
    if (!rabies) {
      elementalMin[type] = elemental[0];
      elementalMax[type] = elemental[1];
    }
    int duration = rabies ? DruidSkills.getRabiesPoisonDuration(
        skill, level, name -> baseSkillLevel(entityId, name)) : 0;
    CombatSystem.CombatResult combat = CombatSystem.INSTANCE
        .calculatePrecomputedMeleeElementalAttack(
            attacker, defender, isPlayerEntity(entityId), isPlayerEntity(targetId),
            physical[0], physical[1], DruidSkills.getShapeAttackRating(
                skill, level, attacker, isPlayerEntity(entityId)),
            elementalMin, elementalMax, 0, duration,
            unitStates.stateList, stateList(targetId), isEntityMoving(targetId));
    if (rabies && combat.hit && !combat.blocked) {
      int rawFixed = MathUtils.random(elemental[0], elemental[1]);
      int resistedFixed = resistedDamage(rawFixed, defender, stateList(targetId),
          Stat.poisonresist, 3);
      combat.poisonDamagePerFrame = resistedFixed / 256f
          + combat.elementalDamage[CombatSystem.DAMAGE_POISON];
    }
    if (rabies) {
      casting.rabiesCombat = combat;
      casting.rabiesTargetId = targetId;
      casting.rabiesPrepared = combat.hit && !combat.blocked;
    } else {
      casting.fireClawsCombat = combat;
      casting.fireClawsTargetId = targetId;
      casting.fireClawsPrepared = true;
    }
    log.info("[DRUID_{}] phase=start source={} target={} level={} physical={}..{} "
            + "element={}..{} duration={} chance={} hit={} blocked={}",
        rabies ? "RABIES" : "FIRE_CLAWS", entityId, targetId, level,
        physical[0], physical[1], elemental[0], elemental[1], duration,
        combat.hitChance, combat.hit, combat.blocked);
  }

  private void resolveRabies(int entityId, int targetId) {
    Casting casting = mCasting.get(entityId);
    if (casting == null || !casting.rabiesPrepared || casting.rabiesTargetId != targetId
        || casting.rabiesCombat == null) {
      log.info("[DRUID_RABIES] phase=keyframe_reject source={} target={} reason=not_prepared",
          entityId, targetId);
      return;
    }
    CombatSystem.CombatResult combat = casting.rabiesCombat;
    casting.rabiesCombat = null;
    casting.rabiesTargetId = Engine.INVALID_ENTITY;
    casting.rabiesPrepared = false;
    Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
    resolveDruidElementalMeleeDamage(entityId, targetId, combat, "RABIES");
    if (!mUnitStates.has(targetId) || !isAlive(targetId)) return;
    UnitStates targetStates = mUnitStates.get(targetId);
    if (targetStates.stateList == null) targetStates.init(targetId);
    if (!targetStates.stateList.hasState(StateId.RABIES)) {
      UnitState infected = targetStates.stateList.addState(
          StateId.RABIES, Math.max(10, combat.poisonDuration),
          Math.max(1, skillLevel(entityId, casting.skillId)), entityId);
      if (infected != null) {
        infected.skillId = casting.skillId;
        infected.needsSync = true;
      }
      createRabiesController(entityId, targetId, skill,
          Math.max(1, skillLevel(entityId, casting.skillId)), combat);
    }
  }

  private void resolveFireClaws(int entityId, int targetId) {
    Casting casting = mCasting.get(entityId);
    if (casting == null || !casting.fireClawsPrepared
        || casting.fireClawsTargetId != targetId || casting.fireClawsCombat == null) {
      log.info("[DRUID_FIRE_CLAWS] phase=keyframe_reject source={} target={} reason=not_prepared",
          entityId, targetId);
      return;
    }
    CombatSystem.CombatResult combat = casting.fireClawsCombat;
    casting.fireClawsCombat = null;
    casting.fireClawsTargetId = Engine.INVALID_ENTITY;
    casting.fireClawsPrepared = false;
    resolveDruidElementalMeleeDamage(entityId, targetId, combat, "FIRE_CLAWS");
  }

  private void resolveDruidElementalMeleeDamage(int sourceId, int targetId,
      CombatSystem.CombatResult combat, String tag) {
    if (combat == null || !combat.hit || combat.blocked
        || !mAttributesWrapper.has(targetId)) {
      if (combat != null && combat.blocked) queueHitReaction(targetId, true);
      return;
    }
    Item weapon = activeAttackWeapon(sourceId);
    if (weapon != null) drainFrenzyDurability(weapon, targetId);
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    StatRef hp = defender.get(Stat.hitpoints, StatRef.obtain());
    if (hp == null || hp.asFixed() <= 0f) return;
    float before = hp.asFixed();
    DamageEvent event = DamageEvent.obtain(sourceId, targetId, Math.max(0, combat.totalDamage));
    events.dispatch(event);
    hp.sub(Math.max(0f, event.damage));
    if (hp.asFixed() < 0f) hp.set(0f);
    applyCombatStates(sourceId, targetId, combat);
    if (hp.asFixed() > 0f) queueHitReaction(targetId, false);
    if (hp.asFixed() <= 0f) events.dispatch(DeathEvent.obtain(sourceId, targetId));
    log.info("[DRUID_{}] phase=keyframe source={} target={} physical={} fire={} "
            + "poison={} poisonDuration={} hp={} -> {}",
        tag, sourceId, targetId, combat.physicalDamage,
        combat.elementalDamage[CombatSystem.DAMAGE_FIRE],
        combat.elementalDamage[CombatSystem.DAMAGE_POISON], combat.poisonDuration,
        before, hp.asFixed());
  }

  private void createRabiesController(int sourceId, int infectedId, Skills.Entry skill,
      int level, CombatSystem.CombatResult combat) {
    if (factory == null || skill == null || !mPosition.has(infectedId)
        || skill.srvmissilea == null || skill.srvmissilea.isEmpty()) return;
    Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
    if (row == null) return;
    int missileId = factory.createMissile(
        row, Vector2.X, mPosition.get(infectedId).position, sourceId);
    if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) return;
    com.riiablo.engine.server.component.Missile controller = mMissile.get(missileId);
    controller.skillId = skill.Id;
    controller.damageLevel = level;
    controller.attached = true;
    controller.attachedEntityId = infectedId;
    controller.rabiesController = true;
    controller.rabiesSourceId = sourceId;
    controller.remainingFrames = Math.max(10, combat.poisonDuration);
    controller.rabiesNextPulseFrame = 0;
    controller.damageMultiplier = Math.max(0f, combat.poisonDamagePerFrame);
    log.info("[DRUID_RABIES] phase=controller_create source={} infected={} missileId={} "
            + "missile={} duration={}", sourceId, infectedId, missileId,
        row.Missile, controller.remainingFrames);
  }

  static boolean allowsDeadTarget(Skills.Entry skill) {
    // Native corpse skills deliberately target a completed death unit.  Keep
    // the animation/keyframe alive so ServerSkillSystem can consume the corpse
    // exactly once (SrvDo069/072/075), just like native Resurrect (097).
    return skill != null && (skill.srvdofunc == 97 || skill.srvdofunc == 69
        || skill.srvdofunc == 72 || skill.srvdofunc == 75);
  }

  /** D2MOO SKILLS_SrvSt12 validation for the Dragon Flight table row. */
  private String dragonFlightStartRejection(
      int entityId, int targetId, Skills.Entry skill, int skillLevel) {
    if (targetId == Engine.INVALID_ENTITY || !mPosition.has(entityId)
        || !mPosition.has(targetId) || !isAlive(entityId) || !isAlive(targetId)) {
      return "invalid_target";
    }
    if (!mPlayer.has(targetId) && !mMonster.has(targetId)
        && !mMercenary.has(targetId) && !mSummonedPet.has(targetId)) {
      return "invalid_unit_type";
    }
    if (mNativeUnitFlags.has(targetId)
        && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) {
      return "not_attackable";
    }
    int range = Math.max(0,
        SkillFormula.evaluate(skill.aurarangecalc, skill, Math.max(1, skillLevel)));
    if (mPosition.get(entityId).position.dst2(mPosition.get(targetId).position)
        > (float) range * range) {
      return "out_of_range";
    }
    boolean sourcePlayerAligned = mPlayer.has(entityId) || mMercenary.has(entityId)
        || mSummonedPet.has(entityId);
    boolean targetPlayerAligned = mPlayer.has(targetId) || mMercenary.has(targetId)
        || mSummonedPet.has(targetId);
    if (!PvpCombatRules.canDamage(
        partyManager, entityId, targetId, sourcePlayerAligned, targetPlayerAligned)) {
      return "relation";
    }
    Map.Zone sourceZone = map != null ? map.getZone(mPosition.get(entityId).position) : null;
    Map.Zone targetZone = map != null ? map.getZone(mPosition.get(targetId).position) : null;
    if ((sourceZone != null && sourceZone.isTown())
        || (targetZone != null && targetZone.isTown())) {
      return "town";
    }
    return null;
  }

  /** First SrvDo052 sequence event: find a native free coordinate and warp. */
  private boolean resolveDragonFlightWarp(int entityId, int targetId) {
    if (map == null || !mPosition.has(entityId) || !mPosition.has(targetId)
        || !isAlive(entityId) || !isAlive(targetId)) {
      return false;
    }
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    Map.Zone sourceZone = map.getZone(source);
    Map.Zone targetZone = map.getZone(target);
    if (sourceZone == null || sourceZone.level == null || targetZone == null
        || sourceZone.level.Teleport == 0) {
      log.info("[ASSASSIN_DRAGON_FLIGHT] phase=warp_reject entity={} target={} "
              + "reason=level_teleport sourceLevel={} teleport={}",
          entityId, targetId,
          sourceZone != null && sourceZone.level != null ? sourceZone.level.Id : -1,
          sourceZone != null && sourceZone.level != null ? sourceZone.level.Teleport : -1);
      return false;
    }
    int targetFlags = map.flags(target);
    if (sourceZone.level.Teleport == 2
        && (targetFlags & (DT1.Tile.FLAG_BLOCK_JUMP | DT1.Tile.FLAG_BLOCK_WALK)) != 0) {
      log.info("[ASSASSIN_DRAGON_FLIGHT] phase=warp_reject entity={} target={} "
              + "reason=flying_collision flags={}",
          entityId, targetId, targetFlags);
      return false;
    }
    int unitSize = mSize.has(entityId) ? mSize.get(entityId).size : Size.MEDIUM;
    Vector2 landing = new Vector2();
    // Native sub_6FCBDFE0 searches with mask 0x1C09. Its DT1 portion is wall
    // plus no-player collision; dynamic objects/doors are represented by the
    // map's walk-block references. The mask deliberately excludes unit
    // presence, so the selected enemy itself does not invalidate the search.
    int collisionMask = DT1.Tile.FLAG_BLOCK_WALK | DT1.Tile.FLAG_BLOCK_PLAYER_WALK;
    if (!targetZone.findFreeCoordinates(
        target, unitSize, 50, collisionMask, false, landing)) {
      log.info("[ASSASSIN_DRAGON_FLIGHT] phase=warp_reject entity={} target={} "
              + "reason=no_free_coordinate level={} requested=({}, {}) size={}",
          entityId, targetId, targetZone.level != null ? targetZone.level.Id : -1,
          target.x, target.y, unitSize);
      return false;
    }
    if (!resolveTeleport(entityId, landing)) return false;
    if (mAngle.has(entityId)) {
      mAngle.get(entityId).target.set(target).sub(landing).nor();
    }
    log.info("[ASSASSIN_DRAGON_FLIGHT] phase=warp entity={} target={} "
            + "requested=({}, {}) landing=({}, {}) level={} size={}",
        entityId, targetId, target.x, target.y, landing.x, landing.y,
        targetZone.level != null ? targetZone.level.Id : -1, unitSize);
    return true;
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
      int bonus = mUnitStates.has(entityId)
          && mUnitStates.get(entityId).stateList != null
          ? mUnitStates.get(entityId).stateList.getTotalSkillModifier() : 0;
      return Math.max(1, mPlayer.get(entityId).data.getSkill(skillId) + bonus);
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

  /** Skills.txt .blvl references hard points and deliberately excludes +skills. */
  private int baseSkillLevel(int entityId, String skillName) {
    Skills.Entry skill = skillName == null ? null : Riiablo.files.skills.get(skillName);
    if (skill == null) return 0;
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      return Math.max(0, mPlayer.get(entityId).data.getSkill(skill.Id));
    }
    return Math.max(0, skillLevel(entityId, skill.Id));
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

  private boolean isPlayerEntity(int entityId) {
    return mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR;
  }

  private Armor.Entry equippedBoots(int entityId) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return null;
    Item item = mPlayer.get(entityId).data.getItems().getEquipped(BodyLoc.FEET);
    return item != null && item.base instanceof Armor.Entry
        ? (Armor.Entry) item.base : null;
  }

  private Item equippedClaw(int entityId, BodyLoc bodyLoc) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return null;
    Item item = mPlayer.get(entityId).data.getItems().getEquipped(bodyLoc);
    return item != null && item.type != null && item.type.is(Type.H2H) ? item : null;
  }

  private Item equippedFrenzyWeapon(int entityId, BodyLoc bodyLoc) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return null;
    Item item = mPlayer.get(entityId).data.getItems().getEquipped(bodyLoc);
    if (item == null || !(item.base instanceof Weapons.Entry) || item.type == null) return null;
    return item.type.is(Type.BOW) || item.type.is(Type.XBOW) ? null : item;
  }

  private void startWhirlwind(int entityId, int targetId, Vector2 targetVec) {
    Casting casting = mCasting.get(entityId);
    Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
    if (casting == null || skill == null || !mPosition.has(entityId)
        || !mVelocity.has(entityId) || !isAlive(entityId)) {
      rejectWhirlwind(entityId, "missing_runtime_data");
      return;
    }
    if (targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)
        && isInMeleeRange(entityId, targetId, 0)) {
      rejectWhirlwind(entityId, "target_in_melee_range");
      return;
    }

    Vector2 requested = targetId != Engine.INVALID_ENTITY && mPosition.has(targetId)
        ? mPosition.get(targetId).position : targetVec;
    Vector2 start = mPosition.get(entityId).position;
    Vector2 destination = new Vector2();
    int size = mSize.has(entityId) ? mSize.get(entityId).size : Size.INSIGNIFICANT;
    if (!WhirlwindSystem.resolveDestination(map, start, requested, size, destination)) {
      rejectWhirlwind(entityId, "invalid_straight_path");
      return;
    }

    if (mPathfind.has(entityId)) mPathfind.remove(entityId);
    if (mTarget.has(entityId)) mTarget.remove(entityId);
    int level = Math.max(1, skillLevel(entityId, casting.skillId));
    mWhirlwindRuntime.create(entityId).set(
        casting.skillId, level, destination, start);
    if (mUnitStates.has(entityId)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states.stateList == null) states.init(entityId);
      UnitState state = states.stateList.addState(
          StateId.WHIRLWIND, 0, level, entityId);
      state.skillId = casting.skillId;
      state.needsSync = true;
    }
    Vector2 direction = new Vector2(destination).sub(start).nor();
    mVelocity.get(entityId).velocity.set(direction)
        .setLength(mVelocity.get(entityId).speed(false));
    if (mAngle.has(entityId)) mAngle.get(entityId).target.set(direction);
    log.info("[WHIRLWIND] phase=start entity={} skill={} level={} target={} "
            + "start=({}, {}) requested=({}, {}) destination=({}, {}) interval={}",
        entityId, casting.skillId, level, targetId,
        start.x, start.y, requested.x, requested.y, destination.x, destination.y,
        whirlwindAttackInterval(entityId));
  }

  private void startBerserk(int entityId, int targetId) {
    Casting casting = mCasting.get(entityId);
    Skills.Entry skill = casting != null ? Riiablo.files.skills.get(casting.skillId) : null;
    if (casting == null || skill == null || skill.srvdofunc != 2
        || targetId == Engine.INVALID_ENTITY || !mPosition.has(entityId)
        || !mPosition.has(targetId) || !isAlive(entityId) || !isAlive(targetId)) {
      rejectBerserk(entityId, "invalid_target");
      return;
    }
    if (!PvpCombatRules.canDamage(
        partyManager, entityId, targetId,
        mPlayer.has(entityId) || mMercenary.has(entityId) || mSummonedPet.has(entityId),
        mPlayer.has(targetId) || mMercenary.has(targetId) || mSummonedPet.has(targetId))) {
      rejectBerserk(entityId, "invalid_combat_relation");
      return;
    }
    if (!isInMeleeRange(entityId, targetId, mPlayer.has(entityId) ? 3 : 0)) {
      rejectBerserk(entityId, "target_out_of_melee_range");
      return;
    }
    int level = Math.max(1, skillLevel(entityId, casting.skillId));
    int duration = BarbarianSkills.getBerserkDuration(
        skill, level, name -> baseSkillLevel(entityId, name));
    if (mUnitStates.has(entityId)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states.stateList == null) states.init(entityId);
      UnitState state = states.stateList.addState(
          StateId.BERSERK, duration, level, entityId);
      if (state != null) {
        state.skillId = casting.skillId;
        // D2MOO's berserk stat list contributes -100% defense while active.
        state.defenseModifier = -100;
        state.needsSync = true;
      }
    }
    log.info("[BERSERK] phase=start entity={} target={} level={} duration={} "
            + "damagePercent={} conversion={}",
        entityId, targetId, level, duration,
        BarbarianSkills.calculateBerserkDamageBonus(
            skill, level, name -> baseSkillLevel(entityId, name)),
        BarbarianSkills.getBerserkMagicConversion(
            skill, level, name -> baseSkillLevel(entityId, name)));
  }

  private void rejectBerserk(int entityId, String reason) {
    if (mCasting.has(entityId)) mCasting.remove(entityId);
    if (mSequence.has(entityId)) mSequence.remove(entityId);
    log.info("[BERSERK] phase=start_reject entity={} reason={}", entityId, reason);
  }

  private void rejectWhirlwind(int entityId, String reason) {
    if (mWhirlwindRuntime.has(entityId)) mWhirlwindRuntime.remove(entityId);
    if (mCasting.has(entityId)) mCasting.remove(entityId);
    if (mSequence.has(entityId)) mSequence.remove(entityId);
    if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
    log.info("[WHIRLWIND] phase=start_reject entity={} reason={}", entityId, reason);
  }

  /** One native SrvDo076 attack window; dual wielding resolves two hands. */
  void resolveWhirlwindPulse(int entityId, WhirlwindRuntime runtime) {
    if (runtime == null || !mAttributesWrapper.has(entityId)) return;
    Skills.Entry skill = Riiablo.files.skills.get(runtime.skillId);
    if (skill == null) return;
    int count = hasTwoWhirlwindWeapons(entityId) ? 2 : 1;
    for (int i = 0; i < count; i++) {
      int targetId = findNextWhirlwindTarget(entityId, runtime.previousTargetId);
      if (targetId == Engine.INVALID_ENTITY) {
        runtime.previousTargetId = Engine.INVALID_ENTITY;
        break;
      }
      runtime.previousTargetId = targetId;
      Item weapon = whirlwindWeapon(entityId, runtime.strikeIndex);
      int strike = ++runtime.strikeIndex;
      resolveWhirlwindStrike(entityId, targetId, skill, runtime.skillLevel, weapon, strike);
    }
  }

  private void resolveWhirlwindStrike(
      int entityId, int targetId, Skills.Entry skill, int level,
      Item weapon, int strike) {
    if (!mAttributesWrapper.has(entityId) || !mAttributesWrapper.has(targetId)
        || !isAlive(entityId) || !isAlive(targetId)) return;
    Attributes attacker = mAttributesWrapper.get(entityId).attrs;
    Attributes defender = mAttributesWrapper.get(targetId).attrs;
    int[] damage = BarbarianSkills.calculateWhirlwindDamage(
        skill, level, attacker, weapon, name -> baseSkillLevel(entityId, name),
        stateList(entityId));
    int attackRating = BarbarianSkills.getWeaponMasteryAttackRating(
        skill, level, attacker, isPlayerEntity(entityId), weapon, stateList(entityId));
    CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculatePrecomputedMeleeAttack(
        attacker, defender, isPlayerEntity(entityId), isPlayerEntity(targetId),
        damage[0], damage[1], attackRating,
        stateList(entityId), stateList(targetId), isEntityMoving(targetId),
        weaponMastery(entityId, weapon, false));
    if (!combat.hit) {
      log.info("[WHIRLWIND] phase=strike entity={} target={} strike={} hand={} "
              + "result=miss chance={}",
          entityId, targetId, strike, whirlwindHand(entityId, strike - 1), combat.hitChance);
      return;
    }
    if (combat.blocked) {
      log.info("[WHIRLWIND] phase=strike entity={} target={} strike={} hand={} result=blocked",
          entityId, targetId, strike, whirlwindHand(entityId, strike - 1));
      queueHitReaction(targetId, true);
      return;
    }
    if (weapon != null) drainFrenzyDurability(weapon, targetId);
    StatRef hp = defender.get(Stat.hitpoints, StatRef.obtain());
    if (hp == null || hp.asFixed() <= 0f) return;
    float before = hp.asFixed();
    DamageEvent event = DamageEvent.obtain(entityId, targetId,
        Math.max(0f, combat.totalDamage));
    events.dispatch(event);
    float applied = Math.max(0f, event.damage);
    hp.sub(applied);
    if (hp.asFixed() < 0f) hp.set(0f);
    if (hp.asFixed() > 0f) queueHitReaction(targetId, false);
    applyCombatStates(entityId, targetId, combat);
    log.info("[WHIRLWIND] phase=strike entity={} target={} strike={} hand={} "
            + "weapon={} result=hit damage={} hp={} -> {} chance={}",
        entityId, targetId, strike, whirlwindHand(entityId, strike - 1),
        weapon != null ? weapon.code : "unarmed", applied, before, hp.asFixed(),
        combat.hitChance);
    if (hp.asFixed() <= 0f) events.dispatch(DeathEvent.obtain(entityId, targetId));
  }

  int whirlwindAttackInterval(int entityId) {
    Item weapon = whirlwindPrimaryWeapon(entityId);
    if (weapon == null) return 10;
    int nativeAttackSpeed = 45;
    if (Riiablo.anim != null && mCofReference.has(entityId)) {
      CofReference cof = mCofReference.get(entityId);
      com.riiablo.codec.D2.Entry anim = Riiablo.anim.getEntry(
          cof.token + "A1" + Engine.getWClass(cof.wclass));
      if (anim != null && anim.framesPerDir > 0 && anim.speed > 0) {
        int baseAttackRate = weapon.base instanceof Weapons.Entry
            ? -((Weapons.Entry) weapon.base).speed : 0;
        int attackRate = 100
            + itemStatInt(weapon, Stat.attackrate, baseAttackRate)
            + itemStatInt(weapon, Stat.item_fasterattackrate, 0);
        int scaledAnimSpeed = Math.max(1, anim.speed * Math.max(1, attackRate) / 100);
        nativeAttackSpeed = Math.max(1, (anim.framesPerDir << 8) / scaledAnimSpeed);
      }
    }
    return BarbarianSkills.getWhirlwindAttackInterval(nativeAttackSpeed);
  }

  private int findNextWhirlwindTarget(int sourceId, int previousTargetId) {
    if (!mPosition.has(sourceId)) return Engine.INVALID_ENTITY;
    Vector2 source = mPosition.get(sourceId).position;
    int next = Engine.INVALID_ENTITY;
    int wrapped = Engine.INVALID_ENTITY;
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int[] ids = entities.getData();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int candidate = ids[i];
      if (candidate == sourceId || !isAlive(candidate)
          || source.dst2(mPosition.get(candidate).position) > 25f
          || !isValidFrenzyTarget(sourceId, candidate)) continue;
      if (candidate > previousTargetId) {
        if (next == Engine.INVALID_ENTITY || candidate < next) next = candidate;
      } else if (wrapped == Engine.INVALID_ENTITY || candidate < wrapped) {
        wrapped = candidate;
      }
    }
    return next != Engine.INVALID_ENTITY ? next : wrapped;
  }

  private Item whirlwindPrimaryWeapon(int entityId) {
    Item right = equippedFrenzyWeapon(entityId, BodyLoc.RARM);
    return right != null ? right : equippedFrenzyWeapon(entityId, BodyLoc.LARM);
  }

  private boolean hasTwoWhirlwindWeapons(int entityId) {
    return equippedFrenzyWeapon(entityId, BodyLoc.RARM) != null
        && equippedFrenzyWeapon(entityId, BodyLoc.LARM) != null;
  }

  private Item whirlwindWeapon(int entityId, int strikeIndex) {
    Item right = equippedFrenzyWeapon(entityId, BodyLoc.RARM);
    Item left = equippedFrenzyWeapon(entityId, BodyLoc.LARM);
    if (right != null && left != null) return (strikeIndex & 1) == 0 ? right : left;
    return right != null ? right : left;
  }

  private String whirlwindHand(int entityId, int strikeIndex) {
    if (!hasTwoWhirlwindWeapons(entityId)) return "primary";
    return (strikeIndex & 1) == 0 ? "right" : "left";
  }

  private static int itemStatInt(Item item, short stat, int fallback) {
    if (item == null || item.attrs == null) return fallback;
    StatRef ref = item.attrs.get(stat, StatRef.obtain());
    if (ref == null) ref = item.attrs.base().get(stat, StatRef.obtain());
    return ref == null ? fallback : ref.asInt();
  }

  private boolean hasTwoFrenzyWeapons(int entityId) {
    return equippedFrenzyWeapon(entityId, BodyLoc.RARM) != null
        && equippedFrenzyWeapon(entityId, BodyLoc.LARM) != null;
  }

  private Item frenzyWeapon(int entityId, int strikeIndex) {
    if (!mPlayer.has(entityId)) return null;
    return equippedFrenzyWeapon(entityId,
        (strikeIndex & 1) == 0 ? BodyLoc.RARM : BodyLoc.LARM);
  }

  private void drainFrenzyDurability(Item weapon, int targetId) {
    ItemDurabilityManager.INSTANCE.drainWeaponDurability(weapon, true);
    if (mPlayer.has(targetId) && mPlayer.get(targetId).data != null) {
      ItemDurabilityManager.INSTANCE.drainArmorDurability(
          mPlayer.get(targetId).data.getItems());
    }
  }

  /** D2MOO sub_6FD107F0: next GUID in melee+4 range, wrapping to the first. */
  private int findNextFrenzyTarget(int sourceId, int previousTargetId) {
    if (!mPosition.has(sourceId)) return Engine.INVALID_ENTITY;
    Vector2 source = mPosition.get(sourceId).position;
    float range = getMeleeRange(sourceId) + 4f;
    float range2 = range * range;
    int next = Engine.INVALID_ENTITY;
    int wrapped = Engine.INVALID_ENTITY;
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int[] ids = entities.getData();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int candidate = ids[i];
      if (candidate == sourceId || !isAlive(candidate)
          || source.dst2(mPosition.get(candidate).position) > range2
          || !isValidFrenzyTarget(sourceId, candidate)) continue;
      if (candidate > previousTargetId) {
        if (next == Engine.INVALID_ENTITY || candidate < next) next = candidate;
      } else if (wrapped == Engine.INVALID_ENTITY || candidate < wrapped) {
        wrapped = candidate;
      }
    }
    return next != Engine.INVALID_ENTITY ? next : wrapped;
  }

  private boolean isValidFrenzyTarget(int sourceId, int targetId) {
    NativeUnitFlags flags = mNativeUnitFlags.get(targetId);
    if (flags != null && !NativeTargeting.isValidCombatTarget(flags)) return false;
    boolean sourcePlayerAligned = mPlayer.has(sourceId) || mMercenary.has(sourceId)
        || mSummonedPet.has(sourceId);
    boolean targetPlayerAligned = mPlayer.has(targetId) || mMercenary.has(targetId)
        || mSummonedPet.has(targetId);
    return PvpCombatRules.canDamage(
        partyManager, sourceId, targetId, sourcePlayerAligned, targetPlayerAligned);
  }

  private Item dragonClawWeapon(int entityId, int strikeIndex) {
    Item right = equippedClaw(entityId, BodyLoc.RARM);
    Item left = equippedClaw(entityId, BodyLoc.LARM);
    if (strikeIndex <= 0) return right != null ? right : left;
    return left != null ? left : right;
  }

  private void drainDragonClawDurability(Item weapon, int targetId) {
    ItemDurabilityManager.INSTANCE.drainWeaponDurability(weapon, true);
    if (mPlayer.has(targetId) && mPlayer.get(targetId).data != null) {
      ItemDurabilityManager.INSTANCE.drainArmorDurability(
          mPlayer.get(targetId).data.getItems());
    }
  }

  /** One native durability resolution for each successful kick combat record. */
  private void drainDragonTalonDurability(int attackerId, int targetId) {
    if (mPlayer.has(attackerId) && mPlayer.get(attackerId).data != null) {
      Item boots = mPlayer.get(attackerId).data.getItems().getEquipped(BodyLoc.FEET);
      ItemDurabilityManager.INSTANCE.drainWeaponDurability(boots, true);
    }
    if (mPlayer.has(targetId) && mPlayer.get(targetId).data != null) {
      ItemDurabilityManager.INSTANCE.drainArmorDurability(
          mPlayer.get(targetId).data.getItems());
    }
  }

  private boolean shouldDragonTalonKnockback(
      Skills.Entry skill, int skillLevel, int targetId) {
    boolean playerOrHireling = mPlayer.has(targetId) || mMercenary.has(targetId);
    Monster monster = mMonster.get(targetId);
    boolean boss = monster != null && (monster.rank == MonsterRank.BOSS
        || monster.monstats != null && (monster.monstats.boss || monster.monstats.primeevil));
    boolean unique = monster != null && MonsterRank.isUnique(monster.rank);
    int chance = AssassinSkills.dragonTalonKnockbackChance(
        skill, skillLevel, playerOrHireling, boss, unique);
    boolean applied = chance > 0 && MathUtils.random(99) < chance;
    log.info("[ASSASSIN_DRAGON_TALON] phase=knockback_roll target={} class={} chance={} applied={}",
        targetId, playerOrHireling ? "player_or_hireling" : boss ? "boss" : unique ? "unique" : "normal",
        chance, applied);
    return applied;
  }

  /** Collision-clamped authoritative displacement for Dragon Talon's final kick. */
  private boolean applyDragonTalonKnockback(int attackerId, int targetId) {
    if (!mPosition.has(attackerId) || !mPosition.has(targetId)) return false;
    Vector2 attacker = mPosition.get(attackerId).position;
    Vector2 target = mPosition.get(targetId).position;
    Vector2 direction = new Vector2(target).sub(attacker);
    if (direction.isZero(0.0001f)) return false;
    direction.nor();
    Vector2 destination = new Vector2();
    boolean found = false;
    for (float distance = StatusEffectApplier.KNOCKBACK_DISTANCE;
         distance >= 0.5f; distance -= 0.5f) {
      destination.set(target).mulAdd(direction, distance);
      int flags = map != null ? map.flags(destination) : 0;
      if ((flags & DT1.Tile.FLAG_BLOCK_WALK) == 0) {
        found = true;
        break;
      }
    }
    if (!found) {
      log.info("[ASSASSIN_DRAGON_TALON] phase=knockback_blocked source={} target={}",
          attackerId, targetId);
      return false;
    }
    target.set(destination);
    if (mPathfind.has(targetId)) mPathfind.remove(targetId);
    if (mVelocity.has(targetId)) mVelocity.get(targetId).velocity.setZero();
    Box2DBody body = mBox2DBody.get(targetId);
    if (body != null && body.body != null) body.body.setTransform(destination, 0);
    log.info("[ASSASSIN_DRAGON_TALON] phase=knockback source={} target={} destination=({}, {})",
        attackerId, targetId, destination.x, destination.y);
    return true;
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

  private StateList.WeaponMasteryBonus weaponMastery(
      int entityId, Item weapon, boolean throwingAttack) {
    StateList states = stateList(entityId);
    if (states == null || weapon == null) return null;
    StateList.WeaponMasteryBonus mastery = states.getWeaponMastery(
        weapon, throwingAttack, new StateList.WeaponMasteryBonus());
    return mastery.isEmpty() ? null : mastery;
  }

  /** Resolves the concrete hand whose weapon supplies this melee packet. */
  private Item activeAttackWeapon(int entityId) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return null;
    int skillId = mCasting.has(entityId) ? mCasting.get(entityId).skillId : SkillCodes.attack;
    BodyLoc preferred = skillId == SkillCodes.left_hand_swing
        || skillId == SkillCodes.left_hand_throw ? BodyLoc.LARM : BodyLoc.RARM;
    Item weapon = mPlayer.get(entityId).data.getItems().getEquipped(preferred);
    if (weapon == null) {
      BodyLoc alternate = preferred == BodyLoc.RARM ? BodyLoc.LARM : BodyLoc.RARM;
      weapon = mPlayer.get(entityId).data.getItems().getEquipped(alternate);
    }
    return weapon != null && weapon.base instanceof Weapons.Entry ? weapon : null;
  }

  private boolean isEntityMoving(int entityId) {
    return mVelocity.has(entityId) && !mVelocity.get(entityId).velocity.isZero(0.0001f);
  }

  /**
   * Native SUNITDMG_ExecuteDamage switches the victim to GH/BL and lets the
   * animation sequence return to NU.  The mode is serialized through
   * CofReference, so remote clients render the same reaction without a local
   * combat re-roll.  Entities without animation data (lightweight tests and
   * non-rendered helpers) are intentionally ignored.
   */
  private void queueHitReaction(int victimId, boolean blocked) {
    CofManager cofs = world.getSystem(CofManager.class);
    if (cofs == null || !mClass.has(victimId) || !mCofReference.has(victimId)
        || !mAnimData.has(victimId)) return;
    Class.Type type = mClass.get(victimId).type;
    if (type != Class.Type.PLR && type != Class.Type.MON) return;
    // Do not interrupt a native death or an already-running multi-stage skill.
    byte current = mCofReference.get(victimId).mode;
    if (type == Class.Type.PLR
        && (current == Engine.Player.MODE_DT || current == Engine.Player.MODE_DD)) return;
    if (type == Class.Type.MON
        && (current == Engine.Monster.MODE_DT || current == Engine.Monster.MODE_DD)) return;
    if (mCasting.has(victimId)) return;
    byte reaction = type == Class.Type.PLR
        ? (blocked ? Engine.Player.MODE_BL : Engine.Player.MODE_GH)
        : (blocked ? Engine.Monster.MODE_BL : Engine.Monster.MODE_GH);
    byte neutral = type == Class.Type.PLR ? Engine.Player.MODE_NU : Engine.Monster.MODE_NU;
    mSequence.create(victimId).sequence(reaction, neutral);
    cofs.setMode(victimId, reaction, true);
    log.info("[HIT_REACTION] victim={} type={} mode={} blocked={} source=server",
        victimId, type, blocked ? "BL" : "GH", blocked);
  }

  private void applyCombatStates(int attackerId, int targetId,
      CombatSystem.CombatResult combat) {
    if (!mUnitStates.has(targetId)) return;
    if (combat.poisonDuration > 0
        && (combat.poisonDamagePerFrame > 0f
            || combat.elementalDamage[CombatSystem.DAMAGE_POISON] > 0)) {
      StatusEffectApplier.INSTANCE.applyPoison(targetId,
          combat.poisonDamagePerFrame > 0f ? combat.poisonDamagePerFrame
              : combat.elementalDamage[CombatSystem.DAMAGE_POISON],
          combat.poisonDuration, attackerId);
    }
    if (combat.coldDuration > 0
        && combat.elementalDamage[CombatSystem.DAMAGE_COLD] > 0) {
      StatusEffectApplier.INSTANCE.applyCold(targetId, combat.coldDuration, attackerId);
    }
  }

  /** D2MOO sub_6FCF5680/sub_6FCF5BC0 progressive damage preparation. */
  private static void applyAssassinProgressiveDamage(
      AssassinSkills.ProgressiveRelease release, CombatSystem.CombatResult combat,
      Attributes defender, com.riiablo.engine.server.state.StateList defenderStates) {
    if (release == null || combat == null) return;
    if (release.tigerDamagePercent > 0 && combat.physicalDamage > 0) {
      int extra = combat.physicalDamage * release.tigerDamagePercent / 100;
      combat.physicalDamage += Math.max(0, extra);
    }

    int converted = 0;
    if (release.fireConversionPercent > 0 && combat.physicalDamage > 0) {
      converted = Math.min(combat.physicalDamage,
          combat.physicalDamage * release.fireConversionPercent / 100);
      combat.physicalDamage -= converted;
    }
    int rawFire = converted + AssassinSkills.rollFireDamage(release);
    if (rawFire > 0) {
      int resistance = statInt(defender, Stat.fireresist);
      if (defenderStates != null) resistance += defenderStates.getTotalResistModifier(0);
      int fire = resistance >= 100 ? 0
          : Math.max(0, rawFire * (100 - Math.min(75, resistance)) / 100);
      combat.elementalDamage[CombatSystem.DAMAGE_FIRE] += fire;
    }

    int rawLightning = AssassinSkills.rollLightningDamage(release);
    if (rawLightning > 0) {
      int lightning = resistedDamage(rawLightning, defender, defenderStates,
          Stat.lightresist, 2);
      combat.elementalDamage[CombatSystem.DAMAGE_LIGHTNING] += lightning;
    }

    int rawCold = AssassinSkills.rollColdDamage(release);
    if (rawCold > 0) {
      int cold = resistedDamage(rawCold, defender, defenderStates,
          Stat.coldresist, 1);
      combat.elementalDamage[CombatSystem.DAMAGE_COLD] += cold;
      if (cold > 0) combat.coldDuration = Math.max(combat.coldDuration, release.coldLength);
    }

    combat.totalDamage = combat.physicalDamage;
    for (int i = 1; i < CombatSystem.DAMAGE_TYPE_COUNT; i++) {
      if (i != CombatSystem.DAMAGE_POISON || combat.poisonDuration <= 0) {
        combat.totalDamage += combat.elementalDamage[i];
      }
    }
  }

  private void applyAssassinProgressiveStageEffects(int sourceId, int primaryTargetId,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    applyFistsOfFireStageEffects(sourceId, primaryTargetId, release, sourceAttrs);
    applyClawsOfThunderStageEffects(sourceId, primaryTargetId, release, sourceAttrs);
    applyBladesOfIceStageEffects(sourceId, primaryTargetId, release, sourceAttrs);
    applyPhoenixStrikeStageEffect(sourceId, primaryTargetId, release, sourceAttrs);
  }

  /**
   * D2MOO SrvDo038/SrvDo039. Fists of Fire has PrgStack enabled, therefore
   * charge three releases both the charge-two blast and charge-three field.
   */
  private void applyFistsOfFireStageEffects(int sourceId, int primaryTargetId,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    if (release == null || release.fireCharges < 2 || !mPosition.has(primaryTargetId)) return;
    Vector2 origin = mPosition.get(primaryTargetId).position;
    applyFistsOfFireArea(sourceId, primaryTargetId, origin, release, sourceAttrs);
    if (release.fireCharges >= 3) {
      createFistsOfFireField(sourceId, origin, release, sourceAttrs);
    }
  }

  /** Applies one rolled physical/elemental record to every valid nearby enemy. */
  private void applyFistsOfFireArea(int sourceId, int primaryTargetId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    int range = Math.max(0, release.fireAreaRange);
    if (range <= 0) return;
    int sourceMin = statInt(sourceAttrs, Stat.mindamage);
    int sourceMax = Math.max(sourceMin, statInt(sourceAttrs, Stat.maxdamage));
    int physicalMin = release.firePhysicalMinDamage
        + sourceMin * release.fireSourceDamageScale / 128;
    int physicalMax = release.firePhysicalMaxDamage
        + sourceMax * release.fireSourceDamageScale / 128;
    int rawPhysical = rollRange(physicalMin, physicalMax);
    int rawFire = rollRange(release.fireMinDamage, release.fireMaxDamage);
    if (rawPhysical <= 0 && rawFire <= 0) return;

    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int[] ids = entities.getData();
    int affected = 0;
    for (int i = 0, size = entities.size(); i < size; i++) {
      int targetId = ids[i];
      if (targetId == sourceId) continue;
      if (!isValidFistsTarget(sourceId, targetId)) continue;
      if (mPosition.get(targetId).position.dst2(origin) > range * range) continue;
      Attributes targetAttrs = mAttributesWrapper.get(targetId).attrs;
      int physical = resistedDamage(rawPhysical, targetAttrs, stateList(targetId),
          Stat.damageresist, -1);
      int fire = resistedDamage(rawFire, targetAttrs, stateList(targetId),
          Stat.fireresist, 0);
      float damage = physical + fire;
      if (damage <= 0f) continue;
      DamageEvent event = DamageEvent.obtain(sourceId, targetId, damage);
      events.dispatch(event);
      float applied = Math.max(0f, event.damage);
      StatRef hp = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null || applied <= 0f) continue;
      hp.sub(applied);
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        // The caller owns the primary finishing target's single DeathEvent.
        if (targetId != primaryTargetId) {
          events.dispatch(DeathEvent.obtain(sourceId, targetId));
        }
      }
      affected++;
      log.info("[ASSASSIN_FISTS] phase=stage2_area source={} primary={} target={} "
              + "range={} physical={} fire={} applied={}",
          sourceId, primaryTargetId, targetId, range, physical, fire, applied);
    }
    log.info("[ASSASSIN_FISTS] phase=stage2_complete source={} primary={} range={} "
            + "rawPhysical={} rawFire={} affected={}",
        sourceId, primaryTargetId, range, rawPhysical, rawFire, affected);
  }

  /** Creates the charge-three random fire field as server-owned missile entities. */
  private void createFistsOfFireField(int sourceId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    int range = Math.max(0, release.fireFieldRange);
    if (range <= 0 || release.fireStageMissile == null
        || release.fireStageMissile.isEmpty()) return;
    Missiles.Entry row = Riiablo.files.Missiles.get(release.fireStageMissile);
    Skills.Entry skill = Riiablo.files.skills.get(release.fireSkillId);
    if (row == null || skill == null) {
      log.warn("[ASSASSIN_FISTS] phase=stage3_reject source={} missile={} reason=missing_data",
          sourceId, release.fireStageMissile);
      return;
    }

    int seed = assassinProgressiveSeeds.get(sourceId,
        Riiablo.gameSeed ^ sourceId * 0x45D9F3B ^ release.fireSkillId * 31);
    NativeRng rng = new NativeRng(seed);
    int area = range * range;
    int created = 0;
    for (int i = 0; i < area; i++) {
      int dx = range - rng.nextInt(2 * range);
      int dy = range - rng.nextInt(2 * range);
      if (dx * dx + dy * dy > area) continue;
      Vector2 position = new Vector2(origin.x + dx, origin.y + dy);
      // D2MOO checks that the coordinate resolves to an active room. Empty
      // maps used by isolated tests have no act and intentionally skip this.
      if (map != null && map.getAct() >= 0) {
        Map.Zone zone = map.getZone(position);
        if (zone == null || zone.findRoomEx(position.x, position.y) == null) continue;
      }
      int missileId = factory.createMissile(row, Vector2.X, position, sourceId);
      if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) continue;
      com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
      MissileDamageResolver.initializeSkillArea(
          projectile, skill, sourceAttrs, release.fireSkillLevel);
      // Vel=0 basic missiles still advance their native frame lifetime. Mark
      // them persistent so the ECS expires them after Range frames; retaining
      // hitTargets for that whole lifetime matches NextHit=false semantics.
      projectile.persistent = true;
      projectile.remainingFrames = Math.max(1, row.Range);
      projectile.tickInterval = projectile.remainingFrames + 1;
      created++;
    }
    assassinProgressiveSeeds.put(sourceId, rng.state());
    log.info("[ASSASSIN_FISTS] phase=stage3_field source={} skill={} missile={} "
            + "range={} attempts={} created={} seed={}",
        sourceId, release.fireSkillId, release.fireStageMissile,
        range, area, created, rng.state());
  }

  /** D2MOO SrvDo036/SrvDo037 stacked Claws of Thunder missile stages. */
  private void applyClawsOfThunderStageEffects(int sourceId, int primaryTargetId,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    if (release == null || release.lightningCharges < 2
        || !mPosition.has(primaryTargetId)) return;
    Vector2 origin = mPosition.get(primaryTargetId).position;
    int novaCount = createClawsNova(
        sourceId, origin, release, sourceAttrs);
    int boltCount = release.lightningCharges >= 3
        ? createClawsChargedBolts(sourceId, origin, release, sourceAttrs) : 0;
    log.info("[ASSASSIN_CLAWS] phase=release source={} primary={} charges={} "
            + "nova={} chargedBolts={}",
        sourceId, primaryTargetId, release.lightningCharges, novaCount, boltCount);
  }

  /** Native sub_6FD14170 emits all 64 quantized radial nova paths. */
  private int createClawsNova(int sourceId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    if (release.lightningNovaMissile == null || release.lightningNovaMissile.isEmpty()) return 0;
    Missiles.Entry row = Riiablo.files.Missiles.get(release.lightningNovaMissile);
    Skills.Entry skill = Riiablo.files.skills.get(release.lightningSkillId);
    if (row == null || skill == null) {
      log.warn("[ASSASSIN_CLAWS] phase=stage2_reject source={} missile={} reason=missing_data",
          sourceId, release.lightningNovaMissile);
      return 0;
    }
    IntSet sharedHits = new IntSet();
    int velocity = row.Vel + Math.max(0,
        SkillFormula.evaluate(skill.calc1, skill, release.lightningSkillLevel));
    int created = 0;
    for (int i = 0; i < 64; i++) {
      Vector2 direction = clawsRadialDirection(i, new Vector2());
      int missileId = factory.createMissile(row, direction, origin, sourceId);
      if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) continue;
      com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
      projectile.shareHitTargets(sharedHits);
      MissileDamageResolver.initializeSkill(
          projectile, skill, sourceAttrs, release.lightningSkillLevel);
      if (velocity > 0 && mVelocity.has(missileId)) {
        mVelocity.get(missileId).velocity.set(direction).setLength(velocity);
      }
      created++;
    }
    log.info("[ASSASSIN_CLAWS] phase=stage2_nova source={} skill={} missile={} "
            + "requested=64 created={} velocity={}",
        sourceId, release.lightningSkillId, release.lightningNovaMissile,
        created, velocity);
    return created;
  }

  /** Native sub_6FCF6600 emits every PrgCalc3-th path with charged-bolt motion. */
  private int createClawsChargedBolts(int sourceId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    if (release.lightningBoltMissile == null || release.lightningBoltMissile.isEmpty()) return 0;
    Missiles.Entry row = Riiablo.files.Missiles.get(release.lightningBoltMissile);
    Skills.Entry skill = Riiablo.files.skills.get(release.lightningSkillId);
    if (row == null || skill == null) {
      log.warn("[ASSASSIN_CLAWS] phase=stage3_reject source={} missile={} reason=missing_data",
          sourceId, release.lightningBoltMissile);
      return 0;
    }
    int step = Math.max(1, Math.min(64, release.lightningBoltStep));
    int seed = assassinProgressiveSeeds.get(sourceId,
        Riiablo.gameSeed ^ sourceId * 0x45D9F3B ^ release.lightningSkillId * 31);
    NativeRng rng = new NativeRng(seed);
    int created = 0;
    for (int i = 0; i < 64; i += step) {
      Vector2 direction = clawsRadialDirection(i, new Vector2());
      int missileId = factory.createMissile(row, direction, origin, sourceId);
      if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) continue;
      com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
      int seedLow = rng.nextInt();
      long rolled = AssassinTrapSystem.chargedBoltRoll(seedLow, 666);
      projectile.chargedBoltPath = true;
      projectile.chargedBoltMainDirection =
          AssassinTrapSystem.chargedBoltMainDirection(direction);
      projectile.chargedBoltSeedLow = (int) rolled;
      projectile.chargedBoltSeedHigh = (int) (rolled >>> 32);
      projectile.chargedBoltNextTurnDistance = 2f;
      projectile.range = Math.min(77f, Math.max(1f, projectile.range));
      MissileDamageResolver.initializeSkill(
          projectile, skill, sourceAttrs, release.lightningSkillLevel);
      created++;
    }
    assassinProgressiveSeeds.put(sourceId, rng.state());
    log.info("[ASSASSIN_CLAWS] phase=stage3_bolts source={} skill={} missile={} "
            + "step={} requested={} created={} seed={}",
        sourceId, release.lightningSkillId, release.lightningBoltMissile,
        step, (64 + step - 1) / step, created, rng.state());
    return created;
  }

  /** Unit vector corresponding to D2MOO's 64-entry radius-30 offset table. */
  static Vector2 clawsRadialDirection(int index, Vector2 out) {
    float radians = MathUtils.PI2 * (index & 63) / 64f;
    // Native table entries are trunc(30*cos/sin), not ideal continuous angles.
    int x = (int) (30f * MathUtils.cos(radians));
    int y = (int) (30f * MathUtils.sin(radians));
    return out.set(x, y).nor();
  }

  /** D2MOO SrvDo038/SrvDo039 stacked Blades of Ice release stages. */
  private void applyBladesOfIceStageEffects(int sourceId, int primaryTargetId,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    if (release == null || release.coldCharges <= 0 || !mPosition.has(primaryTargetId)) return;
    if (release.coldCharges < 2) return;
    Vector2 origin = mPosition.get(primaryTargetId).position;
    applyBladesOfIceArea(sourceId, primaryTargetId, origin, release, sourceAttrs);
    int cubes = release.coldCharges >= 3
        ? createBladesOfIceCubes(sourceId, origin, release, sourceAttrs) : 0;
    log.info("[ASSASSIN_BLADES] phase=release source={} primary={} charges={} cubes={}",
        sourceId, primaryTargetId, release.coldCharges, cubes);
  }

  /** Native SrvDo038 rolls one physical/cold record shared by all targets in range. */
  private void applyBladesOfIceArea(int sourceId, int primaryTargetId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    int range = Math.max(0, release.coldAreaRange);
    if (range <= 0) return;
    int sourceMin = statInt(sourceAttrs, Stat.mindamage);
    int sourceMax = Math.max(sourceMin, statInt(sourceAttrs, Stat.maxdamage));
    int rawPhysical = rollRange(
        release.coldPhysicalMinDamage + sourceMin * release.coldSourceDamageScale / 128,
        release.coldPhysicalMaxDamage + sourceMax * release.coldSourceDamageScale / 128);
    int rawCold = rollRange(release.coldMinDamage, release.coldMaxDamage);
    if (rawPhysical <= 0 && rawCold <= 0) return;

    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int[] ids = entities.getData();
    int affected = 0;
    for (int i = 0, size = entities.size(); i < size; i++) {
      int targetId = ids[i];
      if (targetId == sourceId || !isValidFistsTarget(sourceId, targetId)) continue;
      if (mPosition.get(targetId).position.dst2(origin) > range * range) continue;
      Attributes targetAttrs = mAttributesWrapper.get(targetId).attrs;
      int physical = resistedDamage(rawPhysical, targetAttrs, stateList(targetId),
          Stat.damageresist, -1);
      int cold = resistedDamage(rawCold, targetAttrs, stateList(targetId),
          Stat.coldresist, 1);
      float damage = physical + cold;
      if (damage <= 0f) continue;
      DamageEvent event = DamageEvent.obtain(sourceId, targetId, damage);
      events.dispatch(event);
      float applied = Math.max(0f, event.damage);
      StatRef hp = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null || applied <= 0f) continue;
      hp.sub(applied);
      if (cold > 0 && release.coldLength > 0) {
        StatusEffectApplier.INSTANCE.applyCold(targetId, release.coldLength, sourceId);
      }
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        if (targetId != primaryTargetId) {
          events.dispatch(DeathEvent.obtain(sourceId, targetId));
        }
      }
      affected++;
      log.info("[ASSASSIN_BLADES] phase=stage2_area source={} primary={} target={} "
              + "range={} physical={} cold={} coldLength={} applied={}",
          sourceId, primaryTargetId, targetId, range, physical, cold,
          release.coldLength, applied);
    }
    log.info("[ASSASSIN_BLADES] phase=stage2_complete source={} primary={} range={} "
            + "rawPhysical={} rawCold={} affected={}",
        sourceId, primaryTargetId, range, rawPhysical, rawCold, affected);
  }

  /** Native SrvDo039 scatters Range^2 freeze-cube attempts inside a circle. */
  private int createBladesOfIceCubes(int sourceId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    int range = Math.max(0, release.coldCubeRange);
    if (range <= 0 || release.coldCubeMissile == null
        || release.coldCubeMissile.isEmpty()) return 0;
    Missiles.Entry row = Riiablo.files.Missiles.get(release.coldCubeMissile);
    Skills.Entry skill = Riiablo.files.skills.get(release.coldSkillId);
    if (row == null || skill == null) {
      log.warn("[ASSASSIN_BLADES] phase=stage3_reject source={} missile={} reason=missing_data",
          sourceId, release.coldCubeMissile);
      return 0;
    }
    int seed = assassinProgressiveSeeds.get(sourceId,
        Riiablo.gameSeed ^ sourceId * 0x45D9F3B ^ release.coldSkillId * 31);
    NativeRng rng = new NativeRng(seed);
    int area = range * range;
    int created = 0;
    for (int i = 0; i < area; i++) {
      int dx = range - rng.nextInt(2 * range);
      int dy = range - rng.nextInt(2 * range);
      if (dx * dx + dy * dy > area) continue;
      Vector2 position = new Vector2(origin.x + dx, origin.y + dy);
      if (map != null && map.getAct() >= 0) {
        Map.Zone zone = map.getZone(position);
        if (zone == null || zone.findRoomEx(position.x, position.y) == null) continue;
      }
      int missileId = factory.createMissile(row, Vector2.X, position, sourceId);
      if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) continue;
      com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
      MissileDamageResolver.initializeSkillArea(
          projectile, skill, sourceAttrs, release.coldSkillLevel);
      projectile.freezesTarget = true; // MISSMODE_SrvDmg10_BladesOfIceCubes
      projectile.nativeLifetimeFrames = Math.max(1, row.Range);
      created++;
    }
    assassinProgressiveSeeds.put(sourceId, rng.state());
    log.info("[ASSASSIN_BLADES] phase=stage3_cubes source={} skill={} missile={} "
            + "range={} attempts={} created={} lifetime={} seed={}",
        sourceId, release.coldSkillId, release.coldCubeMissile,
        range, area, created, Math.max(1, row.Range), rng.state());
    return created;
  }

  /** D2MOO SrvDo040/143/041. Royal Strike deliberately has PrgStack disabled. */
  private void applyPhoenixStrikeStageEffect(int sourceId, int primaryTargetId,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    if (release == null || release.phoenixCharges <= 0
        || !mPosition.has(primaryTargetId)
        || release.phoenixStageMissile == null
        || release.phoenixStageMissile.isEmpty()) return;
    Vector2 origin = mPosition.get(primaryTargetId).position;
    int created;
    switch (release.phoenixCharges) {
      case 1:
        created = createPhoenixMeteor(sourceId, origin, release);
        break;
      case 2:
        created = createPhoenixChainLightning(sourceId, origin, release, sourceAttrs);
        break;
      default:
        created = createPhoenixChaosIce(sourceId, origin, release, sourceAttrs);
        break;
    }
    log.info("[ASSASSIN_PHOENIX] phase=release source={} primary={} charges={} "
            + "missile={} stageValue={} created={} prgStack=false",
        sourceId, primaryTargetId, release.phoenixCharges,
        release.phoenixStageMissile, release.phoenixStageValue, created);
  }

  /** SrvDo040 creates one meteor-center controller at the finishing target. */
  private int createPhoenixMeteor(int sourceId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release) {
    Missiles.Entry row = Riiablo.files.Missiles.get(release.phoenixStageMissile);
    if (row == null) return 0;
    int missileId = factory.createMissile(row, Vector2.X, origin, sourceId);
    if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) return 0;
    com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
    projectile.skillId = release.phoenixSkillId;
    projectile.damageLevel = Math.max(1, release.phoenixSkillLevel);
    projectile.nativeLifetimeFrames = Math.max(1, row.Range);
    return 1;
  }

  /** SrvDo143/sub_6FCF6D70 emits every PrgCalc2-th direction in the 64 table. */
  private int createPhoenixChainLightning(int sourceId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    Missiles.Entry row = Riiablo.files.Missiles.get(release.phoenixStageMissile);
    if (row == null) return 0;
    Skills.Entry skill = Riiablo.files.skills.get(release.phoenixSkillId);
    int step = Math.max(1, Math.min(64, release.phoenixStageValue));
    int seed = assassinProgressiveSeeds.get(sourceId,
        Riiablo.gameSeed ^ sourceId * 0x45D9F3B ^ release.phoenixSkillId * 31);
    NativeRng rng = new NativeRng(seed);
    int created = 0;
    for (int i = 0; i < 64; i += step) {
      Vector2 direction = clawsRadialDirection(i, new Vector2());
      int missileId = factory.createMissile(row, direction, origin, sourceId);
      if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) continue;
      com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
      projectile.skillId = release.phoenixSkillId;
      projectile.damageLevel = Math.max(1, release.phoenixSkillLevel);
      MissileDamageResolver.initialize(projectile, sourceAttrs, null,
          -1, projectile.damageLevel, 0);
      projectile.chainHitsRemaining = skill != null && skill.Param != null
          && skill.Param.length > 1 ? Math.max(1, skill.Param[1] + 1) : 1;
      projectile.shareHitTargets(new IntSet());
      int seedLow = rng.nextInt();
      long rolled = AssassinTrapSystem.chargedBoltRoll(seedLow, 666);
      projectile.chargedBoltPath = true;
      projectile.chargedBoltMainDirection =
          AssassinTrapSystem.chargedBoltMainDirection(direction);
      projectile.chargedBoltSeedLow = (int) rolled;
      projectile.chargedBoltSeedHigh = (int) (rolled >>> 32);
      projectile.chargedBoltNextTurnDistance = 2f;
      created++;
    }
    assassinProgressiveSeeds.put(sourceId, rng.state());
    return created;
  }

  /** SrvDo041 creates PrgCalc3 chaos-ice missiles with independent target offsets. */
  private int createPhoenixChaosIce(int sourceId, Vector2 origin,
      AssassinSkills.ProgressiveRelease release, Attributes sourceAttrs) {
    Missiles.Entry row = Riiablo.files.Missiles.get(release.phoenixStageMissile);
    if (row == null) return 0;
    int count = Math.max(0, release.phoenixStageValue);
    int seed = assassinProgressiveSeeds.get(sourceId,
        Riiablo.gameSeed ^ sourceId * 0x45D9F3B ^ release.phoenixSkillId * 31);
    NativeRng rng = new NativeRng(seed);
    int created = 0;
    for (int i = 0; i < count; i++) {
      int dx = rng.nextInt(40) - 20;
      int dy = rng.nextInt(40) - 20;
      if (dx == 0 && dy == 0) dx = 20;
      Vector2 direction = new Vector2(dx, dy).nor();
      int missileId = factory.createMissile(row, direction, origin, sourceId);
      if (missileId == Engine.INVALID_ENTITY || !mMissile.has(missileId)) continue;
      com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
      projectile.skillId = release.phoenixSkillId;
      projectile.damageLevel = Math.max(1, release.phoenixSkillLevel);
      MissileDamageResolver.initialize(projectile, sourceAttrs, null,
          -1, projectile.damageLevel, 0);
      projectile.freezesTarget = true;
      projectile.chaosIcePath = true;
      projectile.chaosIceSeed = rng.nextInt();
      projectile.chaosIceX = dx;
      projectile.chaosIceY = dy;
      projectile.chaosIceNextTurnFrame = Math.max(1,
          row.Param != null && row.Param.length > 0 ? row.Param[0] : 1);
      created++;
    }
    assassinProgressiveSeeds.put(sourceId, rng.state());
    return created;
  }

  /**
   * D2MOO SKILLS_SrvDo050_DragonTail. The kick's resolved physical damage is
   * copied before the primary combat record is applied, then converted into
   * an always-hit fire record around the primary target.
   */
  private void applyDragonTailExplosion(int sourceId, int primaryTargetId,
      Skills.Entry skill, int skillLevel, Attributes sourceAttrs, int physicalDamage) {
    if (skill == null || !mPosition.has(primaryTargetId)) return;
    Vector2 origin = mPosition.get(primaryTargetId).position;
    int radius = AssassinSkills.getDragonTailRadius(skill, skillLevel);
    int rawFire = AssassinSkills.calculateDragonTailExplosionDamage(
        skill, skillLevel, sourceAttrs, physicalDamage);
    int visualId = createDragonTailExplosionVisual(sourceId, origin);
    if (radius <= 0 || rawFire <= 0) {
      log.info("[ASSASSIN_DRAGON_TAIL] phase=explosion source={} primary={} "
              + "physical={} rawFire={} radius={} visual={} affected=0",
          sourceId, primaryTargetId, physicalDamage, rawFire, radius, visualId);
      return;
    }

    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int[] ids = entities.getData();
    int affected = 0;
    for (int i = 0, size = entities.size(); i < size; i++) {
      int targetId = ids[i];
      if (targetId == sourceId || !isValidFistsTarget(sourceId, targetId)) continue;
      if (mPosition.get(targetId).position.dst2(origin) > radius * radius) continue;
      Attributes targetAttrs = mAttributesWrapper.get(targetId).attrs;
      int fire = resistedDamage(rawFire, targetAttrs, stateList(targetId),
          Stat.fireresist, 0);
      if (fire <= 0) continue;
      DamageEvent event = DamageEvent.obtain(sourceId, targetId, fire);
      events.dispatch(event);
      float applied = Math.max(0f, event.damage);
      StatRef hp = targetAttrs.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null || applied <= 0f) continue;
      hp.sub(applied);
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        // The shared melee path emits the primary target's DeathEvent once.
        if (targetId != primaryTargetId) {
          events.dispatch(DeathEvent.obtain(sourceId, targetId));
        }
      }
      affected++;
      log.info("[ASSASSIN_DRAGON_TAIL] phase=area_hit source={} primary={} target={} "
              + "radius={} rawFire={} fire={} applied={}",
          sourceId, primaryTargetId, targetId, radius, rawFire, fire, applied);
    }
    log.info("[ASSASSIN_DRAGON_TAIL] phase=explosion source={} primary={} "
            + "physical={} firePercent={} fireMastery={} rawFire={} radius={} visual={} affected={}",
        sourceId, primaryTargetId, physicalDamage,
        AssassinSkills.getDragonTailFirePercent(skill, skillLevel),
        statInt(sourceAttrs, Stat.passive_fire_mastery), rawFire, radius, visualId, affected);
  }

  /** Server-owned one-shot visual so every connected client sees the same blast. */
  private int createDragonTailExplosionVisual(int sourceId, Vector2 origin) {
    Missiles.Entry row = Riiablo.files.Missiles.get("dragontail missile");
    if (row == null || factory == null) return Engine.INVALID_ENTITY;
    int missileId = factory.createMissile(row, Vector2.X, origin, sourceId);
    if (missileId != Engine.INVALID_ENTITY && mMissile.has(missileId)) {
      com.riiablo.engine.server.component.Missile visual = mMissile.get(missileId);
      visual.nativeLifetimeFrames = Math.max(1, row.Range);
    }
    return missileId;
  }

  private boolean isAlive(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return false;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    return hp != null && hp.asFixed() > 0f;
  }

  private boolean isValidFistsTarget(int sourceId, int targetId) {
    Attributes attrs = mAttributesWrapper.get(targetId).attrs;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    if (hp == null || hp.asFixed() <= 0f) return false;
    if (mMonster.has(targetId) && mNativeUnitFlags.has(targetId)
        && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) {
      return false;
    }
    boolean sourcePlayerAligned = mPlayer.has(sourceId) || mMercenary.has(sourceId)
        || mSummonedPet.has(sourceId);
    boolean targetPlayerAligned = mPlayer.has(targetId) || mMercenary.has(targetId)
        || mSummonedPet.has(targetId);
    return PvpCombatRules.canDamage(
        partyManager, sourceId, targetId, sourcePlayerAligned, targetPlayerAligned);
  }

  private static int rollRange(int min, int max) {
    min = Math.max(0, min);
    max = Math.max(min, max);
    return max > min ? MathUtils.random(min, max) : min;
  }

  private static int resistedDamage(int rawDamage, Attributes defender,
      com.riiablo.engine.server.state.StateList defenderStates,
      short resistStat, int stateResistType) {
    if (rawDamage <= 0 || defender == null) return 0;
    int resistance = statInt(defender, resistStat);
    if (stateResistType >= 0 && defenderStates != null) {
      resistance += defenderStates.getTotalResistModifier(stateResistType);
    }
    if (resistance >= 100) return 0;
    return Math.max(0, rawDamage * (100 - Math.min(75, resistance)) / 100);
  }

  /** Applies Cobra Strike steal after DamageEvent has finalized actual damage. */
  private void applyAssassinProgressiveLeech(int entityId,
      AssassinSkills.ProgressiveRelease release, CombatSystem.CombatResult combat,
      float appliedDamage) {
    if (!mAttributesWrapper.has(entityId) || combat.totalDamage <= 0 || appliedDamage <= 0f) {
      return;
    }
    float physicalApplied = appliedDamage * combat.physicalDamage / combat.totalDamage;
    float life = physicalApplied * Math.max(0, release.lifeLeechPercent) / 100f;
    float mana = physicalApplied * Math.max(0, release.manaLeechPercent) / 100f;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    float lifeAdded = restoreUpToMaximum(attrs, Stat.hitpoints, Stat.maxhp, life);
    float manaAdded = restoreUpToMaximum(attrs, Stat.mana, Stat.maxmana, mana);
    if (lifeAdded > 0f || manaAdded > 0f) {
      log.info("[ASSASSIN_FINISHER] phase=leech source={} physicalApplied={} "
              + "lifePct={} manaPct={} lifeAdded={} manaAdded={}",
          entityId, physicalApplied, release.lifeLeechPercent,
          release.manaLeechPercent, lifeAdded, manaAdded);
    }
  }

  private static float restoreUpToMaximum(
      Attributes attrs, short currentStat, short maximumStat, float amount) {
    if (attrs == null || amount <= 0f) return 0f;
    StatRef current = attrs.get(currentStat, StatRef.obtain());
    StatRef maximum = attrs.get(maximumStat, StatRef.obtain());
    if (current == null || maximum == null) return 0f;
    float before = current.asFixed();
    float after = Math.min(maximum.asFixed(), before + amount);
    if (after <= before) return 0f;
    current.add(after - before);
    return after - before;
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
        stateList(entityId), stateList(targetId), isEntityMoving(targetId));
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
        stateList(entityId), stateList(targetId), isEntityMoving(targetId));
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
    factory.applyNativeUnitFlags(spawned, NativeUnitFlags.MAGGOT_LAY_SUMMON);
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
    factory.applyNativeUnitFlags(spawned, NativeUnitFlags.NEST_SUMMON);
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
        stateList(entityId), stateList(targetId), isEntityMoving(targetId));
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
