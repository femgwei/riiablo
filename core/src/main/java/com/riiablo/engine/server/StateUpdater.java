package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.CharacterClass;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.DifficultyLevels;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.NativeTargeting;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.MeleeAttackEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.item.ItemDurabilityManager;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PvpCombatRules;
import com.riiablo.engine.server.skill.AssassinSkills;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.skill.DruidSkills;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.missile.MissileId;
import com.riiablo.codec.excel.Skills;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;

/**
 * 状态更新系统 - 基于 D2MOD 状态处理逻辑移植
 * 
 * <p>该系统每帧更新所有单位的状态，处理：
 * <ul>
 *   <li>状态持续时间倒计时</li>
 *   <li>过期状态移除</li>
 *   <li>持续伤害（DOT）效果</li>
 *   <li>状态效果应用（减速、眩晕等）</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Common/src/D2States.cpp
 * 
 * @author riiablo team
 */
@All(UnitStates.class)
public class StateUpdater extends IteratingSystem implements StatusEffectApplier.StateSink {
  private static final Logger log = LogManager.getLogger(StateUpdater.class);
  private static final int[] BARBARIAN_PASSIVE_SKILLS = {
      127, 128, 129, 134, 135, 136, 141, 145, 148, 153
  };
  private static final int[] BARBARIAN_PASSIVE_STATES = {
      StateId.SWORDMASTERY, StateId.AXEMASTERY, StateId.MACEMASTERY,
      StateId.POLEARMMASTERY, StateId.THROWINGMASTERY, StateId.SPEARMASTERY,
      StateId.INCREASEDSTAMINA, StateId.IRONSKIN,
      StateId.INCREASEDSPEED, StateId.NATURALRESISTANCE
  };
  private static final int[] PALADIN_HARD_POINT_PASSIVE_SKILLS = {
      SkillId.RESIST_FIRE, SkillId.RESIST_COLD, SkillId.RESIST_LIGHTNING,
      SkillId.BLESSED_AIM
  };
  private static final int[] PALADIN_HARD_POINT_PASSIVE_STATES = {
      StateId.PASSIVE_RESISTFIRE, StateId.PASSIVE_RESISTCOLD,
      StateId.PASSIVE_RESISTLTNG, StateId.PENETRATE
  };

  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;
  protected ComponentMapper<com.riiablo.engine.server.component.Missile> mMissile;

  @Wire(name = "partyManager", failOnNull = false)
  protected PartyManager partyManager;

  @Wire(name = "map", failOnNull = false)
  protected Map map;

  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;

  protected EventSystem events;

  @Override
  protected void initialize() {
    super.initialize();
    StatusEffectApplier.INSTANCE.setStateSink(this);
  }

  /**
   * Native pre-life-loss state callbacks. DamageEvent is dispatched
   * synchronously before the caller subtracts life, matching D2Game's unit
   * event phase; defensive absorption runs before reactive curse effects.
   */
  @Subscribe
  public void onDamageEvent(DamageEvent event) {
    if (event == null || event.victim < 0 || !mUnitStates.has(event.victim)) return;
    UnitStates victimUnitStates = mUnitStates.get(event.victim);
    // Network clients consume authoritative snapshots and must not apply the
    // same curse side effect a second time in their presentation world.
    if (victimUnitStates != null && victimUnitStates.snapshotOnly) return;
    StateList victimStates = victimUnitStates != null ? victimUnitStates.stateList : null;
    if (victimStates == null) return;

    applySorceressArmorReaction(event, victimStates);
    if (event.physicalDamage <= 0f || !mAttributesWrapper.has(event.victim)) return;

    applyNativeGolemHitEffects(event);
    applyBloodGolemDamageLink(event);

    absorbBoneArmor(event, victimStates);
    applyThorns(event, victimStates);

    if (event.attacker < 0 || event.attacker == event.victim
        || event.physicalDamage <= 0f || !event.isLeechableAttack()
        || !mAttributesWrapper.has(event.attacker)) return;

    Attributes victimAttributes = mAttributesWrapper.get(event.victim).attrs;
    StatRef victimLife = victimAttributes != null
        ? victimAttributes.get(Stat.hitpoints, StatRef.obtain()) : null;
    float availableLife = victimLife != null ? Math.max(0f, victimLife.asFixed()) : 0f;
    float physical = Math.min(Math.max(0f, event.physicalDamage), Math.max(0f, event.damage));
    physical = Math.min(physical, availableLife);
    if (physical <= 0f) return;

    applyBloodGolemLifeLink(event, physical);

    UnitState lifeTap = victimStates.getState(StateId.LIFETAP);
    if (lifeTap != null) applyLifeTap(event, lifeTap, physical);

    UnitState ironMaiden = victimStates.getState(StateId.IRONMAIDEN);
    if (ironMaiden != null && event.isMelee()) {
      applyIronMaiden(event, ironMaiden, physical);
    }
  }

  /** Native {@code UNITEVENT_ATTACKEDINMELEE}; unlike damage it includes misses and blocks. */
  @Subscribe
  public void onMeleeAttackEvent(MeleeAttackEvent event) {
    if (event == null || event.victim < 0 || event.attacker < 0
        || event.attacker == event.victim || !mUnitStates.has(event.victim)
        || !world.getEntityManager().isActive(event.attacker)
        || !isHostile(event.victim, event.attacker)) return;
    UnitStates victim = mUnitStates.get(event.victim);
    StateList states = victim != null ? victim.stateList : null;
    UnitState shiver = states != null ? states.getState(StateId.SHIVERARMOR) : null;
    if (shiver != null) applyShiverArmor(event.attacker, event.victim, shiver, states);
  }

  /** Dispatches the three native group-1 Sorceress armor unit events. */
  private void applySorceressArmorReaction(DamageEvent event, StateList victimStates) {
    if (event.attacker < 0 || event.attacker == event.victim
        || !world.getEntityManager().isActive(event.attacker)
        || !isHostile(event.victim, event.attacker)) return;
    UnitState frozen = victimStates.getState(StateId.FROZENARMOR);
    if (frozen != null && event.isMelee() && event.physicalDamage > 0f) {
      applyFrozenArmor(event, frozen);
      return;
    }
    UnitState chilling = victimStates.getState(StateId.CHILLINGARMOR);
    if (chilling != null && event.kind == DamageEvent.MISSILE && event.returnFire) {
      launchChillingArmorBolt(event, chilling, victimStates);
    }
  }

  /** D2Game EventFunc02: freeze a melee attacker after physical damage. */
  private void applyFrozenArmor(DamageEvent event, UnitState armor) {
    Skills.Entry skill = Riiablo.files.skills.get(armor.skillId >= 0
        ? armor.skillId : SkillId.FROZEN_ARMOR);
    int duration = SorceressSkills.getFrozenArmorFreezeLength(
        skill, armor.level, name -> baseSkillLevel(event.victim, name));
    duration = resolveArmorColdDuration(event.victim, event.attacker, duration);
    if (duration <= 0) return;
    StatusEffectApplier.INSTANCE.applyFreeze(event.attacker, duration, event.victim);
    log.info("[SORCERESS_FROZEN_ARMOR] phase=retaliate source={} attacker={} "
            + "skill={} level={} duration={}",
        event.victim, event.attacker, skill != null ? skill.Id : -1,
        armor.level, duration);
  }

  /** D2Game EventFunc03: apply the armor skill's cold packet to a melee attacker. */
  private void applyShiverArmor(
      int attackerId, int victimId, UnitState armor, StateList ownerStates) {
    if (!mAttributesWrapper.has(attackerId)) return;
    Skills.Entry skill = Riiablo.files.skills.get(armor.skillId >= 0
        ? armor.skillId : SkillId.SHIVER_ARMOR);
    if (skill == null) return;
    int[] range = SorceressSkills.getArmorColdDamage(
        skill, armor.level, name -> baseSkillLevel(victimId, name));
    NativeRng rng = new NativeRng(Riiablo.gameSeed
        ^ victimId * 0x45D9F3B ^ attackerId * 31 ^ armor.duration);
    int raw = range[0] + rng.nextInt(Math.max(1, range[1] - range[0] + 1));
    Attributes attacker = mAttributesWrapper.get(attackerId).attrs;
    StateList attackerStates = mUnitStates.has(attackerId)
        ? mUnitStates.get(attackerId).stateList : null;
    int pierce = armorColdPierce(victimId, ownerStates);
    CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateFixedElementalDamage(
        attacker, isPlayerAligned(attackerId), isPlayerAligned(victimId),
        CombatSystem.DAMAGE_COLD, raw, pierce, attackerStates, difficulty());
    applyElementalAbsorb(attacker, result.absorbedLife);
    float applied = 0f;
    StatRef life = attacker != null ? attacker.get(Stat.hitpoints, StatRef.obtain()) : null;
    float before = life != null ? life.asFixed() : 0f;
    if (life != null && before > 0f && result.totalDamage > 0) {
      DamageEvent reactive = DamageEvent.obtainReactive(
          victimId, attackerId, result.totalDamage, 0f);
      if (events != null) events.dispatch(reactive);
      applied = Math.max(0f, reactive.damage);
      life.sub(applied);
      if (life.asFixed() <= 0f) {
        life.set(0f);
        if (events != null) events.dispatch(DeathEvent.obtain(victimId, attackerId));
      }
    }
    int coldLength = SorceressSkills.getArmorColdLength(
        skill, armor.level, name -> baseSkillLevel(victimId, name));
    int duration = resolveArmorColdDuration(victimId, attackerId, coldLength);
    if (duration > 0 && isAlive(attackerId)) {
      StatusEffectApplier.INSTANCE.applyFreeze(attackerId, duration, victimId);
    }
    log.info("[SORCERESS_SHIVER_ARMOR] phase=retaliate source={} attacker={} skill={} "
            + "level={} raw={} applied={} hp={} -> {} freeze={}",
        victimId, attackerId, skill.Id, armor.level, raw, applied,
        before, life != null ? life.asFixed() : before, duration);
  }

  /** D2Game EventFunc01: fire Chilling Armor's authoritative return missile. */
  private void launchChillingArmorBolt(
      DamageEvent event, UnitState armor, StateList ownerStates) {
    if (factory == null || !mPosition.has(event.victim) || !mPosition.has(event.attacker)) return;
    Skills.Entry skill = Riiablo.files.skills.get(armor.skillId >= 0
        ? armor.skillId : SkillId.CHILLING_ARMOR);
    String missileName = skill != null ? skill.srvmissilea : null;
    Missiles.Entry row = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    if (skill == null || row == null) {
      log.warn("[SORCERESS_CHILLING_ARMOR] phase=retaliate_reject source={} attacker={} "
              + "missile={} reason=missing_data",
          event.victim, event.attacker, missileName);
      return;
    }
    Vector2 origin = mPosition.get(event.victim).position;
    Vector2 direction = new Vector2(mPosition.get(event.attacker).position).sub(origin);
    if (direction.isZero(0.0001f)) direction.set(Vector2.X);
    int missileId = factory.createMissile(row, direction.nor(), origin, event.victim);
    if (missileId < 0 || !mMissile.has(missileId)) return;
    com.riiablo.engine.server.component.Missile projectile = mMissile.get(missileId);
    projectile.targetId = event.attacker;
    MissileDamageResolver.initializeSkill(
        projectile, skill,
        mAttributesWrapper.has(event.victim)
            ? mAttributesWrapper.get(event.victim).attrs : null,
        Math.max(1, armor.level), name -> baseSkillLevel(event.victim, name), ownerStates);
    log.info("[SORCERESS_CHILLING_ARMOR] phase=retaliate source={} attacker={} skill={} "
            + "level={} missile={} missileId={}",
        event.victim, event.attacker, skill.Id, armor.level, row.Missile, missileId);
  }

  private int resolveArmorColdDuration(int sourceId, int targetId, int duration) {
    if (duration <= 0 || !mAttributesWrapper.has(targetId)) return 0;
    StateList sourceStates = mUnitStates.has(sourceId)
        ? mUnitStates.get(sourceId).stateList : null;
    StateList targetStates = mUnitStates.has(targetId)
        ? mUnitStates.get(targetId).stateList : null;
    return CombatSystem.INSTANCE.resolveColdDuration(
        mAttributesWrapper.get(targetId).attrs, isPlayerAligned(targetId), duration,
        armorColdPierce(sourceId, sourceStates), targetStates, difficulty());
  }

  private int armorColdPierce(int entityId, StateList states) {
    int pierce = mAttributesWrapper.has(entityId)
        ? statInt(mAttributesWrapper.get(entityId).attrs, Stat.item_pierce_cold)
            + statInt(mAttributesWrapper.get(entityId).attrs, Stat.passive_cold_pierce)
        : 0;
    if (states != null) {
      pierce += states.getTotalStatContribution(Stat.passive_cold_pierce);
    }
    return Math.max(0, pierce);
  }

  private int baseSkillLevel(int entityId, String name) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return 0;
    Skills.Entry skill = Riiablo.files.skills.get(name);
    return skill != null
        ? Math.max(0, mPlayer.get(entityId).data.getBaseSkillLevel(skill.Id)) : 0;
  }

  /** Death removes Conversion allegiance without applying the living-unit HP restore callback. */
  @Subscribe
  public void onDeath(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)) return;
    Monster monster = mMonster.get(event.victim);
    if (!monster.converted) return;
    monster.converted = false;
    monster.conversionOwnerId = -1;
    if (mUnitStates.has(event.victim) && mUnitStates.get(event.victim).stateList != null) {
      StateList states = mUnitStates.get(event.victim).stateList;
      states.removeState(StateId.CONVERSION);
      states.removeState(StateId.CONVERSION_SAVE);
    }
    log.info("[PALADIN_CONVERSION] phase=clear entity={} reason=death", event.victim);
  }

  /** D2Game EventFunc27: Clay Golem's item_slow is a 750-frame SLOWED layer. */
  private void applyNativeGolemHitEffects(DamageEvent event) {
    if (event.attacker < 0 || !event.isMelee() || !mSummonedPet.has(event.victim)
        || !mMonster.has(event.victim) || !mUnitStates.has(event.attacker)
        || (!mPlayer.has(event.attacker) && !mMonster.has(event.attacker))) return;
    SummonedPet pet = mSummonedPet.get(event.victim);
    if (pet == null || pet.skillId != com.riiablo.engine.server.skill.SkillId.CLAY_GOLEM) return;
    Attributes golem = mAttributesWrapper.has(event.victim)
        ? mAttributesWrapper.get(event.victim).attrs : null;
    int slow = golem != null ? statInt(golem, Stat.item_slow) : 0;
    if (slow <= 0) {
      Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
          ? Riiablo.files.skills.get(pet.skillId) : null;
      slow = skill != null ? SkillFormula.evaluate(skill.aurastatcalc[0], skill,
          Math.max(1, pet.skillLevel)) : 0;
    }
    if (slow <= 0) return;
    slow = Math.min(slow, mPlayer.has(event.attacker) ? 50 : 90);
    Monster target = mMonster.get(event.attacker);
    if (target != null && (target.rank == MonsterRank.CHAMPION
        || target.rank == MonsterRank.UNIQUE)) slow = Math.min(slow, 50);
    StateList states = mUnitStates.get(event.attacker).stateList;
    if (states == null) return;
    UnitState slowed = states.addStateLayer(StateId.SLOWED, 750, 1,
        event.victim, com.riiablo.engine.server.skill.SkillId.CLAY_GOLEM);
    if (slowed != null) {
      slowed.setStatContribution(Stat.velocitypercent, 0,
          com.riiablo.attributes.NativeStatResolver.Operation.ADD, -slow);
      slowed.setStatContribution(Stat.attackrate, 0,
          com.riiablo.attributes.NativeStatResolver.Operation.ADD, -slow);
      slowed.setStatContribution(Stat.other_animrate, 0,
          com.riiablo.attributes.NativeStatResolver.Operation.ADD, -slow);
      slowed.needsSync = true;
      log.info("[NECRO_CLAY_GOLEM] phase=slow source={} target={} slow={} duration={}",
          event.victim, event.attacker, slow, 750);
    }
  }

  private static int statInt(Attributes attrs, short stat) {
    StatRef ref = attrs != null ? attrs.get(stat, StatRef.obtain()) : null;
    return ref != null ? ref.asInt() : 0;
  }

  /**
   * D2Game EventFunc26: before incoming melee/missile damage is consumed, a
   * Blood Golem copies its owner's current life minus Param5's absorbed share.
   * Param5 is zero in 1.10f, but the life-copy side effect still occurs.
   */
  private void applyBloodGolemDamageLink(DamageEvent event) {
    if ((event.kind != DamageEvent.MELEE && event.kind != DamageEvent.MISSILE)
        || event.damage <= 0f || !mSummonedPet.has(event.victim)
        || !mAttributesWrapper.has(event.victim)) return;
    SummonedPet pet = mSummonedPet.get(event.victim);
    if (pet == null || pet.skillId != com.riiablo.engine.server.skill.SkillId.BLOOD_GOLEM
        || !mAttributesWrapper.has(pet.ownerId)) return;
    Attributes owner = mAttributesWrapper.get(pet.ownerId).attrs;
    Attributes golem = mAttributesWrapper.get(event.victim).attrs;
    StatRef ownerLife = owner != null ? owner.get(Stat.hitpoints, StatRef.obtain()) : null;
    StatRef golemLife = golem != null ? golem.get(Stat.hitpoints, StatRef.obtain()) : null;
    if (ownerLife == null || golemLife == null || ownerLife.asFixed() < 1f) return;

    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(pet.skillId) : null;
    int share = skill != null && skill.Param != null && skill.Param.length > 4
        ? Math.max(0, skill.Param[4]) : 0;
    float reduced = Math.max(0f, event.damage) * share / 100f;
    float linkedLife = Math.max(1f, ownerLife.asFixed() - reduced);
    golemLife.set(linkedLife);
    event.damage = Math.max(0f, event.damage - reduced);
    log.info("[NECRO_BLOOD_GOLEM] phase=damage_link pet={} owner={} kind={} share={} "
            + "absorbed={} linkedHp={} remaining={}",
        event.victim, pet.ownerId, event.kind, share, reduced, linkedLife, event.damage);
  }

  /** D2Game EventFunc23: distribute Blood Golem melee leech to owner and pet. */
  private void applyBloodGolemLifeLink(DamageEvent event, float physicalDamage) {
    if (!event.isMelee() || !mSummonedPet.has(event.attacker)) return;
    SummonedPet pet = mSummonedPet.get(event.attacker);
    if (pet == null || pet.skillId != com.riiablo.engine.server.skill.SkillId.BLOOD_GOLEM
        || !mAttributesWrapper.has(event.attacker)) return;
    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(pet.skillId) : null;
    int percent = NecromancerSkills.nativeDiminishingPercent(skill, pet.skillLevel, 0, 1);
    if (percent <= 0) return;
    int drain = bloodGolemDrainPercent(event.victim);
    if (drain <= 0) return;
    float leechBase = physicalDamage * drain / 100f * percent / 100f;
    if (leechBase <= 0f) return;

    float ownerShare = skill != null && skill.Param != null && skill.Param.length > 2
        ? leechBase * Math.max(0, skill.Param[2]) / 100f : 0f;
    float healedOwner = heal(pet.ownerId, ownerShare);
    float remaining = Math.max(0f, leechBase - healedOwner);
    float healedPet = heal(event.attacker, remaining);
    healedOwner += heal(pet.ownerId, Math.max(0f, remaining - healedPet));
    log.info("[NECRO_BLOOD_GOLEM] phase=life_link pet={} owner={} target={} physical={} "
            + "drain={} percent={} petHeal={} ownerHeal={}",
        event.attacker, pet.ownerId, event.victim, physicalDamage, drain, percent,
        healedPet, healedOwner);
  }

  private int bloodGolemDrainPercent(int victimId) {
    if (mPlayer.has(victimId)) return 100;
    Monster monster = mMonster.has(victimId) ? mMonster.get(victimId) : null;
    if (monster == null || monster.monstats == null || monster.monstats.Drain == null) return 0;
    int difficulty = difficulty();
    return difficulty < monster.monstats.Drain.length
        ? Math.max(0, monster.monstats.Drain[difficulty]) : 0;
  }

  /** Native thorns_percent EventFunc: aura, item and Iron Golem lists share one path. */
  private void applyThorns(DamageEvent event, StateList victimStates) {
    if (!event.isMelee() || event.attacker < 0 || event.attacker == event.victim
        || !mAttributesWrapper.has(event.attacker)
        || !mAttributesWrapper.has(event.victim)) return;
    Attributes victim = mAttributesWrapper.get(event.victim).attrs;
    int percent = victim != null ? statInt(victim, Stat.thorns_percent) : 0;
    if (victimStates != null) {
      percent += victimStates.getTotalStatContribution(Stat.thorns_percent);
    }
    // D2Game reduces the percentage before multiplying when the melee
    // attacker is a player or hireling. Keep the native rounding order.
    if (mPlayer.has(event.attacker) || mMercenary.has(event.attacker)) {
      percent = (percent + 4) / 8;
    }
    int raw = (int) Math.floor(Math.min(Math.max(0f, event.physicalDamage),
        Math.max(0f, event.damage)) * Math.max(0, percent) / 100f);
    if (raw <= 0) return;
    Attributes attacker = mAttributesWrapper.get(event.attacker).attrs;
    StateList attackerStates = mUnitStates.has(event.attacker)
        ? mUnitStates.get(event.attacker).stateList : null;
    CombatSystem.CombatResult reflected = CombatSystem.INSTANCE.calculateFixedPhysicalDamage(
        attacker, isPlayerAligned(event.attacker), true, raw, attackerStates);
    if (reflected.totalDamage <= 0) return;
    DamageEvent reactive = DamageEvent.obtainReactive(
        event.victim, event.attacker, reflected.totalDamage, reflected.physicalDamage);
    if (events != null) events.dispatch(reactive);
    StatRef life = attacker.get(Stat.hitpoints, StatRef.obtain());
    if (life == null || life.asFixed() <= 0f) return;
    float before = life.asFixed();
    life.sub(Math.max(0f, reactive.damage));
    if (life.asFixed() <= 0f) {
      life.set(0f);
      if (events != null) events.dispatch(DeathEvent.obtain(event.victim, event.attacker));
    }
    log.info("[THORNS] phase=reflect defender={} attacker={} percent={} raw={} "
            + "reflected={} hp={} -> {}",
        event.victim, event.attacker, percent, raw, reactive.damage, before, life.asFixed());
  }

  private float heal(int entityId, float requested) {
    if (entityId < 0 || requested <= 0f || !mAttributesWrapper.has(entityId)) return 0f;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef life = attrs != null ? attrs.get(Stat.hitpoints, StatRef.obtain()) : null;
    StatRef maximum = attrs != null ? attrs.get(Stat.maxhp, StatRef.obtain()) : null;
    if (life == null || maximum == null || life.asFixed() <= 0f) return 0f;
    float healed = Math.min(requested, Math.max(0f, maximum.asFixed() - life.asFixed()));
    if (healed > 0f) life.add(healed);
    return healed;
  }

  /** Native SrvDo066 periodic damage granted to Fire Golem through SumSkill1. */
  private void processHolyFireAura(int entityId, StateList states) {
    UnitState aura = states.getState(StateId.HOLYFIRE);
    if (aura == null || aura.skillId < 0 || !mSummonedPet.has(entityId)
        || !mPosition.has(entityId) || !isAlive(entityId)) return;
    if (aura.periodicCountdownFrames > 0) {
      aura.periodicCountdownFrames--;
      if (aura.periodicCountdownFrames > 0) return;
    }
    log.info("[DRUID_STORM] phase=tick source={} skill={} state={} delay={} position=({}, {})",
        entityId, aura.skillId, StateId.getName(aura.stateId), aura.periodicDelayFrames,
        mPosition.get(entityId).position.x, mPosition.get(entityId).position.y);
    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(aura.skillId) : null;
    if (skill == null) return;
    aura.periodicCountdownFrames = Math.max(1, aura.periodicDelayFrames);
    if (map != null) {
      Map.Zone zone = map.getZone(mPosition.get(entityId).position.x,
          mPosition.get(entityId).position.y);
      if (zone != null && zone.isTown()) return;
    }
    int range = Math.max(0, SkillFormula.evaluate(skill.aurarangecalc, skill, aura.level));
    int[] damageRange = NecromancerSkills.nativeElementalDamageRange(skill, aura.level);
    if (range <= 0 || damageRange[1] <= 0) return;
    NativeRng rng = new NativeRng(Riiablo.gameSeed ^ entityId * 0x45D9F3B
        ^ ++aura.runtimeValue * 0x9E3779B9);
    IntBag targets = world.getAspectSubscriptionManager()
        .get(Aspect.all(AttributesWrapper.class, Position.class)).getEntities();
    Vector2 origin = mPosition.get(entityId).position;
    int hits = 0;
    for (int i = 0; i < targets.size(); i++) {
      int targetId = targets.get(i);
      if (targetId == entityId || !isAlive(targetId) || !isHostile(entityId, targetId)
          || origin.dst2(mPosition.get(targetId).position) > range * range) continue;
      Attributes target = mAttributesWrapper.get(targetId).attrs;
      int raw = damageRange[0] + rng.nextInt(Math.max(1, damageRange[1] - damageRange[0] + 1));
      StateList targetStates = mUnitStates.has(targetId)
          ? mUnitStates.get(targetId).stateList : null;
      CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateFixedElementalDamage(
          target, isPlayerAligned(targetId), true, CombatSystem.DAMAGE_FIRE,
          raw, 0, targetStates, difficulty());
      applyElementalAbsorb(target, combat.absorbedLife);
      if (combat.totalDamage <= 0) continue;
      DamageEvent event = DamageEvent.obtain(entityId, targetId, combat.totalDamage);
      if (events != null) events.dispatch(event);
      StatRef hp = target.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null) continue;
      hp.sub(Math.max(0f, event.damage));
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        if (events != null) events.dispatch(DeathEvent.obtain(entityId, targetId));
      }
      hits++;
    }
    log.info("[NECRO_FIRE_GOLEM] phase=holy_fire pet={} skill={} level={} range={} "
            + "damage={}..{} hits={}",
        entityId, skill.skill, aura.level, range, damageRange[0], damageRange[1], hits);
  }

  /**
   * Native SKILLS_SrvDo029_ThunderStorm periodic strike.  D2MOO runs the
   * selector from the periodic-skill phase, creates one temporary
   * {@code thunderstorm} missile at the chosen unit and immediately invokes
   * SrvDmgHitHandler.  We retain the missile as a one-tick authoritative
   * carrier so the normal elemental resistance, mastery, absorb, PvP and
   * death paths remain shared with every other lightning skill.
   */
  private void processThunderStorm(int entityId, StateList states) {
    UnitState aura = states.getState(StateId.THUNDERSTORM);
    if (aura == null || aura.skillId < 0 || factory == null
        || !mPosition.has(entityId) || !isAlive(entityId)) {
      if (aura != null && aura.skillId >= 0) {
        log.debug("[DRUID_STORM] phase=skip source={} skill={} reason=missing_runtime_binding "
            + "factory={} position={} alive={}", entityId, aura.skillId, factory != null,
            mPosition.has(entityId), isAlive(entityId));
      }
      return;
    }
    if (aura.periodicCountdownFrames > 0) {
      aura.periodicCountdownFrames--;
      if (aura.periodicCountdownFrames > 0) return;
    }
    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(aura.skillId) : null;
    if (skill == null) {
      aura.expired = true;
      return;
    }
    int delay = Math.max(1, aura.periodicDelayFrames);
    aura.periodicCountdownFrames = delay;
    Map.Zone sourceZone = map != null ? map.getZone(mPosition.get(entityId).position) : null;
    if (sourceZone != null && sourceZone.isTown()) return;
    int range = Math.max(0, SkillFormula.evaluate(skill.aurarangecalc, skill, aura.level,
        name -> baseSkillLevel(entityId, name)));
    // 1.10f leaves AuraRangeCalc blank for Thunder Storm; the native target
    // selector receives Param6 as its bounded search radius/type argument.
    if (range <= 0 && skill.Param != null && skill.Param.length > 5) {
      range = Math.max(1, skill.Param[5]);
    }
    if (range <= 0) return;
    int targetId = selectThunderStormTarget(entityId, aura, range, skill.aurafilter, sourceZone);
    if (targetId < 0 || !mPosition.has(targetId)) {
      aura.thunderStormTargetId = -1;
      return;
    }
    String missileName = skill.srvmissilea != null && !skill.srvmissilea.isEmpty()
        ? skill.srvmissilea : "thunderstorm";
    Missiles.Entry row = Riiablo.files.Missiles.get(missileName);
    if (row == null) {
      log.warn("[SORCERESS_THUNDER_STORM] phase=strike_reject source={} target={} "
              + "reason=missing_missile missile={}", entityId, targetId, missileName);
      return;
    }
    Vector2 target = mPosition.get(targetId).position;
    int missileId = factory.createMissile(row, Vector2.X, target, entityId);
    if (missileId < 0 || !mMissile.has(missileId)) return;
    Missile strike = mMissile.get(missileId);
    strike.thunderStormStrike = true;
    strike.targetId = targetId;
    strike.skillId = skill.Id;
    strike.damageLevel = Math.max(1, aura.level);
    // Publish the one-shot strike for at least one network snapshot. A
    // one-frame lifetime is consumed by MissileCollisionSystem in the same
    // fixed tick in which the periodic aura creates it.
    strike.nativeLifetimeFrames = 2;
    strike.range = 0f;
    if (mVelocity.has(missileId)) mVelocity.get(missileId).velocity.setZero();
    Attributes owner = mAttributesWrapper.has(entityId)
        ? mAttributesWrapper.get(entityId).attrs : null;
    MissileDamageResolver.initializeSkill(strike, skill, owner, strike.damageLevel,
        name -> baseSkillLevel(entityId, name), states);
    aura.thunderStormTargetId = targetId;
    aura.needsSync = true;
    log.info("[SORCERESS_THUNDER_STORM] phase=strike source={} target={} missileId={} "
            + "skill={} level={} range={} delay={} damageSnapshot={}",
        entityId, targetId, missileId, skill.Id, strike.damageLevel, range, delay,
        strike.damageSnapshot);
  }

  private int selectThunderStormTarget(int sourceId, UnitState aura, int range,
      int filter, Map.Zone sourceZone) {
    int previous = aura.thunderStormTargetId;
    float range2 = range * (float) range;
    IntBag candidates = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    int best = -1;
    float bestDistance = Float.MAX_VALUE;
    int fallback = -1;
    float fallbackDistance = Float.MAX_VALUE;
    Vector2 origin = mPosition.get(sourceId).position;
    for (int i = 0; i < candidates.size(); i++) {
      int targetId = candidates.get(i);
      boolean hostile = isHostile(sourceId, targetId)
          || (mPlayer.has(sourceId) && mMonster.has(targetId));
      if (targetId == sourceId || !isAlive(targetId) || !hostile
          || !mPlayer.has(targetId) && !mMonster.has(targetId)) continue;
      if (mNativeUnitFlags.has(targetId)
          && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) continue;
      if (mMonster.has(targetId)) {
        Monster target = mMonster.get(targetId);
        if (target.monstats != null && target.monstats.npc) continue;
      }
      if (map != null) {
        Map.Zone zone = map.getZone(mPosition.get(targetId).position);
        if (zone != null && zone.isTown()) continue;
        if (sourceZone != null && zone != null && zone != sourceZone) continue;
      }
      float distance = origin.dst2(mPosition.get(targetId).position);
      if (distance > range2) continue;
      if (distance < fallbackDistance || (distance == fallbackDistance && targetId < fallback)) {
        fallback = targetId;
        fallbackDistance = distance;
      }
      if (targetId == previous) continue;
      if (distance < bestDistance || (distance == bestDistance && targetId < best)) {
        best = targetId;
        bestDistance = distance;
      }
    }
    return best >= 0 ? best : fallback;
  }

  /** Consumes Bone Armor after physical resistance but before life is removed. */
  private void absorbBoneArmor(DamageEvent event, StateList victimStates) {
    UnitState armor = victimStates.getState(StateId.BONEARMOR);
    if (armor == null) return;
    int remaining = armor.runtimeValue > 0
        ? armor.runtimeValue : armor.getStatContributionValue(Stat.bonearmor);
    if (remaining <= 0) {
      victimStates.removeState(StateId.BONEARMOR);
      return;
    }
    float absorbable = Math.min(Math.max(0f, event.physicalDamage),
        Math.max(0f, event.damage));
    float absorbed = Math.min(remaining, absorbable);
    if (absorbed <= 0f) return;
    int consumed = Math.min(remaining, Math.max(1, (int) Math.ceil(absorbed)));
    int next = Math.max(0, remaining - consumed);
    event.physicalDamage = Math.max(0f, event.physicalDamage - absorbed);
    event.damage = Math.max(0f, event.damage - absorbed);
    if (next == 0) {
      victimStates.removeState(StateId.BONEARMOR);
    } else {
      armor.runtimeValue = next;
      armor.setStatContribution(
          Stat.bonearmor, 0, NativeStatResolver.Operation.ADD, next);
      armor.needsSync = true;
    }
    log.info("[NECRO_BONE_ARMOR] phase=absorb victim={} attacker={} absorbed={} "
            + "remaining={} physicalAfter={} totalAfter={}",
        event.victim, event.attacker, absorbed, next,
        event.physicalDamage, event.damage);
  }

  private void applyLifeTap(DamageEvent event, UnitState state, float physicalDamage) {
    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(state.skillId) : null;
    int percent = NecromancerSkills.reactiveCursePercent(skill, state.level);
    if (percent <= 0) return;
    Attributes attacker = mAttributesWrapper.get(event.attacker).attrs;
    StatRef life = attacker != null ? attacker.get(Stat.hitpoints, StatRef.obtain()) : null;
    StatRef maximum = attacker != null ? attacker.get(Stat.maxhp, StatRef.obtain()) : null;
    if (life == null || maximum == null || life.asFixed() <= 0f) return;
    float before = life.asFixed();
    float restored = Math.max(0f, Math.min(
        physicalDamage * percent / 100f, maximum.asFixed() - before));
    if (restored <= 0f) return;
    life.add(restored);
    log.info("[NECRO_LIFE_TAP] source={} victim={} skill={} level={} physical={} percent={} "
            + "restored={} hp={} -> {}",
        event.attacker, event.victim, state.skillId, state.level, physicalDamage,
        percent, restored, before, life.asFixed());
  }

  private void applyIronMaiden(DamageEvent event, UnitState state, float physicalDamage) {
    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(state.skillId) : null;
    boolean playerOrHireling = mPlayer.has(event.attacker) || mMercenary.has(event.attacker);
    int percent = NecromancerSkills.ironMaidenPercent(
        skill, state.level, playerOrHireling);
    int raw = (int) Math.floor(physicalDamage * percent / 100f);
    if (raw <= 0) return;

    Attributes attacker = mAttributesWrapper.get(event.attacker).attrs;
    StateList attackerStates = mUnitStates.has(event.attacker)
        ? mUnitStates.get(event.attacker).stateList : null;
    CombatSystem.CombatResult reflected = CombatSystem.INSTANCE.calculateFixedPhysicalDamage(
        attacker, mPlayer.has(event.attacker), mPlayer.has(event.victim), raw, attackerStates);
    if (reflected.totalDamage <= 0) return;
    DamageEvent reflectedEvent = DamageEvent.obtainReactive(
        event.victim, event.attacker, reflected.totalDamage, reflected.physicalDamage);
    if (events != null) events.dispatch(reflectedEvent);
    float applied = Math.max(0f, reflectedEvent.damage);
    StatRef life = attacker != null ? attacker.get(Stat.hitpoints, StatRef.obtain()) : null;
    if (life == null || life.asFixed() <= 0f || applied <= 0f) return;
    float before = life.asFixed();
    life.sub(applied);
    if (life.asFixed() <= 0f) {
      life.set(0f);
      if (events != null) events.dispatch(DeathEvent.obtain(event.victim, event.attacker));
    }
    log.info("[NECRO_IRON_MAIDEN] source={} defender={} skill={} level={} physical={} "
            + "percent={} raw={} reflected={} hp={} -> {}",
        event.attacker, event.victim, state.skillId, state.level, physicalDamage,
        percent, raw, applied, before, life.asFixed());
  }

  //==========================================================================
  // 系统处理
  //==========================================================================

  @Override
  protected void process(int entityId) {
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates == null || unitStates.stateList == null) {
      return;
    }

    // Network clients render the server snapshot. They must not independently
    // tick DOT, expire states, or emit DeathEvent and rewards a second time.
    if (unitStates.snapshotOnly) {
      if (mVelocity.has(entityId)) applyVelocityModifiers(entityId, unitStates.stateList);
      return;
    }

    StateList stateList = unitStates.stateList;

    synchronizeBarbarianPassives(entityId, stateList);
    synchronizePaladinHardPointPassives(entityId, stateList);
    synchronizeSorceressPassives(entityId, stateList);

    processHolyFireAura(entityId, stateList);
    processThunderStorm(entityId, stateList);
    processDruidStorm(entityId, stateList);
    processBladeShield(entityId, stateList);
    processBlazeTrail(entityId, stateList);
    processSpiderLayTrail(entityId, stateList);
    
    // Resolve this tick before decrementing duration. A one-frame state must
    // still deal its final DOT tick, then expire.
    processDamageOverTime(entityId, stateList);
    expireOrphanedConversion(entityId, stateList);
    boolean conversionExpiring = stateList.getState(StateId.CONVERSION) != null
        && stateList.getState(StateId.CONVERSION).duration == 1;
    stateList.update();
    if (conversionExpiring && !stateList.hasState(StateId.CONVERSION)) {
      restoreConversion(entityId, stateList);
    }
    int removedDruidStates = DruidSkills.removeInvalidFeralMaulStates(stateList);
    if (removedDruidStates > 0) {
      log.info("[DRUID_FERAL_MAUL] phase=shape_dependency_cleanup entity={} removed={}",
          entityId, removedDruidStates);
    }

    applyMaximumResourceModifiers(entityId, unitStates, stateList);

    // Apply movement/control effects only while the state remains active.
    if (mVelocity.has(entityId)) {
      applyVelocityModifiers(entityId, stateList);
    }
  }

  //==========================================================================
  // 状态效果应用
  //==========================================================================

  /**
   * Folds native percentage max-resource stats into the authoritative
   * aggregate without compounding them every tick. The unmodified permanent/
   * equipment aggregate is retained explicitly, and all active state
   * percentages are combined into one final encoded-value phase.
   */
  private void applyMaximumResourceModifiers(
      int entityId, UnitStates unitStates, StateList stateList) {
    if (!mAttributesWrapper.has(entityId)) return;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return;
    int previousMaxLifePercent = unitStates.appliedMaxLifePercent;
    int previousMaxManaPercent = unitStates.appliedMaxManaPercent;
    int previousMaxStaminaPercent = unitStates.appliedMaxStaminaPercent;
    boolean aggregateRebuilt = unitStates.observedAggregateRevision != attrs.aggregateRevision();
    int maxLifePercent = stateList.getTotalMaxLifeModifier();
    int currentMaxLife = encoded(attrs, Stat.maxhp);
    unitStates.baseMaxLifeEncoded = resolveUnmodifiedBase(currentMaxLife,
        unitStates.resolvedMaxLifeEncoded, unitStates.baseMaxLifeEncoded, aggregateRebuilt);
    unitStates.resolvedMaxLifeEncoded = applyMaximumResourceModifier(attrs,
        Stat.hitpoints, Stat.maxhp, unitStates.baseMaxLifeEncoded, maxLifePercent);
    unitStates.appliedMaxLifePercent = maxLifePercent;
    int maxManaPercent = stateList.getTotalMaxManaModifier();
    int currentMaxMana = encoded(attrs, Stat.maxmana);
    unitStates.baseMaxManaEncoded = resolveUnmodifiedBase(currentMaxMana,
        unitStates.resolvedMaxManaEncoded, unitStates.baseMaxManaEncoded, aggregateRebuilt);
    unitStates.resolvedMaxManaEncoded = applyMaximumResourceModifier(attrs,
        Stat.mana, Stat.maxmana, unitStates.baseMaxManaEncoded, maxManaPercent);
    unitStates.appliedMaxManaPercent = maxManaPercent;
    int maxStaminaPercent = stateList.getTotalMaxStaminaModifier();
    int currentMaxStamina = encoded(attrs, Stat.maxstamina);
    unitStates.baseMaxStaminaEncoded = resolveUnmodifiedBase(currentMaxStamina,
        unitStates.resolvedMaxStaminaEncoded, unitStates.baseMaxStaminaEncoded, aggregateRebuilt);
    unitStates.resolvedMaxStaminaEncoded = applyMaximumResourceModifier(attrs,
        Stat.stamina, Stat.maxstamina, unitStates.baseMaxStaminaEncoded, maxStaminaPercent);
    unitStates.appliedMaxStaminaPercent = maxStaminaPercent;
    unitStates.observedAggregateRevision = attrs.aggregateRevision();
    if (previousMaxLifePercent != maxLifePercent
        || previousMaxManaPercent != maxManaPercent
        || previousMaxStaminaPercent != maxStaminaPercent) {
      log.info("[BARBARIAN_BATTLE_ORDERS] phase=resource_refresh entity={} "
              + "lifePercent={}=>{} manaPercent={}=>{} staminaPercent={}=>{} "
              + "maxHp={} maxMana={} maxStamina={}",
          entityId, previousMaxLifePercent, maxLifePercent,
          previousMaxManaPercent, maxManaPercent,
          previousMaxStaminaPercent, maxStaminaPercent,
          attrs.aggregate().getValue(Stat.maxhp, 0f),
          attrs.aggregate().getValue(Stat.maxmana, 0f),
          attrs.aggregate().getValue(Stat.maxstamina, 0f));
    }
  }

  /** Native SrvDo124 periodic Armageddon/Hurricane missile emission. */
  private void processDruidStorm(int entityId, StateList states) {
    UnitState aura = states.getState(StateId.HURRICANE);
    if (aura == null) aura = states.getState(StateId.ARMAGEDDON);
    if (aura == null) return;
    if (aura.skillId < 0 || factory == null || !mPosition.has(entityId) || !isAlive(entityId)) {
      log.info("[DRUID_STORM] phase=skip source={} skill={} state={} reason=missing_runtime_binding "
              + "factory={} position={} alive={} countdown={} delay={}",
          entityId, aura.skillId, StateId.getName(aura.stateId), factory != null,
          mPosition.has(entityId), isAlive(entityId), aura.periodicCountdownFrames,
          aura.periodicDelayFrames);
      return;
    }
    if (aura.periodicCountdownFrames > 0) {
      aura.periodicCountdownFrames--;
      if (aura.periodicCountdownFrames > 0) return;
    }
    Skills.Entry skill = Riiablo.files != null && Riiablo.files.skills != null
        ? Riiablo.files.skills.get(aura.skillId) : null;
    if (skill == null) {
      log.info("[DRUID_STORM] phase=skip source={} skill={} reason=missing_skill_entry",
          entityId, aura.skillId);
      return;
    }
    aura.periodicCountdownFrames = Math.max(1, aura.periodicDelayFrames);
    Map.Zone zone = map != null ? map.getZone(mPosition.get(entityId).position) : null;
    if (zone != null && zone.isTown()) {
      log.debug("[DRUID_STORM] phase=skip source={} skill={} reason=town", entityId, skill.Id);
      return;
    }
    String missileName = firstStormMissile(skill);
    Missiles.Entry row = missileName != null ? Riiablo.files.Missiles.get(missileName) : null;
    // A few 1.10f TXT exports leave SrvMissile/CltMissile blank for the
    // SrvDo124 storm rows. D2MOO resolves the native MissileIds directly in
    // that case; retain the same data-driven fallback instead of silently
    // dropping the periodic strike.
    if (row == null) {
      int missileId = skill.Id == SkillId.ARMAGEDDON
          ? MissileId.ARMAGEDDON : skill.Id == SkillId.HURRICANE
          ? MissileId.HURRICANE : -1;
      if (missileId >= 0) row = missileByNativeId(missileId);
      if (row != null) missileName = row.Missile;
    }
    if (row == null) {
      log.warn("[DRUID_STORM] phase=skip source={} skill={} reason=missing_missile name={} ",
          entityId, skill.Id, missileName);
      return;
    }
    int range = Math.max(1, SkillFormula.evaluate(skill.aurarangecalc, skill, aura.level));
    int targetId = findStormTarget(entityId, range);
    Vector2 origin = mPosition.get(entityId).position;
    if (targetId >= 0 && mPosition.has(targetId)) origin = mPosition.get(targetId).position;
    int id = factory.createMissile(row, Vector2.X, origin, entityId);
    if (id < 0 || !mMissile.has(id)) {
      log.info("[DRUID_STORM] phase=skip source={} skill={} reason=create_missile_failed "
              + "row={} id={} hasMissile={}",
          entityId, skill.Id, row.Missile, id, id >= 0 && mMissile.has(id));
      return;
    }
    Missile strike = mMissile.get(id);
    strike.skillId = skill.Id;
    strike.damageLevel = Math.max(1, aura.level);
    // Keep the carrier alive through the creation tick and one snapshot
    // boundary. With a one-frame lifetime MissileCollisionSystem removes a
    // newly-created storm visual before NetworkSynchronizer can publish it.
    strike.nativeLifetimeFrames = 2;
    strike.range = 0f;
    if (mVelocity.has(id)) mVelocity.get(id).velocity.setZero();
    Attributes owner = mAttributesWrapper.has(entityId)
        ? mAttributesWrapper.get(entityId).attrs : null;
    MissileDamageResolver.initializeSkill(strike, skill, owner, strike.damageLevel,
        name -> baseSkillLevel(entityId, name), states);
    aura.needsSync = true;
    log.info("[DRUID_STORM] phase=strike source={} skill={} target={} missileId={} "
            + "state={} level={} range={} delay={} snapshot={}",
        entityId, skill.Id, targetId, id, StateId.getName(aura.stateId), aura.level,
        range, aura.periodicDelayFrames, strike.damageSnapshot);
  }

  private static String firstStormMissile(Skills.Entry skill) {
    if (skill == null) return null;
    if (skill.srvmissilea != null && !skill.srvmissilea.isEmpty()) return skill.srvmissilea;
    if (skill.srvmissile != null && !skill.srvmissile.isEmpty()) return skill.srvmissile;
    return skill.cltmissilea;
  }

  private static Missiles.Entry missileByNativeId(int id) {
    if (Riiablo.files == null || Riiablo.files.Missiles == null) return null;
    for (Missiles.Entry candidate : Riiablo.files.Missiles) {
      if (candidate != null && candidate.Id == id) return candidate;
    }
    return null;
  }

  private int findStormTarget(int sourceId, int range) {
    float range2 = range * (float) range;
    Vector2 source = mPosition.get(sourceId).position;
    int best = -1;
    float bestDistance = Float.MAX_VALUE;
    IntBag candidates = world.getAspectSubscriptionManager()
        .get(Aspect.all(AttributesWrapper.class, Position.class)).getEntities();
    for (int i = 0; i < candidates.size(); i++) {
      int id = candidates.get(i);
      if (id == sourceId || !isAlive(id) || !isHostile(sourceId, id)) continue;
      float distance = source.dst2(mPosition.get(id).position);
      if (distance > range2 || distance >= bestDistance) continue;
      best = id;
      bestDistance = distance;
    }
    return best;
  }

  private void expireOrphanedConversion(int entityId, StateList states) {
    UnitState conversion = states.getState(StateId.CONVERSION);
    if (conversion == null) return;
    int owner = conversion.sourceEntityId;
    boolean invalid = owner < 0 || !mPlayer.has(owner) || !isAlive(owner);
    if (!invalid && map != null && mPosition.has(owner) && mPosition.has(entityId)) {
      Map.Zone ownerZone = map.getZone(mPosition.get(owner).position);
      Map.Zone targetZone = map.getZone(mPosition.get(entityId).position);
      invalid = ownerZone != targetZone;
    }
    if (invalid && conversion.duration != 1) {
      conversion.duration = 1;
      conversion.needsSync = true;
      log.info("[PALADIN_CONVERSION] phase=expire entity={} owner={} reason=owner_or_zone",
          entityId, owner);
    }
  }

  /** Restores the native Conversion save-list payload when its aura expires. */
  private void restoreConversion(int entityId, StateList states) {
    if (!mMonster.has(entityId)) {
      states.removeState(StateId.CONVERSION_SAVE);
      return;
    }
    Monster monster = mMonster.get(entityId);
    UnitState save = states.getState(StateId.CONVERSION_SAVE);
    if (save != null && mAttributesWrapper.has(entityId)) {
      Attributes attrs = mAttributesWrapper.get(entityId).attrs;
      if (attrs != null) {
        StatRef level = attrs.get(Stat.level, StatRef.obtain());
        StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
        StatRef maxHp = attrs.get(Stat.maxhp, StatRef.obtain());
        if (level != null && hp != null && maxHp != null && save.conversionOriginalLevel > 0
            && save.conversionOriginalMaxHpEncoded > 0) {
          float ratio = Math.max(0f, Math.min(1f, hp.asFixed() / Math.max(1f, maxHp.asFixed())));
          float originalMax = save.conversionOriginalMaxHpEncoded / 256f;
          level.set(save.conversionOriginalLevel);
          maxHp.set(originalMax);
          hp.set(Math.max(1f, Math.min(originalMax, originalMax * ratio)));
        }
      }
    }
    monster.converted = false;
    monster.conversionOwnerId = -1;
    states.removeState(StateId.CONVERSION_SAVE);
    log.info("[PALADIN_CONVERSION] phase=restore entity={} status=PASS", entityId);
  }

  /** Keeps D2Common passive stat lists aligned with the authoritative skill table. */
  private void synchronizeBarbarianPassives(int entityId, StateList states) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null) return;
    if (mPlayer.get(entityId).data.classId != CharacterClass.BARBARIAN) return;
    int skillBonus = states.getTotalSkillModifier();
    for (int i = 0; i < BARBARIAN_PASSIVE_SKILLS.length; i++) {
      int skillId = BARBARIAN_PASSIVE_SKILLS[i];
      int stateId = BARBARIAN_PASSIVE_STATES[i];
      Skills.Entry skill = Riiablo.files.skills.get(skillId);
      int ownedLevel = skill != null
          ? Math.max(0, mPlayer.get(entityId).data.getSkill(skillId)) : 0;
      // Native SKILLS_GetHighestLevelSkillFromSkillId first requires an
      // owned skill instance. +allskills can raise that instance, but cannot
      // create an unlearned passive by itself.
      int level = ownedLevel > 0 ? ownedLevel + skillBonus : 0;
      UnitState current = states.getState(stateId);
      if (level <= 0 || skill == null || !skill.passive) {
        if (current != null) {
          states.removeState(stateId);
          log.info("[BARBARIAN_PASSIVE] phase=remove entity={} skill={} state={}",
              entityId, skillId, StateId.getName(stateId));
        }
        continue;
      }
      if (current != null && current.level == level && !current.expired) continue;
      UnitState applied = BarbarianSkills.applyPassiveState(states, skill, level, entityId);
      if (applied != null) {
        log.info("[BARBARIAN_PASSIVE] phase=refresh entity={} skill={} level={} state={} "
                + "stamina={} defense={} velocity={} resists={}/{}/{}/{} "
                + "itype={} masteryAr={} masteryDamage={} masteryCrit={} throwing={}",
            entityId, skill.skill, level, StateId.getName(applied.stateId),
            applied.maxStaminaModifier, applied.defenseModifier, applied.velocityModifier,
            applied.fireResistModifier, applied.coldResistModifier,
            applied.lightResistModifier, applied.poisonResistModifier,
            applied.masteryItemType, applied.masteryAttackRatingModifier,
            applied.masteryDamageModifier, applied.masteryCriticalChance,
            applied.throwingMastery);
      }
    }
  }

  /** Keeps native aura hard-point passive lists independent of the selected aura. */
  private void synchronizePaladinHardPointPassives(int entityId, StateList states) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null
        || mPlayer.get(entityId).data.classId != CharacterClass.PALADIN) return;
    for (int i = 0; i < PALADIN_HARD_POINT_PASSIVE_SKILLS.length; i++) {
      int skillId = PALADIN_HARD_POINT_PASSIVE_SKILLS[i];
      int stateId = PALADIN_HARD_POINT_PASSIVE_STATES[i];
      Skills.Entry skill = Riiablo.files.skills.get(skillId);
      int hardLevel = skill == null ? 0
          : Math.max(0, mPlayer.get(entityId).data.getBaseSkillLevel(skillId));
      UnitState current = states.getState(stateId);
      if (hardLevel <= 0 || PaladinSkills.getHardPointPassiveStateId(skill) == StateId.NONE) {
        if (current != null) {
          states.removeState(stateId);
          log.info("[PALADIN_HARD_POINT_PASSIVE] phase=remove entity={} skill={} state={}",
              entityId, skillId, StateId.getName(stateId));
        }
        continue;
      }
      if (current != null && current.level == hardLevel && !current.expired) continue;
      UnitState applied = PaladinSkills.applyHardPointPassiveState(
          states, skill, hardLevel, entityId);
      if (applied != null) {
        log.info("[PALADIN_HARD_POINT_PASSIVE] phase=refresh entity={} skill={} level={} "
                + "state={} maxFire={} maxCold={} maxLightning={} attackRating={}",
            entityId, skill.skill, hardLevel, StateId.getName(applied.stateId),
            applied.getStatContributionValue(Stat.maxfireresist),
            applied.getStatContributionValue(Stat.maxcoldresist),
            applied.getStatContributionValue(Stat.maxlightresist),
            applied.getStatContributionValue(Stat.item_tohit_percent));
      }
    }
  }

  /** Keeps Fire Mastery's native permanent passive stat list current. */
  private void synchronizeSorceressPassives(int entityId, StateList states) {
    if (!mPlayer.has(entityId) || mPlayer.get(entityId).data == null
        || mPlayer.get(entityId).data.classId != CharacterClass.SORCERESS) return;
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.FIRE_MASTERY);
    int ownedLevel = skill != null
        ? Math.max(0, mPlayer.get(entityId).data.getSkill(SkillId.FIRE_MASTERY)) : 0;
    int level = ownedLevel > 0 ? ownedLevel + states.getTotalSkillModifier() : 0;
    UnitState current = states.getState(StateId.FIREMASTERY);
    if (level <= 0 || skill == null || !skill.passive) {
      if (current != null) {
        states.removeState(StateId.FIREMASTERY);
        log.info("[SORCERESS_FIRE_MASTERY] phase=remove entity={}", entityId);
      }
      return;
    }
    if (current != null && current.level == level && !current.expired) return;
    UnitState applied = SorceressSkills.applyFireMasteryState(
        states, skill, level, entityId);
    if (applied != null) {
      log.info("[SORCERESS_FIRE_MASTERY] phase=refresh entity={} level={} percent={}",
          entityId, level,
          applied.getStatContributionValue(Stat.passive_fire_mastery));
    }
  }

  static int resolveUnmodifiedBase(
      int currentEncoded, int previousResolvedEncoded, int previousBaseEncoded,
      boolean aggregateRebuilt) {
    if (aggregateRebuilt || previousBaseEncoded == Integer.MIN_VALUE
        || currentEncoded != previousResolvedEncoded) {
      return currentEncoded;
    }
    return previousBaseEncoded;
  }

  static int applyMaximumResourceModifier(
      Attributes attrs, short currentStat, short maximumStat,
      int unmodifiedEncoded, int nextPercent) {
    StatRef maximum = attrs.get(maximumStat, StatRef.obtain());
    if (maximum == null || unmodifiedEncoded == Integer.MIN_VALUE) return Integer.MIN_VALUE;
    int resolved = Math.max(0,
        NativeStatResolver.applyPercentEncoded(unmodifiedEncoded, Math.max(-99, nextPercent)));
    attrs.aggregate().putEncoded(maximumStat, resolved);
    StatRef current = attrs.get(currentStat, StatRef.obtain());
    if (current != null && current.encodedValues() > resolved) {
      attrs.aggregate().putEncoded(currentStat, resolved);
    }
    return resolved;
  }

  private static int encoded(Attributes attrs, short stat) {
    StatRef value = attrs.get(stat, StatRef.obtain());
    return value == null ? Integer.MIN_VALUE : value.encodedValues();
  }

  /**
   * 应用移动速度修正
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void applyVelocityModifiers(int entityId, StateList stateList) {
    Velocity velocity = mVelocity.get(entityId);

    // Keep the desired velocity untouched. VelocityAdder applies this state
    // multiplier after Pathfinder has selected the direction for this tick.
    velocity.stateSpeedMultiplier = 1f;
    velocity.stateMovementLocked = false;
    
    // 检查冰冻状态 - 完全停止移动
    if (stateList.hasState(StateId.FREEZE)) {
      velocity.stateMovementLocked = true;
      return;
    }
    
    // 检查眩晕状态 - 完全停止移动
    if (stateList.hasState(StateId.STUNNED)) {
      velocity.stateMovementLocked = true;
      return;
    }
    
    // 计算减速效果
    int slowPercent = 0;

    // Frenzy velocitypercent has already been evaluated from AuraStatCalc at
    // its native runtime stack. It is not a fixed percentage per hit.
    int frenzyPercent = 0;
    UnitState frenzy = stateList.getState(StateId.FRENZY);
    if (frenzy == null) frenzy = stateList.getState(StateId.MONFRENZY);
    if (frenzy != null) {
      frenzyPercent = Math.min(200, Math.max(0, frenzy.velocityModifier));
    }
    
    // 减速状态
    if (stateList.hasState(StateId.SLOWED)) {
      UnitState slowState = stateList.getState(StateId.SLOWED);
      // Native EventFunc27 carries velocitypercent in the state stat-list.
      // Retain the old scalar fallback only for legacy SLOWED producers.
      if (!slowState.hasStatContribution(Stat.velocitypercent)) {
        slowPercent += 25 + slowState.level * 5;
      }
    }
    
    // 衰老诅咒
    if (stateList.hasState(StateId.DECREPIFY)) {
      slowPercent += 50;
    }
    
    // 应用减速（限制最大减速为90%）
    if (slowPercent > 0) {
      slowPercent = Math.min(slowPercent, 90);
      velocity.stateSpeedMultiplier = 1.0f - (slowPercent / 100.0f);
    }
    if (frenzyPercent > 0 && !velocity.stateMovementLocked) {
      velocity.stateSpeedMultiplier *= 1.0f + frenzyPercent / 100.0f;
    }
    int auraVelocityPercent = stateList.getTotalVelocityModifier();
    if (auraVelocityPercent != 0 && !velocity.stateMovementLocked) {
      // Aura velocity modifiers are percentages; unlike Frenzy they are not
      // stack counts and are therefore applied independently.
      auraVelocityPercent = Math.max(-90, Math.min(200, auraVelocityPercent));
      velocity.stateSpeedMultiplier *= 1.0f + auraVelocityPercent / 100.0f;
    }
  }

  /**
   * 处理持续伤害效果
   * 
   * @param entityId 实体ID
   * @param stateList 状态列表
   */
  private void processDamageOverTime(int entityId, StateList stateList) {
    // 处理中毒
    if (stateList.hasState(StateId.POISON)) {
      UnitState poisonState = stateList.getState(StateId.POISON);
      if (poisonState.exactDamagePerFrame > 0f || poisonState.damagePerFrame > 0) {
        applyDamageOverTime(entityId, poisonState.sourceEntityId,
            poisonState.exactDamagePerFrame > 0f
                ? poisonState.exactDamagePerFrame : poisonState.damagePerFrame,
            stateList, StateId.POISON);
      }
    }
    
    // 处理燃烧
    if (stateList.hasState(StateId.BURNING)) {
      UnitState burningState = stateList.getState(StateId.BURNING);
      if (burningState.exactDamagePerFrame > 0f || burningState.damagePerFrame > 0) {
        applyDamageOverTime(entityId, burningState.sourceEntityId,
            burningState.exactDamagePerFrame > 0f
                ? burningState.exactDamagePerFrame : burningState.damagePerFrame,
            stateList, StateId.BURNING);
      }
    }
    
    // 处理撕开伤口
    if (stateList.hasState(StateId.OPENWOUNDS)) {
      UnitState woundsState = stateList.getState(StateId.OPENWOUNDS);
      // 撕开伤口伤害基于角色等级
      int damage = woundsState.level * 2;
      if (damage > 0) {
        applyDamageOverTime(entityId, woundsState.sourceEntityId,
            damage, stateList, StateId.OPENWOUNDS);
      }
    }
  }

  /**
   * Rebuilds movement modifiers immediately after an aura stat-list changes.
   * Native {@code SKILLS_AuraCallback_BasicAura} invokes
   * {@code UNITS_UpdateAnimRateAndVelocity} in the same game frame; the normal
   * fixed-tick decay pass remains the owner of all other state updates.
   */
  public void refreshAuraVelocity(int entityId) {
    if (!mVelocity.has(entityId) || !mUnitStates.has(entityId)) return;
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates == null || unitStates.stateList == null) return;
    applyVelocityModifiers(entityId, unitStates.stateList);
  }

  /** D2MOO EVENTTYPE_PERIODICSKILLS -> SrvDo054 -> SrvDo142. */
  private void processBladeShield(int entityId, StateList states) {
    UnitState state = states.getState(StateId.BLADESHIELD);
    if (state == null || state.periodicCountdownFrames < 0) return;
    if (state.periodicCountdownFrames > 0) {
      state.periodicCountdownFrames--;
      if (state.periodicCountdownFrames > 0) return;
    }
    Skills.Entry skill = Riiablo.files.skills.get(state.skillId);
    if (skill == null || skill.srvdofunc != 54 || !isAlive(entityId)
        || !stillOwnsSkill(entityId, state.skillId)) {
      state.expired = true;
      log.info("[ASSASSIN_BLADE_SHIELD] phase=periodic_stop entity={} skill={} reason=invalid_owner",
          entityId, state.skillId);
      return;
    }
    state.periodicCountdownFrames = Math.max(5, state.periodicDelayFrames);
    Map.Zone zone = map != null && mPosition.has(entityId)
        ? map.getZone(mPosition.get(entityId).position) : null;
    if (zone != null && zone.isTown()) {
      log.debug("[ASSASSIN_BLADE_SHIELD] phase=pulse_skip entity={} reason=town", entityId);
      return;
    }
    int level = Math.max(1, state.level);
    int range = AssassinSkills.bladeShieldRange(skill, level);
    int[] skillDamage = AssassinSkills.bladeShieldDamageRange(skill, level);
    if (range <= 0 || !mPosition.has(entityId) || !mAttributesWrapper.has(entityId)) return;
    Attributes source = mAttributesWrapper.get(entityId).attrs;
    if (source == null) return;
    Vector2 origin = mPosition.get(entityId).position;
    float range2 = range * (float) range;
    int affected = 0;
    IntBag candidates = world.getAspectSubscriptionManager()
        .get(Aspect.all(Position.class, AttributesWrapper.class)).getEntities();
    for (int i = 0; i < candidates.size(); i++) {
      int targetId = candidates.get(i);
      if (targetId == entityId || !isAlive(targetId)
          || origin.dst2(mPosition.get(targetId).position) > range2
          || !isHostile(entityId, targetId)
          || mNativeUnitFlags.has(targetId)
              && !NativeTargeting.isValidCombatTarget(mNativeUnitFlags.get(targetId))) continue;
      Attributes target = mAttributesWrapper.get(targetId).attrs;
      StateList targetStates = null;
      if (mUnitStates.has(targetId)) {
        UnitStates targetUnitStates = mUnitStates.get(targetId);
        if (targetUnitStates.stateList == null) targetUnitStates.init(targetId);
        targetStates = targetUnitStates.stateList;
      }
      int toHitPercent = skill.ToHit + Math.max(0, level - 1) * skill.LevToHit;
      boolean alwaysHit = (skill.ResultFlags & 1) != 0;
      CombatSystem.CombatResult combat = CombatSystem.INSTANCE.calculateBladeShieldAttack(
          source, target, isPlayerAligned(entityId), isPlayerAligned(targetId),
          skillDamage[0], skillDamage[1], skill.SrcDam, toHitPercent, alwaysHit,
          states, targetStates, isMoving(targetId));
      if (!combat.hit || combat.blocked) continue;
      StatRef hp = target.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null || hp.asFixed() <= 0f) continue;
      float requested = Math.max(0f, combat.totalDamage);
      if (requested > 0f || combat.absorbedLife > 0) {
        DamageEvent damage = DamageEvent.obtain(entityId, targetId, requested);
        if (events != null) events.dispatch(damage);
        applyElementalAbsorb(target, combat.absorbedLife);
        hp.sub(Math.max(0f, damage.damage));
        if (hp.asFixed() < 0f) hp.set(0f);
      }
      if (combat.poisonDuration > 0
          && combat.elementalDamage[CombatSystem.DAMAGE_POISON] > 0
          && mUnitStates.has(targetId)) {
        applyState(targetId, StateId.POISON, combat.poisonDuration, 1, entityId,
            combat.elementalDamage[CombatSystem.DAMAGE_POISON], CombatSystem.DAMAGE_POISON);
      }
      if (combat.coldDuration > 0
          && combat.elementalDamage[CombatSystem.DAMAGE_COLD] > 0
          && mUnitStates.has(targetId)) {
        UnitState cold = targetStates.addState(
            StateId.COLD, combat.coldDuration, 1, entityId);
        if (cold != null) cold.needsSync = true;
      }
      drainBladeShieldDurability(entityId, targetId);
      affected++;
      if (hp.asFixed() <= 0f && events != null) {
        hp.set(0f);
        events.dispatch(DeathEvent.obtain(entityId, targetId));
      }
    }
    log.info("[ASSASSIN_BLADE_SHIELD] phase=pulse entity={} skill={} level={} range={} "
            + "damage={}..{} srcDam={} affected={}",
        entityId, skill.Id, level, range, skillDamage[0], skillDamage[1],
        skill.SrcDam, affected);
  }

  private boolean stillOwnsSkill(int entityId, int skillId) {
    if (!mPlayer.has(entityId)) return true;
    Player player = mPlayer.get(entityId);
    return player.data != null && player.data.getSkill(skillId) > 0;
  }

  private boolean isAlive(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return false;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return false;
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null && hp.asFixed() > 0f;
  }

  private static float applyElementalAbsorb(Attributes target, int absorbedLife) {
    if (target == null || absorbedLife <= 0) return 0f;
    StatRef hp = target.get(Stat.hitpoints, StatRef.obtain());
    StatRef max = target.get(Stat.maxhp, StatRef.obtain());
    if (hp == null || max == null) return 0f;
    float before = hp.asFixed();
    float healed = Math.max(0f, Math.min((float) absorbedLife, max.asFixed() - before));
    if (healed > 0f) hp.add(healed);
    return healed;
  }

  private boolean isMoving(int entityId) {
    return mVelocity.has(entityId) && !mVelocity.get(entityId).velocity.isZero(0.0001f);
  }

  private boolean isPlayerAligned(int entityId) {
    return mPlayer.has(entityId) || mMercenary.has(entityId) || mSummonedPet.has(entityId);
  }

  private boolean isHostile(int sourceId, int targetId) {
    boolean sourceConverted = mMonster.has(sourceId) && mMonster.get(sourceId).converted;
    boolean targetConverted = mMonster.has(targetId) && mMonster.get(targetId).converted;
    if (sourceConverted) return mMonster.has(targetId) && !targetConverted;
    if (targetConverted) return mMonster.has(sourceId);
    if (mMonster.has(targetId) && !mMercenary.has(targetId)
        && !mSummonedPet.has(targetId)) return isPlayerAligned(sourceId);
    return PvpCombatRules.canDamage(partyManager,
        isPlayerAligned(sourceId) ? playerAlignmentOwner(sourceId) : sourceId,
        isPlayerAligned(targetId) ? playerAlignmentOwner(targetId) : targetId,
        isPlayerAligned(sourceId), isPlayerAligned(targetId));
  }

  private int playerAlignmentOwner(int entityId) {
    if (mMercenary.has(entityId)) return mMercenary.get(entityId).ownerId;
    if (mSummonedPet.has(entityId)) return mSummonedPet.get(entityId).ownerId;
    return entityId;
  }

  private void drainBladeShieldDurability(int sourceId, int targetId) {
    if (mPlayer.has(sourceId) && mPlayer.get(sourceId).data != null) {
      Item weapon = mPlayer.get(sourceId).data.getItems().getEquipped(BodyLoc.RARM);
      if (weapon == null) {
        weapon = mPlayer.get(sourceId).data.getItems().getEquipped(BodyLoc.LARM);
      }
      ItemDurabilityManager.INSTANCE.drainWeaponDurability(weapon, true);
    }
    if (mPlayer.has(targetId) && mPlayer.get(targetId).data != null) {
      ItemDurabilityManager.INSTANCE.drainArmorDurability(
          mPlayer.get(targetId).data.getItems());
    }
  }

  /** Applies one server tick of DOT and emits the normal damage/death events. */
  private void applyDamageOverTime(int entityId, int sourceEntityId, float damage,
      StateList stateList, int stateId) {
    if (damage <= 0 || !mAttributesWrapper.has(entityId)) return;
    if (isPlayerAligned(sourceEntityId) && isPlayerAligned(entityId)
        && !PvpCombatRules.canDamage(partyManager,
            playerAlignmentOwner(sourceEntityId), playerAlignmentOwner(entityId), true, true)) {
      // Hostility may be removed while poison/open-wounds is active.  Native
      // friendly checks must still prevent later DOT ticks from bypassing the
      // current authoritative relation.
      log.info("[PVP] phase=dot_reject source={} target={} state={} reason=not_hostile",
          sourceEntityId, entityId, StateId.getName(stateId));
      return;
    }
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return;
    StatRef hitpoints = attrs.get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints == null || hitpoints.asFixed() <= 0f) return;

    DamageEvent event = DamageEvent.obtain(sourceEntityId, entityId, damage);
    if (events != null) events.dispatch(event);
    float appliedDamage = Math.max(0f, event.damage);
    hitpoints.sub(appliedDamage);
    float hpAfter = hitpoints.asFixed();
    if (hpAfter <= 0f) {
      hitpoints.set(0f);
      log.debug("Entity {} died from state {} (damage={})", entityId,
          StateId.getName(stateId), appliedDamage);
      if (events != null) events.dispatch(DeathEvent.obtain(sourceEntityId, entityId));
      // Death subscribers apply the States.txt stay-death policy. Do not wipe
      // the list here: hitpoints==0 already prevents duplicate DOT events and
      // clearAll would incorrectly remove plr/mon/bossstaydeath entries.
    } else {
      log.trace("Entity {} takes {} damage from state {} (hp={})", entityId,
          appliedDamage, StateId.getName(stateId), hpAfter);
    }
  }

  /** Emits the native SpiderLay movement trail into the authoritative missile pipeline. */
  private void processSpiderLayTrail(int entityId, StateList stateList) {
    if (!stateList.hasState(StateId.SPIDERLAY) || factory == null
        || !mVelocity.has(entityId) || !mPosition.has(entityId)) return;
    Velocity velocity = mVelocity.get(entityId);
    if (velocity.velocity.isZero(0.0001f)) return;
    UnitState state = stateList.getState(StateId.SPIDERLAY);
    int elapsed = Math.max(0, state.initialDuration - state.duration);
    if ((elapsed & 3) != 0) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get("spidergoolay");
    if (missile == null) {
      log.warn("[SPIDER_LAY] phase=reject entity={} reason=missing_spidergoolay", entityId);
      state.expired = true;
      return;
    }
    Vector2 direction = new Vector2(velocity.velocity).nor();
    Vector2 position = new Vector2(mPosition.get(entityId).position).mulAdd(direction, -0.5f);
    int missileId = factory.createMissile(missile, direction, position, entityId);
    log.info("[SPIDER_LAY] phase=missile entity={} skill={} missileId={} position=({}, {})",
        entityId, state.skillId, missileId, position.x, position.y);
  }

  /** Emits one native Blaze ground missile whenever the owner actually moves. */
  private void processBlazeTrail(int entityId, StateList stateList) {
    UnitState state = stateList.getState(StateId.BLAZE);
    if (state == null || factory == null || !mVelocity.has(entityId)
        || !mPosition.has(entityId)) return;
    Velocity velocity = mVelocity.get(entityId);
    if (velocity.velocity.isZero(0.0001f)) return;
    Vector2 position = mPosition.get(entityId).position;
    if (state.trailPositionSet
        && Math.abs(position.x - state.trailX) < 0.0001f
        && Math.abs(position.y - state.trailY) < 0.0001f) return;
    state.trailX = position.x;
    state.trailY = position.y;
    state.trailPositionSet = true;

    Map currentMap = map;
    if (currentMap != null) {
      Map.Zone zone = currentMap.getZone(position);
      if (zone != null && zone.isTown()) return;
    }
    Skills.Entry skill = Riiablo.files.skills.get(state.skillId);
    String missileName = skill != null && skill.srvmissilea != null
        && !skill.srvmissilea.isEmpty() ? skill.srvmissilea : "blaze";
    Missiles.Entry row = Riiablo.files.Missiles.get(missileName);
    if (skill == null || row == null) {
      log.warn("[SORCERESS_BLAZE] phase=trail_reject source={} skill={} missile={}",
          entityId, state.skillId, missileName);
      state.expired = true;
      return;
    }

    int missileId = factory.createMissile(row, Vector2.X, position, entityId);
    if (missileId < 0 || !world.getEntityManager().isActive(missileId)) return;
    com.artemis.ComponentMapper<com.riiablo.engine.server.component.Missile> missiles =
        world.getMapper(com.riiablo.engine.server.component.Missile.class);
    if (!missiles.has(missileId)) return;
    com.riiablo.engine.server.component.Missile projectile = missiles.get(missileId);
    projectile.persistent = true;
    projectile.remainingFrames = nativeMissileRange(row, state.level);
    projectile.tickInterval = 1;
    projectile.range = 0f;
    if (mVelocity.has(missileId)) mVelocity.get(missileId).velocity.setZero();
    Attributes owner = mAttributesWrapper.has(entityId)
        ? mAttributesWrapper.get(entityId).attrs : null;
    MissileDamageResolver.initializeSorceressFireArea(
        projectile, skill, owner, mPlayer.has(entityId), state.level,
        name -> {
          Skills.Entry synergy = Riiablo.files.skills.get(name);
          return synergy != null && mPlayer.has(entityId)
              && mPlayer.get(entityId).data != null
              ? mPlayer.get(entityId).data.getBaseSkillLevel(synergy.Id) : 0;
        }, stateList);
    log.info("[SORCERESS_BLAZE] phase=trail source={} skill={} level={} missileId={} "
            + "position=({}, {}) lifetime={} rawFixed={}..{}",
        entityId, state.skillId, state.level, missileId, position.x, position.y,
        projectile.remainingFrames, projectile.elementalMinRateFixed,
        projectile.elementalMaxRateFixed);
  }

  private static int nativeMissileRange(Missiles.Entry row, int level) {
    if (row == null) return 0;
    long frames = (long) row.Range + (long) Math.max(1, level) * row.LevRange;
    return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, frames));
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 向实体添加状态（便捷方法）
   * 
   * @param entityId 目标实体ID
   * @param stateId 状态ID
   * @param duration 持续时间（帧数）
   * @param level 状态等级
   * @param sourceId 来源实体ID
   */
  public void addState(int entityId, int stateId, int duration, int level, int sourceId) {
    if (!mUnitStates.has(entityId)) {
      log.warn("实体 {} 没有 UnitStates 组件", entityId);
      return;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) {
      unitStates.init(entityId);
    }
    
    unitStates.stateList.addState(stateId, duration, level, sourceId);
  }

  /** Native Cleansing callback: shorten remaining poison/curable curse time. */
  public void applyCleansingReduction(int entityId, int percent,
      int sourceEntityId, int skillId) {
    if (percent <= 0 || percent >= 100 || !mUnitStates.has(entityId)) return;
    StateList states = mUnitStates.get(entityId).stateList;
    if (states == null) return;
    int changed = 0;
    for (UnitState state : states.getStates()) {
      if (state.duration <= 0 || state.stateId == StateId.CLEANSING
          || state.stateId == StateId.MEDITATION || state.stateId == StateId.REDEMPTION) continue;
      boolean poison = state.stateId == StateId.POISON;
      States.Entry definition = Riiablo.files != null && Riiablo.files.States != null
          ? Riiablo.files.States.get(state.stateId) : null;
      boolean curse = definition != null ? definition.curse : StateId.isCurse(state.stateId);
      // Native Cleansing only shortens curses carrying the States.txt
      // curable bit.  Keep the legacy StateId fallback for headless tests
      // which do not load the table.
      if (definition != null && curse && !definition.curable) continue;
      if (!poison && !curse) continue;
      int remaining = Math.max(1, state.duration * percent / 100);
      if (remaining < state.duration) {
        state.duration = remaining;
        state.initialDuration = Math.min(state.initialDuration, remaining);
        state.needsSync = true;
        changed++;
      }
    }
    if (changed > 0) {
      log.info("[CLEANSING] phase=shorten entity={} source={} skill={} percent={} states={}",
          entityId, sourceEntityId, skillId, percent, changed);
    }
  }

  @Override
  public void applyState(int entityId, int stateId, int duration, int level,
      int sourceId, int damagePerFrame, int damageType) {
    applyStateExact(entityId, stateId, duration, level, sourceId,
        damagePerFrame, damageType);
  }

  @Override
  public void applyStateExact(int entityId, int stateId, int duration, int level,
      int sourceId, float damagePerFrame, int damageType) {
    if (!mUnitStates.has(entityId)) {
      log.warn("Entity {} has no UnitStates component; state {} ignored", entityId, stateId);
      return;
    }
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) unitStates.init(entityId);
    StateList states = unitStates.stateList;
    if ((stateId == StateId.POISON || stateId == StateId.BURNING)
        && damagePerFrame > 0f) {
      states.applyDamageOverTimeState(stateId, Math.max(1, duration), level,
          sourceId, damagePerFrame, damageType);
      return;
    }
    if (stateId == StateId.COLD) {
      applyNativeColdState(entityId, states, duration, level, sourceId);
      return;
    }
    if (stateId == StateId.FREEZE) {
      applyNativeFreezeState(entityId, states, duration, level, sourceId);
      return;
    }
    // All remaining curse states use source/skill-owned native stat-list
    // layers.  This prevents a second caster from overwriting the first and
    // lets States.txt group/strength arbitration run in one place.
    if (StateId.isCurse(stateId)) {
      states.applyCurseState(
          Riiablo.files != null ? Riiablo.files.States : null,
          stateId, Math.max(1, duration), Math.max(1, level), sourceId, -1,
          Math.max(1, level));
      return;
    }
    UnitState state = states.addState(stateId, duration, level, sourceId);
    if (state == null) return;
    if (damagePerFrame > state.damagePerFrame) state.damagePerFrame = (int) damagePerFrame;
    state.exactDamagePerFrame = Math.max(state.exactDamagePerFrame, damagePerFrame);
    state.damageType = damageType;
    state.needsSync = true;
  }

  private UnitState applyNativeColdState(int entityId, StateList states,
      int duration, int level, int sourceId) {
    if (duration <= 0) return null;
    int difficulty = difficulty();
    int coldEffect = -50;
    if (mMonster.has(entityId)) {
      Monster monster = mMonster.get(entityId);
      if (monster.monstats != null && monster.monstats.coldeffect != null
          && difficulty < monster.monstats.coldeffect.length) {
        coldEffect = monster.monstats.coldeffect[difficulty];
      }
      if (coldEffect == 0) return null;
      if (coldEffect < 0) duration /= coldDivisor(difficulty);
    }
    duration = Math.max(1, duration);
    boolean created = states.getState(StateId.COLD) == null;
    UnitState cold = states.extendState(StateId.COLD, duration, level, sourceId);
    if (cold != null && created) {
      cold.setStatContribution(Stat.velocitypercent, 0,
          NativeStatResolver.Operation.ADD, coldEffect);
      cold.setStatContribution(Stat.attackrate, 0,
          NativeStatResolver.Operation.ADD, coldEffect);
      cold.setStatContribution(Stat.other_animrate, 0,
          NativeStatResolver.Operation.ADD, coldEffect);
      cold.needsSync = true;
    }
    return cold;
  }

  private UnitState applyNativeFreezeState(int entityId, StateList states,
      int duration, int level, int sourceId) {
    if (duration <= 0) return null;
    if (mPlayer.has(entityId)) {
      return applyNativeColdState(entityId, states, duration, level, sourceId);
    }
    if (mMonster.has(entityId)) {
      if (states.hasState(StateId.UNINTERRUPTABLE)) return null;
      Monster monster = mMonster.get(entityId);
      if (mMercenary.has(entityId) || (monster.monstats != null && monster.monstats.boss)
          || monster.rank == MonsterRank.UNIQUE
          || monster.rank == MonsterRank.SUPER_UNIQUE) {
        return applyNativeColdState(entityId, states, duration, level, sourceId);
      }
      int difficulty = difficulty();
      int coldEffect = monster.monstats != null && monster.monstats.coldeffect != null
          && difficulty < monster.monstats.coldeffect.length
          ? monster.monstats.coldeffect[difficulty] : -50;
      if (coldEffect >= 0) return null;
      duration /= freezeDivisor(difficulty);
    }
    UnitState freeze = states.extendState(
        StateId.FREEZE, Math.max(1, duration), level, sourceId);
    if (freeze != null) freeze.needsSync = true;
    return freeze;
  }

  private int difficulty() {
    return map == null ? 0 : Math.max(0, Math.min(2, map.getDifficulty()));
  }

  private int coldDivisor(int difficulty) {
    DifficultyLevels.Entry entry = difficultyEntry(difficulty);
    return entry == null ? 1 : Math.max(1, entry.MonsterColdDivisor);
  }

  private int freezeDivisor(int difficulty) {
    DifficultyLevels.Entry entry = difficultyEntry(difficulty);
    return entry == null ? 1 : Math.max(1, entry.MonsterFreezeDivisor);
  }

  private DifficultyLevels.Entry difficultyEntry(int difficulty) {
    return Riiablo.files == null || Riiablo.files.DifficultyLevels == null
        ? null : Riiablo.files.DifficultyLevels.get(difficulty);
  }

  /**
   * 移除实体的状态
   * 
   * @param entityId 目标实体ID
   * @param stateId 状态ID
   */
  public void removeState(int entityId, int stateId) {
    if (!mUnitStates.has(entityId)) {
      return;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList != null) {
      unitStates.stateList.removeState(stateId);
    }
  }

  /**
   * 检查实体是否有指定状态
   * 
   * @param entityId 实体ID
   * @param stateId 状态ID
   * @return true 如果有状态
   */
  public boolean hasState(int entityId, int stateId) {
    if (!mUnitStates.has(entityId)) {
      return false;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    return unitStates.stateList != null && unitStates.stateList.hasState(stateId);
  }

  /**
   * 检查实体是否被冰冻
   * 
   * @param entityId 实体ID
   * @return true 如果被冰冻
   */
  public boolean isFrozen(int entityId) {
    return hasState(entityId, StateId.FREEZE);
  }

  /**
   * 检查实体是否被眩晕
   * 
   * @param entityId 实体ID
   * @return true 如果被眩晕
   */
  public boolean isStunned(int entityId) {
    return hasState(entityId, StateId.STUNNED);
  }

  /**
   * 检查实体是否能行动
   * 
   * @param entityId 实体ID
   * @return true 如果能行动
   */
  public boolean canAct(int entityId) {
    return !isFrozen(entityId) && !isStunned(entityId);
  }

  /**
   * 检查实体是否在变形状态
   * 
   * @param entityId 实体ID
   * @return true 如果在变形状态
   */
  public boolean isTransformed(int entityId) {
    if (!mUnitStates.has(entityId)) {
      return false;
    }
    
    UnitStates unitStates = mUnitStates.get(entityId);
    return unitStates.stateList != null && unitStates.stateList.isTransformed();
  }
}
