package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Type;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.skill.AmazonSkills;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

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

  public ServerSkillSystem() {
    this(false);
  }

  /** Local games use this mode because legacy player presentation owns its projectiles. */
  public ServerSkillSystem(boolean monstersOnly) {
    this.monstersOnly = monstersOnly;
  }

  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Missile> mMissile;
  private volatile int mercenaryMissileCount;
  private volatile int mercenarySkillDoCount;
  private volatile int mercenaryConfiguredMissiles;
  private volatile int mercenaryLastSrvDoFunc;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<SummonedPet> mSummonedPet;

  @com.artemis.annotations.Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  /** Registered as "factory" by D2GS. */
  @Wire(name = "factory")
  protected EntityFactory factory;

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
    if (event.targetId >= 0 && mPlayer.has(event.targetId)
        && !PvpCombatRules.canTarget(partyManager, event.entityId, event.targetId,
            true, true)) {
      reject(event, 8, "target player is not hostile");
      log.info("[PVP] phase=skill_reject source={} target={} skill={} reason=not_hostile",
          event.entityId, event.targetId, event.skillId);
      return;
    }
    if (event.targetId >= 0
        && (mMercenary.has(event.targetId) || mSummonedPet.has(event.targetId))) {
      reject(event, 8, "target is a friendly owned unit");
      log.info("[SKILL_CAST] phase=reject source={} target={} skill={} reason=friendly_pet",
          event.entityId, event.targetId, event.skillId);
      return;
    }
    if (event.targetId >= 0 && mMonster.has(event.targetId)
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
    // Local games retain their legacy player projectile presentation, but
    // native summons are server entities rather than visual projectiles and
    // must still be created in the local authoritative world.
    if (monstersOnly && !mMonster.has(event.entityId)
        && event.srvdofunc != 15 && event.srvdofunc != 16
        && skill.srvdofunc != 15 && skill.srvdofunc != 16) return;
    int skillLevel = getSkillLevel(event.entityId, event.skillId);

    Vector2 start = mPosition.get(event.entityId).position;
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
    if (event.srvdofunc == 22 || skill.srvdofunc == 22) {
      spawnNova(event, skill, start);
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
      UnitState state = states.stateList.addState(
          StateId.DIMVISION, duration, skillLevel, event.entityId);
      if (state != null) {
        state.skillId = event.skillId;
        state.defenseModifier = defenseReduction;
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

  private void spawnNova(SkillDoEvent event, Skills.Entry skill, Vector2 start) {
    String missileName = firstNonEmpty(skill.srvmissilea, skill.cltmissilea);
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
      if (createMissile(missile, direction, start, event.entityId,
          sharedHitTargets, skillLevel) >= 0) {
        created++;
      }
    }
    log.debug("Server nova projectiles: entity={}, skill={}, missile={}, created={}",
        event.entityId, event.skillId, missileName, created);
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
      if (createMissile(missile, direction, origin, event.entityId,
          hitTargets, skillLevel) >= 0) created++;
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
      if (!direction.isZero(0.0001f)
          && createMissile(missile, direction.nor(), from, event.entityId,
              segmentHits, skillLevel) >= 0) {
        created++;
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
    boolean sourcePlayer = mPlayer.has(sourceId) || mMercenary.has(sourceId)
        || mSummonedPet.has(sourceId);
    if (sourcePlayer) {
      if (mMonster.has(candidate) && !mMercenary.has(candidate)
          && !mSummonedPet.has(candidate)) return true;
      return mPlayer.has(candidate) && PvpCombatRules.canTarget(
          partyManager, sourceId, candidate, true, true);
    }
    return mPlayer.has(candidate);
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
    int total = SkillFormula.evaluate(skill.calc1, skill, skillLevel);
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
      if (createMissile(missile, direction, start, event.entityId, sharedHitTargets,
          skillLevel) >= 0) {
        created++;
      }
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
    int id = createMissile(missile, direction.nor(), start, event.entityId, null, skillLevel);
    if (id >= 0 && mMissile.has(id)) {
      Missile projectile = mMissile.get(id);
      projectile.targetId = targetId;
      projectile.homing = targetId >= 0 && mPosition.has(targetId);
      int bonus = SkillFormula.evaluate(skill.calc1, skill, skillLevel);
      if (bonus <= 0) bonus = AmazonSkills.calculateGuidedArrowDamageBonus(skillLevel);
      projectile.damageMultiplier = 1f + Math.max(0, bonus) / 100f;
      configurePierce(projectile, event.entityId, skillLevel, true);
      log.info("[GUIDED_ARROW] phase=create entity={} missileId={} target={} homing={} "
              + "level={} damageBonus={} pierceChance={}", event.entityId, id, targetId,
          projectile.homing, skillLevel, bonus, projectile.pierceChance);
    }
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
        configurePierce(mMissile.get(id), event.entityId, skillLevel, false);
        created = 1;
      }
    }
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

  private void reject(SkillCastEvent event, int resultCode, String reason) {
    event.accepted = false;
    event.resultCode = resultCode;
    log.debug("Server skill rejected: entity={}, skill={}, resultCode={}, reason={}",
        event.entityId, event.skillId, resultCode, reason);
  }
}
