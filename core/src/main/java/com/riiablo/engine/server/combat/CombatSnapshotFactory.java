package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.CombatSystem.*;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.AuthoritativeSimulation;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Host-only attribute/state adapter. It constructs snapshots but never resolves attacks. */
final class CombatSnapshotFactory {
  private static final Logger log = LogManager.getLogger(CombatSystem.class);
  private static final short[] MIN_STATS = {0, Stat.firemindam, Stat.lightmindam, Stat.coldmindam,
      Stat.poisonmindam, Stat.magicmindam};
  private static final short[] MAX_STATS = {0, Stat.firemaxdam, Stat.lightmaxdam, Stat.coldmaxdam,
      Stat.poisonmaxdam, Stat.magicmaxdam};

  /** Named host request; legacy positional overloads are translated once at the facade. */
  static final class Request {
    Attributes attacker;
    Attributes defender;
    boolean attackerPlayer;
    boolean defenderPlayer;
    boolean missile;
    int attackMinDamageOverride;
    int attackMaxDamageOverride;
    int attackRatingOverride;
    boolean alwaysHit;
    int[] elementalMinOverride;
    int[] elementalMaxOverride;
    int coldLengthOverride;
    int poisonLengthOverride;
    StateList attackerStates;
    StateList defenderStates;
    boolean defenderMoving;
    boolean precomputedPhysicalDamage;
    int physicalConversionPercent;
    int physicalConversionType = DAMAGE_PHYSICAL;
    StateList.WeaponMasteryBonus mastery;
    NativeCombatContext context;
    int magicTargetBonusPercent;
    boolean defenderDemon;
    boolean defenderUndead;
    boolean defenderIgnoreTargetDefenseAllowed = true;
    int enhancedDamageBonus;
    boolean fixedPointElements;
    NativeMissilePhysicalDamage missilePhysical;

    Request(Attributes attacker, Attributes defender, boolean attackerPlayer,
        boolean defenderPlayer, NativeCombatContext context) {
      this.attacker = attacker;
      this.defender = defender;
      this.attackerPlayer = attackerPlayer;
      this.defenderPlayer = defenderPlayer;
      this.context = context;
    }

    void profile(NativeSkillAttackProfile profile) {
      attackMinDamageOverride = profile.physicalMin;
      attackMaxDamageOverride = profile.physicalMax;
      attackRatingOverride = profile.attackRating;
      alwaysHit = profile.alwaysHit;
      elementalMinOverride = profile.elementalMin;
      elementalMaxOverride = profile.elementalMax;
      coldLengthOverride = profile.coldLength;
      poisonLengthOverride = profile.poisonLength;
      precomputedPhysicalDamage = profile.precomputedPhysical;
      physicalConversionPercent = profile.conversionPercent;
      physicalConversionType = profile.conversionType;
      enhancedDamageBonus = profile.enhancedDamageBonus;
    }
  }

  static final class Snapshot {
    final AttackerData attacker;
    final DefenderData defender;
    Snapshot(AttackerData attacker, DefenderData defender) {
      this.attacker = attacker;
      this.defender = defender;
    }
  }

  static NativeSkillAttackProfile.Source skillSource(Attributes attacker, StateList states) {
    NativeSkillAttackProfile.Source source = new NativeSkillAttackProfile.Source();
    source.weaponMin = statInt(attacker, Stat.mindamage, 0);
    source.weaponMax = statInt(attacker, Stat.maxdamage, source.weaponMin);
    source.attackRating = statInt(attacker, Stat.tohit, 0);
    source.coldLength = statInt(attacker, Stat.coldlength, 0);
    source.poisonLength = statInt(attacker, Stat.poisonlength, 0);
    for (int type = DAMAGE_FIRE; type < DAMAGE_TYPE_COUNT; type++) {
      source.itemMin[type] = statInt(attacker, MIN_STATS[type], 0);
      StatRef max = attacker != null ? attacker.get(MAX_STATS[type]) : null;
      source.itemMaxPresent[type] = max != null;
      source.itemMax[type] = max != null ? max.asInt() : 0;
      if (states != null) {
        source.stateMin[type] = states.getTotalStatContribution(MIN_STATS[type]);
        source.stateMax[type] = states.getTotalStatContribution(MAX_STATS[type]);
      }
    }
    UnitState venom = states != null ? states.getState(StateId.VENOMCLAWS) : null;
    if (venom != null) {
      source.venomActive = true;
      source.venomMin = venom.poisonMinDamage;
      source.venomMax = venom.poisonMaxDamage;
      source.venomLength = venom.poisonLengthOverride;
    }
    return source;
  }

  /** Caller handles absent combatants before this snapshot-only boundary. */
  static Snapshot create(Request request) {
    Attributes attacker = request.attacker;
    Attributes defender = request.defender;
    boolean attackerPlayer = request.attackerPlayer;
    boolean defenderPlayer = request.defenderPlayer;
    boolean missile = request.missile;
    int attackMinDamageOverride = request.attackMinDamageOverride;
    int attackMaxDamageOverride = request.attackMaxDamageOverride;
    int attackRatingOverride = request.attackRatingOverride;
    boolean alwaysHit = request.alwaysHit;
    int[] elementalMinOverride = request.elementalMinOverride;
    int[] elementalMaxOverride = request.elementalMaxOverride;
    int coldLengthOverride = request.coldLengthOverride;
    int poisonLengthOverride = request.poisonLengthOverride;
    StateList attackerStates = request.attackerStates;
    StateList defenderStates = request.defenderStates;
    boolean defenderMoving = request.defenderMoving;
    boolean precomputedPhysicalDamage = request.precomputedPhysicalDamage;
    int physicalConversionPercent = request.physicalConversionPercent;
    int physicalConversionType = request.physicalConversionType;
    StateList.WeaponMasteryBonus mastery = request.mastery;
    NativeCombatContext context = request.context;
    int magicTargetBonusPercent = request.magicTargetBonusPercent;
    boolean defenderDemon = request.defenderDemon;
    boolean defenderUndead = request.defenderUndead;
    boolean defenderIgnoreTargetDefenseAllowed = request.defenderIgnoreTargetDefenseAllowed;
    int enhancedDamageBonus = request.enhancedDamageBonus;
    boolean fixedPointElements = request.fixedPointElements;
    AttackerData a = new AttackerData();
    a.isPlayer = attackerPlayer;
    a.isMissile = missile;
    a.fixedPointElements = fixedPointElements;
    // Migrate weaponless monster melee only. Player precomputed profiles and
    // missiles retain their separate endpoint/RNG contracts.
    a.fixedPointPhysical = !attackerPlayer && !missile && !precomputedPhysicalDamage;
    a.missilePhysical = missile ? request.missilePhysical : null;
    a.normalDamage = statInt(attacker, Stat.item_normaldamage, 0);
    a.minimumDamagePercent = statInt(attacker, Stat.item_mindamage_percent, 0);
    a.maximumDamagePercent = statInt(attacker, Stat.item_maxdamage_percent, 0);
    a.alwaysHit = alwaysHit;
    NativeCombatContext combatContext = context
        .withCombatants(attackerPlayer, defenderPlayer)
        .withTargetType(defenderDemon, defenderUndead);
    a.difficulty = combatContext.difficulty();
    a.stateDurationsDeferred = combatContext.stateDurationsDeferred();
    a.level = Math.max(1, statInt(attacker, Stat.level, 1));
    a.strength = statInt(attacker, Stat.strength, 0);
    a.dexterity = statInt(attacker, Stat.dexterity, 0);
    boolean hasAttackProfileOverride = precomputedPhysicalDamage || attackMinDamageOverride > 0
        || attackMaxDamageOverride > 0 || attackRatingOverride > 0;
    a.attackRating = attackRatingOverride > 0
        ? attackRatingOverride : statInt(attacker, Stat.tohit, 0);
    // A newly-created character may not have a tohit entry until an item/stat
    // refresh has completed.  D2 still gives every player a level/dexterity
    // based attack rating; treating the missing value as zero produces the
    // artificial 5% floor seen in game.log.
    if (attackerPlayer && a.attackRating <= 0) {
      a.attackRating = Math.max(1, a.dexterity * 5 + Math.max(1, a.level) * 2);
    }
    a.attackRatingPercent = statInt(attacker, Stat.item_tohit_percent, 0);
    // "Attack Rating vs Monster" is a separate native percentage modifier;
    // it must affect monster targets only and must not leak into PvP/player
    // hit checks.
    if (!defenderPlayer) {
      a.attackRatingPercent += statInt(attacker, Stat.item_tohit_percent_vs_monster, 0);
    }
    a.demonToHit = statInt(attacker, Stat.item_demon_tohit, 0);
    a.undeadToHit = statInt(attacker, Stat.item_undead_tohit, 0);
    a.demonDamagePercent = statInt(attacker, Stat.item_demondamage_percent, 0);
    a.undeadDamagePercent = statInt(attacker, Stat.item_undeaddamage_percent, 0);
    a.bypassUndeadPhysicalResistance = statInt(attacker, Stat.skill_bypass_undead, 0) > 0;
    a.minDamage = hasAttackProfileOverride
        ? Math.max(0, attackMinDamageOverride) : statInt(attacker, Stat.mindamage, 1);
    a.maxDamage = hasAttackProfileOverride
        ? Math.max(a.minDamage, attackMaxDamageOverride)
        : statInt(attacker, Stat.maxdamage, Math.max(2, a.minDamage));
    if (missile) {
      int throwMin = statInt(attacker, Stat.item_throw_mindamage, 0);
      int throwMax = statInt(attacker, Stat.item_throw_maxdamage, 0);
      if (throwMin > 0 && throwMax >= throwMin) {
        a.minDamage = throwMin;
        a.maxDamage = throwMax;
      }
    }
    a.enhancedDamagePercent = statInt(attacker, Stat.damagepercent, 0)
        + Math.max(0, enhancedDamageBonus);
    a.physicalConversionPercent = Math.max(0, Math.min(100, physicalConversionPercent));
    a.physicalConversionType = physicalConversionType;
    if (attackerStates != null) {
      a.enhancedDamagePercent += attackerStates.getTotalDamageModifier();
      a.normalDamage += attackerStates.getTotalStatContribution(Stat.item_normaldamage);
      a.minimumDamagePercent += attackerStates.getTotalStatContribution(Stat.item_mindamage_percent);
      a.maximumDamagePercent += attackerStates.getTotalStatContribution(Stat.item_maxdamage_percent);
      a.attackRatingPercent += attackerStates.getTotalAttackModifier();
      a.demonToHit += attackerStates.getTotalStatContribution(Stat.item_demon_tohit);
      a.undeadToHit += attackerStates.getTotalStatContribution(Stat.item_undead_tohit);
      a.demonDamagePercent +=
          attackerStates.getTotalStatContribution(Stat.item_demondamage_percent);
      a.undeadDamagePercent +=
          attackerStates.getTotalStatContribution(Stat.item_undeaddamage_percent);
      a.bypassUndeadPhysicalResistance |=
          attackerStates.getTotalStatContribution(Stat.skill_bypass_undead) > 0;
    }
    if (mastery != null) {
      if (!precomputedPhysicalDamage) {
        a.attackRatingPercent += mastery.attackRatingPercent;
        a.enhancedDamagePercent += mastery.damagePercent;
      }
    }
    if (precomputedPhysicalDamage) {
      // The caller has already applied strength/dexterity, skill ED,
      // damagepercent and item_maxdamage_percent exactly as D2Common does.
      a.strength = 0;
      a.enhancedDamagePercent = 0;
    }
    a.elementalMinDamage[DAMAGE_FIRE] = statInt(attacker, Stat.firemindam, 0);
    a.elementalMaxDamage[DAMAGE_FIRE] = statInt(attacker, Stat.firemaxdam, 0);
    a.elementalMinDamage[DAMAGE_LIGHTNING] = statInt(attacker, Stat.lightmindam, 0);
    a.elementalMaxDamage[DAMAGE_LIGHTNING] = statInt(attacker, Stat.lightmaxdam, 0);
    a.elementalMinDamage[DAMAGE_COLD] = statInt(attacker, Stat.coldmindam, 0);
    a.elementalMaxDamage[DAMAGE_COLD] = statInt(attacker, Stat.coldmaxdam, 0);
    a.elementalMinDamage[DAMAGE_POISON] = statInt(attacker, Stat.poisonmindam, 0);
    a.elementalMaxDamage[DAMAGE_POISON] = statInt(attacker, Stat.poisonmaxdam, 0);
    a.elementalMinDamage[DAMAGE_MAGIC] = statInt(attacker, Stat.magicmindam, 0);
    a.elementalMaxDamage[DAMAGE_MAGIC] = statInt(attacker, Stat.magicmaxdam, 0);
    a.magicTargetBonusPercent = Math.max(0, magicTargetBonusPercent);
    if (attackerStates != null) {
      for (int type = DAMAGE_FIRE; type < DAMAGE_TYPE_COUNT; type++) {
        a.elementalMinDamage[type] +=
            attackerStates.getTotalStatContribution(MIN_STATS[type]);
        a.elementalMaxDamage[type] +=
            attackerStates.getTotalStatContribution(MAX_STATS[type]);
      }
    }
    a.elementalPierce[DAMAGE_FIRE] = statInt(attacker, Stat.item_pierce_fire, 0)
        + statInt(attacker, Stat.passive_fire_pierce, 0);
    a.elementalPierce[DAMAGE_LIGHTNING] = statInt(attacker, Stat.item_pierce_ltng, 0)
        + statInt(attacker, Stat.passive_ltng_pierce, 0);
    a.elementalPierce[DAMAGE_COLD] = statInt(attacker, Stat.item_pierce_cold, 0)
        + statInt(attacker, Stat.passive_cold_pierce, 0);
    a.elementalPierce[DAMAGE_POISON] = statInt(attacker, Stat.item_pierce_pois, 0)
        + statInt(attacker, Stat.passive_pois_pierce, 0);
    a.elementalPierce[DAMAGE_MAGIC] = statInt(attacker, Stat.passive_mag_pierce, 0);
    a.deadlyStrike = statInt(attacker, Stat.item_deadlystrike, 0);
    a.criticalStrike = statInt(attacker, Stat.passive_critical_strike, 0);
    if (attackerStates != null) {
      a.criticalStrike += attackerStates.getTotalStatContribution(
          Stat.passive_critical_strike);
    }
    if (mastery != null) a.criticalStrike += mastery.criticalChance;
    a.crushingBlow = statInt(attacker, Stat.item_crushingblow, 0);
    a.lifeLeech = statInt(attacker, Stat.lifedrainmindam, 0);
    a.manaLeech = statInt(attacker, Stat.manadrainmindam, 0);
    if (attackerStates != null) {
      a.lifeLeech += attackerStates.getTotalLifeLeechModifier();
    }
    a.coldLength = statInt(attacker, Stat.coldlength, 0);
    a.poisonLength = statInt(attacker, Stat.poisonlength, 0);
    // DefensiveBuff SrvDo018 installs Venom as a state stat-list. It adds to
    // item poison damage but overrides (rather than averages with) the normal
    // poison duration.
    if (attackerStates != null) {
      UnitState venom = attackerStates.getState(StateId.VENOMCLAWS);
      if (venom != null) {
        a.elementalMinDamage[DAMAGE_POISON] += Math.max(0, venom.poisonMinDamage);
        a.elementalMaxDamage[DAMAGE_POISON] += Math.max(0, venom.poisonMaxDamage);
        if (venom.poisonLengthOverride > 0) {
          a.poisonLength = venom.poisonLengthOverride;
        }
      }
    }
    if (elementalMinOverride != null && elementalMaxOverride != null) {
      for (int i = DAMAGE_FIRE; i < DAMAGE_TYPE_COUNT; i++) {
        int min = i < elementalMinOverride.length ? elementalMinOverride[i] : 0;
        int max = i < elementalMaxOverride.length ? elementalMaxOverride[i] : 0;
        a.elementalMinDamage[i] = Math.max(0, min);
        a.elementalMaxDamage[i] = Math.max(a.elementalMinDamage[i], max);
      }
      a.coldLength = Math.max(0, coldLengthOverride);
      a.poisonLength = Math.max(0, poisonLengthOverride);
    }
    a.ignoreTargetDefense = statInt(attacker, Stat.item_ignoretargetac, 0) > 0;

    DefenderData d = new DefenderData();
    d.isPlayer = defenderPlayer;
    d.isMonster = !defenderPlayer;
    d.ignoreTargetDefenseAllowed = defenderIgnoreTargetDefenseAllowed;
    d.isDemon = defenderDemon;
    d.isUndead = defenderUndead;
    d.level = Math.max(1, statInt(defender, Stat.level, 1));
    d.dexterity = statInt(defender, Stat.dexterity, 0);
    d.defense = statInt(defender, Stat.armorclass, 0);
    // D2 keeps the base armor class and the attack-mode-specific armor class
    // as separate additive values. The previous fallback only used the
    // specialized stat when base defense was zero, silently dropping missile
    // or melee defense bonuses on normally armored targets.
    d.defenseVsMissile = statInt(defender, Stat.armorclass_vs_missile, 0);
    d.defenseVsMelee = statInt(defender, Stat.armorclass_vs_hth, 0);
    if (defenderStates != null) {
      d.defense = Math.max(0, d.defense + defenderStates.getTotalFlatDefenseModifier());
      d.defense = Math.max(0, d.defense * (100 + defenderStates.getTotalDefenseModifier()) / 100);
    }
    d.currentLife = Math.max(0, statInt(defender, Stat.hitpoints, 0));
    d.maxLife = Math.max(d.currentLife, statInt(defender, Stat.maxhp, d.currentLife));
    d.blockChance = statInt(defender, Stat.toblock, 0);
    // Holy Shield owns its native `toblock` value in a source stat-list;
    // Attributes only contains the folded permanent/item stats. Include the
    // active state layers before applying the normal dexterity/level formula.
    if (defenderStates != null) {
      d.blockChance += defenderStates.getTotalStatContribution(Stat.toblock);
    }
    // D2MOO MissMode passes bBlock when the missile carries physical damage.
    // Shield block is therefore not melee-only; eligibility is finalized from
    // the attack packet in calculateAttack(AttackerData, DefenderData).
    // A player can only shield-block when the runtime equipment context says
    // an active shield is equipped.  Monsters use the same context when their
    // native MonStats/MonStats2 presentation actually supplies a shield;
    // treating every monster as shielded makes a player's attacks produce a
    // monster BL reaction and block sound even for monsters with only a
    // non-zero ToBlock row.
    d.hasEquippedShield = defender.hasCombatShieldContext()
        ? defender.combatHasShield() : defenderPlayer;
    d.blockFactor = defenderPlayer && defender.hasCombatShieldContext()
        ? defender.combatBlockFactor() : 0;
    d.isRunning = defender.hasCombatRunningContext()
        ? defender.combatRunning() : (defenderPlayer && defenderMoving);
    d.fasterBlockRate = Math.max(0, statInt(defender, Stat.item_fasterblockrate, 0));
    d.lastBlockFrame = statInt(defender, Stat.lastblockframe, 0);
    d.combatAttributes = defender;
    AuthoritativeSimulation simulation = AuthoritativeSimulation.current();
    long frame = simulation != null ? simulation.tickNumber()
        : System.nanoTime() / (SimulationClock.STEP_MILLIS * 1_000_000L);
    d.currentFrame = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, frame));
    // A Buckler has native ToBlock=0: the player's class BlockFactor still
    // grants a block chance. D2MOO checks the equipped shield, then computes
    // class + unit ToBlock; never reject the raw unit stat before that sum.
    d.canBlock = d.hasEquippedShield;
    d.attackType = missile ? DefenseCalculator.ATTACK_RANGED : DefenseCalculator.ATTACK_MELEE;
    d.isMoving = defenderMoving;
    if (defenderPlayer) {
      log.debug("[COMBAT_BLOCK_CHECK] shieldContext={} hasShield={} toBlock={} blockFactor={} "
              + "dexterity={} level={} running={} canBlock={} attackType={} missile={}",
          defender.hasCombatShieldContext(), d.hasEquippedShield, d.blockChance,
          d.blockFactor, d.dexterity, d.level, d.isRunning, d.canBlock,
          d.attackType, missile);
    }
    d.passiveDodge = statInt(defender, Stat.passive_dodge, 0);
    d.passiveAvoid = statInt(defender, Stat.passive_avoid, 0);
    d.passiveEvade = statInt(defender, Stat.passive_evade, 0);
    if (defenderStates != null) {
      d.passiveDodge += defenderStates.getTotalStatContribution(Stat.passive_dodge);
      d.passiveAvoid += defenderStates.getTotalStatContribution(Stat.passive_avoid);
      d.passiveEvade += defenderStates.getTotalStatContribution(Stat.passive_evade);
    }
    d.passiveWeaponBlock = statInt(defender, Stat.passive_weaponblock, 0);
    d.resistances[DAMAGE_PHYSICAL] = statInt(defender, Stat.damageresist, 0);
    d.resistances[DAMAGE_FIRE] = statInt(defender, Stat.fireresist, 0);
    d.resistances[DAMAGE_LIGHTNING] = statInt(defender, Stat.lightresist, 0);
    d.resistances[DAMAGE_COLD] = statInt(defender, Stat.coldresist, 0);
    d.resistances[DAMAGE_POISON] = statInt(defender, Stat.poisonresist, 0);
    d.resistances[DAMAGE_MAGIC] = statInt(defender, Stat.magicresist, 0);
    int difficultyPenalty = defenderPlayer
        ? NativeDifficultyRules.remainingResistancePenalty(defender, combatContext) : 0;
    for (int i = DAMAGE_FIRE; i < DAMAGE_TYPE_COUNT; i++) {
      if (i != DAMAGE_MAGIC) d.resistances[i] += difficultyPenalty;
    }
    d.maxResistances[DAMAGE_FIRE] = 75 + statInt(defender, Stat.maxfireresist, 0);
    d.maxResistances[DAMAGE_LIGHTNING] = 75 + statInt(defender, Stat.maxlightresist, 0);
    d.maxResistances[DAMAGE_COLD] = 75 + statInt(defender, Stat.maxcoldresist, 0);
    d.maxResistances[DAMAGE_POISON] = 75 + statInt(defender, Stat.maxpoisonresist, 0);
    d.maxResistances[DAMAGE_MAGIC] = 75 + statInt(defender, Stat.maxmagicresist, 0);
    d.absorbPercent[DAMAGE_FIRE] = statInt(defender, Stat.item_absorbfire_percent, 0);
    d.absorbPercent[DAMAGE_LIGHTNING] = statInt(defender, Stat.item_absorblight_percent, 0);
    d.absorbPercent[DAMAGE_COLD] = statInt(defender, Stat.item_absorbcold_percent, 0);
    d.absorbPercent[DAMAGE_MAGIC] = statInt(defender, Stat.item_absorbmagic_percent, 0);
    d.absorbFlat[DAMAGE_FIRE] = statInt(defender, Stat.item_absorbfire, 0);
    d.absorbFlat[DAMAGE_LIGHTNING] = statInt(defender, Stat.item_absorblight, 0);
    d.absorbFlat[DAMAGE_COLD] = statInt(defender, Stat.item_absorbcold, 0);
    d.absorbFlat[DAMAGE_MAGIC] = statInt(defender, Stat.item_absorbmagic, 0);
    d.cannotBeFrozen = statInt(defender, Stat.item_cannotbefrozen, 0) != 0;
    d.halfFreezeDuration = statInt(defender, Stat.item_halffreezeduration, 0) != 0;
    if (defenderStates != null) {
      d.resistances[DAMAGE_PHYSICAL] += defenderStates.getTotalPhysicalResistModifier();
      d.resistances[DAMAGE_FIRE] += defenderStates.getTotalResistModifier(0);
      d.resistances[DAMAGE_COLD] += defenderStates.getTotalResistModifier(1);
      d.resistances[DAMAGE_LIGHTNING] += defenderStates.getTotalResistModifier(2);
      d.resistances[DAMAGE_POISON] += defenderStates.getTotalResistModifier(3);
      d.resistances[DAMAGE_MAGIC] += defenderStates.getTotalResistModifier(4);
      d.maxResistances[DAMAGE_FIRE] +=
          defenderStates.getTotalStatContribution(Stat.maxfireresist);
      d.maxResistances[DAMAGE_COLD] +=
          defenderStates.getTotalStatContribution(Stat.maxcoldresist);
      d.maxResistances[DAMAGE_LIGHTNING] +=
          defenderStates.getTotalStatContribution(Stat.maxlightresist);
      d.maxResistances[DAMAGE_POISON] +=
          defenderStates.getTotalStatContribution(Stat.maxpoisonresist);
      d.maxResistances[DAMAGE_MAGIC] +=
          defenderStates.getTotalStatContribution(Stat.maxmagicresist);
    }
    // Amplify Damage and Decrepify contribute negative damageresist. Native
    // physical resistance keeps that sign so the same percentage formula
    // increases damage below zero instead of silently clamping the curse out.
    d.damageReducedPercent = Math.max(MIN_RESISTANCE, d.resistances[DAMAGE_PHYSICAL]);
    // This must reach the attack snapshot as well as the direct-damage path.
    // Omitting it silently ignores item/state flat DR on real melee/missile hits.
    d.damageReduced = Math.max(0, statInt(defender, Stat.normal_damage_reduction, 0)
        + (defenderStates == null ? 0
            : defenderStates.getTotalStatContribution(Stat.normal_damage_reduction)));
    d.magicDamageReduced = statInt(defender, Stat.magic_damage_reduction, 0);
    d.immunePhysical = d.resistances[DAMAGE_PHYSICAL] >= 100;
    for (int i = DAMAGE_FIRE; i < DAMAGE_TYPE_COUNT; i++) {
      d.immuneElemental[i] = d.isMonster && d.resistances[i] >= 100;
    }
    return new Snapshot(a, d);
  }

  private static int statInt(Attributes attrs, short stat, int fallback) {
    if (attrs == null) return fallback;
    StatRef ref = attrs.get(stat);
    return ref != null ? ref.asInt() : fallback;
  }
}
