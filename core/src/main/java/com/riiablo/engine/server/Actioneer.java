package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntSet;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.Engine;
import com.riiablo.item.Item;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
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
  protected ComponentMapper<com.riiablo.engine.server.component.Missile> mMissile;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<CofReference> mCofReference;

  @com.artemis.annotations.Wire(name = "factory")
  protected EntityFactory factory;

  // teleport-specific components
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Box2DBody> mBox2DBody;

  protected EventSystem events;
  protected Pathfinder pathfinder;

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
      Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();
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
      Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();
      
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
      case 1: { // attack
        if (targetId == Engine.INVALID_ENTITY) break;
        if (!mAttributesWrapper.has(targetId)) break;
        log.debug("{} attack {}", entityId, targetId);

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
            Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();
            
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
            isPlayerEntity(entityId),
            isPlayerEntity(targetId),
            false,
            monsterAttackMinDamage(entityId),
            monsterAttackMaxDamage(entityId),
            monsterAttackRating(entityId));
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

        if (hitpoints.asFixed() <= 0f) {
          log.debug("{} is dead!", targetId);
          events.dispatch(DeathEvent.obtain(entityId, targetId));
        }
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
            Item weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();
            
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
      case 27: // teleport
    mPosition.get(entityId).position.set(targetVec);
    Box2DBody box2dWrapper = mBox2DBody.get(entityId);
        if (box2dWrapper != null) box2dWrapper.body.setTransform(targetVec, 0);
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

  private boolean isPlayerEntity(int entityId) {
    return mClass.has(entityId) && mClass.get(entityId).type == Class.Type.PLR;
  }

  private static int statInt(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
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
      projectile.attackMinDamage = arrayValue(monster.monstats.A2MinD);
      projectile.attackMaxDamage = arrayValue(monster.monstats.A2MaxD);
      projectile.attackRating = arrayValue(monster.monstats.A2TH);
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
        && monster != null && monster.monstats != null
        ? arrayValue(monster.monstats.A2MinD) : 0;
  }

  private int monsterAttackMaxDamage(int entityId) {
    Monster monster = mMonster.has(entityId) ? mMonster.get(entityId) : null;
    return currentMonsterAttackMode(entityId) == Engine.Monster.MODE_A2
        && monster != null && monster.monstats != null
        ? arrayValue(monster.monstats.A2MaxD) : 0;
  }

  private int monsterAttackRating(int entityId) {
    Monster monster = mMonster.has(entityId) ? mMonster.get(entityId) : null;
    return currentMonsterAttackMode(entityId) == Engine.Monster.MODE_A2
        && monster != null && monster.monstats != null
        ? arrayValue(monster.monstats.A2TH) : 0;
  }

  private static int arrayValue(int[] values) {
    return values != null && values.length > 0 ? Math.max(0, values[0]) : 0;
  }
}
