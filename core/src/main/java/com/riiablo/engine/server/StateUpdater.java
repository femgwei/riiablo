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
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
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

  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<NativeUnitFlags> mNativeUnitFlags;

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
    if (event == null || event.victim < 0 || event.physicalDamage <= 0f
        || !mAttributesWrapper.has(event.victim) || !mUnitStates.has(event.victim)) return;
    UnitStates victimUnitStates = mUnitStates.get(event.victim);
    // Network clients consume authoritative snapshots and must not apply the
    // same curse side effect a second time in their presentation world.
    if (victimUnitStates != null && victimUnitStates.snapshotOnly) return;
    StateList victimStates = victimUnitStates != null ? victimUnitStates.stateList : null;
    if (victimStates == null) return;

    absorbBoneArmor(event, victimStates);

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

    UnitState lifeTap = victimStates.getState(StateId.LIFETAP);
    if (lifeTap != null) applyLifeTap(event, lifeTap, physical);

    UnitState ironMaiden = victimStates.getState(StateId.IRONMAIDEN);
    if (ironMaiden != null && event.isMelee()) {
      applyIronMaiden(event, ironMaiden, physical);
    }
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

    processBladeShield(entityId, stateList);
    processSpiderLayTrail(entityId, stateList);
    
    // Resolve this tick before decrementing duration. A one-frame state must
    // still deal its final DOT tick, then expire.
    processDamageOverTime(entityId, stateList);
    stateList.update();
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
      slowPercent += 25 + slowState.level * 5;
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
