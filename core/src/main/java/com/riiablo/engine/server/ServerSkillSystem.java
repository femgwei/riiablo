package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import com.riiablo.engine.server.item.LootManager;
import com.riiablo.engine.server.item.GroundDropOwnership;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Type;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.ItemData;
import com.riiablo.skill.SkillCodes;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.skill.CorpseConsumption;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntSet;

/**
 * Server-authoritative part of the player skill pipeline.
 *
 * <p>Actioneer owns animation state and emits skill events. This system keeps
 * validation and projectile creation on the server, while the client remains
 * responsible for presentation. Effects are created on {@link SkillDoEvent}
 * so a projectile cannot be spawned before the cast animation reaches its
 * active frame.</p>
 */
public class ServerSkillSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(ServerSkillSystem.class);
  private static final float MULTI_MISSILE_SPREAD_RADIANS = 0.12f;
  private static final int NOVA_MISSILE_COUNT = 64;
  private static final float CHAIN_LIGHTNING_JUMP_RANGE2 = 13f * 13f;
  private final boolean monstersOnly;
  private final Ray<Vector2> auraRay = new Ray<>(new Vector2(), new Vector2());
  private final Collision<Vector2> auraCollision =
      new Collision<>(new Vector2(), new Vector2());

  public ServerSkillSystem() {
    this(false);
  }

  /** Local games use this mode because legacy player presentation owns its projectiles. */
  public ServerSkillSystem(boolean monstersOnly) {
    this.monstersOnly = monstersOnly;
  }

  @Override
  protected void initialize() {
    itemGenerator = world.getSystem(ItemGenerator.class);
  }

  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Velocity> mVelocity;
  private volatile int mercenaryMissileCount;
  private volatile int mercenarySkillDoCount;
  private volatile int mercenaryConfiguredMissiles;
  private volatile int mercenaryLastSrvDoFunc;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<NativeAiTargetOverride> mNativeAiTargetOverride;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<com.riiablo.engine.server.component.Item> mItem;
  protected net.mostlyoriginal.api.event.common.EventSystem events;

  /** Item generator is wired by both local and dedicated server worlds. */
  @com.artemis.annotations.SkipWire
  protected ItemGenerator itemGenerator;
  private final LootManager barbarianLoot = new LootManager();

  @com.artemis.annotations.Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  /** Registered as "factory" by D2GS. */
  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "map", failOnNull = false)
  protected Map map;

  @Subscribe
  public void onSkillCast(SkillCastEvent event) {
    if (monstersOnly) return;
    // Monsters use their existing AI/casting path and do not have player mana.
    if (!mPlayer.has(event.entityId)) return;

    Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) {
      reject(event, 7, "skill data is missing");
      return;
    }

    Player player = mPlayer.get(event.entityId);
    if (event.targetId >= 0 && (mPlayer.has(event.targetId)
        || mMercenary.has(event.targetId) || mSummonedPet.has(event.targetId))
        && !PvpCombatRules.canTarget(partyManager, event.entityId,
            playerAlignmentOwner(event.targetId), true, true)) {
      reject(event, 8, "target player or owned unit is not hostile");
      log.info("[PVP] phase=skill_reject source={} target={} skill={} reason=not_hostile",
          event.entityId, event.targetId, event.skillId);
      return;
    }
    boolean corpseSkill = skill.srvdofunc == 31 || skill.srvdofunc == 55
        || skill.srvdofunc == 58 || skill.srvdofunc == 63
        || skill.srvdofunc == 69 || skill.srvdofunc == 72
        || skill.srvdofunc == 75;
    if (event.targetId >= 0 && mMonster.has(event.targetId) && !corpseSkill
        && mNativeUnitFlags.has(event.targetId)
        && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(event.targetId))) {
      reject(event, 8, "target is not a native combat target");
      log.info("[SKILL_CAST] phase=reject source={} target={} skill={} reason=native_target_flags",
          event.entityId, event.targetId, event.skillId);
      return;
    }
    int skillLevel = player.data != null ? player.data.getSkill(event.skillId) : 1;
    if (mUnitStates.has(event.entityId)
        && mUnitStates.get(event.entityId).stateList != null) {
      skillLevel += mUnitStates.get(event.entityId).stateList.getTotalSkillModifier();
    }
    skillLevel = Math.max(1, skillLevel);
    int casterLevel = 1;
    Attributes attrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    if (attrs == null) {
      reject(event, 7, "caster attributes are missing");
      return;
    }
    StatRef level = attrs.get(Stat.level, StatRef.obtain());
    if (level != null) casterLevel = Math.max(1, level.asInt());
    int validation = NativeSkillResolver.validatePlayerCast(
        player.data, skill, skillLevel, casterLevel);
    if (validation != NativeSkillResolver.OK) {
      String reason;
      switch (validation) {
        case NativeSkillResolver.WRONG_CLASS: reason = "skill does not belong to character class"; break;
        case NativeSkillResolver.MISSING_PREREQUISITE: reason = "skill prerequisite is missing"; break;
        case NativeSkillResolver.LEVEL_TOO_LOW: reason = "caster level is below skill requirement"; break;
        default: reason = "skill has not been learned"; break;
      }
      reject(event, validation, reason);
      return;
    }

    UnitStates casterStates = mUnitStates.has(event.entityId)
        ? mUnitStates.get(event.entityId) : null;
    StateList casterStateList = casterStates != null ? casterStates.stateList : null;
    boolean transformed = casterStateList != null
        && (casterStateList.hasState(StateId.WOLF) || casterStateList.hasState(StateId.BEAR));
    if ((skill.restrict == 2 || transformed)
        && !DruidSkills.isSkillAllowedInCurrentShape(skill, casterStateList)) {
      reject(event, 10, "skill is restricted to the current druid shape");
      log.info("[DRUID_SHAPE_RESTRICT] phase=cast_reject source={} skill={} restrict={} "
              + "wolf={} bear={}",
          event.entityId, skill.skill, skill.restrict,
          casterStateList != null && casterStateList.hasState(StateId.WOLF),
          casterStateList != null && casterStateList.hasState(StateId.BEAR));
      return;
    }

    if (corpseSkill) {
      boolean requiresMonster = skill.srvdofunc == 31 || skill.srvdofunc == 55
          || skill.srvdofunc == 58 || skill.srvdofunc == 63
          || skill.srvdofunc == 72
          || skill.srvdofunc == 75;
      if (!selectableCorpse(event.targetId)
          || requiresMonster && !mMonster.has(event.targetId)
          || (skill.srvdofunc == 31 || skill.srvdofunc == 55
              || skill.srvdofunc == 58 || skill.srvdofunc == 63)
              && isTownCorpse(event.targetId)
          || skill.srvdofunc == 58 && !isReviveableMonster(event.targetId)) {
        reject(event, 3, "skill requires a selectable monster corpse");
        log.info("[CORPSE_SKILL] phase=cast_reject source={} target={} skill={} reason=corpse_eligibility",
            event.entityId, event.targetId, skill.skill);
        return;
      }
    }

    if (skill.srvdofunc == 57 && !isValidIronGolemItem(event.targetId)) {
      reject(event, 3, "Iron Golem requires an identified metal ground item");
      log.info("[NECRO_IRON_GOLEM] phase=cast_reject source={} item={} reason=item_eligibility",
          event.entityId, event.targetId);
      return;
    }

    if (NecromancerSkills.isBoneWall(skill)) {
      Vector2 target = castTargetPoint(event);
      if (target == null || invalidBoneWallPoint(event.entityId, target)) {
        reject(event, 3, "Bone Wall requires a valid non-town ground position");
        log.info("[NECRO_BONE_WALL] phase=cast_reject source={} target={} reason=ground_or_town",
            event.entityId, target);
        return;
      }
    }
    if (NecromancerSkills.isBonePrison(skill)
        && (event.targetId < 0 || !mPosition.has(event.targetId)
            || isTownUnit(event.targetId))) {
      reject(event, 3, "Bone Prison requires a non-town unit target");
      log.info("[NECRO_BONE_PRISON] phase=cast_reject source={} target={} reason=unit_or_town",
          event.entityId, event.targetId);
      return;
    }

    // Native SrvDo080 has no ground-target fallback: it obtains the caster's
    // selected Unit and fails before creating the delay missile when absent.
    if (PaladinSkills.isFistOfTheHeavens(skill)
        && (event.targetId < 0 || !mPosition.has(event.targetId)
            || !hasPositiveLife(event.targetId)
            || !isHostile(event.entityId, event.targetId))) {
      reject(event, 8, "Fist of the Heavens requires a living hostile unit target");
      log.info("[FIST_OF_HEAVENS] phase=cast_reject source={} target={} reason=unit_target",
          event.entityId, event.targetId);
      return;
    }

    ItemData items = player.data != null ? player.data.getItems() : null;
    if (NecromancerSkills.isPoisonDagger(skill)) {
      Item weapon = activeMeleeWeapon(items);
      if (!NecromancerSkills.isPoisonDaggerWeapon(weapon)) {
        reject(event, 9, "Poison Dagger requires an equipped melee dagger");
        log.info("[NECRO_POISON_DAGGER] phase=cast_reject source={} skill={} "
                + "weapon={} reason=requires_dagger",
            event.entityId, event.skillId, weapon != null ? weapon.code : "none");
        return;
      }
      if (event.targetId < 0 || !hasPositiveLife(event.targetId)
          || isTownUnit(event.targetId)) {
        reject(event, 8, "Poison Dagger requires a living non-town target");
        log.info("[NECRO_POISON_DAGGER] phase=cast_reject source={} target={} "
                + "reason=invalid_or_town_target",
            event.entityId, event.targetId);
        return;
      }
    }
    Item rangedWeapon = items != null ? items.getEquippedRangedWeapon() : null;
    if (isAmazonBowSkill(skill) && rangedWeapon == null) {
      reject(event, 9, "skill requires an equipped bow or crossbow");
      return;
    }
    if (requiresRangedAmmo(skill, rangedWeapon)) {
      Item ammo = items.getEquippedAmmo(rangedWeapon);
      if (!hasQuantity(ammo)) {
        reject(event, 9, ammo == null
            ? "matching arrow or bolt quiver is not equipped"
            : "equipped arrow or bolt quiver is empty");
        log.info("[RANGED_AMMO] phase=cast_reject entity={} skill={} weapon={} ammo={} reason={}",
            event.entityId, event.skillId, rangedWeapon.code,
            ammo != null ? ammo.code : "none", ammo == null ? "missing" : "empty");
        return;
      }
    }

    StatRef mana = attrs.get(Stat.mana, StatRef.obtain());
    if (mana == null) {
      reject(event, 1, "caster has no mana stat");
      return;
    }

    float manaCost = NativeSkillResolver.manaCost(skill, skillLevel);
    event.manaCost = manaCost;
    if (manaCost > 0 && mana.asFixed() + 0.0001f < manaCost) {
      reject(event, 1, "not enough mana");
      return;
    }
    if (manaCost > 0) {
      mana.sub(manaCost);
      log.debug("Server skill accepted: entity={}, skill={}, level={}, manaCost={}, manaLeft={}",
          event.entityId, event.skillId, skillLevel, manaCost, mana.asFixed());
    }
  }

  private static Item activeMeleeWeapon(ItemData items) {
    if (items == null) return null;
    Item right = items.getEquipped(BodyLoc.RARM);
    return right != null ? right : items.getEquipped(BodyLoc.LARM);
  }

  private boolean isTownUnit(int entityId) {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper != null && wrapper.zone != null && wrapper.zone.isTown();
  }

  private Vector2 castTargetPoint(SkillCastEvent event) {
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      return new Vector2(mPosition.get(event.targetId).position);
    }
    return event.targetVec != null ? new Vector2(event.targetVec) : null;
  }

  private boolean invalidBoneWallPoint(int source, Vector2 target) {
    if (target == null || !Float.isFinite(target.x) || !Float.isFinite(target.y)) return true;
    if (!mMapWrapper.has(source)) return false;
    MapWrapper wrapper = mMapWrapper.get(source);
    if (wrapper == null || wrapper.map == null) return false;
    com.riiablo.map.Map.Zone zone = wrapper.map.getZone(target);
    return zone == null || zone.isTown();
  }

  private boolean hasPositiveLife(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return false;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef life = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    return life != null && life.asFixed() > 0f;
  }

  @Subscribe
  public void onSkillDo(SkillDoEvent event) {
    if (mMercenary.has(event.entityId)) mercenarySkillDoCount++;
    if (mPlayer.has(event.entityId)) {
      com.badlogic.gdx.Gdx.app.log("ServerSkillSystem", String.format(
          "[SKILL_DO] phase=receive entity=%d skill=%d target=%d srvDoFunc=%d cltDoFunc=%d",
          event.entityId, event.skillId, event.targetId, event.srvdofunc, event.cltdofunc));
    }
    if (!mPosition.has(event.entityId)) return;
    if (!mPlayer.has(event.entityId) && !mMonster.has(event.entityId)) return;
    Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) return;
    if (mPlayer.has(event.entityId) && mPlayer.get(event.entityId).data != null) {
      ItemData items = mPlayer.get(event.entityId).data.getItems();
      Item weapon = items.getEquippedRangedWeapon();
      if (requiresRangedAmmo(skill, weapon) && !hasQuantity(items.getEquippedAmmo(weapon))) {
        log.info("[RANGED_AMMO] phase=do_reject entity={} skill={} weapon={} reason=missing_or_empty",
            event.entityId, event.skillId, weapon != null ? weapon.code : "none");
        return;
      }
    }
    // Local games retain their legacy player projectile presentation, but
    // native summons are server entities rather than visual projectiles and
    // must still be created in the local authoritative world.
    if (monstersOnly && !mMonster.has(event.entityId)
        && event.srvdofunc != 15 && event.srvdofunc != 16
        && event.srvdofunc != 18 && event.srvdofunc != 44 && event.srvdofunc != 45
        && event.srvdofunc != 22 && event.srvdofunc != 49 && event.srvdofunc != 54
        && event.srvdofunc != 68 && event.srvdofunc != 71
        && event.srvdofunc != 31 && event.srvdofunc != 55 && event.srvdofunc != 56
        && event.srvdofunc != 57 && event.srvdofunc != 58
        && event.srvdofunc != 30 && event.srvdofunc != 59 && event.srvdofunc != 61
        && event.srvdofunc != 60 && event.srvdofunc != 62 && event.srvdofunc != 63
        && event.srvdofunc != 20 && event.srvdofunc != 73 && event.srvdofunc != 80
        && event.srvdofunc != 114 && event.srvdofunc != 115 && event.srvdofunc != 119
        && skill.srvdofunc != 15 && skill.srvdofunc != 16
        && skill.srvdofunc != 18 && skill.srvdofunc != 44 && skill.srvdofunc != 45
        && skill.srvdofunc != 22 && skill.srvdofunc != 49 && skill.srvdofunc != 54
        && skill.srvdofunc != 68 && skill.srvdofunc != 71
        && skill.srvdofunc != 31 && skill.srvdofunc != 55 && skill.srvdofunc != 56
        && skill.srvdofunc != 57 && skill.srvdofunc != 58
        && skill.srvdofunc != 30 && skill.srvdofunc != 59 && skill.srvdofunc != 61
        && skill.srvdofunc != 60 && skill.srvdofunc != 62 && skill.srvdofunc != 63
        && skill.srvdofunc != 20 && skill.srvdofunc != 73 && skill.srvdofunc != 80
        && skill.srvdofunc != 114 && skill.srvdofunc != 115 && skill.srvdofunc != 119
        && !PaladinSkills.isHolyBolt(skill)) {
      consumeRangedAmmoForSkill(event, skill);
      return;
    }
    int skillLevel = getSkillLevel(event.entityId, event.skillId);

    Vector2 start = mPosition.get(event.entityId).position;
    if (event.srvdofunc == 20 || skill.srvdofunc == 20) {
      applyStaticField(event, skill, skillLevel, start);
      return;
    }
    if (event.srvdofunc == 80 || skill.srvdofunc == 80) {
      spawnPaladinFistOfTheHeavens(event, skill, skillLevel);
      return;
    }
    if (event.srvdofunc == 73 || skill.srvdofunc == 73) {
      spawnPaladinBlessedHammer(event, skill, skillLevel, start);
      return;
    }
    if (event.srvdofunc == 31 || skill.srvdofunc == 31) {
      raiseNecromancerSkeleton(event, skill, skillLevel);
      return;
    }
    if (event.srvdofunc == 56 || skill.srvdofunc == 56) {
      spawnNecromancerGolem(event, skill, skillLevel, start, false);
      return;
    }
    if (event.srvdofunc == 57 || skill.srvdofunc == 57) {
      spawnNecromancerGolem(event, skill, skillLevel, start, true);
      return;
    }
    if (event.srvdofunc == 58 || skill.srvdofunc == 58) {
      reviveNecromancerMonster(event, skill, skillLevel);
      return;
    }
    if (event.srvdofunc == 55 || skill.srvdofunc == 55) {
      explodeNecromancerCorpse(event, skill, skillLevel);
      return;
    }
    if (event.srvdofunc == 63 || skill.srvdofunc == 63) {
      spawnNecromancerPoisonExplosion(event, skill, skillLevel);
      return;
    }
    if (event.srvdofunc == 60 || skill.srvdofunc == 60) {
      spawnNecromancerBoneWall(event, skill, skillLevel, start);
      return;
    }
    if (event.srvdofunc == 62 || skill.srvdofunc == 62) {
      spawnNecromancerBonePrison(event, skill, skillLevel);
      return;
    }
    if (NecromancerSkills.isPoisonNova(skill)) {
      spawnNecromancerPoisonNova(event, skill, skillLevel, start);
      return;
    }
    if (handleBarbarianCorpseSkill(event, skill, skillLevel)) return;
    if ((event.srvdofunc == 30 || skill.srvdofunc == 30
        || event.srvdofunc == 59 || skill.srvdofunc == 59
        || event.srvdofunc == 61 || skill.srvdofunc == 61)) {
      applyNecromancerCurse(event, skill, skillLevel, start);
      return;
    }
    if (event.srvdofunc == 71 || skill.srvdofunc == 71) {
      applyTaunt(event, skill, skillLevel, start);
      return;
    }
    if (event.srvdofunc == 68 || skill.srvdofunc == 68) {
      applyBasicWarCry(event, skill, skillLevel);
      spawnNova(event, skill, start);
      return;
    }
    if (event.srvdofunc == 18 || skill.srvdofunc == 18) {
      if (isVenom(skill)) {
        applyVenom(event, skill, skillLevel);
        return;
      }
      if (isBoneArmor(skill)) {
        applyBoneArmor(event, skill, skillLevel);
        return;
      }
      if (PaladinSkills.isHolyShield(skill)) {
        applyHolyShield(event, skill, skillLevel);
        return;
      }
    }
    if (event.srvdofunc == 54 || skill.srvdofunc == 54) {
      armBladeShield(event, skill, skillLevel);
      return;
    }
    // Actioneer owns Rabies' precomputed melee packet and creates its
    // target-attached SrvDo30 controller. Generic projectile creation would
    // incorrectly launch rabiesplague from the caster a second time.
    if (event.srvdofunc == 121 || skill.srvdofunc == 121) return;
    // D2MOO SrvDo034/SrvDo035 are melee combat records. Actioneer resolves
    // their hit and progressive-state update at this same keyframe. Their
    // SrvMissileA-D columns describe charge-release stages and must not be
    // spawned as generic projectiles while building a charge.
    if (AssassinSkills.isProgressiveStrike(event.srvdofunc)
        || AssassinSkills.isProgressiveStrike(skill.srvdofunc)) {
      log.info("[ASSASSIN_CHARGE] phase=projectile_skip source={} skill={} srvDoFunc={} reason=charge_up",
          event.entityId, event.skillId, event.srvdofunc);
      return;
    }
    if ((event.srvdofunc == 23 || skill.srvdofunc == 23)
        && "SpiderLay".equalsIgnoreCase(skill.skill)) {
      applySpiderLayState(event, skill, skillLevel);
      return;
    }
    // D2MOO SKILLS_SrvDo047_CloakOfShadows applies Dim Vision through an
    // aura callback.  It does not create a projectile; the state snapshot is
    // the authoritative multiplayer effect and is consumed by clients.
    if (event.srvdofunc == 47 || skill.srvdofunc == 47 || isCloakOfShadows(skill)) {
      applyCloakOfShadows(event, skill, skillLevel, start);
      return;
    }
    // D2MOO SrvDo049 creates one owned Shadow Warrior/Master pet.  Keep the
    // summon data-driven because this project’s Skills.txt uses the native
    // ids 268/279 rather than the legacy SkillId constants.
    if (event.srvdofunc == 49 || skill.srvdofunc == 49) {
      spawnAssassinShadow(event, skill, skillLevel, start);
      return;
    }
    // D2MOO SrvDo044/SrvDo045 create an owned Blade Sentinel/Sentry unit.
    // The trap entity owns its later attack cadence; do not treat this as a
    // normal player missile or the trap would recursively summon itself.
    if ((event.srvdofunc == 44 || skill.srvdofunc == 44
        || event.srvdofunc == 45 || skill.srvdofunc == 45)
        && mPlayer.has(event.entityId)) {
      spawnAssassinTrap(event, skill, skillLevel, start);
      return;
    }
    if (event.srvdofunc == 22 || skill.srvdofunc == 22) {
      spawnNova(event, skill, start);
      return;
    }
    // D2MOO SKILLS_SrvDo017_ChargedBolt_BoltSentry is also used by the
    // Sorceress Charged Bolt row.  The native callback evaluates calc1 and
    // creates one independently collidable missile per count; falling through
    // to the generic path would create only one bolt and lose the native
    // Charged-Bolt path initialiser.
    if (event.skillId == SkillId.CHARGED_BOLT
        || "Charged Bolt".equalsIgnoreCase(skill.skill)) {
      spawnSorceressChargedBolt(event, skill, start, skillLevel);
      return;
    }
    if (event.srvdofunc == 8 || skill.srvdofunc == 8) {
      spawnMultipleShotTeethShockWave(event, skill, start);
      return;
    }
    if (event.srvdofunc == 10 || skill.srvdofunc == 10
        || event.skillId == SkillId.GUIDED_ARROW) {
      spawnGuidedArrow(event, skill, start, skillLevel);
      return;
    }
    if (event.srvdofunc == 12 || skill.srvdofunc == 12
        || event.skillId == SkillId.STRAFE) {
      spawnStrafe(event, skill, start, skillLevel);
      return;
    }
    if (event.srvdofunc == 11 || skill.srvdofunc == 11) {
      spawnChargedStrike(event, skill, start);
      return;
    }
    if (event.srvdofunc == 14 || skill.srvdofunc == 14) {
      spawnLightningStrike(event, skill);
      return;
    }
    if (event.srvdofunc == 15 || skill.srvdofunc == 15) {
      spawnAmazonSummon(event, skill, start, false);
      return;
    }
    if (event.srvdofunc == 16 || skill.srvdofunc == 16) {
      spawnAmazonSummon(event, skill, start, true);
      return;
    }
    // D2MOO SKILLS_SrvDo114/115/119 are all native Druid summon paths.  They
    // create monster units through the same owner/pet-list pipeline; only the
    // spawn position and post-spawn aura differ.  Keep them out of the generic
    // missile fallback so every client receives one authoritative entity.
    if (event.srvdofunc == 114 || skill.srvdofunc == 114
        || event.srvdofunc == 115 || skill.srvdofunc == 115
        || event.srvdofunc == 119 || skill.srvdofunc == 119) {
      spawnDruidSummon(event, skill, skillLevel, start);
      return;
    }
    if (event.srvdofunc == 24 || skill.srvdofunc == 24) {
      spawnFireWall(event, skill, start);
      return;
    }
    if (event.srvdofunc == 28 || skill.srvdofunc == 28) {
      spawnMeteor(event, skill, start);
      return;
    }
    if (event.skillId == SkillId.CHAIN_LIGHTNING
        || (skill.skill != null
            && skill.skill.toLowerCase(java.util.Locale.ROOT).contains("chain lightning"))) {
      spawnChainLightning(event, skill, start);
      return;
    }

    Vector2 target = new Vector2();
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      target.set(mPosition.get(event.targetId).position);
    } else if (event.targetVec != null) {
      target.set(event.targetVec);
    } else {
      target.set(start).add(1, 0);
    }
    target.sub(start);
    if (target.isZero(0.0001f)) target.set(1, 0);
    target.nor();

    String throwableMissile = resolveThrowableMissile(event.entityId, event.skillId, skill);
    String normalAttackMissile = resolveNormalAttackMissile(event.entityId, event.skillId);
    boolean hasGenericServerMissile = hasText(skill.srvmissile);
    boolean hasServerMissile = hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled);
    // D2MOO's D2GAME_SKILLS_Handler always creates Skills.txt/SrvMissile
    // after dispatching SrvDoFunc. SrvMissileA-D are parameters consumed by
    // individual SrvDo functions and must not replace that generic missile.
    // Client missiles are presentation fallbacks, not additional
    // authoritative projectiles.  Mixing cltMissileB with srvMissileA made
    // skills such as FetishInferno spawn an extra damaging stream.
    String[] missileNames = hasGenericServerMissile
        ? new String[] {skill.srvmissile, null, null, null}
        : hasServerMissile
        ? new String[] {skill.srvmissilea, skill.srvmissileb,
            skill.srvmissilec, skill.srvmissiled}
        : hasText(skill.cltmissile)
        ? new String[] {skill.cltmissile, null, null, null}
        : new String[] {skill.cltmissilea, skill.cltmissileb,
            skill.cltmissilec, skill.cltmissiled};
    if ((event.srvdofunc == 85 || skill.srvdofunc == 85) && mMonster.has(event.entityId)) {
      missileNames[0] = resolveMonsterChainMissile(mMonster.get(event.entityId), missileNames[0]);
    }
    int configuredCount = 0;
    for (String name : missileNames) {
      if (name != null && !name.isEmpty()) configuredCount++;
    }
    if (mMercenary.has(event.entityId)) {
      mercenaryConfiguredMissiles = configuredCount;
      mercenaryLastSrvDoFunc = event.srvdofunc;
    }
    if (configuredCount == 0 && throwableMissile != null && !throwableMissile.isEmpty()) {
      missileNames[0] = throwableMissile;
      configuredCount = 1;
    }
    if (configuredCount == 0 && normalAttackMissile != null) {
      missileNames[0] = normalAttackMissile;
      configuredCount = 1;
    }
    if (event.skillId == SkillCodes.throw_ || event.skillId == SkillCodes.left_hand_throw
        || event.srvdofunc == 3 || event.srvdofunc == 5) {
      com.badlogic.gdx.Gdx.app.log("ServerSkillSystem", String.format(
          "[MISSILE_CREATE] phase=resolve entity=%d skill=%d weaponMissile=%s configured=%d",
          event.entityId, event.skillId, throwableMissile, configuredCount));
    }
    if (event.skillId == SkillCodes.throw_ || event.skillId == SkillCodes.left_hand_throw
        || event.srvdofunc == 3 || event.srvdofunc == 5) {
      log.info("[THROW_ATTACK] phase=missile_resolve entity={} skill={} srvDoFunc={} "
              + "weaponCode={} configuredMissiles={} missileA={} missileB={} missileC={} missileD={}",
          event.entityId, event.skillId, event.srvdofunc, throwableMissile, configuredCount,
          missileNames[0], missileNames[1], missileNames[2], missileNames[3]);
    }
    if (configuredCount == 0) return;

    IntSet sharedHitTargets = configuredCount > 1 ? new IntSet() : null;
    int ordinal = 0;
    int created = 0;
    for (String missileName : missileNames) {
      if (missileName == null || missileName.isEmpty()) continue;
      Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
      if (missile == null) {
        log.warn("Server skill missile lookup failed: entity={}, skill={}, missile={}",
            event.entityId, event.skillId, missileName);
        continue;
      }

      Vector2 direction = new Vector2(target);
      if (configuredCount > 1) {
        float offset = (ordinal - (configuredCount - 1) * 0.5f)
            * MULTI_MISSILE_SPREAD_RADIANS;
        direction.rotateRad(offset);
      }
      int missileId = createMissile(missile, direction, start, event.entityId,
          sharedHitTargets, skillLevel);
      if (event.skillId == SkillCodes.throw_ || event.skillId == SkillCodes.left_hand_throw
          || event.srvdofunc == 3 || event.srvdofunc == 5) {
        com.badlogic.gdx.Gdx.app.log("ServerSkillSystem", String.format(
            "[MISSILE_CREATE] phase=create entity=%d skill=%d missile=%s missileId=%d owner=%d",
            event.entityId, event.skillId, missile.Missile, missileId, event.entityId));
      }
      if (missileId < 0) {
        log.warn("[MISSILE_CREATE] phase=failed entity={} owner={} skillId={} missile={}",
            event.entityId, event.entityId, event.skillId, missile.Missile);
        ordinal++;
        continue;
      }
      created++;
      initializeSkillDamage(missileId, skill, event.entityId, skillLevel);
      if (event.skillId == SkillCodes.throw_ || event.skillId == SkillCodes.left_hand_throw
          || event.srvdofunc == 3 || event.srvdofunc == 5) {
        log.info("[MISSILE_CREATE] phase=throw entity={} missileId={} owner={} missile={} "
                + "speed={} range={} start=({}, {}) direction=({}, {})", event.entityId,
            missileId, event.entityId, missile.Missile, missile.Vel, missile.Range,
            start.x, start.y, direction.x, direction.y);
      }
      log.debug("Server skill projectile: entity={}, skill={}, missile={}, entityId={}, dir=({}, {})",
          event.entityId, event.skillId, missileName, missileId, direction.x, direction.y);
      if (mMonster.has(event.entityId)) {
        log.info("[MONSTER_SKILL] phase=missile entity={} skillId={} missileId={} missile={} "
                + "speed={} range={} direction=({}, {})",
            event.entityId, event.skillId, missileId, missile.Missile,
            missile.Vel, missile.Range, direction.x, direction.y);
      }
      ordinal++;
    }
    if (created > 0) consumeRangedAmmoForSkill(event, skill);
  }

  /** D2MOO SrvDo020: immediate authoritative current-life area damage. */
  private void applyStaticField(
      SkillDoEvent event, Skills.Entry skill, int skillLevel, Vector2 origin) {
    if (!SorceressSkills.isStaticField(skill)) {
      log.warn("[STATIC_FIELD] phase=reject source={} skill={} reason=native_row_mismatch",
          event.entityId, event.skillId);
      return;
    }
    int range = SorceressSkills.getStaticFieldRadius(skill, skillLevel);
    int damagePercent = SorceressSkills.getStaticFieldDamagePercent(skill, skillLevel);
    int minimumDamageFixed =
        SorceressSkills.getStaticFieldMinimumDamageFixed(skill, skillLevel);
    int difficulty = combatDifficulty(event.entityId, event.entityId);
    com.riiablo.codec.excel.DifficultyLevels.Entry difficultyRow =
        Riiablo.files != null && Riiablo.files.DifficultyLevels != null
            ? Riiablo.files.DifficultyLevels.get(difficulty) : null;
    boolean expansion = !mPlayer.has(event.entityId)
        || mPlayer.get(event.entityId).data == null
        || mPlayer.get(event.entityId).data.isExpansion();
    int floorPercent = SorceressSkills.getStaticFieldLifeFloorPercent(
        difficultyRow, expansion);
    int damageType = staticFieldDamageType(skill.EType);
    int auraFilter = skill.aurafilter != 0 ? skill.aurafilter : 0x8783;
    float range2 = (float) range * range;
    IntBag candidates = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int hit = 0;
    int skippedAtFloor = 0;
    for (int i = 0; i < candidates.size(); i++) {
      int targetId = candidates.get(i);
      if (!isStaticFieldTarget(event.entityId, targetId, auraFilter)
          || origin.dst2(mPosition.get(targetId).position) > range2) continue;
      Attributes target = mAttributesWrapper.get(targetId).attrs;
      StatRef hp = target != null ? target.get(Stat.hitpoints, StatRef.obtain()) : null;
      StatRef maxHp = target != null ? target.get(Stat.maxhp, StatRef.obtain()) : null;
      if (hp == null || maxHp == null) continue;
      int rawFixed = SorceressSkills.calculateStaticFieldRawDamageFixed(
          toFixed8(hp.asFixed()), toFixed8(maxHp.asFixed()), damagePercent,
          minimumDamageFixed, floorPercent);
      if (rawFixed <= 0) {
        skippedAtFloor++;
        continue;
      }
      StateList targetStates = mUnitStates.has(targetId)
          ? mUnitStates.get(targetId).stateList : null;
      CombatSystem.StaticFieldDamageResult resolved =
          CombatSystem.INSTANCE.calculateStaticFieldDamage(
              target, mPlayer.has(targetId), mPlayer.has(event.entityId), damageType,
              rawFixed, targetStates, difficulty);
      if (resolved.absorbedLifeFixed > 0) {
        float restored = resolved.absorbedLifeFixed / 256f;
        hp.add(Math.max(0f, Math.min(restored, maxHp.asFixed() - hp.asFixed())));
      }
      if (resolved.damageFixed <= 0) continue;
      DamageEvent damage = DamageEvent.obtain(
          event.entityId, targetId, resolved.damageFixed / 256f);
      if (events != null) events.dispatch(damage);
      hp.sub(Math.max(0f, damage.damage));
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        if (events != null) events.dispatch(DeathEvent.obtain(event.entityId, targetId));
      }
      hit++;
    }
    log.info("[STATIC_FIELD] phase=apply source={} skill={} level={} range={} filter=0x{} "
            + "damagePercent={} minimumFixed={} floorPercent={} difficulty={} hit={} "
            + "skippedAtFloor={}",
        event.entityId, event.skillId, skillLevel, range,
        Integer.toHexString(auraFilter), damagePercent, minimumDamageFixed,
        floorPercent, difficulty, hit, skippedAtFloor);
  }

  private boolean isStaticFieldTarget(int sourceId, int targetId, int filter) {
    if (sourceId == targetId || mCorpse.has(targetId) || !mPosition.has(targetId)
        || !mAttributesWrapper.has(targetId) || !sameZone(sourceId, targetId)) return false;
    boolean player = mPlayer.has(targetId);
    boolean monster = mMonster.has(targetId);
    if (!player && !monster) return false;
    if ((player && (filter & 0x0001) == 0)
        || (monster && (filter & 0x0002) == 0)) return false;
    if (monster) {
      Monster target = mMonster.get(targetId);
      if (target.monstats != null && target.monstats.npc) return false;
      if ((filter & 0x0080) != 0 && mNativeUnitFlags.has(targetId)
          && !NativeTargeting.canBeAttacked(mNativeUnitFlags.get(targetId))) return false;
      if ((filter & 0x0400) != 0) {
        if (target.monstats2 != null && target.monstats2.noSel) return false;
        if (mNativeUnitFlags.has(targetId)
            && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) return false;
      }
    }
    if ((filter & (0x0100 | 0x2000)) != 0 && isTownUnit(targetId)) return false;
    if ((filter & 0x8000) != 0 && !isHostile(sourceId, targetId)) return false;
    return (filter & 0x0200) == 0 || hasStaticFieldLineOfSight(sourceId, targetId);
  }

  private boolean hasStaticFieldLineOfSight(int sourceId, int targetId) {
    Map currentMap = null;
    if (mMapWrapper.has(sourceId)) currentMap = mMapWrapper.get(sourceId).map;
    if (currentMap == null && mMapWrapper.has(targetId)) {
      currentMap = mMapWrapper.get(targetId).map;
    }
    if (currentMap == null
        || currentMap.getZone(mPosition.get(sourceId).position) == null) return true;
    auraRay.set(mPosition.get(sourceId).position, mPosition.get(targetId).position);
    return !currentMap.castRay(auraRay, DT1.Tile.FLAG_BLOCK_JUMP, 0, auraCollision);
  }

  private static int staticFieldDamageType(String element) {
    if ("fire".equalsIgnoreCase(element)) return CombatSystem.DAMAGE_FIRE;
    if ("cold".equalsIgnoreCase(element)) return CombatSystem.DAMAGE_COLD;
    if ("pois".equalsIgnoreCase(element) || "poison".equalsIgnoreCase(element)) {
      return CombatSystem.DAMAGE_POISON;
    }
    if ("mag".equalsIgnoreCase(element) || "magic".equalsIgnoreCase(element)) {
      return CombatSystem.DAMAGE_MAGIC;
    }
    return CombatSystem.DAMAGE_LIGHTNING;
  }

  private static int toFixed8(float value) {
    if (!(value > 0f)) return 0;
    double fixed = Math.floor(value * 256d);
    return fixed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) fixed;
  }

  /** D2MOO SrvDo023: SpiderLay installs a movement state; StateUpdater emits its trail. */
  private void applySpiderLayState(SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (!mUnitStates.has(event.entityId)) {
      log.warn("[SPIDER_LAY] phase=reject entity={} reason=no_unit_states", event.entityId);
      return;
    }
    UnitStates states = mUnitStates.get(event.entityId);
    if (states.stateList == null) states.init(event.entityId);
    int duration = SkillFormula.evaluate(skill.auralencalc, skill, skillLevel);
    if (duration <= 0) duration = 250;
    UnitState state = states.stateList.addState(
        StateId.SPIDERLAY, duration, skillLevel, event.entityId);
    if (state != null) {
      state.skillId = event.skillId;
      state.needsSync = true;
    }
    log.info("[SPIDER_LAY] phase=state entity={} skill={} level={} duration={}",
        event.entityId, event.skillId, skillLevel, duration);
  }

  private void applyVenom(SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (!mUnitStates.has(event.entityId)) return;
    UnitStates states = mUnitStates.get(event.entityId);
    if (states.stateList == null) states.init(event.entityId);
    UnitState venom = AssassinSkills.applyVenomState(
        states.stateList, skill, skillLevel, event.entityId);
    if (venom == null) {
      log.warn("[ASSASSIN_VENOM] phase=reject entity={} skill={} reason=native_state_data",
          event.entityId, event.skillId);
      return;
    }
    log.info("[ASSASSIN_VENOM] phase=apply entity={} skill={} level={} duration={} "
            + "poison={}..{} poisonLength={}",
        event.entityId, event.skillId, skillLevel, venom.duration,
        venom.poisonMinDamage, venom.poisonMaxDamage, venom.poisonLengthOverride);
  }

  /** Native SrvDo018 defensive-buff path for Necromancer Bone Armor. */
  private void applyBoneArmor(SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (!mUnitStates.has(event.entityId)) mUnitStates.create(event.entityId).init(event.entityId);
    UnitStates states = mUnitStates.get(event.entityId);
    if (states.stateList == null) states.init(event.entityId);
    UnitState armor = NecromancerSkills.applyBoneArmorState(
        states.stateList, skill, skillLevel, event.entityId,
        name -> getBaseSkillLevel(event.entityId, name),
        name -> Riiablo.files.skills.get(name));
    if (armor == null) {
      log.warn("[NECRO_BONE_ARMOR] phase=reject entity={} skill={} reason=native_state_data",
          event.entityId, event.skillId);
      return;
    }
    log.info("[NECRO_BONE_ARMOR] phase=apply entity={} skill={} level={} duration={} "
            + "absorb={}/{}",
        event.entityId, event.skillId, skillLevel, armor.duration,
        armor.runtimeValue, armor.getStatContributionValue(Stat.bonearmormax));
  }

  /** Native SrvDo018 defensive-buff path for Paladin Holy Shield. */
  private void applyHolyShield(SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (!mPlayer.has(event.entityId) || !hasEquippedShield(event.entityId)) {
      log.info("[PALADIN_HOLY_SHIELD] phase=do_reject entity={} skill={} reason=no_shield",
          event.entityId, event.skillId);
      return;
    }
    if (!mUnitStates.has(event.entityId)) mUnitStates.create(event.entityId).init(event.entityId);
    UnitStates states = mUnitStates.get(event.entityId);
    if (states.stateList == null) states.init(event.entityId);
    UnitState shield = PaladinSkills.applyHolyShieldState(
        states.stateList, skill, skillLevel, event.entityId,
        name -> getBaseSkillLevel(event.entityId, name));
    if (shield == null) {
      log.warn("[PALADIN_HOLY_SHIELD] phase=do_reject entity={} skill={} reason=native_state_data",
          event.entityId, event.skillId);
      return;
    }
    log.info("[PALADIN_HOLY_SHIELD] phase=do_apply entity={} skill={} level={} duration={} "
            + "block={} defense={}", event.entityId, event.skillId, shield.level,
        shield.duration, shield.getStatContributionValue(Stat.toblock),
        shield.getStatContributionValue(Stat.skill_armor_percent));
  }

  private boolean hasEquippedShield(int entityId) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return false;
    ItemData items = mPlayer.get(entityId).data.getItems();
    if (items == null) return false;
    Item left = items.getEquipped(BodyLoc.LARM);
    Item right = items.getEquipped(BodyLoc.RARM);
    return (left != null && left.type != null && left.type.is(com.riiablo.item.Type.SHLD))
        || (right != null && right.type != null && right.type.is(com.riiablo.item.Type.SHLD));
  }

  /** D2MOO SrvDo060: central segment plus two perpendicular maker paths. */
  private void spawnNecromancerBoneWall(
      SkillDoEvent event, Skills.Entry skill, int skillLevel, Vector2 caster) {
    if (factory == null || !NecromancerSkills.isBoneWall(skill)) return;
    MonStats.Entry summon = resolveSummonMonster(skill.summon);
    if (summon == null) {
      log.warn("[NECRO_BONE_WALL] phase=reject source={} reason=missing_summon row={}",
          event.entityId, skill.summon);
      return;
    }
    Vector2 center = resolveTargetPoint(event, caster, new Vector2());
    int duration = NecromancerSkills.getBoneWallDurationFrames(skill);
    int controller = createBoneWallSegment(
        event.entityId, summon, skill, skillLevel, duration, center, center,
        Engine.INVALID_ENTITY);
    if (controller == Engine.INVALID_ENTITY) {
      log.info("[NECRO_BONE_WALL] phase=reject source={} position=({}, {}) reason=center_blocked",
          event.entityId, center.x, center.y);
      return;
    }
    SummonedPet root = mSummonedPet.has(controller) ? mSummonedPet.get(controller) : null;
    if (root != null) root.controllerId = controller;

    Vector2 direction = boneWallDirection(caster, center, new Vector2());
    int perSide = NecromancerSkills.getBoneWallSegmentsPerSide(skill, skillLevel);
    IntSet coordinates = new IntSet(perSide * 2 + 1);
    coordinates.add(coordinateKey(MathUtils.round(center.x), MathUtils.round(center.y)));
    int created = 1;
    for (int side : new int[] {-1, 1}) {
      for (int index = 1; index <= perSide; index++) {
        // SrvDo13 emits a segment whenever the maker crosses a new subtile.
        Vector2 position = new Vector2(center).mulAdd(direction, side * index);
        int x = MathUtils.round(position.x);
        int y = MathUtils.round(position.y);
        if (!coordinates.add(coordinateKey(x, y))) continue;
        if (createBoneWallSegment(event.entityId, summon, skill, skillLevel,
            duration, new Vector2(x, y), center, controller) != Engine.INVALID_ENTITY) {
          created++;
        }
      }
    }
    log.info("[NECRO_BONE_WALL] phase=created source={} skill={} level={} controller={} "
            + "requested={} created={} duration={} center=({}, {}) direction=({}, {})",
        event.entityId, skill.Id, skillLevel, controller, 1 + perSide * 2, created,
        duration, center.x, center.y, direction.x, direction.y);
  }

  /** D2MOO SrvDo062: twelve fixed offsets around the selected unit. */
  private void spawnNecromancerBonePrison(
      SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (factory == null || !NecromancerSkills.isBonePrison(skill)
        || event.targetId < 0 || !mPosition.has(event.targetId)
        || isTownUnit(event.targetId)) {
      log.info("[NECRO_BONE_PRISON] phase=reject source={} target={} reason=target_or_town",
          event.entityId, event.targetId);
      return;
    }
    MonStats.Entry summon = resolveSummonMonster(skill.summon);
    if (summon == null) {
      log.warn("[NECRO_BONE_PRISON] phase=reject source={} reason=missing_summon row={}",
          event.entityId, skill.summon);
      return;
    }
    Vector2 center = new Vector2(mPosition.get(event.targetId).position);
    int duration = NecromancerSkills.getBoneWallDurationFrames(skill);
    int controller = Engine.INVALID_ENTITY;
    int created = 0;
    int[] offset = new int[2];
    for (int index = 0; index < NecromancerSkills.getBonePrisonSegmentCount(); index++) {
      NecromancerSkills.getBonePrisonOffset(index, offset);
      Vector2 position = new Vector2(center.x + offset[0], center.y + offset[1]);
      int segment = createBoneWallSegment(event.entityId, summon, skill, skillLevel,
          duration, position, center, controller);
      if (segment == Engine.INVALID_ENTITY) continue;
      if (controller == Engine.INVALID_ENTITY) {
        controller = segment;
        SummonedPet root = mSummonedPet.has(segment) ? mSummonedPet.get(segment) : null;
        if (root != null) root.controllerId = segment;
      }
      created++;
    }
    log.info("[NECRO_BONE_PRISON] phase=created source={} target={} skill={} level={} "
            + "controller={} requested={} created={} duration={} center=({}, {})",
        event.entityId, event.targetId, skill.Id, skillLevel, controller,
        NecromancerSkills.getBonePrisonSegmentCount(), created, duration,
        center.x, center.y);
  }

  private int createBoneWallSegment(int owner, MonStats.Entry summon, Skills.Entry skill,
      int skillLevel, int duration, Vector2 position, Vector2 facing, int controller) {
    int entity = factory.createBoneWallSegment(
        owner, summon, skill.Id, skillLevel, duration, position.x, position.y);
    if (entity == Engine.INVALID_ENTITY) return entity;
    if (mSummonedPet.has(entity)) {
      SummonedPet pet = mSummonedPet.get(entity);
      pet.boneWall = true;
      pet.controllerId = controller == Engine.INVALID_ENTITY ? entity : controller;
    }
    applySummonSkillStats(owner, entity, skill, skillLevel, true);
    if (mCofReference.has(entity) && skill.summode != null && !skill.summode.isEmpty()) {
      int mode = Riiablo.files.MonMode.index(skill.summode);
      if (mode >= 0) mCofReference.get(entity).mode = (byte) mode;
    }
    if (mAngle.has(entity)) {
      Vector2 direction = mAngle.get(entity).target.set(facing).sub(position);
      if (direction.isZero(0.0001f)) direction.set(1f, 0f);
      direction.nor();
    }
    return entity;
  }

  static Vector2 boneWallDirection(Vector2 caster, Vector2 target, Vector2 out) {
    out.set(target).sub(caster);
    if (out.isZero(0.0001f)) out.set(1f, 0f);
    return out.rotate90(1).nor();
  }

  private static int coordinateKey(int x, int y) {
    return x * 73856093 ^ y * 19349663;
  }

  private void armBladeShield(SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (!mUnitStates.has(event.entityId)) return;
    UnitStates states = mUnitStates.get(event.entityId);
    if (states.stateList == null) states.init(event.entityId);
    UnitState bladeShield = states.stateList.getState(StateId.BLADESHIELD);
    if (bladeShield == null || bladeShield.skillId != event.skillId) {
      // Synthetic/server-only event paths may not have run Actioneer's start
      // callback. Recreate the same native stat list before arming it.
      bladeShield = AssassinSkills.applyBladeShieldState(
          states.stateList, skill, skillLevel, event.entityId);
    }
    if (bladeShield == null) {
      log.warn("[ASSASSIN_BLADE_SHIELD] phase=do_reject entity={} skill={} reason=missing_state",
          event.entityId, event.skillId);
      return;
    }
    bladeShield.periodicCountdownFrames = 0;
    bladeShield.needsSync = true;
    log.info("[ASSASSIN_BLADE_SHIELD] phase=armed entity={} skill={} level={} delay={}",
        event.entityId, event.skillId, bladeShield.level, bladeShield.periodicDelayFrames);
  }

  private static boolean isVenom(Skills.Entry skill) {
    return skill != null && skill.aurastate != null
        && "venomclaws".equalsIgnoreCase(skill.aurastate.trim());
  }

  static boolean isBoneArmor(Skills.Entry skill) {
    return skill != null && (skill.Id == SkillId.BONE_ARMOR
        || skill.aurastate != null
        && "bonearmor".equalsIgnoreCase(skill.aurastate.trim()));
  }

  /** Native SrvDo047: apply DIMVISION and defense reduction to hostile units in range. */
  private void applyCloakOfShadows(SkillDoEvent event, Skills.Entry skill, int skillLevel,
      Vector2 caster) {
    int duration = SkillFormula.evaluate(skill.auralencalc, skill, skillLevel);
    if (duration <= 0) {
      int base = firstParam(skill, 3, 200);
      int step = firstParam(skill, 4, 25);
      duration = base + skillLevel * step;
    }
    duration = Math.max(1, duration);
    int range = SkillFormula.evaluate(skill.aurarangecalc, skill, skillLevel);
    if (range <= 0) {
      int base = firstParam(skill, 1, 30);
      int step = firstParam(skill, 2, 30);
      range = base + skillLevel * step;
    }
    range = Math.max(1, Math.min(128, range));
    int defenseReduction = -AssassinSkills.calculateCloakOfShadowsDefenseReduce(skillLevel);
    int affected = 0;
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class)).getEntities();
    float range2 = range * (float) range;
    for (int i = 0; i < entities.size(); i++) {
      int targetId = entities.get(i);
      if (targetId == event.entityId || !isHostile(event.entityId, targetId)
          || !mPosition.has(targetId)
          || caster.dst2(mPosition.get(targetId).position) > range2) continue;
      if (!mUnitStates.has(targetId)) mUnitStates.create(targetId).init(targetId);
      UnitStates states = mUnitStates.get(targetId);
      if (states.stateList == null) states.init(targetId);
      UnitState state = states.stateList.applyCurseState(
          Riiablo.files != null ? Riiablo.files.States : null,
          StateId.DIMVISION, duration, skillLevel, event.entityId, event.skillId,
          Math.abs(defenseReduction), Stat.item_armor_percent, defenseReduction,
          NativeStatResolver.Operation.ADD);
      if (state != null) {
        state.needsSync = true;
        affected++;
      }
    }
    log.info("[CLOAK_OF_SHADOWS] phase=apply source={} skill={} level={} range={} duration={} "
            + "defense={} affected={} status=PASS",
        event.entityId, event.skillId, skillLevel, range, duration, defenseReduction, affected);
  }

  static boolean isCloakOfShadows(Skills.Entry skill) {
    return skill != null && skill.skill != null
        && "cloak of shadows".equalsIgnoreCase(skill.skill.trim());
  }

  /**
   * Native Necromancer SrvDo030/SrvDo059/SrvDo061 authority path. SrvDo030
   * and Confuse search around the selected point; Attract applies only to its
   * selected evil monster and lets the AI state drive later target selection.
   */
  private void applyNecromancerCurse(SkillDoEvent event, Skills.Entry skill, int skillLevel,
      Vector2 caster) {
    int function = event.srvdofunc != 0 ? event.srvdofunc : skill.srvdofunc;
    Vector2 center = resolveTargetPoint(event, caster, new Vector2());
    int nativeAuraRange = SkillFormula.evaluate(skill.aurarangecalc, skill, skillLevel);
    int range = function == 59 ? 0 : nativeAuraRange;
    range = Math.max(0, Math.min(128, range));
    float range2 = range * (float) range;
    int difficulty = curseDifficulty(event.entityId);
    com.riiablo.codec.excel.DifficultyLevels.Entry difficultyRow =
        Riiablo.files != null && Riiablo.files.DifficultyLevels != null
            ? Riiablo.files.DifficultyLevels.get(difficulty) : null;
    int affected = 0;
    int curseStateId = NecromancerSkills.resolveCurseStateId(
        skill.auratargetstate, skill.skill);

    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class)).getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int targetId = entities.get(i);
      if (function == 59 && targetId != event.targetId) continue;
      if (function != 59 && center.dst2(mPosition.get(targetId).position) > range2) continue;
      if (!isNecromancerCurseTarget(event.entityId, targetId, skill.aurafilter,
          function == 59 || function == 61)) continue;
      if ((curseStateId == StateId.DIMVISION || function == 61)
          && !canSwitchCurseAi(targetId)) continue;
      if (function == 59 && isNativeUnique(targetId)) continue;

      UnitStates targetStates = mUnitStates.has(targetId)
          ? mUnitStates.get(targetId) : mUnitStates.create(targetId).init(targetId);
      if (targetStates.stateList == null) targetStates.init(targetId);
      Attributes targetAttributes = mAttributesWrapper.has(targetId)
          ? mAttributesWrapper.get(targetId).attrs : null;
      boolean playerOrHireling = mPlayer.has(targetId) || mMercenary.has(targetId);
      UnitState state = NecromancerSkills.applyCurse(
          targetStates.stateList, Riiablo.files != null ? Riiablo.files.States : null,
          skill, skillLevel, event.entityId, difficultyRow,
          targetAttributes, playerOrHireling);
      if (state != null) {
        affected++;
        if (function == 61) {
          int seed = Riiablo.gameSeed ^ event.entityId * 0x45D9F3B
              ^ targetId * 31 ^ event.skillId;
          mNativeAiTargetOverride.create(targetId).setConfuse(
              event.entityId, event.skillId, state.duration, seed);
        } else if (function == 59) {
          installAttractTargetOverrides(event, skill, targetId, state.duration,
              Math.max(0, Math.min(128, nativeAuraRange)), caster);
        }
      }
    }
    log.info("[NECROMANCER_CURSE] phase=apply source={} skill={} function={} state={} "
            + "level={} difficulty={} center=({}, {}) range={} affected={} status={}",
        event.entityId, event.skillId, function,
        StateId.getName(NecromancerSkills.resolveCurseStateId(
            skill.auratargetstate, skill.skill)),
        skillLevel, difficulty, center.x, center.y, range, affected,
        affected > 0 ? "PASS" : "NO_TARGET");
  }

  /** Native SrvDo059: nearby switch-AI monsters receive a fixed monster target. */
  private void installAttractTargetOverrides(SkillDoEvent event, Skills.Entry skill,
      int attractedTargetId, int duration, int range, Vector2 caster) {
    if (range <= 0 || !mPosition.has(attractedTargetId)) return;
    int redirected = 0;
    float range2 = range * (float) range;
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, Monster.class)).getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int candidate = entities.get(i);
      if (candidate == attractedTargetId || caster.dst2(mPosition.get(candidate).position) > range2
          || !isNecromancerCurseTarget(event.entityId, candidate, skill.aurafilter, true)) {
        continue;
      }
      Monster monster = mMonster.get(candidate);
      if (!canSwitchCurseAi(candidate)) continue;
      mNativeAiTargetOverride.create(candidate).setAttract(
          attractedTargetId, event.entityId, event.skillId, duration);
      redirected++;
    }
    log.info("[NECROMANCER_ATTRACT_AI] source={} skill={} target={} range={} duration={} "
            + "redirected={} status={}",
        event.entityId, event.skillId, attractedTargetId, range, duration, redirected,
        redirected > 0 ? "PASS" : "NO_NEARBY_MONSTER");
  }

  private boolean canSwitchCurseAi(int entityId) {
    if (!mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    return monster.monstats != null && monster.monstats.switchai && !isNativeUnique(entityId);
  }

  private boolean isNativeUnique(int entityId) {
    Monster monster = mMonster.get(entityId);
    return monster.rank == MonsterRank.UNIQUE || monster.rank == MonsterRank.SUPER_UNIQUE;
  }

  private boolean isNecromancerCurseTarget(
      int sourceId, int targetId, int auraFilter, boolean monstersOnly) {
    if (targetId == sourceId || mCorpse.has(targetId) || mSummonedPet.has(targetId)) return false;
    boolean player = mPlayer.has(targetId);
    boolean monster = mMonster.has(targetId) && !mMercenary.has(targetId);
    if (!player && !monster) return false;
    if (monstersOnly && !monster) return false;
    int filter = auraFilter != 0 ? auraFilter : 0x583;
    if ((player && (filter & 1) == 0) || (monster && (filter & 2) == 0)) return false;
    if (monster) {
      Monster target = mMonster.get(targetId);
      if (target.monstats != null && target.monstats.npc) return false;
      if (!mNativeUnitFlagsValid(targetId)) return false;
    }
    if (!isHostile(sourceId, targetId)) return false;
    if (mAttributesWrapper.has(targetId)) {
      StatRef hp = mAttributesWrapper.get(targetId).attrs.get(Stat.hitpoints, StatRef.obtain());
      if (hp != null && hp.asFixed() <= 0f) return false;
    }
    if (mMapWrapper.has(sourceId) && mMapWrapper.has(targetId)) {
      MapWrapper sourceMap = mMapWrapper.get(sourceId);
      MapWrapper targetMap = mMapWrapper.get(targetId);
      if (sourceMap.map != null && targetMap.map != null && sourceMap.map != targetMap.map) {
        return false;
      }
      if ((filter & (0x100 | 0x2000)) != 0
          && targetMap.zone != null && targetMap.zone.isTown()) return false;
    }
    return true;
  }

  private int curseDifficulty(int sourceId) {
    if (mMapWrapper.has(sourceId) && mMapWrapper.get(sourceId).map != null) {
      return Math.max(0, Math.min(2, mMapWrapper.get(sourceId).map.getDifficulty()));
    }
    if (mPlayer.has(sourceId) && mPlayer.get(sourceId).data != null) {
      return Math.max(0, Math.min(2, mPlayer.get(sourceId).data.diff));
    }
    return 0;
  }

  private void spawnAssassinShadow(SkillDoEvent event, Skills.Entry skill, int skillLevel,
      Vector2 caster) {
    if (!mPlayer.has(event.entityId) || skill.summon == null || skill.summon.isEmpty()) {
      log.warn("[ASSASSIN_SHADOW] phase=reject owner={} skill={} reason=missing_owner_or_summon",
          event.entityId, event.skillId);
      return;
    }
    MonStats.Entry summon = Riiablo.files.monstats.get(skill.summon);
    if (summon == null) {
      log.warn("[ASSASSIN_SHADOW] phase=reject owner={} skill={} row={} reason=missing_monstats",
          event.entityId, event.skillId, skill.summon);
      return;
    }
    String petType = skill.pettype == null || skill.pettype.isEmpty()
        ? "shadowwarrior" : skill.pettype;
    int petMax = SkillFormula.evaluate(skill.petmax, skill, skillLevel);
    if (petMax <= 0) petMax = 1;
    Vector2 target = resolveTargetPoint(event, caster, new Vector2());
    int petId = factory.createSummonedPet(event.entityId, summon, petType, event.skillId,
        skillLevel, petMax, false, 0, target.x, target.y);
    if (petId < 0) {
      log.warn("[ASSASSIN_SHADOW] phase=reject owner={} skill={} reason=create_failed",
          event.entityId, event.skillId);
      return;
    }
    if (mUnitStates.has(petId)) {
      UnitStates states = mUnitStates.get(petId);
      if (states.stateList == null) states.init(petId);
      UnitState state = states.stateList.addState(StateId.SHADOWWARRIOR, 0, skillLevel,
          event.entityId);
      if (state != null) {
        state.skillId = event.skillId;
        state.needsSync = true;
      }
    }
    log.info("[ASSASSIN_SHADOW] phase=spawn owner={} entity={} summon={} petType={} level={} max={} "
            + "position=({}, {}) status=PASS",
        event.entityId, petId, summon.Id, petType, skillLevel, petMax, target.x, target.y);
  }

  /** Native SKILLS_SrvDo044/SrvDo045 summon path (sub_6FCF8610). */
  private void spawnAssassinTrap(SkillDoEvent event, Skills.Entry skill, int skillLevel,
      Vector2 caster) {
    if (!mPlayer.has(event.entityId) || skill.summon == null || skill.summon.isEmpty()) {
      log.warn("[ASSASSIN_TRAP] phase=reject owner={} skill={} reason=missing_owner_or_summon",
          event.entityId, event.skillId);
      return;
    }
    MonStats.Entry summon = Riiablo.files.monstats.get(skill.summon);
    if (summon == null) {
      log.warn("[ASSASSIN_TRAP] phase=reject owner={} skill={} row={} reason=missing_monstats",
          event.entityId, event.skillId, skill.summon);
      return;
    }
    String petType = skill.pettype == null || skill.pettype.isEmpty()
        ? "assassintrap" : skill.pettype;
    int petMax = Math.max(1, SkillFormula.evaluate(skill.petmax, skill, skillLevel));
    boolean bladeSentinel = event.srvdofunc == 44 || skill.srvdofunc == 44;
    Vector2 target = resolveTargetPoint(event, caster, new Vector2());
    Vector2 spawn = bladeSentinel ? caster : target;
    int petId = factory.createSummonedPet(event.entityId, summon, petType, event.skillId,
        skillLevel, petMax, false, 0, spawn.x, spawn.y);
    if (petId < 0) {
      log.warn("[ASSASSIN_TRAP] phase=reject owner={} skill={} reason=create_failed",
          event.entityId, event.skillId);
      return;
    }
    if (mSummonedPet.has(petId)) {
      com.riiablo.engine.server.component.SummonedPet trap = mSummonedPet.get(petId);
      Skills.Entry attackSkill = summon.Skill1 == null || summon.Skill1.isEmpty()
          ? null : Riiablo.files.skills.get(summon.Skill1);
      int shots = attackSkill != null
          ? SkillFormula.evaluate(attackSkill.calc4, attackSkill, skillLevel,
              name -> getBaseSkillLevel(event.entityId, name)) : 0;
      if (shots <= 0) shots = attackSkill != null
          ? firstParam(attackSkill, 7, firstParam(skill, 0, 5))
          : firstParam(skill, 0, 5);
      trap.maxShots = bladeSentinel ? 1 : Math.max(1, shots);
      trap.attackCooldownFrames = bladeSentinel ? 1 : normalAiParam(summon.aip3, 1);
      trap.bladeSentinel = bladeSentinel;
      trap.hasTrapTarget = true;
      trap.trapTargetX = target.x;
      trap.trapTargetY = target.y;
      if (bladeSentinel) {
        trap.bladeOriginX = caster.x;
        trap.bladeOriginY = caster.y;
        trap.bladeMovingToTarget = true;
        trap.bladeMissileId = Engine.INVALID_ENTITY;
        int duration = Math.max(1, SkillFormula.evaluate(skill.calc4, skill, skillLevel));
        trap.durationFrames = duration;
      }
    }
    log.info("[ASSASSIN_TRAP] phase=spawn owner={} entity={} skill={} summon={} petType={} "
            + "level={} max={} shots={} position=({}, {}) status=PASS",
        event.entityId, petId, event.skillId, summon.Id, petType, skillLevel, petMax,
        mSummonedPet.has(petId) ? mSummonedPet.get(petId).maxShots : 0, spawn.x, spawn.y);
  }

  private static int normalAiParam(int[] values, int fallback) {
    return values != null && values.length > 0 && values[0] > 0 ? values[0] : fallback;
  }

  private int getBaseSkillLevel(int entityId, String name) {
    if (name == null || name.isEmpty() || !mPlayer.has(entityId)
        || mPlayer.get(entityId).data == null) return 0;
    Skills.Entry skill = resolveSkill(name);
    return skill != null ? mPlayer.get(entityId).data.getBaseSkillLevel(skill.Id) : 0;
  }

  /** D2MOO SrvDo071: target a switchable hostile, falling back to radius 20. */
  private void applyTaunt(
      SkillDoEvent event, Skills.Entry skill, int skillLevel, Vector2 caster) {
    int targetId = isTauntTarget(event.entityId, event.targetId)
        ? event.targetId : Engine.INVALID_ENTITY;
    if (targetId == Engine.INVALID_ENTITY) {
      float nearest = Float.MAX_VALUE;
      IntBag entities = world.getAspectSubscriptionManager()
          .get(Aspect.all(Monster.class, Position.class)).getEntities();
      for (int i = 0; i < entities.size(); i++) {
        int candidate = entities.get(i);
        if (!isTauntTarget(event.entityId, candidate)) continue;
        float distance = caster.dst2(mPosition.get(candidate).position);
        if (distance <= 20f * 20f && distance < nearest) {
          nearest = distance;
          targetId = candidate;
        }
      }
    }
    log.info("[BARBARIAN_TAUNT] phase=select source={} requested={} selected={}",
        event.entityId, event.targetId, targetId);
    if (targetId == Engine.INVALID_ENTITY) {
      log.info("[BARBARIAN_TAUNT] phase=reject source={} skill={} reason=no_switchable_target",
          event.entityId, event.skillId);
      return;
    }
    if (!mUnitStates.has(targetId)) mUnitStates.create(targetId).init(targetId);
    UnitStates states = mUnitStates.get(targetId);
    if (states.stateList == null) states.init(targetId);
    UnitState state = BarbarianSkills.applyWarCryState(
        states.stateList, skill, skillLevel, event.entityId, true,
        name -> getBaseSkillLevel(event.entityId, name));
    if (state == null) {
      log.warn("[BARBARIAN_TAUNT] phase=reject source={} target={} skill={} reason=state_data",
          event.entityId, targetId, event.skillId);
      return;
    }
    log.info("[BARBARIAN_TAUNT] phase=apply source={} target={} skill={} level={} duration={} "
            + "attack={} damage={} status=PASS",
        event.entityId, targetId, event.skillId, skillLevel, state.duration,
        state.attackModifier, state.damageModifier);
  }

  private boolean isTauntTarget(int sourceId, int targetId) {
    if (targetId < 0 || !mMonster.has(targetId) || !mPosition.has(targetId)
        || mMercenary.has(targetId) || mSummonedPet.has(targetId)
        || !isHostile(sourceId, targetId) || !mNativeUnitFlagsValid(targetId)) return false;
    Monster monster = mMonster.get(targetId);
    StateList stateList = mUnitStates.has(targetId) ? mUnitStates.get(targetId).stateList : null;
    if (!BarbarianSkills.canSwitchWarCryAi(monster, stateList)) return false;
    Attributes attrs = mAttributesWrapper.has(targetId)
        ? mAttributesWrapper.get(targetId).attrs : null;
    StatRef hp = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    return hp == null || hp.asFixed() > 0f;
  }

  /** SrvDo068 applies AuraState to the caster before its 64-way shout wave. */
  private void applyBasicWarCry(SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (!mUnitStates.has(event.entityId)) mUnitStates.create(event.entityId).init(event.entityId);
    UnitStates states = mUnitStates.get(event.entityId);
    if (states.stateList == null) states.init(event.entityId);
    UnitState state = BarbarianSkills.applyWarCryState(
        states.stateList, skill, skillLevel, event.entityId, false,
        name -> getBaseSkillLevel(event.entityId, name));
    log.info("[BARBARIAN_WAR_CRY] phase=self source={} skill={} level={} state={} duration={}",
        event.entityId, event.skillId, skillLevel,
        state != null ? StateId.getName(state.stateId) : "none",
        state != null ? state.duration : 0);
  }

  private void spawnNova(SkillDoEvent event, Skills.Entry skill, Vector2 start) {
    String missileName = firstNonEmpty(skill.srvmissile,
        firstNonEmpty(skill.srvmissilea,
            firstNonEmpty(skill.cltmissile, skill.cltmissilea)));
    if (missileName == null) {
      log.warn("Server nova has no missile configured: entity={}, skill={}",
          event.entityId, event.skillId);
      return;
    }
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      log.warn("Server nova missile lookup failed: entity={}, skill={}, missile={}",
          event.entityId, event.skillId, missileName);
      return;
    }

    IntSet sharedHitTargets = new IntSet();
    int skillLevel = getSkillLevel(event.entityId, event.skillId);
    Vector2 direction = new Vector2();
    int created = 0;
    for (int i = 0; i < NOVA_MISSILE_COUNT; i++) {
      radialDirection(i, NOVA_MISSILE_COUNT, direction);
      int missileId = createMissile(missile, direction, start, event.entityId,
          sharedHitTargets, skillLevel);
      if (missileId >= 0) {
        initializeSkillDamage(missileId, skill, event.entityId, skillLevel);
        created++;
      }
    }
    log.debug("Server nova projectiles: entity={}, skill={}, missile={}, created={}",
        event.entityId, event.skillId, missileName, created);
  }

  /** Native {@code SKILLS_SrvDo017_ChargedBolt_BoltSentry} for the Sorceress. */
  private void spawnSorceressChargedBolt(
      SkillDoEvent event, Skills.Entry skill, Vector2 start, int skillLevel) {
    String missileName = firstNonEmpty(skill.srvmissilea,
        firstNonEmpty(skill.srvmissile, skill.cltmissilea));
    Missiles.Entry row = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (row == null) {
      log.warn("[SORCERESS_CHARGED_BOLT] phase=reject source={} skill={} "
              + "reason=missing_missile name={}",
          event.entityId, skill.Id, missileName);
      return;
    }

    Vector2 targetPoint = resolveTargetPoint(event, start, new Vector2());
    Vector2 base = targetPoint.sub(start);
    if (base.isZero(0.0001f)) base.set(Vector2.X);
    base.nor();

    int count = chargedBoltCount(skill, skillLevel);
    int mainDirection = AssassinTrapSystem.chargedBoltMainDirection(base);
    int originX = MathUtils.floor(start.x);
    int created = 0;
    for (int i = 0; i < count; i++) {
      Vector2 direction = chargedBoltDirection(base, i, count, new Vector2());
      int missileId = createMissile(row, direction, start, event.entityId, null, skillLevel);
      if (missileId < 0 || !mMissile.has(missileId)) continue;

      Missile bolt = mMissile.get(missileId);
      // SKILLS_MissileInit_ChargedBolt seeds the missile from ordinal + X,
      // changes the path type, and caps the path's total frames at 77.
      int seedLow = i + originX;
      long rolled = AssassinTrapSystem.chargedBoltRoll(seedLow, 666);
      bolt.chargedBoltPath = true;
      bolt.chargedBoltMainDirection = mainDirection;
      bolt.chargedBoltSeedLow = (int) rolled;
      bolt.chargedBoltSeedHigh = (int) (rolled >>> 32);
      bolt.chargedBoltNextTurnDistance = 2f;
      bolt.range = Math.min(77f, Math.max(1f, bolt.range));
      initializeSkillDamage(missileId, skill, event.entityId, skillLevel);
      created++;
    }
    log.info("[SORCERESS_CHARGED_BOLT] phase=create source={} skill={} level={} "
            + "missile={} requested={} created={} calc1={} origin=({}, {})",
        event.entityId, skill.Id, skillLevel, row.Missile, count, created,
        skill.calc1, start.x, start.y);
  }

  /**
   * Native Poison Nova (SrvDo022): 64 fixed-offset poisonnova missiles.  The
   * poison packet is captured once at cast time as an 8.8 rate and resolved by
   * MissileCollisionSystem on the authoritative server; clients only render
   * the replicated missiles and never apply damage locally.
   */
  private void spawnNecromancerPoisonNova(
      SkillDoEvent event, Skills.Entry skill, int skillLevel, Vector2 start) {
    String missileName = firstNonEmpty(skill.srvmissilea,
        firstNonEmpty(skill.srvmissile, skill.cltmissilea));
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[NECRO_POISON_NOVA] phase=reject source={} skill={} reason=missing_missile name={}",
          event.entityId, event.skillId, missileName);
      return;
    }
    int[] poison = NecromancerSkills.getPoisonNovaDamage(
        skill, skillLevel, name -> getBaseSkillLevel(event.entityId, name));
    Attributes sourceAttrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    int mastery = Math.max(0, statInt(sourceAttrs, Stat.passive_pois_mastery));
    poison[0] = saturatedScale(poison[0], 100 + mastery, 100);
    poison[1] = saturatedScale(poison[1], 100 + mastery, 100);
    int duration = NecromancerSkills.getPoisonNovaDurationFrames(skill, skillLevel);
    int pierce = Math.max(0, statInt(sourceAttrs, Stat.item_pierce_pois)
        + statInt(sourceAttrs, Stat.passive_pois_pierce));
    IntSet sharedHitTargets = new IntSet();
    Vector2 direction = new Vector2();
    int created = 0;
    for (int i = 0; i < NOVA_MISSILE_COUNT; i++) {
      radialDirection(i, NOVA_MISSILE_COUNT, direction);
      int missileId = createMissile(missile, direction, start, event.entityId,
          sharedHitTargets, skillLevel);
      if (missileId < 0 || !mMissile.has(missileId)) continue;
      Missile projectile = mMissile.get(missileId);
      projectile.skillId = skill.Id;
      projectile.damageLevel = skillLevel;
      projectile.fixedPoisonRate = true;
      projectile.poisonMinRateFixed = poison[0];
      projectile.poisonMaxRateFixed = poison[1];
      projectile.poisonDurationFrames = duration;
      projectile.poisonPiercePercent = pierce;
      projectile.poisonAttackerPlayer = mPlayer.has(event.entityId);
      // Poison Nova is a single-hit radial wave.  A target is claimed by the
      // first missile in this cast through sharedHitTargets; Pierce does not
      // cause the same target to be damaged repeatedly by the wave.
      projectile.pierceEnabled = false;
      created++;
    }
    log.info("[NECRO_POISON_NOVA] phase=create source={} skill={} level={} missile={} "
            + "created={} duration={} rawFixed={}..{} mastery={} pierce={} range={} velocity={}",
        event.entityId, skill.Id, skillLevel, missile.Missile, created, duration,
        poison[0], poison[1], mastery, pierce, missile.Range, missile.Vel);
  }

  /** D2MOO SKILLS_SrvDo024_FireWall: create only the maker at the target. */
  private void spawnFireWall(SkillDoEvent event, Skills.Entry skill, Vector2 caster) {
    String missileName = firstNonEmpty(skill.srvmissilea, skill.cltmissilea);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[MONSTER_VAMPIRE] phase=firewall_rejected source={} reason=missing_missile missile={}",
          event.entityId, missileName);
      return;
    }
    Vector2 target = resolveTargetPoint(event, caster, new Vector2());
    Vector2 direction = firewallDirection(caster, target, new Vector2());
    int missileId = createMissile(missile, direction, target, event.entityId, null,
        getSkillLevel(event.entityId, event.skillId));
    log.info("[MONSTER_VAMPIRE] phase=firewall source={} target={} missile={} missileId={} "
            + "position=({}, {}) direction=({}, {})",
        event.entityId, event.targetId, missileName, missileId,
        target.x, target.y, direction.x, direction.y);
  }

  /** D2MOO SKILLS_SrvDo028_Meteor: create the centre missile at the target. */
  private void spawnMeteor(SkillDoEvent event, Skills.Entry skill, Vector2 caster) {
    String missileName = firstNonEmpty(skill.srvmissilea, skill.cltmissilea);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[MONSTER_VAMPIRE] phase=meteor_rejected source={} reason=missing_missile missile={}",
          event.entityId, missileName);
      return;
    }
    Vector2 target = resolveTargetPoint(event, caster, new Vector2());
    Vector2 direction = new Vector2(target).sub(caster);
    if (direction.isZero(0.0001f)) direction.set(1f, 0f);
    direction.nor();
    int missileId = createMissile(missile, direction, target, event.entityId, null,
        getSkillLevel(event.entityId, event.skillId));
    log.info("[MONSTER_VAMPIRE] phase=meteor source={} target={} missile={} missileId={} position=({}, {})",
        event.entityId, event.targetId, missileName, missileId, target.x, target.y);
  }

  /**
   * Native chain lightning walks the nearby hostile-unit list, never revisits
   * a target, and emits one authoritative segment per jump.  Each segment is
   * represented by the normal missile/collision path, preserving resistance,
   * hit and death event handling instead of applying damage directly here.
   */
  private void spawnChainLightning(SkillDoEvent event, Skills.Entry skill, Vector2 start) {
    String missileName = firstNonEmpty(skill.srvmissilea, skill.cltmissilea);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[CHAIN_LIGHTNING] phase=reject source={} reason=missing_missile name={}",
          event.entityId, missileName);
      return;
    }
    int maxHits = Math.min(12, Math.max(1,
        5 + getSkillLevel(event.entityId, event.skillId) / 5));
    IntSet visited = new IntSet();
    Vector2 from = new Vector2(start);
    int created = 0;
    for (int jump = 0; jump < maxHits; jump++) {
      int next = jump == 0 && event.targetId >= 0 && mPosition.has(event.targetId)
          && isHostile(event.entityId, event.targetId)
          ? event.targetId : findNearestHostile(
              event.entityId, from, visited, CHAIN_LIGHTNING_JUMP_RANGE2);
      if (next < 0 || !mPosition.has(next)) break;
      Vector2 destination = mPosition.get(next).position;
      Vector2 direction = new Vector2(destination).sub(from);
      if (direction.isZero(0.0001f)) {
        visited.add(next);
        continue;
      }
      direction.nor();
      // Each segment owns its collision set. Sharing the target-selection set
      // would pre-mark every intended victim and make collision skip them.
      if (createMissile(missile, direction, from, event.entityId, null,
          getSkillLevel(event.entityId, event.skillId)) >= 0) created++;
      visited.add(next);
      from.set(destination);
    }
    log.info("[CHAIN_LIGHTNING] phase=spawn source={} initialTarget={} hits={} missile={} status={}",
        event.entityId, event.targetId, created, missileName, created > 0 ? "PASS" : "EMPTY");
  }

  /** Native Amazon SrvDo011: release Calc1 charged bolts from the hit target. */
  private void spawnChargedStrike(SkillDoEvent event, Skills.Entry skill, Vector2 caster) {
    if (event.targetId < 0 || !mPosition.has(event.targetId)) {
      log.debug("[AMAZON_CHARGED_STRIKE] phase=reject source={} target={} reason=no_target",
          event.entityId, event.targetId);
      return;
    }
    String missileName = firstNonEmpty(skill.srvmissilea, skill.srvmissileb);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[AMAZON_CHARGED_STRIKE] phase=reject source={} reason=missing_missile name={}",
          event.entityId, missileName);
      return;
    }
    int skillLevel = getSkillLevel(event.entityId, event.skillId);
    int count = chargedStrikeBoltCount(skill, skillLevel);
    Vector2 origin = mPosition.get(event.targetId).position;
    Vector2 base = new Vector2(origin).sub(caster);
    if (base.isZero(0.0001f)) base.set(1f, 0f);
    base.nor();
    IntSet hitTargets = new IntSet();
    // The melee stage resolves the struck target. Native charged bolts start
    // on that target but do not immediately re-hit it.
    hitTargets.add(event.targetId);
    int created = 0;
    Vector2 direction = new Vector2();
    for (int i = 0; i < count; i++) {
      chargedStrikeDirection(base, i, count, direction);
      int missileId = createMissile(missile, direction, origin, event.entityId,
          hitTargets, skillLevel);
      if (missileId >= 0) {
        initializeSkillDamage(missileId, skill, event.entityId, skillLevel);
        created++;
      }
    }
    log.info("[AMAZON_CHARGED_STRIKE] phase=spawn source={} target={} level={} "
            + "missile={} requested={} created={}",
        event.entityId, event.targetId, skillLevel, missileName, count, created);
  }

  /** Native Amazon SrvDo014: chain from the melee victim to nearby enemies. */
  private void spawnLightningStrike(SkillDoEvent event, Skills.Entry skill) {
    if (event.targetId < 0 || !mPosition.has(event.targetId)) return;
    String missileName = firstNonEmpty(skill.srvmissilea, skill.srvmissileb);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[AMAZON_LIGHTNING_STRIKE] phase=reject source={} reason=missing_missile name={}",
          event.entityId, missileName);
      return;
    }
    int skillLevel = getSkillLevel(event.entityId, event.skillId);
    int maxJumps = lightningStrikeJumpCount(skill, skillLevel);
    float range = lightningStrikeRange(skill, skillLevel);
    float range2 = range * range;
    IntSet visited = new IntSet();
    visited.add(event.targetId);
    Vector2 from = new Vector2(mPosition.get(event.targetId).position);
    int created = 0;
    for (int jump = 0; jump < maxJumps; jump++) {
      int next = findNearestHostile(event.entityId, from, visited, range2);
      if (next < 0 || !mPosition.has(next)) break;
      Vector2 destination = mPosition.get(next).position;
      Vector2 direction = new Vector2(destination).sub(from);
      IntSet segmentHits = copySet(visited);
      if (!direction.isZero(0.0001f)) {
        int missileId = createMissile(missile, direction.nor(), from, event.entityId,
            segmentHits, skillLevel);
        if (missileId >= 0) {
          initializeSkillDamage(missileId, skill, event.entityId, skillLevel);
          created++;
        }
      }
      visited.add(next);
      from.set(destination);
    }
    log.info("[AMAZON_LIGHTNING_STRIKE] phase=spawn source={} initialTarget={} "
            + "level={} range={} maxJumps={} created={} missile={}",
        event.entityId, event.targetId, skillLevel, range, maxJumps, created, missileName);
  }

  /** Native Amazon SrvDo015/SrvDo016: create an owned Decoy or Valkyrie. */
  private void spawnAmazonSummon(SkillDoEvent event, Skills.Entry skill,
      Vector2 caster, boolean valkyrie) {
    if (!mPlayer.has(event.entityId)) return;
    MonStats.Entry summon = skill.summon == null || skill.summon.isEmpty()
        ? null : Riiablo.files.monstats.get(skill.summon);
    if (summon == null) {
      log.warn("[AMAZON_{}] phase=reject owner={} skill={} reason=missing_summon row={}",
          valkyrie ? "VALKYRIE" : "DECOY", event.entityId, event.skillId, skill.summon);
      return;
    }
    int skillLevel = getSkillLevel(event.entityId, event.skillId);
    int petMax = SkillFormula.evaluate(skill.petmax, skill, skillLevel);
    if (petMax <= 0) petMax = 1;
    int duration = valkyrie ? 0 : Math.max(0,
        SkillFormula.evaluate(skill.calc2, skill, skillLevel));
    Vector2 target = resolveTargetPoint(event, caster, new Vector2());
    int petId = factory.createSummonedPet(
        event.entityId, summon, skill.pettype, event.skillId, skillLevel,
        petMax, !valkyrie, duration, target.x, target.y);
    if (petId == Engine.INVALID_ENTITY || !mAttributesWrapper.has(petId)) {
      log.warn("[AMAZON_{}] phase=reject owner={} skill={} reason=create_failed target=({}, {})",
          valkyrie ? "VALKYRIE" : "DECOY", event.entityId, event.skillId,
          target.x, target.y);
      return;
    }

    Attributes ownerAttrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    Attributes petAttrs = mAttributesWrapper.get(petId).attrs;
    int ownerLevel = Math.max(1, statInt(ownerAttrs, Stat.level));
    int petLevel = summonBaseLevel(ownerLevel, skillLevel);
    StatRef level = petAttrs != null ? petAttrs.get(Stat.level, StatRef.obtain()) : null;
    if (level != null) level.set(petLevel);

    if (!valkyrie && ownerAttrs != null && petAttrs != null) {
      int hpPercent = Math.max(1, SkillFormula.evaluate(skill.calc3, skill, skillLevel));
      float ownerMaxHp = statFixed(ownerAttrs, Stat.maxhp);
      float hitpoints = Math.max(1f, ownerMaxHp * hpPercent / 100f);
      StatRef hp = petAttrs.get(Stat.hitpoints, StatRef.obtain());
      StatRef maxHp = petAttrs.get(Stat.maxhp, StatRef.obtain());
      if (hp != null) hp.set(hitpoints);
      if (maxHp != null) maxHp.set(hitpoints);
    } else if (valkyrie && mUnitStates.has(petId)) {
      UnitStates states = mUnitStates.get(petId);
      if (states.stateList == null) states.init(petId);
      states.stateList.addState(StateId.VALKYRIE, 0, skillLevel, event.entityId);
    }

    log.info("[AMAZON_{}] phase=spawn owner={} entity={} summon={} petType={} "
            + "skill={} level={} petLevel={} max={} duration={} target=({}, {})",
        valkyrie ? "VALKYRIE" : "DECOY", event.entityId, petId, summon.Id,
        skill.pettype, event.skillId, skillLevel, petLevel, petMax, duration,
        target.x, target.y);
  }

  /** Native SKILLS_SrvDo114 Raven, SrvDo115 Vines and SrvDo119 DruidSummon. */
  private void spawnDruidSummon(SkillDoEvent event, Skills.Entry skill,
      int skillLevel, Vector2 caster) {
    if (!mPlayer.has(event.entityId) || skill == null
        || skill.summon == null || skill.summon.isEmpty()) {
      log.warn("[DRUID_SUMMON] phase=reject owner={} skill={} reason=missing_owner_or_summon",
          event.entityId, event.skillId);
      return;
    }
    MonStats.Entry summon = Riiablo.files.monstats.get(skill.summon);
    if (summon == null) {
      log.warn("[DRUID_SUMMON] phase=reject owner={} skill={} row={} reason=missing_monstats",
          event.entityId, event.skillId, skill.summon);
      return;
    }
    String petType = PetType.canonical(skill.pettype);
    if (petType.isEmpty()) {
      petType = PetType.canonical(skill.summon);
    }
    int petMax = Math.max(1, SkillFormula.evaluate(skill.petmax, skill, skillLevel,
        name -> getBaseSkillLevel(event.entityId, name)));
    Vector2 target = (event.srvdofunc == 119 || skill.srvdofunc == 119)
        ? resolveTargetPoint(event, caster, new Vector2()) : new Vector2(caster);
    int petId = factory.createSummonedPet(event.entityId, summon, petType, event.skillId,
        skillLevel, petMax, false, 0, target.x, target.y);
    if (petId == Engine.INVALID_ENTITY) {
      log.warn("[DRUID_SUMMON] phase=reject owner={} skill={} petType={} reason=create_failed",
          event.entityId, event.skillId, petType);
      return;
    }

    Attributes ownerAttrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    Attributes petAttrs = mAttributesWrapper.has(petId)
        ? mAttributesWrapper.get(petId).attrs : null;
    int ownerLevel = Math.max(1, statInt(ownerAttrs, Stat.level));
    int petLevel = summonBaseLevel(ownerLevel, skillLevel);
    if (petAttrs != null) {
      StatRef level = petAttrs.get(Stat.level, StatRef.obtain());
      if (level != null) level.set(petLevel);
      applyDruidSummonStats(petAttrs, skill, skillLevel,
          name -> getBaseSkillLevel(event.entityId, name));
    }
    if (mUnitStates.has(petId)) {
      UnitStates states = mUnitStates.get(petId);
      if (states.stateList == null) states.init(petId);
      int auraState = DruidSkills.getSummonAuraState(skill);
      if (auraState != StateId.NONE) {
        UnitState state = states.stateList.addState(auraState,
            Math.max(0, SkillFormula.evaluate(skill.auralencalc, skill, skillLevel,
                name -> getBaseSkillLevel(event.entityId, name))),
            skillLevel, event.entityId);
        if (state != null) {
          state.skillId = event.skillId;
          DruidSkills.applySummonAuraModifiers(state, skill, skillLevel,
              name -> getBaseSkillLevel(event.entityId, name));
          state.needsSync = true;
        }
      }
    }
    log.info("[DRUID_SUMMON] phase=spawn owner={} entity={} summon={} petType={} skill={} "
            + "level={} petLevel={} max={} srvDo={} target=({}, {})",
        event.entityId, petId, summon.Id, petType, skill.skill, skillLevel, petLevel,
        petMax, skill.srvdofunc, target.x, target.y);
  }

  private static void applyDruidSummonStats(Attributes attrs, Skills.Entry skill, int level,
      java.util.function.ToIntFunction<String> baseSkills) {
    if (attrs == null || skill == null || skill.passivestat == null
        || skill.passivecalc == null) return;
    for (int i = 0; i < skill.passivestat.length && i < skill.passivecalc.length; i++) {
      String name = skill.passivestat[i];
      if (name == null || name.isEmpty()) continue;
      short stat = Stat.index(name);
      if (stat < 0) continue;
      int value = SkillFormula.evaluate(skill.passivecalc[i], skill, level, baseSkills,
          skillName -> Riiablo.files.skills.get(skillName));
      if (value != 0) attrs.base().put(stat, value);
    }
  }

  static int summonBaseLevel(int ownerLevel, int skillLevel) {
    int normalizedOwner = Math.max(1, ownerLevel);
    int level = Math.max(1, skillLevel) + 3 * normalizedOwner / 4;
    return Math.max(1, Math.min(normalizedOwner, level));
  }

  private static int statInt(Attributes attrs, short stat) {
    StatRef ref = attrs != null ? attrs.get(stat, StatRef.obtain()) : null;
    return ref != null ? ref.asInt() : 0;
  }

  private static float statFixed(Attributes attrs, short stat) {
    StatRef ref = attrs != null ? attrs.get(stat, StatRef.obtain()) : null;
    return ref != null ? ref.asFixed() : 0f;
  }

  private int findNearestHostile(int sourceId, Vector2 origin, IntSet visited, float range2) {
    IntBag entities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class)).getEntities();
    int nearest = Engine.INVALID_ENTITY;
    float nearestDistance = Float.MAX_VALUE;
    for (int i = 0; i < entities.size(); i++) {
      int candidate = entities.get(i);
      if (candidate == sourceId || visited.contains(candidate) || !isHostile(sourceId, candidate)
          || !mPosition.has(candidate)) continue;
      float distance = origin.dst2(mPosition.get(candidate).position);
      if (distance <= range2 && distance < nearestDistance) {
        nearestDistance = distance;
        nearest = candidate;
      }
    }
    return nearest;
  }

  static int chargedStrikeBoltCount(Skills.Entry skill, int skillLevel) {
    int count = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, skillLevel);
    return Math.max(1, Math.min(64, count));
  }

  /** Native Charged Bolt count (Skills.txt calc1, clamped like D2's loop). */
  static int chargedBoltCount(Skills.Entry skill, int skillLevel) {
    int count = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, skillLevel);
    // A few custom 1.10f exports omit calc1 but retain Param1.  The native
    // table's effective level-one value is three, so preserve that fallback
    // without reintroducing the old random/hard-coded damage helper.
    if (count <= 0) count = firstParam(skill, 1, 3);
    return Math.max(1, Math.min(64, count));
  }

  static Vector2 chargedBoltDirection(Vector2 base, int index, int count, Vector2 out) {
    if (count <= 1) return out.set(base).nor();
    // The native callback does not fan bolts by a fixed angle; each missile's
    // path initialiser chooses one of the three neighbouring octants from its
    // per-missile seed.  Start each bolt in the target octant and let the
    // deterministic path turn logic provide the native spread.
    return out.set(base).nor();
  }

  static Vector2 chargedStrikeDirection(Vector2 base, int index, int count, Vector2 out) {
    if (count <= 1) return out.set(base).nor();
    // Native SKILLS_MissileInit_ChargedBolt seeds each bolt by ordinal. A
    // deterministic 180-degree fan preserves that separation on this engine's
    // direction-based missile API.
    float offset = MathUtils.PI * (index / (float) (count - 1) - 0.5f);
    return out.set(base).rotateRad(offset).nor();
  }

  static int lightningStrikeJumpCount(Skills.Entry skill, int skillLevel) {
    int count = SkillFormula.evaluate(skill != null ? skill.calc2 : null, skill, skillLevel);
    return Math.max(1, Math.min(64, count));
  }

  static float lightningStrikeRange(Skills.Entry skill, int skillLevel) {
    int range = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, skillLevel);
    return Math.max(1, Math.min(64, range));
  }

  private static IntSet copySet(IntSet source) {
    IntSet copy = new IntSet(source != null ? source.size : 0);
    if (source == null) return copy;
    for (IntSet.IntSetIterator it = source.iterator(); it.hasNext; ) copy.add(it.next());
    return copy;
  }

  private boolean isHostile(int sourceId, int candidate) {
    boolean sourcePlayer = isPlayerAligned(sourceId);
    boolean targetPlayer = isPlayerAligned(candidate);
    if (sourcePlayer != targetPlayer) return true;
    if (!sourcePlayer) return false;
    return PvpCombatRules.canTarget(
        partyManager, playerAlignmentOwner(sourceId), playerAlignmentOwner(candidate),
        true, true);
  }

  private boolean isPlayerAligned(int entityId) {
    return mPlayer.has(entityId) || mMercenary.has(entityId) || mSummonedPet.has(entityId)
        || mMonster.has(entityId) && mMonster.get(entityId).converted;
  }

  private int playerAlignmentOwner(int entityId) {
    if (mMercenary.has(entityId)) return mMercenary.get(entityId).ownerId;
    if (mSummonedPet.has(entityId)) return mSummonedPet.get(entityId).ownerId;
    if (mMonster.has(entityId) && mMonster.get(entityId).converted) {
      return mMonster.get(entityId).conversionOwnerId;
    }
    return entityId;
  }

  private Vector2 resolveTargetPoint(SkillDoEvent event, Vector2 fallback, Vector2 out) {
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      return out.set(mPosition.get(event.targetId).position);
    }
    if (event.targetVec != null) return out.set(event.targetVec);
    return out.set(fallback).add(1f, 0f);
  }

  static Vector2 firewallDirection(Vector2 caster, Vector2 target, Vector2 out) {
    out.set(target).sub(caster);
    if (out.isZero(0.0001f)) out.set(1f, 0f);
    // Native SrvDo024 sets the maker target perpendicular to caster -> target.
    return out.rotate90(1).nor();
  }

  /**
   * D2MOO's SKILLS_SrvDo008_MultipleShot_Teeth_ShockWave.  The native
   * implementation evaluates calc1 as the total count and calc3 as the
   * centre group (calc2 is the missile activation frame), then emits
   * left/centre/right groups along a perpendicular
   * target offset.  The entity factory currently accepts a direction rather
   * than an explicit target point, so the same lane layout is represented by
   * a deterministic narrow fan of directions.
   */
  private void spawnMultipleShotTeethShockWave(SkillDoEvent event, Skills.Entry skill,
      Vector2 start) {
    Vector2 target = new Vector2();
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      target.set(mPosition.get(event.targetId).position);
    } else if (event.targetVec != null) {
      target.set(event.targetVec);
    } else {
      target.set(start).add(1, 0);
    }
    target.sub(start);
    if (target.isZero(0.0001f)) target.set(1, 0);
    target.nor();

    int skillLevel = getSkillLevel(event.entityId, event.skillId);
    int total = DruidSkills.isShockWave(skill)
        ? DruidSkills.getShockWaveMissileCount(skill, skillLevel)
        : SkillFormula.evaluate(skill.calc1, skill, skillLevel);
    if (DruidSkills.isShockWave(skill) && total <= 0) {
      log.warn("[DRUID_SHOCK_WAVE] phase=spawn_reject entity={} skill={} level={} "
              + "reason=invalid_calc1 formula={}",
          event.entityId, skill.skill, skillLevel, skill.calc1);
      return;
    }
    if (total <= 0) total = firstParam(skill, 1, 1);
    total = Math.max(1, Math.min(64, total));

    int centre = SkillFormula.evaluate(skill.calc3, skill, skillLevel);
    if (centre <= 0) centre = total;
    centre = Math.max(0, Math.min(total, centre));

    int left = (total - centre) / 2;
    int right = total - left - centre;
    String missileName = selectSrvDo008Missile(event.entityId, skill);
    if (missileName == null) {
      log.warn("SrvDo008 has no missile configured: entity={}, skill={}, total={}, centre={}",
          event.entityId, event.skillId, total, centre);
      return;
    }
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      log.warn("SrvDo008 missile lookup failed: entity={}, skill={}, missile={}",
          event.entityId, event.skillId, missileName);
      return;
    }

    IntSet sharedHitTargets = total > 1 ? new IntSet() : null;
    Vector2 direction = new Vector2();
    int created = 0;
    for (int i = 0; i < total; i++) {
      fanDirection(target, i, total, direction);
      int missileId = createMissile(missile, direction, start, event.entityId,
          sharedHitTargets, skillLevel);
      if (missileId >= 0) {
        initializeSkillDamage(missileId, skill, event.entityId, skillLevel);
        created++;
      }
    }
    if (created > 0) consumeRangedAmmoForSkill(event, skill);
    if (DruidSkills.isShockWave(skill)) {
      int[] damage = DruidSkills.getShockWaveDamageRange(skill, skillLevel);
      log.info("[DRUID_SHOCK_WAVE] phase=spawn entity={} skill={} missile={} level={} "
              + "total={} centre={} left={} right={} created={} damage={}..{} stunFrames={}",
          event.entityId, skill.skill, missileName, skillLevel, total, centre, left, right,
          created, damage[0], damage[1],
          DruidSkills.getShockWaveStunDuration(skill, skillLevel));
    }
    log.debug("Server SrvDo008 projectiles: entity={}, skill={}, missile={}, level={}, total={}, "
            + "left={}, centre={}, right={}, created={}",
        event.entityId, event.skillId, missileName, skillLevel, total, left, centre, right, created);
  }

  private String selectSrvDo008Missile(int entityId, Skills.Entry skill) {
    // D2MOO selects wSrvMissileB for every weapon class except HTH.  The
    // component is absent for old/remote entities, where HTH is the safe
    // native default.
    boolean nonHandToHand = mCofReference != null && mCofReference.has(entityId)
        && mCofReference.get(entityId).wclass != Engine.WEAPON_HTH;
    if (nonHandToHand) {
      String missile = firstNonEmpty(skill.srvmissileb, skill.srvmissilea);
      if (missile != null) return missile;
    }
    return firstNonEmpty(skill.srvmissilea, skill.cltmissilea);
  }

  /** Native SKILLS_SrvDo010_GuidedArrow_BoneSpirit. */
  private void spawnGuidedArrow(SkillDoEvent event, Skills.Entry skill,
      Vector2 start, int skillLevel) {
    String missileName = firstNonEmpty(skill.srvmissilea, skill.srvmissileb);
    if (missileName == null) missileName = firstNonEmpty(skill.srvmissile, skill.cltmissilea);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[GUIDED_ARROW] phase=reject entity={} skill={} reason=missing_missile name={}",
          event.entityId, event.skillId, missileName);
      return;
    }
    Vector2 direction = new Vector2();
    int targetId = event.targetId;
    if (targetId >= 0 && mPosition.has(targetId)) {
      direction.set(mPosition.get(targetId).position).sub(start);
    } else if (event.targetVec != null) {
      direction.set(event.targetVec).sub(start);
    } else {
      direction.set(1f, 0f);
    }
    if (direction.isZero(0.0001f)) direction.set(1f, 0f);
    if (targetId < 0 && NecromancerSkills.isBoneSpirit(skill)) {
      targetId = nearestHostileMonster(event.entityId, start, 128f);
      if (targetId >= 0 && mPosition.has(targetId)) {
        direction.set(mPosition.get(targetId).position).sub(start);
      }
    }
    if (direction.isZero(0.0001f)) direction.set(1f, 0f);
    int id = createMissile(missile, direction.nor(), start, event.entityId, null, skillLevel);
    if (id >= 0 && mMissile.has(id)) {
      initializeSkillDamage(id, skill, event.entityId, skillLevel);
      Missile projectile = mMissile.get(id);
      projectile.targetId = targetId;
      projectile.homing = targetId >= 0 && mPosition.has(targetId);
      int bonus = SkillFormula.evaluate(skill.calc1, skill, skillLevel);
      if (bonus <= 0 && !NecromancerSkills.isBoneSpirit(skill)) {
        bonus = AmazonSkills.calculateGuidedArrowDamageBonus(skillLevel);
      }
      projectile.damageMultiplier = 1f + Math.max(0, bonus) / 100f;
      configurePierce(projectile, event.entityId, skillLevel, true);
      consumeRangedAmmoForSkill(event, skill);
      log.info("[GUIDED_ARROW] phase=create entity={} missileId={} target={} homing={} "
              + "level={} damageBonus={} pierceChance={}", event.entityId, id, targetId,
          projectile.homing, skillLevel, bonus, projectile.pierceChance);
    }
  }

  private int nearestHostileMonster(int sourceId, Vector2 origin, float maxRange) {
    if (origin == null) return Engine.INVALID_ENTITY;
    int nearest = Engine.INVALID_ENTITY;
    float nearestDistance = maxRange * maxRange;
    IntBag candidates = world.getAspectSubscriptionManager()
        .get(Aspect.all(Monster.class, Position.class)).getEntities();
    for (int i = 0; i < candidates.size(); i++) {
      int candidate = candidates.get(i);
      if (!isHostile(sourceId, candidate) || !mNativeUnitFlagsValid(candidate)) continue;
      float distance = origin.dst2(mPosition.get(candidate).position);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = candidate;
      }
    }
    return nearest;
  }

  /** Native SKILLS_SrvDo012_Strafe: one arrow per selected hostile target. */
  private void spawnStrafe(SkillDoEvent event, Skills.Entry skill,
      Vector2 start, int skillLevel) {
    String missileName = firstNonEmpty(skill.srvmissilea, skill.srvmissileb);
    if (missileName == null) missileName = firstNonEmpty(skill.srvmissile, skill.cltmissilea);
    Missiles.Entry missile = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (missile == null) {
      log.warn("[STRAFE] phase=reject entity={} reason=missing_missile name={}",
          event.entityId, missileName);
      return;
    }
    int count = SkillFormula.evaluate(skill.calc1, skill, skillLevel);
    if (count <= 0) count = AmazonSkills.getStrafeArrowCount(skillLevel);
    count = Math.max(1, Math.min(24, count));
    int range = SkillFormula.evaluate(skill.aurarangecalc, skill, skillLevel);
    if (range <= 0) range = firstParam(skill, 5, 50);
    range = Math.max(1, Math.min(64, range));
    java.util.ArrayList<Integer> targets = new java.util.ArrayList<>();
    com.artemis.EntitySubscription subscription = world.getAspectSubscriptionManager()
        .get(Aspect.all(Monster.class, Position.class));
    IntBag bag = subscription.getEntities();
    for (int i = 0; i < bag.size(); i++) {
      int candidate = bag.get(i);
      if (isHostile(event.entityId, candidate) && mNativeUnitFlagsValid(candidate)
          && mPosition.get(candidate).position.dst(start) <= range) targets.add(candidate);
    }
    if (event.targetId >= 0 && mPosition.has(event.targetId) && !targets.contains(event.targetId)) {
      targets.add(0, event.targetId);
    }
    final Vector2 origin = start;
    targets.sort((a, b) -> Float.compare(mPosition.get(a).position.dst2(origin),
        mPosition.get(b).position.dst2(origin)));
    int created = 0;
    for (int i = 0; i < targets.size() && created < count; i++) {
      int targetId = targets.get(i);
      Vector2 direction = new Vector2(mPosition.get(targetId).position).sub(start);
      if (direction.isZero(0.0001f)) continue;
      int id = createMissile(missile, direction.nor(), start, event.entityId, null, skillLevel);
      if (id >= 0 && mMissile.has(id)) {
        initializeSkillDamage(id, skill, event.entityId, skillLevel);
        Missile arrow = mMissile.get(id);
        arrow.targetId = targetId;
        configurePierce(arrow, event.entityId, skillLevel, false);
        created++;
      }
    }
    if (created == 0) {
      Vector2 direction = resolveTargetPoint(event, start, new Vector2()).sub(start).nor();
      int id = createMissile(missile, direction, start, event.entityId, null, skillLevel);
      if (id >= 0 && mMissile.has(id)) {
        initializeSkillDamage(id, skill, event.entityId, skillLevel);
        configurePierce(mMissile.get(id), event.entityId, skillLevel, false);
        created = 1;
      }
    }
    if (created > 0) consumeRangedAmmoForSkill(event, skill);
    log.info("[STRAFE] phase=create entity={} level={} requested={} targets={} created={} missile={}",
        event.entityId, skillLevel, count, targets.size(), created, missileName);
  }

  private boolean mNativeUnitFlagsValid(int entityId) {
    return !mNativeUnitFlags.has(entityId)
        || NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(entityId));
  }

  private void configurePierce(Missile projectile, int ownerId, int skillLevel,
      boolean guided) {
    if (projectile == null || projectile.missile == null) return;
    int chance = projectile.missile.Pierce ? 100 : 0;
    if (mAttributesWrapper.has(ownerId)) {
      chance = Math.max(chance, statInt(mAttributesWrapper.get(ownerId).attrs, Stat.skill_pierce));
    }
    if (mPlayer.has(ownerId)) {
      Player player = mPlayer.get(ownerId);
      if (player.data != null) {
        int pierceLevel = player.data.getSkill(SkillId.PIERCE);
        if (pierceLevel > 0) {
          chance = Math.max(chance, AmazonSkills.getPierceChance(pierceLevel));
        }
      }
    }
    projectile.pierceChance = Math.max(0, Math.min(100, chance));
    projectile.pierceEnabled = projectile.pierceChance > 0;
  }

  static int firstParam(Skills.Entry skill, int index, int fallback) {
    if (skill == null || skill.Param == null || index < 1 || index > skill.Param.length) return fallback;
    return skill.Param[index - 1];
  }

  static int getSrvDo008Total(Skills.Entry skill, int skillLevel) {
    int total = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, skillLevel);
    return total > 0 ? Math.min(64, total) : Math.max(1, firstParam(skill, 1, 1));
  }

  static int getSrvDo008Centre(Skills.Entry skill, int skillLevel, int total) {
    int centre = SkillFormula.evaluate(skill != null ? skill.calc3 : null, skill, skillLevel);
    return Math.max(0, Math.min(total, centre > 0 ? centre : total));
  }

  static Vector2 fanDirection(Vector2 base, int index, int count, Vector2 out) {
    if (count <= 1) return out.set(base).nor();
    float offset = (index - (count - 1) * 0.5f) * MULTI_MISSILE_SPREAD_RADIANS;
    return out.set(base).rotateRad(offset).nor();
  }

  private int createMissile(Missiles.Entry missile, Vector2 direction, Vector2 start,
      int ownerId, IntSet sharedHitTargets, int damageLevel) {
    if (factory == null) return -1;
    int missileId = factory.createMissile(missile, direction, start, ownerId);
    if (missileId >= 0 && mMercenary.has(ownerId)) mercenaryMissileCount++;
    if (missileId >= 0 && mMissile.has(missileId)) {
      Missile projectile = mMissile.get(missileId);
      if (sharedHitTargets != null) projectile.shareHitTargets(sharedHitTargets);
      Attributes ownerAttrs = mAttributesWrapper.has(ownerId)
          ? mAttributesWrapper.get(ownerId).attrs : null;
      Monster ownerMonster = mMonster.has(ownerId) ? mMonster.get(ownerId) : null;
      int ownerMode = mCofReference.has(ownerId) ? mCofReference.get(ownerId).mode : -1;
      MissileDamageResolver.initialize(projectile, ownerAttrs, ownerMonster,
          ownerMode, damageLevel, 0);
    }
    return missileId;
  }

  /** Native {@code SKILLS_SrvDo080_FistOfTheHeavens}. */
  private void spawnPaladinFistOfTheHeavens(
      SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    if (!PaladinSkills.isFistOfTheHeavens(skill) || !hasText(skill.srvmissilea)
        || event.targetId < 0 || !world.getEntityManager().isActive(event.targetId)
        || !mPosition.has(event.targetId) || !hasPositiveLife(event.targetId)
        || !isHostile(event.entityId, event.targetId)) {
      log.info("[FIST_OF_HEAVENS] phase=do_reject source={} target={} skill={} reason=unit_target",
          event.entityId, event.targetId, skill != null ? skill.Id : -1);
      return;
    }
    Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
    if (row == null) {
      log.warn("[FIST_OF_HEAVENS] phase=do_reject source={} missile={} reason=missing_row",
          event.entityId, skill.srvmissilea);
      return;
    }

    Vector2 targetPosition = new Vector2(mPosition.get(event.targetId).position);
    int missileId = createMissile(
        row, Vector2.X, targetPosition, event.entityId, null, skillLevel);
    if (missileId < 0 || !mMissile.has(missileId)) return;

    Missile delay = mMissile.get(missileId);
    delay.fistOfHeavensDelay = true;
    delay.fistOfHeavensTriggered = false;
    delay.targetId = event.targetId;
    delay.range = 0f;
    delay.nativeLifetimeFrames = Math.max(1, row.Range);
    Attributes ownerAttrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    MissileDamageResolver.initializePaladinFistOfTheHeavens(
        delay, skill, ownerAttrs, skillLevel,
        name -> getBaseSkillLevel(event.entityId, name));

    com.riiablo.codec.excel.NativeSkills.Entry nativeSkill =
        Riiablo.files.NativeSkills != null ? Riiablo.files.NativeSkills.get(skill.Id) : null;
    String overlay = nativeSkill != null ? nativeSkill.string("srvoverlay") : "";
    log.info("[FIST_OF_HEAVENS] phase=delay_create source={} target={} missileId={} "
            + "level={} position=({}, {}) lifetime={} overlay={}",
        event.entityId, event.targetId, missileId, skillLevel,
        targetPosition.x, targetPosition.y, delay.nativeLifetimeFrames, overlay);
  }

  /** Native SKILLS_SrvDo073_BlessedHammer. */
  private void spawnPaladinBlessedHammer(
      SkillDoEvent event, Skills.Entry skill, int skillLevel, Vector2 start) {
    if (!PaladinSkills.isBlessedHammer(skill) || !hasText(skill.srvmissilea)) return;
    Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
    if (row == null) {
      log.warn("[BLESSED_HAMMER] phase=reject entity={} missile={} reason=missing_row",
          event.entityId, skill.srvmissilea);
      return;
    }

    int missileId = createMissile(
        row, Vector2.X, start, event.entityId, null, skillLevel);
    if (missileId < 0 || !mMissile.has(missileId)) return;

    Missile projectile = mMissile.get(missileId);
    projectile.blessedHammerPath = true;
    projectile.blessedHammerOrigin.set(start);
    projectile.blessedHammerPointIndex = 1;
    // Missiles.txt Range is a frame lifetime for this path, not a linear
    // distance. The 77 native path points consume almost exactly 120 frames
    // at the row's velocity.
    projectile.range = 0f;
    projectile.nativeLifetimeFrames = Math.max(1, row.Range);

    int concentration = blessedHammerConcentrationPercent(event.entityId, skill);
    Attributes ownerAttrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    MissileDamageResolver.initializePaladinBlessedHammer(
        projectile, skill, ownerAttrs, skillLevel,
        name -> getBaseSkillLevel(event.entityId, name), concentration);
    log.info("[BLESSED_HAMMER] phase=create entity={} missileId={} level={} "
            + "pathPoints={} lifetime={} concentration={}",
        event.entityId, missileId, skillLevel,
        MissileCollisionSystem.BLESSED_HAMMER_PATH_POINTS,
        projectile.nativeLifetimeFrames, concentration);
  }

  private int blessedHammerConcentrationPercent(int entityId, Skills.Entry skill) {
    StateList states = mUnitStates.has(entityId)
        ? mUnitStates.get(entityId).stateList : null;
    UnitState concentration = states != null ? states.getState(StateId.CONCENTRATION) : null;
    int damagePercent = concentration != null
        ? concentration.getStatContributionValue(Stat.damagepercent) : 0;
    return PaladinSkills.getBlessedHammerConcentrationPercent(skill, damagePercent);
  }

  /** Applies the Skills.txt damage profile after generic missile initialization. */
  private void initializeSkillDamage(int missileId, Skills.Entry skill,
      int ownerId, int skillLevel) {
    if (missileId < 0 || skill == null || !mMissile.has(missileId)
        || !mAttributesWrapper.has(ownerId)) return;
    Missile projectile = mMissile.get(missileId);
    boolean nativePaladin = MissileDamageResolver.initializePaladinHolyBolt(
        projectile, skill, mAttributesWrapper.get(ownerId).attrs, skillLevel,
        name -> getBaseSkillLevel(ownerId, name));
    boolean nativeBone = !nativePaladin && MissileDamageResolver.initializeNecromancerBoneMagic(
        projectile, skill, mAttributesWrapper.get(ownerId).attrs, skillLevel,
        name -> getBaseSkillLevel(ownerId, name));
    if (!nativePaladin && !nativeBone) {
      MissileDamageResolver.initializeSkill(projectile, skill,
          mAttributesWrapper.get(ownerId).attrs, skillLevel,
          name -> getBaseSkillLevel(ownerId, name));
    }
    if (NecromancerSkills.isBoneSpear(skill)) {
      // Native bonespear has LastCollide and CollideKill=0: after a successful
      // hit it continues through additional hostile units, while the missile's
      // per-projectile hit set prevents re-hitting one unit.
      projectile.pierceEnabled = true;
      projectile.pierceChance = 100;
    }
    captureThrowingMastery(mMissile.get(missileId), skill, ownerId);
  }

  /** D2Common D2Common_11024: throwing mastery is fixed at launch time. */
  private void captureThrowingMastery(Missile missile, Skills.Entry skill, int ownerId) {
    if (missile == null || skill == null || !mPlayer.has(ownerId)
        || mPlayer.get(ownerId).data == null || !mUnitStates.has(ownerId)) return;
    boolean throwingAttack = skill.Id == SkillCodes.throw_
        || skill.Id == SkillCodes.left_hand_throw
        || skill.srvdofunc == 3 || skill.srvdofunc == 5;
    if (!throwingAttack) return;
    Item weapon = mPlayer.get(ownerId).data.getItems().getEquippedThrowableWeapon();
    UnitStates unitStates = mUnitStates.get(ownerId);
    if (weapon == null || unitStates.stateList == null) return;
    StateList.WeaponMasteryBonus mastery = unitStates.stateList.getWeaponMastery(
        weapon, true, new StateList.WeaponMasteryBonus());
    missile.masteryAttackRatingPercent = mastery.attackRatingPercent;
    missile.masteryDamagePercent = mastery.damagePercent;
    missile.masteryCriticalChance = mastery.criticalChance;
    log.info("[BARBARIAN_MASTERY] phase=throw_snapshot entity={} skill={} weapon={} "
            + "attackRating={} damage={} critical={}",
        ownerId, skill.Id, weapon.code, mastery.attackRatingPercent,
        mastery.damagePercent, mastery.criticalChance);
  }

  public int mercenaryMissileCount() {
    return mercenaryMissileCount;
  }

  public int mercenarySkillDoCount() {
    return mercenarySkillDoCount;
  }

  public int mercenaryConfiguredMissiles() {
    return mercenaryConfiguredMissiles;
  }

  public int mercenaryLastSrvDoFunc() {
    return mercenaryLastSrvDoFunc;
  }

  static Vector2 radialDirection(int index, int count, Vector2 out) {
    if (count <= 0) return out.setZero();
    float radians = MathUtils.PI2 * index / count;
    return out.set(MathUtils.cos(radians), MathUtils.sin(radians)).nor();
  }

  private static String firstNonEmpty(String primary, String fallback) {
    if (primary != null && !primary.isEmpty()) return primary;
    if (fallback != null && !fallback.isEmpty()) return fallback;
    return null;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  /**
   * Native SrvDo085 adds MonStats' derived chain id to srvMissileA.  The Java
   * table does not store that derived field, so reproduce it from the
   * BaseId/NextInClass chain generated by D2Common.
   */
  static String resolveMonsterChainMissile(Monster monster, String baseMissileName) {
    if (monster == null || monster.monstats == null || !hasText(baseMissileName)) {
      return baseMissileName;
    }
    Missiles.Entry baseMissile = Riiablo.files.Missiles.get(baseMissileName);
    if (baseMissile == null) return baseMissileName;
    int chainId = getMonsterChainId(monster.monstats);
    Missiles.Entry resolved = Riiablo.files.Missiles.get(baseMissile.Id + chainId);
    if (resolved == null) return baseMissileName;
    log.info("[SHAMAN_FIREBALL] phase=missile_resolve monster={} baseMissile={} "
            + "chainId={} missile={} missileId={} celFile={}",
        monster.monstats.Id, baseMissileName, chainId, resolved.Missile,
        resolved.Id, resolved.CelFile);
    return resolved.Missile;
  }

  static int getMonsterChainId(MonStats.Entry monster) {
    if (monster == null || !hasText(monster.Id)) return 0;
    String baseId = hasText(monster.BaseId) ? monster.BaseId : monster.Id;
    MonStats.Entry cursor = Riiablo.files.monstats.get(baseId);
    for (int chainId = 0; cursor != null && chainId < 256; chainId++) {
      if (monster.Id.equalsIgnoreCase(cursor.Id)) return chainId;
      if (!hasText(cursor.NextInClass) || cursor.NextInClass.equalsIgnoreCase(cursor.Id)) break;
      cursor = Riiablo.files.monstats.get(cursor.NextInClass);
    }
    return 0;
  }

  private int getSkillLevel(int entityId, int skillId) {
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      int bonus = mUnitStates.has(entityId)
          && mUnitStates.get(entityId).stateList != null
          ? mUnitStates.get(entityId).stateList.getTotalSkillModifier() : 0;
      return Math.max(1, mPlayer.get(entityId).data.getSkill(skillId) + bonus);
    }
    if (mMonster.has(entityId)) {
      if (mMercenary.has(entityId)) {
        Mercenary merc = mMercenary.get(entityId);
        for (int i = 0; i < merc.skills.length; i++) {
          if (merc.skills[i] == skillId) return Math.max(1, merc.skillLevels[i]);
        }
      }
      Monster monster = mMonster.get(entityId);
      if (monster.monstats != null) {
        String skillName = Riiablo.files.skills.get(skillId) != null
            ? Riiablo.files.skills.get(skillId).skill : null;
        String[] names = {
            monster.monstats.Skill1, monster.monstats.Skill2,
            monster.monstats.Skill3, monster.monstats.Skill4,
            monster.monstats.Skill5, monster.monstats.Skill6,
            monster.monstats.Skill7, monster.monstats.Skill8
        };
        int[] levels = {
            monster.monstats.Sk1lvl, monster.monstats.Sk2lvl,
            monster.monstats.Sk3lvl, monster.monstats.Sk4lvl,
            monster.monstats.Sk5lvl, monster.monstats.Sk6lvl,
            monster.monstats.Sk7lvl, monster.monstats.Sk8lvl
        };
        for (int i = 0; i < names.length; i++) {
          if (skillName != null && skillName.equals(names[i])) return Math.max(1, levels[i]);
        }
      }
    }
    return 1;
  }

  /** Kept as a narrow compatibility wrapper for existing diagnostics/tests. */
  private float getManaCost(Skills.Entry skill, int level) {
    return NativeSkillResolver.manaCost(skill, level);
  }

  /** Native SrvDo069/SrvDo072/SrvDo075 corpse-tool dispatch. */
  private boolean handleBarbarianCorpseSkill(SkillDoEvent event, Skills.Entry skill, int level) {
    if (!mPlayer.has(event.entityId) || skill == null) return false;
    int function = event.srvdofunc != 0 ? event.srvdofunc : skill.srvdofunc;
    if (function != 69 && function != 72 && function != 75) return false;
    int target = event.targetId;
    if (target < 0 || !mCorpse.has(target) || !mPosition.has(target)) {
      log.info("[BARBARIAN_CORPSE] phase=reject source={} target={} skill={} reason=invalid_corpse",
          event.entityId, target, skill.skill);
      return true;
    }
    Corpse corpse = mCorpse.get(target);
    if (corpse == null || !corpse.usable || corpse.fading || hasCorpseNoSelect(target)) {
      log.info("[BARBARIAN_CORPSE] phase=reject source={} target={} skill={} reason=corpse_unusable",
          event.entityId, target, skill.skill);
      return true;
    }
    if (!markCorpseConsumed(
        target, function == 75, level, event.entityId, skill.Id)) {
      log.info("[BARBARIAN_CORPSE] phase=reject source={} target={} skill={} reason=reserve_failed",
          event.entityId, target, skill.skill);
      return true;
    }
    NativeRng rng = new NativeRng(Riiablo.gameSeed ^ event.entityId * 0x45D9F3B ^ target * 31);
    int chance = function == 69
        ? BarbarianSkills.getFindPotionChance(skill, level)
        : function == 72 ? BarbarianSkills.getFindItemChance(skill, level) : 100;
    int roll = rng.nextInt(100);
    if (function == 75) {
      spawnGrimWard(event.entityId, target, skill, level);
      return true;
    }
    if (roll >= chance) {
      log.info("[BARBARIAN_CORPSE] phase=roll source={} target={} skill={} chance={} roll={} success=false",
          event.entityId, target, skill.skill, chance, roll);
      return true;
    }
    if (function == 69) spawnFindPotion(event.entityId, target, skill, level, rng);
    else spawnFindItem(event.entityId, target, skill, level, rng);
    return true;
  }

  private boolean hasCorpseNoSelect(int entityId) {
    return mUnitStates.has(entityId) && mUnitStates.get(entityId).stateList != null
        && mUnitStates.get(entityId).stateList.hasState(StateId.CORPSE_NOSELECT);
  }

  private boolean selectableCorpse(int entityId) {
    if (entityId < 0 || !mCorpse.has(entityId) || !mMonster.has(entityId)
        || !mAttributesWrapper.has(entityId)) return false;
    StateList states = mUnitStates.has(entityId)
        ? mUnitStates.get(entityId).stateList : null;
    return CorpseConsumption.selectable(
        mCorpse.get(entityId), mMonster.get(entityId),
        mAttributesWrapper.get(entityId).attrs, states);
  }

  private boolean reserveCorpse(
      int entityId, boolean hide, int level, int sourceEntityId, int skillId) {
    if (entityId < 0 || !mCorpse.has(entityId) || !mMonster.has(entityId)
        || !mAttributesWrapper.has(entityId)) return false;
    UnitStates unitStates = mUnitStates.has(entityId)
        ? mUnitStates.get(entityId) : mUnitStates.create(entityId).init(entityId);
    if (unitStates.stateList == null) unitStates.init(entityId);
    return CorpseConsumption.tryReserve(
        mCorpse.get(entityId), mMonster.get(entityId),
        mAttributesWrapper.get(entityId).attrs, unitStates.stateList,
        hide, level, sourceEntityId, skillId);
  }

  /** Native room gate used by Raise Skeleton/Mage (SrvSt15/SrvDo031). */
  private boolean isTownCorpse(int entityId) {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper != null && wrapper.zone != null && wrapper.zone.isTown();
  }

  /** D2MOO SrvDo055: consume a corpse and apply its split physical/fire packet. */
  private void explodeNecromancerCorpse(
      SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    int source = event.entityId;
    int corpseId = event.targetId;
    if (!NecromancerSkills.isCorpseExplosion(skill) || isTownCorpse(corpseId)
        || !mPosition.has(corpseId)
        || !reserveCorpse(corpseId, true, skillLevel, source, skill.Id)) {
      log.info("[NECRO_CORPSE_EXPLOSION] phase=reject source={} corpse={} reason=corpse_unusable",
          source, corpseId);
      return;
    }

    Attributes corpseAttrs = mAttributesWrapper.get(corpseId).attrs;
    int corpseMaxHp = nativeCorpseExplosionLife(corpseId, corpseAttrs);
    int[] percent = NecromancerSkills.getCorpseExplosionDamagePercent(skill, skillLevel);
    NativeRng rng = NativeRng.forUnit(
        Riiablo.gameSeed ^ source * 31 ^ skill.Id * 131, corpseId);
    int rolledPercent = percent[0];
    if (percent[1] > percent[0]) rolledPercent += rng.nextInt(percent[1] - percent[0]);
    int total = Math.max(0, corpseMaxHp * rolledPercent / 100);
    int corpseLevel = Math.max(1, statInt(corpseAttrs, Stat.level));
    int sourceLevel = mAttributesWrapper.has(source)
        ? Math.max(1, statInt(mAttributesWrapper.get(source).attrs, Stat.level)) : 1;
    if (sourceLevel < corpseLevel) total = total * sourceLevel / corpseLevel;
    int elementalPercent = NecromancerSkills.getCorpseExplosionElementalPercent(
        skill, skillLevel);
    int fire = total * elementalPercent / 100;
    int physical = total - fire;
    int[] radii = NecromancerSkills.getCorpseExplosionRadii(skill, skillLevel);
    Vector2 origin = new Vector2(mPosition.get(corpseId).position);
    int hit = damageCorpseExplosion(
        source, origin, radii[0], radii[1], physical, fire);
    log.info("[NECRO_CORPSE_EXPLOSION] phase=explode source={} corpse={} skill={} level={} "
            + "corpseHp={} percent={}..{} roll={} total={} physical={} fire={} "
            + "innerRadius={} outerRadius={} hit={}",
        source, corpseId, skill.Id, skillLevel, corpseMaxHp,
        percent[0], percent[1], rolledPercent, total, physical, fire,
        radii[0], radii[1], hit);
  }

  private int nativeCorpseExplosionLife(int corpseId, Attributes fallback) {
    if (mMapWrapper.has(corpseId) && mMapWrapper.get(corpseId).map != null
        && mMonster.has(corpseId) && mMonster.get(corpseId).monstats != null) {
      int level = Math.max(1, statInt(fallback, Stat.level));
      int difficulty = combatDifficulty(corpseId, corpseId);
      MonsterStatsCalculator.MonsterStatsInit calculated =
          new MonsterStatsCalculator.MonsterStatsInit();
      if (MonsterStatsCalculator.calculateMonsterStatsByLevel(
          mMonster.get(corpseId).monstats.hcIdx, 1, difficulty, level,
          (short) 1, calculated)
          && calculated.minHP > 0 && calculated.maxHP >= calculated.minHP) {
        return Math.max(1, (calculated.minHP + calculated.maxHP) / 2);
      }
    }
    return Math.max(1, Math.round(statFixed(fallback, Stat.maxhp)));
  }

  private int damageCorpseExplosion(
      int source, Vector2 origin, int physicalRadius, int outerRadius,
      int physicalDamage, int fireDamage) {
    IntBag candidates = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int hit = 0;
    for (int i = 0; i < candidates.size(); i++) {
      int target = candidates.get(i);
      if (target == source || mCorpse.has(target)
          || (!mPlayer.has(target) && !mMonster.has(target))
          || !isHostile(source, target) || !sameZone(source, target)
          || !mNativeUnitFlagsValid(target)) continue;
      float distance2 = origin.dst2(mPosition.get(target).position);
      if (distance2 > outerRadius * outerRadius) continue;
      Attributes targetAttrs = mAttributesWrapper.get(target).attrs;
      StatRef hp = targetAttrs != null
          ? targetAttrs.get(Stat.hitpoints, StatRef.obtain()) : null;
      if (hp == null || hp.asFixed() <= 0f) continue;
      StateList targetStates = mUnitStates.has(target)
          ? mUnitStates.get(target).stateList : null;
      int physical = distance2 <= physicalRadius * physicalRadius ? physicalDamage : 0;
      boolean sourcePlayer = mPlayer.has(source);
      boolean targetPlayer = mPlayer.has(target);
      CombatSystem.CombatResult physicalResult =
          CombatSystem.INSTANCE.calculateFixedPhysicalDamage(
              targetAttrs, targetPlayer, sourcePlayer, physical, targetStates);
      CombatSystem.CombatResult fireResult =
          CombatSystem.INSTANCE.calculateFixedElementalDamage(
              targetAttrs, targetPlayer, sourcePlayer, CombatSystem.DAMAGE_FIRE,
              fireDamage, 0, targetStates, combatDifficulty(source, target));
      int resolved = physicalResult.totalDamage + fireResult.totalDamage;
      if (resolved <= 0 && fireResult.absorbedLife <= 0) continue;
      if (fireResult.absorbedLife > 0) {
        StatRef maxHp = targetAttrs.get(Stat.maxhp, StatRef.obtain());
        if (maxHp != null) hp.add(Math.max(0f,
            Math.min((float) fireResult.absorbedLife, maxHp.asFixed() - hp.asFixed())));
      }
      DamageEvent damage = DamageEvent.obtain(source, target, resolved);
      damage.physicalDamage = physicalResult.physicalDamage;
      if (events != null) events.dispatch(damage);
      hp.sub(Math.max(0f, damage.damage));
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        if (events != null) events.dispatch(DeathEvent.obtain(source, target));
      }
      hit++;
    }
    return hit;
  }

  /** D2MOO SrvDo063: eight drifting, authoritative poison-cloud missiles. */
  private void spawnNecromancerPoisonExplosion(
      SkillDoEvent event, Skills.Entry skill, int skillLevel) {
    int source = event.entityId;
    int corpseId = event.targetId;
    Missiles.Entry cloudRow = skill != null && skill.srvmissilea != null
        ? Riiablo.files.Missiles.get(skill.srvmissilea) : null;
    if (!NecromancerSkills.isPoisonExplosion(skill) || cloudRow == null || factory == null
        || isTownCorpse(corpseId) || !mPosition.has(corpseId)
        || !reserveCorpse(corpseId, false, skillLevel, source, skill.Id)) {
      log.info("[NECRO_POISON_EXPLOSION] phase=reject source={} corpse={} reason={}",
          source, corpseId, cloudRow == null ? "missile_missing" : "corpse_unusable");
      return;
    }

    int[] poison = NecromancerSkills.getPoisonExplosionDamage(
        skill, skillLevel, name -> getBaseSkillLevel(source, name));
    Attributes sourceAttrs = mAttributesWrapper.has(source)
        ? mAttributesWrapper.get(source).attrs : null;
    int mastery = Math.max(0, statInt(sourceAttrs, Stat.passive_pois_mastery));
    poison[0] = saturatedScale(poison[0], 100 + mastery, 100);
    poison[1] = saturatedScale(poison[1], 100 + mastery, 100);
    int duration = NecromancerSkills.getPoisonExplosionDurationFrames(skill, skillLevel);
    int pierce = Math.max(0, statInt(sourceAttrs, Stat.item_pierce_pois)
        + statInt(sourceAttrs, Stat.passive_pois_pierce));
    Vector2 origin = new Vector2(mPosition.get(corpseId).position);
    int[] x = {0, 2, 2, 2, 0, -2, -2, -2};
    int[] y = {2, 2, 0, -2, -2, -2, 0, 2};
    int velocity = cloudRow.Param != null && cloudRow.Param.length > 0
        ? Math.max(0, cloudRow.Param[0]) : 0;
    int created = 0;
    for (int i = 0; i < x.length; i++) {
      Vector2 direction = new Vector2(x[i], y[i]).nor();
      int missileId = createMissile(
          cloudRow, direction, origin, source, null, skillLevel);
      if (missileId < 0 || !mMissile.has(missileId)) continue;
      Missile cloud = mMissile.get(missileId);
      cloud.skillId = skill.Id;
      cloud.damageLevel = skillLevel;
      cloud.fixedPoisonRate = true;
      cloud.poisonMinRateFixed = poison[0];
      cloud.poisonMaxRateFixed = poison[1];
      cloud.poisonDurationFrames = duration;
      cloud.poisonPiercePercent = pierce;
      cloud.poisonAttackerPlayer = mPlayer.has(source);
      cloud.persistent = true;
      cloud.remainingFrames = Math.max(1, cloudRow.Range);
      cloud.tickInterval = 1;
      cloud.pierceEnabled = true;
      if (mVelocity.has(missileId)) {
        mVelocity.get(missileId).velocity.set(direction).setLength(velocity);
      }
      created++;
    }
    log.info("[NECRO_POISON_EXPLOSION] phase=cloud_create source={} corpse={} skill={} "
            + "level={} missile={} created={} velocity={} duration={} rawFixed={}..{} "
            + "mastery={} pierce={}",
        source, corpseId, skill.Id, skillLevel, cloudRow.Missile, created,
        velocity, duration, poison[0], poison[1], mastery, pierce);
  }

  private static int saturatedScale(int value, int numerator, int denominator) {
    if (value <= 0 || numerator <= 0 || denominator <= 0) return 0;
    long result = (long) value * numerator / denominator;
    return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }

  private int combatDifficulty(int source, int target) {
    if (mMapWrapper.has(target) && mMapWrapper.get(target).map != null) {
      return mMapWrapper.get(target).map.getDifficulty();
    }
    if (mMapWrapper.has(source) && mMapWrapper.get(source).map != null) {
      return mMapWrapper.get(source).map.getDifficulty();
    }
    return map != null ? map.getDifficulty() : 0;
  }

  private boolean sameZone(int first, int second) {
    if (!mMapWrapper.has(first) || !mMapWrapper.has(second)) return true;
    MapWrapper a = mMapWrapper.get(first);
    MapWrapper b = mMapWrapper.get(second);
    return a == null || b == null || a.zone == null || b.zone == null || a.zone == b.zone;
  }

  /**
   * D2MOO SKILLS_SrvDo031_RaiseSkeleton_Mage.  The corpse is reserved before
   * spawning so two keyframes (or two necromancers) cannot consume it twice;
   * it is removed only after the pet was created successfully.  A failed
   * spawn rolls the reservation back, matching the native consumeable check.
   */
  private void raiseNecromancerSkeleton(SkillDoEvent event, Skills.Entry skill, int level) {
    final int source = event.entityId;
    final int corpseId = event.targetId;
    if (!mPlayer.has(source) || skill == null || corpseId < 0
        || !mMonster.has(corpseId) || !mCorpse.has(corpseId)
        || !mPosition.has(corpseId)) {
      log.info("[NECRO_SUMMON] phase=reject source={} corpse={} reason=invalid_target", source, corpseId);
      return;
    }
    Corpse corpse = mCorpse.get(corpseId);
    if (corpse == null || !corpse.usable || corpse.fading || hasCorpseNoSelect(corpseId)
        || isTownCorpse(corpseId)) {
      log.info("[NECRO_SUMMON] phase=reject source={} corpse={} reason=corpse_unusable", source, corpseId);
      return;
    }
    Monster dead = mMonster.get(corpseId);
    if (dead == null || dead.monstats == null || dead.monstats2 == null) {
      log.info("[NECRO_SUMMON] phase=reject source={} corpse={} reason=monster_data_missing", source, corpseId);
      return;
    }

    // Reserve atomically before invoking the factory.  The state is also
    // replicated, so a second client cannot race the same corpse.
    if (!markCorpseConsumed(corpseId, true, level, source, skill.Id)) {
      log.info("[NECRO_SUMMON] phase=reject source={} corpse={} reason=reserve_failed",
          source, corpseId);
      return;
    }
    String summonName = skill.summon;
    MonStats.Entry summon = resolveSummonMonster(summonName);
    if (summon == null) {
      corpse.usable = true;
      if (mUnitStates.has(corpseId) && mUnitStates.get(corpseId).stateList != null) {
        mUnitStates.get(corpseId).stateList.removeState(StateId.CORPSE_NOSELECT);
        mUnitStates.get(corpseId).stateList.removeState(StateId.CORPSE_NODRAW);
      }
      log.warn("[NECRO_SUMMON] phase=rollback source={} corpse={} reason=missing_summon row={}",
          source, corpseId, summonName);
      return;
    }
    String petType = PetType.canonical(skill.pettype);
    if (petType.isEmpty()) petType = inferSkeletonPetType(skill, summon);
    int petMax = Math.max(1, SkillFormula.evaluate(skill.petmax, skill, level,
        name -> getBaseSkillLevel(source, name)));
    Vector2 position = mPosition.get(corpseId).position;
    int petId = factory == null ? Engine.INVALID_ENTITY : factory.createSummonedPet(
        source, summon, petType, event.skillId, level, petMax, false, 0,
        position.x, position.y);
    if (petId == Engine.INVALID_ENTITY) {
      corpse.usable = true;
      if (mUnitStates.has(corpseId) && mUnitStates.get(corpseId).stateList != null) {
        mUnitStates.get(corpseId).stateList.removeState(StateId.CORPSE_NOSELECT);
        mUnitStates.get(corpseId).stateList.removeState(StateId.CORPSE_NODRAW);
      }
      log.warn("[NECRO_SUMMON] phase=rollback source={} corpse={} reason=create_failed", source, corpseId);
      return;
    }
    applySummonSkillStats(source, petId, skill, level, true);
    applySummonResistance(source, petId);
    // Native SrvDo031 removes the consumed corpse unit from the room.
    mCorpse.remove(corpseId);
    world.delete(corpseId);
    log.info("[NECRO_SUMMON] phase=created source={} corpse={} pet={} summon={} petType={} level={} max={} position=({}, {})",
        source, corpseId, petId, summon.Id, petType, level, petMax, position.x, position.y);
  }

  private static String inferSkeletonPetType(Skills.Entry skill, MonStats.Entry summon) {
    String value = (skill.skill == null ? "" : skill.skill).toLowerCase(java.util.Locale.ROOT);
    String row = (summon.Id == null ? "" : summon.Id).toLowerCase(java.util.Locale.ROOT);
    return value.contains("mage") || row.contains("mage") ? "skeletonmage" : "skeleton";
  }

  /** Skills.txt summon names are symbolic and are not consistently cased in 1.10f. */
  private static MonStats.Entry resolveSummonMonster(String summonName) {
    if (summonName == null || summonName.isEmpty()) return null;
    MonStats.Entry summon = Riiablo.files.monstats.get(summonName);
    if (summon == null) summon = Riiablo.files.monstats.get(
        summonName.toLowerCase(java.util.Locale.ROOT));
    if (summon != null) return summon;
    for (MonStats.Entry candidate : Riiablo.files.monstats) {
      if (candidate != null && candidate.Id != null
          && candidate.Id.equalsIgnoreCase(summonName)) return candidate;
    }
    return null;
  }

  /** Native txt compilation resolves symbolic skill references without preserving case. */
  private static Skills.Entry resolveSkill(String skillName) {
    if (skillName == null || skillName.trim().isEmpty()
        || Riiablo.files == null || Riiablo.files.skills == null) return null;
    String name = skillName.trim();
    Skills.Entry skill = Riiablo.files.skills.get(name);
    if (skill != null) return skill;
    for (Skills.Entry candidate : Riiablo.files.skills) {
      if (candidate != null && candidate.skill != null
          && candidate.skill.equalsIgnoreCase(name)) return candidate;
    }
    return null;
  }

  /** D2MOO SrvDo056 and SrvDo057. */
  private void spawnNecromancerGolem(SkillDoEvent event, Skills.Entry skill, int level,
      Vector2 caster, boolean iron) {
    if (!mPlayer.has(event.entityId) || skill == null || factory == null) return;
    com.riiablo.engine.server.component.Item ground = iron && mItem.has(event.targetId)
        ? mItem.get(event.targetId) : null;
    if (iron && !isValidIronGolemItem(event.targetId)) {
      log.info("[NECRO_IRON_GOLEM] phase=reject source={} item={} reason=item_eligibility",
          event.entityId, event.targetId);
      return;
    }
    if (ground != null) ground.skillReserved = true;
    MonStats.Entry summon = resolveSummonMonster(skill.summon);
    if (summon == null) {
      if (ground != null) ground.skillReserved = false;
      log.warn("[NECRO_GOLEM] phase=rollback source={} skill={} reason=missing_summon row={}",
          event.entityId, skill.skill, skill.summon);
      return;
    }
    String petType = PetType.canonical(skill.pettype);
    if (petType.isEmpty()) petType = "golem";
    int petMax = Math.max(1, SkillFormula.evaluate(skill.petmax, skill, level,
        name -> getBaseSkillLevel(event.entityId, name)));
    Vector2 target = iron
        ? new Vector2(mPosition.get(event.targetId).position)
        : resolveTargetPoint(event, caster, new Vector2());
    int petId = factory.createSummonedPet(event.entityId, summon, petType,
        event.skillId, level, petMax, false, 0, target.x, target.y);
    if (petId == Engine.INVALID_ENTITY) {
      if (ground != null) ground.skillReserved = false;
      log.warn("[NECRO_GOLEM] phase=rollback source={} skill={} reason=create_failed",
          event.entityId, skill.skill);
      return;
    }
    applySummonSkillStats(event.entityId, petId, skill, level, true);
    applySummonResistance(event.entityId, petId);
    applySummonGrantedSkills(event.entityId, petId, skill, level);
    if (iron && ground != null) {
      if (mSummonedPet.has(petId)) mSummonedPet.get(petId).sourceItem = ground.item;
      applyIronGolemSourceItemStats(petId, ground.item);
      world.delete(event.targetId);
    }
    log.info("[NECRO_{}GOLEM] phase=created source={} pet={} summon={} petType={} level={} max={} item={} position=({}, {})",
        iron ? "IRON_" : "", event.entityId, petId, summon.Id, petType, level,
        petMax, iron ? event.targetId : Engine.INVALID_ENTITY, target.x, target.y);
  }

  /** D2GAME_SetSummonPassiveStats: install SumSkill/SumSkCalc entries. */
  private void applySummonGrantedSkills(int source, int petId, Skills.Entry summonSkill,
      int summonLevel) {
    if (summonSkill == null || summonSkill.sumskill == null || summonSkill.sumskcalc == null
        || !mUnitStates.has(petId)) return;
    int count = Math.min(summonSkill.sumskill.length, summonSkill.sumskcalc.length);
    for (int i = 0; i < count; i++) {
      String name = summonSkill.sumskill[i];
      if (name == null || name.isEmpty()) continue;
      Skills.Entry granted = resolveSkill(name);
      if (granted == null) continue;
      int grantedLevel = SkillFormula.evaluate(summonSkill.sumskcalc[i], summonSkill,
          summonLevel, skill -> getBaseSkillLevel(source, skill));
      if (grantedLevel <= 0) continue;
      int stateId = stateId(granted.aurastate);
      if (stateId == StateId.NONE) continue;
      UnitStates states = mUnitStates.get(petId);
      if (states.stateList == null) states.init(petId);
      UnitState aura = states.stateList.addStateLayer(
          stateId, 0, grantedLevel, petId, granted.Id);
      if (aura != null) {
        aura.periodicDelayFrames = Math.max(1,
            SkillFormula.evaluate(granted.perdelay, granted, grantedLevel));
        aura.periodicCountdownFrames = 0;
        aura.needsSync = true;
      }
      log.info("[SUMMON_SKILL] owner={} pet={} sourceSkill={} granted={} level={} state={} delay={}",
          source, petId, summonSkill.skill, granted.skill, grantedLevel,
          StateId.getName(stateId), aura != null ? aura.periodicDelayFrames : 0);
    }
  }

  /** Native SrvDo057 equips the consumed metal item on the Iron Golem. */
  private void applyIronGolemSourceItemStats(int petId, Item item) {
    if (item == null || item.attrs == null || !mAttributesWrapper.has(petId)) return;
    Attributes pet = mAttributesWrapper.get(petId).attrs;
    if (pet == null || item.type == null) return;
    com.riiablo.attributes.AttributesUpdater updater =
        new com.riiablo.attributes.AttributesUpdater();
    item.aggFlags |= com.riiablo.attributes.StatListFlags.FLAG_MAGIC
        | com.riiablo.attributes.StatListFlags.FLAG_RUNE;
    item.update(updater, pet, null, new com.badlogic.gdx.utils.IntIntMap());
    updater.update(pet, null).add(item.attrs.remaining()).apply();
    copyIronGolemBaseStat(pet, item, Stat.armorclass);
    copyIronGolemBaseStat(pet, item, Stat.mindamage);
    copyIronGolemBaseStat(pet, item, Stat.maxdamage);
    log.info("[NECRO_IRON_GOLEM] phase=item_stats pet={} item={} code={} stats={}",
        petId, item.id, item.code, item.attrs.remaining().size());
  }

  private static void copyIronGolemBaseStat(Attributes pet, Item item, short stat) {
    StatRef value = item.attrs.get(stat, StatRef.obtain());
    if (value != null) pet.aggregate().add(value);
  }

  /** D2MOO SrvDo058: revive the original monster entity and move it to the owner's pet list. */
  private void reviveNecromancerMonster(SkillDoEvent event, Skills.Entry skill, int level) {
    int corpseId = event.targetId;
    if (!mPlayer.has(event.entityId) || !isReviveableMonster(corpseId)
        || !mCorpse.has(corpseId) || hasCorpseNoSelect(corpseId) || isTownCorpse(corpseId)
        || factory == null) {
      log.info("[NECRO_REVIVE] phase=reject source={} corpse={} reason=corpse_eligibility",
          event.entityId, corpseId);
      return;
    }
    // ServerEntityFactory reserves Corpse.usable before restoring any state,
    // making the in-place conversion idempotent within this fixed tick.
    if (!factory.reviveMonster(corpseId, event.entityId)) {
      log.warn("[NECRO_REVIVE] phase=rollback source={} corpse={} reason=restore_failed",
          event.entityId, corpseId);
      return;
    }
    String petType = PetType.canonical(skill.pettype);
    if (petType.isEmpty()) petType = "revive";
    int petMax = Math.max(1, SkillFormula.evaluate(skill.petmax, skill, level,
        name -> getBaseSkillLevel(event.entityId, name)));
    int duration = Math.max(0, SkillFormula.evaluate(skill.calc2, skill, level,
        name -> getBaseSkillLevel(event.entityId, name)));
    SummonedPet pet = mSummonedPet.has(corpseId)
        ? mSummonedPet.get(corpseId) : mSummonedPet.create(corpseId);
    pet.set(event.entityId, petType, event.skillId, level, false, duration);
    if (mNativeUnitFlags.has(corpseId)) {
      mNativeUnitFlags.get(corpseId).reset()
          .set(NativeUnitFlags.PLAYER_SUMMON | NativeUnitFlags.IS_REVIVE);
    }
    if (mUnitStates.has(corpseId)) {
      UnitStates states = mUnitStates.get(corpseId);
      if (states.stateList == null) states.init(corpseId);
      UnitState revive = states.stateList.addState(StateId.REVIVE, duration, level, event.entityId);
      if (revive != null) {
        revive.skillId = event.skillId;
        revive.needsSync = true;
      }
    }
    scaleReviveToOwnerLevel(event.entityId, corpseId);
    applySummonSkillStats(event.entityId, corpseId, skill, level, false);
    enforcePetMaximum(event.entityId, corpseId, petType, petMax);
    log.info("[NECRO_REVIVE] phase=created source={} pet={} monster={} petType={} level={} max={} duration={}",
        event.entityId, corpseId,
        mMonster.get(corpseId).monstats != null ? mMonster.get(corpseId).monstats.Id : "unknown",
        petType, level, petMax, duration);
  }

  private boolean isReviveableMonster(int entityId) {
    if (entityId < 0 || !mMonster.has(entityId)) return false;
    Monster monster = mMonster.get(entityId);
    return monster != null && monster.monstats2 != null && monster.monstats2.revive;
  }

  private boolean isValidIronGolemItem(int entityId) {
    if (entityId < 0 || !mItem.has(entityId) || !mPosition.has(entityId)) return false;
    com.riiablo.engine.server.component.Item ground = mItem.get(entityId);
    Item item = ground != null ? ground.item : null;
    return ground != null && !ground.skillReserved && item != null
        && item.location == com.riiablo.item.Location.GROUND && item.isIdentified()
        && item.base != null && (item.base.bitfield1 & 2) != 0;
  }

  private void scaleReviveToOwnerLevel(int ownerId, int petId) {
    if (!mAttributesWrapper.has(ownerId) || !mAttributesWrapper.has(petId)) return;
    Attributes owner = mAttributesWrapper.get(ownerId).attrs;
    Attributes pet = mAttributesWrapper.get(petId).attrs;
    int ownerLevel = Math.max(1, statInt(owner, Stat.level));
    int petLevel = Math.max(1, statInt(pet, Stat.level));
    if (ownerLevel >= petLevel) return;
    float maxHp = Math.max(1f, statFixed(pet, Stat.maxhp));
    float scaled = Math.max(1f, maxHp * ownerLevel / petLevel);
    pet.base().put(Stat.level, ownerLevel);
    pet.base().put(Stat.maxhp, scaled);
    pet.base().put(Stat.hitpoints, scaled);
    pet.reset();
  }

  private void enforcePetMaximum(int ownerId, int newestId, String petType, int maximum) {
    IntBag pets = world.getAspectSubscriptionManager().get(Aspect.all(SummonedPet.class)).getEntities();
    int excess = 0;
    for (int i = 0; i < pets.size(); i++) {
      int id = pets.get(i);
      SummonedPet pet = mSummonedPet.get(id);
      if (pet != null && pet.ownerId == ownerId && PetType.sameNativeType(petType, pet.petType)) excess++;
    }
    excess -= Math.max(1, maximum);
    for (int i = 0; excess > 0 && i < pets.size(); i++) {
      int id = pets.get(i);
      SummonedPet pet = mSummonedPet.get(id);
      if (id != newestId && pet != null && pet.ownerId == ownerId
          && PetType.sameNativeType(petType, pet.petType)) {
        world.delete(id);
        excess--;
      }
    }
  }

  /** Applies the native summon row's passive stat list to the new pet. */
  private void applySummonSkillStats(int source, int petId, Skills.Entry skill, int level,
      boolean setBaseLevel) {
    if (!mAttributesWrapper.has(petId) || skill == null) return;
    Attributes attrs = mAttributesWrapper.get(petId).attrs;
    if (attrs == null) return;
    if (setBaseLevel) {
      int ownerLevel = mAttributesWrapper.has(source)
          ? Math.max(1, statInt(mAttributesWrapper.get(source).attrs, Stat.level)) : 1;
      attrs.base().put(Stat.level, summonBaseLevel(ownerLevel, level));
    }
    if (skill.passivestat != null && skill.passivecalc != null) {
      for (int i = 0; i < skill.passivestat.length && i < skill.passivecalc.length; i++) {
        String name = skill.passivestat[i];
        if (name == null || name.isEmpty()) continue;
        short stat = Stat.index(name);
        if (stat < 0) continue;
        int value = SkillFormula.evaluate(skill.passivecalc[i], skill, level,
            name2 -> getBaseSkillLevel(source, name2));
        if (value != 0) attrs.base().add(stat, value);
      }
    }
    // D2MOO posts the summon aura stat-list after passive stats.  Keeping the
    // values on the pet's base aggregate makes the same snapshot visible to
    // combat, missile and remote clients instead of using hard-coded golem
    // bonuses in AI code.
    if (skill.aurastat != null && skill.aurastatcalc != null) {
      for (int i = 0; i < skill.aurastat.length && i < skill.aurastatcalc.length; i++) {
        String name = skill.aurastat[i];
        if (name == null || name.isEmpty()) continue;
        short stat = Stat.index(name);
        if (stat < 0) continue;
        int value = SkillFormula.evaluate(skill.aurastatcalc[i], skill, level,
            name2 -> getBaseSkillLevel(source, name2));
        if (value != 0) attrs.base().add(stat, value);
      }
    }
    if (skill.aurastate != null && !skill.aurastate.isEmpty() && mUnitStates.has(petId)) {
      int stateId = stateId(skill.aurastate);
      if (stateId != StateId.NONE) {
        UnitStates unitStates = mUnitStates.get(petId);
        if (unitStates.stateList == null) unitStates.init(petId);
        UnitState aura = unitStates.stateList.addStateLayer(stateId, 0, level, source, skill.Id);
        if (aura != null) aura.needsSync = true;
      }
    }
    float maxHp = Math.max(1f, statFixed(attrs, Stat.maxhp));
    int hpPercent = SkillFormula.evaluate(skill.calc1, skill, level,
        name -> getBaseSkillLevel(source, name));
    float adjustedHp = Math.max(1f, maxHp * (100f + hpPercent) / 100f);
    attrs.base().put(Stat.maxhp, adjustedHp);
    attrs.base().put(Stat.hitpoints, adjustedHp);
    attrs.reset();
  }

  private static int stateId(String name) {
    if (name == null) return StateId.NONE;
    switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "holyfire": return StateId.HOLYFIRE;
      case "thorns": return StateId.THORNS;
      default: return StateId.NONE;
    }
  }

  /** D2GAME_SetSummonResistance_6FD0C2E0, with absorb guards. */
  private void applySummonResistance(int source, int petId) {
    if (!mAttributesWrapper.has(source) || !mAttributesWrapper.has(petId)) return;
    Attributes owner = mAttributesWrapper.get(source).attrs;
    Attributes pet = mAttributesWrapper.get(petId).attrs;
    int resist = statInt(owner, Stat.passive_summon_resist);
    if (resist == 0 || pet == null) return;
    if (statInt(pet, Stat.item_absorbfire_percent) <= 0) pet.base().add(Stat.fireresist, resist);
    if (statInt(pet, Stat.item_absorblight_percent) <= 0) pet.base().add(Stat.lightresist, resist);
    if (statInt(pet, Stat.item_absorbcold_percent) <= 0) pet.base().add(Stat.coldresist, resist);
    pet.base().add(Stat.poisonresist, resist);
    pet.reset();
  }

  private boolean markCorpseConsumed(
      int entityId, boolean hide, int level, int sourceEntityId, int skillId) {
    boolean reserved = reserveCorpse(
        entityId, hide, level, sourceEntityId, skillId);
    if (reserved) {
      log.debug("[CORPSE_SKILL] phase=consume entity={} source={} skill={} hide={}",
          entityId, sourceEntityId, skillId, hide);
    }
    return reserved;
  }

  private void spawnFindPotion(int source, int corpseId, Skills.Entry skill, int level, NativeRng rng) {
    if (factory == null || itemGenerator == null) return;
    String[] health = {"hp2", "hp3", "hp3", "hp4", "hp4", "hp4", "hp5", "hp5", "hp5", "hp5", "hp5", "hp5", "hp5", "hp5", "hp5"};
    String[] mana = {"mp2", "mp3", "mp3", "mp4", "mp4", "mp4", "mp5", "mp5", "mp5", "mp5", "mp5", "mp5", "mp5", "mp5", "mp5"};
    String[] rejuv = {"rvs", "rvs", "rvs", "rvl", "rvl", "rvl", "rvl", "rvl", "rvl", "rvl", "rvl", "rvl", "rvl", "rvl", "rvl"};
    int index = potionTableIndex(source, corpseId);
    int bucket = rng.nextInt(100);
    int manaChance = skill.Param != null && skill.Param.length > 2 ? skill.Param[2] : 60;
    int rejuvChance = skill.Param != null && skill.Param.length > 3 ? skill.Param[3] : 30;
    String code = bucket < manaChance ? mana[index]
        : bucket < manaChance + rejuvChance ? rejuv[index] : health[index];
    createCorpseDrop(source, corpseId, code, Quality.NORMAL, level, rng);
    log.info("[BARBARIAN_FIND_POTION] source={} corpse={} code={} tableIndex={}", source, corpseId, code, index);
  }

  private int potionTableIndex(int source, int corpseId) {
    if (!mMapWrapper.has(corpseId) || mMapWrapper.get(corpseId).zone == null
        || mMapWrapper.get(corpseId).zone.level == null) return 0;
    int act = Math.max(0, Math.min(4, mMapWrapper.get(corpseId).zone.level.Act));
    int difficulty = mPlayer.has(source) && mPlayer.get(source).data != null
        ? Math.max(0, Math.min(2, mPlayer.get(source).data.diff)) : 0;
    return act + difficulty * 5;
  }

  private void spawnFindItem(int source, int corpseId, Skills.Entry skill, int level, NativeRng rng) {
    if (factory == null || itemGenerator == null || !mMonster.has(corpseId)) return;
    Monster monster = mMonster.get(corpseId);
    String tc = monster.monstats != null && monster.monstats.TreasureClass1 != null
        ? monster.monstats.TreasureClass1[Math.max(0, Math.min(2,
            mPlayer.get(source).data != null ? mPlayer.get(source).data.diff : 0))] : null;
    if (tc == null || tc.isEmpty()) return;
    LootManager.LootConfig config = new LootManager.LootConfig();
    config.treasureClass = tc;
    StatRef monsterLevel = mAttributesWrapper.has(corpseId)
        ? mAttributesWrapper.get(corpseId).attrs.get(Stat.level, StatRef.obtain()) : null;
    config.monsterLevel = monsterLevel != null ? Math.max(1, monsterLevel.asInt()) : 1;
    config.areaLevel = config.monsterLevel;
    config.difficulty = Math.max(0, Math.min(2,
        mPlayer.get(source).data != null ? mPlayer.get(source).data.diff : 0));
    config.playerCount = 1;
    config.rngSeed = rng.nextInt(Integer.MAX_VALUE);
    if (mAttributesWrapper.has(source)) {
      barbarianLoot.applyPlayerBonuses(mAttributesWrapper.get(source).attrs, config);
    }
    LootManager.LootResult result = barbarianLoot.calculateLoot(config);
    for (int i = 0; i < result.getItemCount(); i++) {
      Quality quality = Quality.valueOf(result.itemQualities.get(i));
      createCorpseDrop(source, corpseId, result.itemCodes.get(i),
          quality != null ? quality : Quality.NORMAL, result.itemLevels.get(i), rng);
    }
    if (result.goldAmount > 0) createCorpseGold(source, corpseId, result.goldAmount, rng);
    log.info("[BARBARIAN_FIND_ITEM] source={} corpse={} tc={} drops={} gold={}",
        source, corpseId, tc, result.getItemCount(), result.goldAmount);
  }

  private void spawnGrimWard(int source, int corpseId, Skills.Entry skill, int level) {
    if (factory == null || !mPosition.has(corpseId)) return;
    String missileName = skill.srvmissilea;
    if (mMonster.has(corpseId) && mMonster.get(corpseId).monstats2 != null) {
      if (mMonster.get(corpseId).monstats2.large && hasText(skill.srvmissilec)) {
        missileName = skill.srvmissilec;
      } else if (mMonster.get(corpseId).monstats2.small && hasText(skill.srvmissileb)) {
        missileName = skill.srvmissileb;
      }
    }
    Missiles.Entry missile = missileName == null ? null : Riiablo.files.Missiles.get(missileName);
    if (missile == null) return;
    int id = createMissile(missile, new Vector2(1, 0), mPosition.get(corpseId).position,
        source, null, level);
    log.info("[BARBARIAN_GRIM_WARD] source={} corpse={} missile={} entity={}", source, corpseId, missileName, id);
  }

  private void createCorpseDrop(int source, int corpseId, String code, Quality quality,
      int level, NativeRng rng) {
    try {
      com.riiablo.item.Item item = itemGenerator.generateLootItem(code, Math.max(1, level), quality,
          rng.nextInt(Integer.MAX_VALUE), mPlayer.get(source).data != null ? mPlayer.get(source).data.diff : 0);
      int id = factory.createItem(item, mPosition.get(corpseId).position.x,
          mPosition.get(corpseId).position.y);
      if (id >= 0) {
        item.id = id;
        GroundDropOwnership.register(id, source, (short) -1, 10_000L, 10_000L, false);
      }
    } catch (Throwable t) {
      log.error("[BARBARIAN_CORPSE] drop failed source={} corpse={} code={}", source, corpseId, code, t);
    }
  }

  private void createCorpseGold(int source, int corpseId, int amount, NativeRng rng) {
    try {
      com.riiablo.item.Item gold = itemGenerator.generate("gld");
      gold.quality = Quality.NORMAL;
      gold.flags |= com.riiablo.item.Item.ITEMFLAG_IDENTIFIED;
      gold.attrs.base().put(Stat.quantity, amount);
      int id = factory.createItem(gold, mPosition.get(corpseId).position.x,
          mPosition.get(corpseId).position.y);
      if (id >= 0) {
        gold.id = id;
        GroundDropOwnership.register(id, source, -1, 10_000L, 10_000L, true);
      }
    } catch (Throwable t) {
      log.error("[BARBARIAN_FIND_ITEM] gold drop failed source={} corpse={} amount={}",
          source, corpseId, amount, t);
    }
  }

  private String resolveThrowableMissile(int entityId, int skillId, Skills.Entry skill) {
    if (skillId != SkillCodes.throw_ && skillId != SkillCodes.left_hand_throw
        && skill.srvdofunc != 3 && skill.srvdofunc != 5) {
      return null;
    }
    if (!mPlayer.has(entityId)) return null;
    Player player = mPlayer.get(entityId);
    if (player.data == null || player.data.getItems() == null) return null;
    Item weapon = player.data.getItems().getEquippedThrowableWeapon();
    if (weapon == null || !hasText(weapon.code)) return null;

    // Item codes (for example "jav") are not necessarily Missiles.txt row
    // names.  Use the same native-data candidates as the presentation path,
    // but only return a name that actually resolves on the authoritative
    // server.  Returning the raw item code made Throw consume quantity while
    // silently failing to create a missile.
    String[] candidates = {
        weapon.code,
        weapon.code + "s",
        "electric" + weapon.code,
        "electric " + weapon.code,
        "throwing" + weapon.code,
        weapon.code + "throw",
        "javelin",
        "javelins"
    };
    for (String candidate : candidates) {
      Missiles.Entry missile = Riiablo.files.Missiles.get(candidate);
      if (missile != null) return missile.Missile;
    }
    log.warn("[THROW_ATTACK] phase=missile_resolve_failed entity={} skill={} weaponCode={}",
        entityId, skillId, weapon.code);
    return null;
  }

  /** Native normal Attack uses the equipped bow's ammunition missile. */
  private String resolveNormalAttackMissile(int entityId, int skillId) {
    if (skillId != SkillCodes.attack || !mPlayer.has(entityId)) return null;
    Player player = mPlayer.get(entityId);
    if (player.data == null || player.data.getItems() == null) return null;
    Item weapon = player.data.getItems().getEquipped(BodyLoc.RARM);
    if (weapon == null) weapon = player.data.getItems().getEquipped(BodyLoc.LARM);
    if (weapon == null || weapon.type == null) return null;
    String name = weapon.type.is(Type.BOW) ? "arrow"
        : weapon.type.is(Type.XBOW) ? "bolt" : null;
    return name != null && Riiablo.files.Missiles.get(name) != null ? name : null;
  }

  static boolean isAmazonBowSkill(Skills.Entry skill) {
    if (skill == null || skill.skill == null) return false;
    switch (skill.skill.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "magic arrow":
      case "fire arrow":
      case "cold arrow":
      case "multiple shot":
      case "exploding arrow":
      case "ice arrow":
      case "guided arrow":
      case "strafe":
      case "immolation arrow":
      case "freezing arrow":
        return true;
      default:
        return false;
    }
  }

  static boolean requiresRangedAmmo(Skills.Entry skill, Item weapon) {
    if (!ItemData.isRangedWeapon(weapon) || skill == null || skill.noammo) return false;
    return skill.Id == SkillCodes.attack || skill.decquant || isAmazonBowSkill(skill);
  }

  static boolean hasQuantity(Item item) {
    if (item == null || item.attrs == null) return false;
    StatRef quantity = item.attrs.base().get(Stat.quantity, StatRef.obtain());
    return quantity != null && quantity.asInt() > 0;
  }

  static boolean consumeRangedAmmo(ItemData items, Item weapon) {
    if (items == null || weapon == null) return false;
    Item ammo = items.getEquippedAmmo(weapon);
    if (!hasQuantity(ammo)) return false;
    StatRef quantity = ammo.attrs.base().get(Stat.quantity);
    int before = quantity.asInt();
    quantity.sub(1);
    // Aggregate is normally rebuilt by ItemData, but quantity is not an
    // equipped combat modifier. Keep the item's display/save lists aligned
    // without rebuilding the character's derived attributes every shot.
    StatRef aggregate = ammo.attrs.aggregate().get(Stat.quantity);
    if (aggregate != null) aggregate.set(before - 1);
    log.info("[RANGED_AMMO] phase=consume weapon={} ammo={} itemId={} before={} after={}",
        weapon.code, ammo.code, ammo.id, before, before - 1);
    return true;
  }

  private void consumeRangedAmmoForSkill(SkillDoEvent event, Skills.Entry skill) {
    if (!mPlayer.has(event.entityId) || !requiresRangedAmmo(skill,
        mPlayer.get(event.entityId).data != null
            ? mPlayer.get(event.entityId).data.getItems().getEquippedRangedWeapon() : null)) {
      return;
    }
    ItemData items = mPlayer.get(event.entityId).data.getItems();
    Item weapon = items.getEquippedRangedWeapon();
    consumeRangedAmmo(items, weapon);
  }

  private void reject(SkillCastEvent event, int resultCode, String reason) {
    event.accepted = false;
    event.resultCode = resultCode;
    log.debug("Server skill rejected: entity={}, skill={}, resultCode={}, reason={}",
        event.entityId, event.skillId, resultCode, reason);
  }
}
