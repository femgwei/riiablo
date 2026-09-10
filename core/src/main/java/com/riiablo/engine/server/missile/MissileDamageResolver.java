package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.MonsterStatsCalculator;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.StateList;
import java.util.function.ToIntFunction;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Builds the damage stat snapshot D2MOO stores on each missile unit. */
public final class MissileDamageResolver {
  private static final Logger log = LogManager.getLogger(MissileDamageResolver.class);

  private static final int PHYSICAL = 0;
  private static final int FIRE = 1;
  private static final int LIGHTNING = 2;
  private static final int COLD = 3;
  private static final int POISON = 4;
  private static final int MAGIC = 5;
  private static final int DAMAGE_TYPES = 6;

  private MissileDamageResolver() {}

  /**
   * Resolves non-skill Missiles.txt damage and source damage at spawn time.
   * Skill-owned missiles are left on the legacy path until the complete
   * Skills.txt damage schema is available; this avoids silently replacing a
   * working player spell with an empty snapshot.
   */
  public static boolean initialize(Missile projectile, Attributes ownerAttrs,
      Monster ownerMonster, int currentMode, int level, int difficulty) {
    if (projectile == null || projectile.missile == null) return false;
    Missiles.Entry row = projectile.missile;
    level = Math.max(1, level);
    difficulty = Math.max(0, Math.min(2, difficulty));
    projectile.damageLevel = level;

    boolean skillDamage = hasText(row.Skill) || row.MissileSkill;
    if (skillDamage) {
      projectile.damageSnapshot = false;
      return false;
    }

    int physicalMin = shiftedDamage(row.MinDamage, row.MinLevDam, level, row.HitShift);
    int physicalMax = shiftedDamage(row.MaxDamage, row.MaxLevDam, level, row.HitShift);
    int[] elementalMin = new int[DAMAGE_TYPES];
    int[] elementalMax = new int[DAMAGE_TYPES];
    int coldLength = 0;
    int poisonLength = 0;

    int directType = damageType(row.EType);
    if (directType > PHYSICAL) {
      elementalMin[directType] = shiftedDamage(row.EMin, row.MinELev, level, row.HitShift);
      elementalMax[directType] = shiftedDamage(row.Emax, row.MaxELev, level, row.HitShift);
      int length = elementalLength(row, level);
      if (directType == COLD) coldLength = length;
      if (directType == POISON) poisonLength = length;
    }

    int sourceScale = Math.max(0, row.SrcDamage);
    int attackRating = statInt(ownerAttrs, Stat.tohit);
    if (sourceScale > 0 && ownerAttrs != null) {
      int sourceMin = statInt(ownerAttrs, Stat.item_throw_mindamage);
      int sourceMax = statInt(ownerAttrs, Stat.item_throw_maxdamage);
      if (sourceMax <= 0) {
        sourceMin = statInt(ownerAttrs, Stat.mindamage);
        sourceMax = statInt(ownerAttrs, Stat.maxdamage);
      }
      physicalMin += scaleSource(sourceMin, sourceScale);
      physicalMax += scaleSource(sourceMax, sourceScale);
      addOwnerElemental(ownerAttrs, sourceScale, elementalMin, elementalMax);
      coldLength = Math.max(coldLength,
          scaleSource(statInt(ownerAttrs, Stat.coldlength), sourceScale));
      poisonLength = Math.max(poisonLength,
          scaleSource(statInt(ownerAttrs, Stat.poisonlength), sourceScale));
    }

    if (sourceScale > 0 && ownerMonster != null && ownerMonster.monstats != null) {
      int mode = resolveAttackMode(ownerMonster.monstats, row, currentMode);
      int[] duration = {coldLength, poisonLength};
      addMonsterElemental(ownerMonster.monstats, mode, level, difficulty,
          sourceScale, elementalMin, elementalMax, duration);
      coldLength = duration[0];
      poisonLength = duration[1];
    }

    physicalMin = Math.max(0, physicalMin);
    physicalMax = Math.max(physicalMin, physicalMax);
    boolean meaningful = physicalMax > 0;
    for (int i = 1; i < DAMAGE_TYPES; i++) {
      elementalMin[i] = Math.max(0, elementalMin[i]);
      elementalMax[i] = Math.max(elementalMin[i], elementalMax[i]);
      meaningful |= elementalMax[i] > 0;
    }
    if (!meaningful) {
      projectile.damageSnapshot = false;
      return false;
    }

    writeSnapshot(projectile, ownerAttrs, sourceScale > 0, level,
        physicalMin, physicalMax, attackRating,
        elementalMin, elementalMax, coldLength, poisonLength);
    log.info("[MISSILE_DAMAGE_SNAPSHOT] missile={} owner={} level={} toHit={} srcDamage={} "
            + "physical={}..{} fire={}..{} lightning={}..{} cold={}..{} poison={}..{} "
            + "magic={}..{} coldLength={} poisonLength={}",
        row.Missile, projectile.ownerId, level, row.ToHit, row.SrcDamage,
        physicalMin, physicalMax, elementalMin[FIRE], elementalMax[FIRE],
        elementalMin[LIGHTNING], elementalMax[LIGHTNING],
        elementalMin[COLD], elementalMax[COLD],
        elementalMin[POISON], elementalMax[POISON],
        elementalMin[MAGIC], elementalMax[MAGIC], coldLength, poisonLength);
    return true;
  }

  /** Builds the native Skills.txt damage snapshot used by elemental arrows. */
  public static boolean initializeSkill(Missile projectile, Skills.Entry skill,
      Attributes ownerAttrs, int level) {
    boolean impactCreatesExplosion = projectile != null && projectile.missile != null
        && projectile.missile.pSrvHitFunc == 4;
    return initializeSkill(projectile, skill, ownerAttrs, level, true, !impactCreatesExplosion,
        name -> 0, 0);
  }

  /** Native skill snapshot with a resolver for EDmgSymPerCalc/ELenSymPerCalc. */
  public static boolean initializeSkill(Missile projectile, Skills.Entry skill,
      Attributes ownerAttrs, int level, ToIntFunction<String> baseSkillLevel) {
    boolean impactCreatesExplosion = projectile != null && projectile.missile != null
        && projectile.missile.pSrvHitFunc == 4;
    return initializeSkill(projectile, skill, ownerAttrs, level, true, !impactCreatesExplosion,
        baseSkillLevel == null ? name -> 0 : baseSkillLevel, 0);
  }

  /** Skill snapshot including permanent passive stat lists owned by the caster. */
  public static boolean initializeSkill(Missile projectile, Skills.Entry skill,
      Attributes ownerAttrs, int level, ToIntFunction<String> baseSkillLevel,
      StateList ownerStates) {
    boolean impactCreatesExplosion = projectile != null && projectile.missile != null
        && projectile.missile.pSrvHitFunc == 4;
    int additionalMastery = 0;
    if (ownerStates != null && skill != null) {
      short masteryStat = masteryStat(skill.EType);
      if (masteryStat != 0) {
        additionalMastery = Math.max(0,
            ownerStates.getTotalStatContribution(masteryStat));
      }
    }
    return initializeSkill(projectile, skill, ownerAttrs, level, true, !impactCreatesExplosion,
        baseSkillLevel == null ? name -> 0 : baseSkillLevel, additionalMastery);
  }

  /** Builds the elemental-only snapshot inherited by an explosion sub-missile. */
  public static boolean initializeSkillArea(Missile projectile, Skills.Entry skill,
      Attributes ownerAttrs, int level) {
    return initializeSkill(projectile, skill, ownerAttrs, level, false, true, name -> 0, 0);
  }

  /** Elemental-only explosion snapshot including the owner's passive stat lists. */
  public static boolean initializeSkillArea(Missile projectile, Skills.Entry skill,
      Attributes ownerAttrs, int level, ToIntFunction<String> baseSkillLevel,
      StateList ownerStates) {
    int additionalMastery = 0;
    if (ownerStates != null && skill != null) {
      short masteryStat = masteryStat(skill.EType);
      if (masteryStat != 0) {
        additionalMastery = Math.max(0,
            ownerStates.getTotalStatContribution(masteryStat));
      }
    }
    return initializeSkill(projectile, skill, ownerAttrs, level, false, true,
        baseSkillLevel == null ? name -> 0 : baseSkillLevel, additionalMastery);
  }

  /**
   * Captures the native 8.8 per-frame fire packet used by Blaze/Fire Wall.
   * D2Common's skill-owned missile path always applies elemental mastery;
   * {@code Missiles.ApplyMastery} only gates table-owned missile damage.
   */
  public static boolean initializeSorceressFireArea(Missile projectile,
      Skills.Entry skill, Attributes ownerAttrs, boolean attackerPlayer, int level,
      ToIntFunction<String> baseSkillLevel) {
    return initializeSorceressFireArea(projectile, skill, ownerAttrs, attackerPlayer,
        level, baseSkillLevel, null);
  }

  /** Fire-area snapshot including Fire Mastery's source-owned passive state. */
  public static boolean initializeSorceressFireArea(Missile projectile,
      Skills.Entry skill, Attributes ownerAttrs, boolean attackerPlayer, int level,
      ToIntFunction<String> baseSkillLevel, StateList ownerStates) {
    if (projectile == null || projectile.missile == null || skill == null
        || !"fire".equalsIgnoreCase(skill.EType)) return false;
    level = Math.max(1, level);
    int min = skillElementalDamageFixed(skill, level, true, baseSkillLevel);
    int max = skillElementalDamageFixed(skill, level, false, baseSkillLevel);
    int mastery = Math.max(0, statInt(ownerAttrs, Stat.passive_fire_mastery));
    if (ownerStates != null) {
      mastery += Math.max(0,
          ownerStates.getTotalStatContribution(Stat.passive_fire_mastery));
    }
    min = percentage(min, 100 + mastery);
    max = percentage(max, 100 + mastery);
    if (max <= 0) return false;

    projectile.skillId = skill.Id;
    projectile.damageLevel = level;
    projectile.damageSnapshot = true;
    projectile.fixedElementalRate = true;
    projectile.fixedElementalType = com.riiablo.engine.server.combat.CombatSystem.DAMAGE_FIRE;
    projectile.elementalMinRateFixed = Math.max(0, min);
    projectile.elementalMaxRateFixed = Math.max(projectile.elementalMinRateFixed, max);
    projectile.elementalPiercePercent =
        statInt(ownerAttrs, Stat.item_pierce_fire)
            + statInt(ownerAttrs, Stat.passive_fire_pierce);
    projectile.elementalDamageRate = Math.max(0, projectile.missile.DamageRate);
    projectile.elementalAttackerPlayer = attackerPlayer;
    log.info("[FIRE_AREA_DAMAGE] missile={} skill={} level={} rawFixed={}..{} "
            + "mastery={} pierce={} damageRate={}",
        projectile.missile.Missile, skill.skill, level,
        projectile.elementalMinRateFixed, projectile.elementalMaxRateFixed,
        mastery, projectile.elementalPiercePercent, projectile.elementalDamageRate);
    return true;
  }

  /** Builds the native magic packet used by Bone Spear and Bone Spirit. */
  public static boolean initializeNecromancerBoneMagic(Missile projectile,
      Skills.Entry skill, Attributes ownerAttrs, int level,
      ToIntFunction<String> baseSkillLevel) {
    if (projectile == null || skill == null
        || (!NecromancerSkills.isBoneSpear(skill) && !NecromancerSkills.isBoneSpirit(skill))) {
      return false;
    }
    int[] damage = NecromancerSkills.getBoneProjectileMagicDamage(
        skill, level, baseSkillLevel);
    if (damage[1] <= 0) return false;
    int[] elementalMin = new int[DAMAGE_TYPES];
    int[] elementalMax = new int[DAMAGE_TYPES];
    elementalMin[MAGIC] = damage[0];
    elementalMax[MAGIC] = damage[1];
    writeSnapshot(projectile, ownerAttrs, false, Math.max(1, level),
        0, 0, statInt(ownerAttrs, Stat.tohit), elementalMin, elementalMax, 0, 0);
    projectile.skillId = skill.Id;
    projectile.damageLevel = Math.max(1, level);
    projectile.usesAttackRating = false;
    projectile.freezesTarget = false;
    log.info("[BONE_PROJECTILE_DAMAGE] missile={} skill={} level={} magic={}..{}",
        projectile.missile != null ? projectile.missile.Missile : "", skill.skill,
        level, damage[0], damage[1]);
    return true;
  }

  /** Builds the SrvDo073 magic snapshot after its cast-time Concentration bonus. */
  public static boolean initializePaladinBlessedHammer(Missile projectile,
      Skills.Entry skill, Attributes ownerAttrs, int level,
      ToIntFunction<String> baseSkillLevel, int concentrationPercent) {
    if (projectile == null || !PaladinSkills.isBlessedHammer(skill)) return false;
    int[] damage = PaladinSkills.getBlessedHammerMagicDamage(
        skill, level, baseSkillLevel);
    int bonus = Math.max(0, concentrationPercent);
    damage[0] += damage[0] * bonus / 100;
    damage[1] += damage[1] * bonus / 100;
    int[] elementalMin = new int[DAMAGE_TYPES];
    int[] elementalMax = new int[DAMAGE_TYPES];
    elementalMin[MAGIC] = damage[0];
    elementalMax[MAGIC] = damage[1];
    writeSnapshot(projectile, ownerAttrs, false, Math.max(1, level),
        0, 0, statInt(ownerAttrs, Stat.tohit), elementalMin, elementalMax, 0, 0);
    projectile.skillId = skill.Id;
    projectile.damageLevel = Math.max(1, level);
    projectile.usesAttackRating = false;
    log.info("[BLESSED_HAMMER_DAMAGE] missile={} level={} magic={}..{} concentration={}",
        projectile.missile != null ? projectile.missile.Missile : "",
        level, damage[0], damage[1], bonus);
    return true;
  }

  /** Builds the SrvDo080 center lightning snapshot stored on the delay missile. */
  public static boolean initializePaladinFistOfTheHeavens(Missile projectile,
      Skills.Entry skill, Attributes ownerAttrs, int level,
      ToIntFunction<String> baseSkillLevel) {
    if (projectile == null || !PaladinSkills.isFistOfTheHeavens(skill)) return false;
    int[] damage = PaladinSkills.getFistOfHeavensLightningDamage(
        skill, level, baseSkillLevel);
    boolean initialized = initializePaladinElemental(
        projectile, skill, ownerAttrs, level, Stat.lightmindam, Stat.lightmaxdam, damage);
    if (initialized) {
      log.info("[FIST_OF_HEAVENS_DAMAGE] phase=center missile={} level={} lightning={}..{}",
          projectile.missile != null ? projectile.missile.Missile : "",
          level, damage[0], damage[1]);
    }
    return initialized;
  }

  /** Builds the ordinary Holy Bolt magic packet from Skills.txt. */
  public static boolean initializePaladinHolyBolt(Missile projectile,
      Skills.Entry skill, Attributes ownerAttrs, int level,
      ToIntFunction<String> baseSkillLevel) {
    if (projectile == null || !PaladinSkills.isHolyBolt(skill)) return false;
    int[] damage = PaladinSkills.getHolyBoltMagicDamage(skill, level, baseSkillLevel);
    boolean initialized = initializePaladinElemental(
        projectile, skill, ownerAttrs, level, Stat.magicmindam, Stat.magicmaxdam, damage);
    if (initialized) {
      log.info("[HOLY_BOLT_DAMAGE] missile={} level={} magic={}..{}",
          projectile.missile != null ? projectile.missile.Missile : "",
          level, damage[0], damage[1]);
    }
    return initialized;
  }

  /** Builds each SrvHit22 child from the FoH-bolt Missiles.txt row. */
  public static boolean initializePaladinFistOfHeavensBolt(Missile projectile,
      Skills.Entry fist, Attributes ownerAttrs, int level,
      ToIntFunction<String> baseSkillLevel) {
    if (projectile == null || projectile.missile == null
        || !PaladinSkills.isFistOfTheHeavens(fist)) return false;
    int[] damage = PaladinSkills.getFistOfHeavensBoltMagicDamage(
        projectile.missile, fist, level, baseSkillLevel);
    boolean initialized = initializePaladinElemental(
        projectile, fist, ownerAttrs, level, Stat.magicmindam, Stat.magicmaxdam, damage);
    if (initialized) {
      log.info("[FIST_OF_HEAVENS_DAMAGE] phase=holy_bolt missile={} level={} magic={}..{}",
          projectile.missile.Missile, level, damage[0], damage[1]);
    }
    return initialized;
  }

  private static boolean initializePaladinElemental(Missile projectile,
      Skills.Entry skill, Attributes ownerAttrs, int level,
      short minStat, short maxStat, int[] damage) {
    if (damage == null || damage.length < 2 || damage[1] <= 0) return false;
    int[] elementalMin = new int[DAMAGE_TYPES];
    int[] elementalMax = new int[DAMAGE_TYPES];
    int type = minStat == Stat.lightmindam ? LIGHTNING : MAGIC;
    elementalMin[type] = Math.max(0, damage[0]);
    elementalMax[type] = Math.max(elementalMin[type], damage[1]);
    writeSnapshot(projectile, ownerAttrs, false, Math.max(1, level),
        0, 0, statInt(ownerAttrs, Stat.tohit), elementalMin, elementalMax, 0, 0);
    projectile.skillId = skill.Id;
    projectile.damageLevel = Math.max(1, level);
    projectile.usesAttackRating = false;
    return projectile.damage.get(minStat) != null && projectile.damage.get(maxStat) != null;
  }

  private static boolean initializeSkill(Missile projectile, Skills.Entry skill,
      Attributes ownerAttrs, int level, boolean includeSource, boolean includeElement,
      ToIntFunction<String> baseSkillLevel, int additionalMastery) {
    if (projectile == null || skill == null) return false;
    level = Math.max(1, level);
    projectile.skillId = skill.Id;
    projectile.damageLevel = level;
    int sourceScale = includeSource ? Math.max(0, skill.SrcDam) : 0;
    int sourceMin = statInt(ownerAttrs, Stat.mindamage);
    int sourceMax = statInt(ownerAttrs, Stat.maxdamage);
    int physicalMin = (sourceScale > 0 ? sourceMin * sourceScale / 128 : 0)
        + (includeSource ? shiftedDamage(skill.MinDam, skill.MinLevDam, level, skill.HitShift) : 0);
    int physicalMax = (sourceScale > 0 ? sourceMax * sourceScale / 128 : 0)
        + (includeSource ? shiftedDamage(skill.MaxDam, skill.MaxLevDam, level, skill.HitShift) : 0);
    int type = damageType(hasText(skill.EType) ? skill.EType
        : projectile.missile != null ? projectile.missile.EType : null);
    int[] elementalMin = new int[DAMAGE_TYPES];
    int[] elementalMax = new int[DAMAGE_TYPES];
    if (includeElement && type > PHYSICAL) {
      elementalMin[type] = shiftedDamage(skill.EMin, skill.EMinLev, level, skill.HitShift);
      elementalMax[type] = shiftedDamage(skill.EMax, skill.EMaxLev, level, skill.HitShift);
      int synergy = Math.max(0, SkillFormula.evaluate(skill.EDmgSymPerCalc, skill, level,
          baseSkillLevel));
      if (synergy > 0) {
        elementalMin[type] += elementalMin[type] * synergy / 100;
        elementalMax[type] += elementalMax[type] * synergy / 100;
      }
      // D2Common MISSILE_CalculateDamageData has two branches: table-owned
      // missile damage is gated by Missiles.ApplyMastery, while a missile
      // backed by Skills.txt calls SKILLS_GetMin/MaxElemDamage(..., true) and
      // therefore always includes the caster's elemental mastery. This
      // resolver is the latter, skill-owned branch; Fire Bolt notably leaves
      // ApplyMastery blank in Missiles.txt but still receives Fire Mastery.
      if (projectile.missile != null) {
        short masteryStat = masteryStat(skill.EType);
        int mastery = masteryStat != 0 ? Math.max(0,
            statInt(ownerAttrs, masteryStat) + additionalMastery) : 0;
        elementalMin[type] += elementalMin[type] * mastery / 100;
        elementalMax[type] += elementalMax[type] * mastery / 100;
      }
    }
    int coldLength = includeElement && type == COLD ? Math.max(0, skill.ELen
        + damageBonusByLevel(level, skill.ELevLen)) : 0;
    if (includeSource && projectile.missile != null
        && projectile.missile.pSrvDmgFunc == 1 && type > PHYSICAL) {
      int conversion = Math.min(100, Math.max(0,
          damageConversionPercent(projectile.missile, level)));
      int convertedMin = physicalMin * conversion / 100;
      int convertedMax = physicalMax * conversion / 100;
      physicalMin -= convertedMin;
      physicalMax -= convertedMax;
      elementalMin[type] += convertedMin;
      elementalMax[type] += convertedMax;
    }
    if (physicalMax <= 0 && elementalMax[type] <= 0) {
      projectile.damageSnapshot = false;
      return false;
    }
    writeSnapshot(projectile, ownerAttrs, includeSource, level, physicalMin, physicalMax,
        statInt(ownerAttrs, Stat.tohit), elementalMin, elementalMax, coldLength, 0);
    log.info("[SKILL_DAMAGE_SNAPSHOT] missile={} skill={} level={} physical={}..{} element={}..{} "
            + "coldLength={} srcDam={}", projectile.missile != null ? projectile.missile.Missile : "",
        skill.skill, level, physicalMin, physicalMax, elementalMin[type], elementalMax[type],
        coldLength, sourceScale);
    projectile.freezesTarget = projectile.missile != null
        && (projectile.missile.pSrvDmgFunc == 2
            || "freeze".equalsIgnoreCase(projectile.missile.EType)
            || "frze".equalsIgnoreCase(projectile.missile.EType));
    projectile.usesAttackRating = includeSource && skill.SrcDam > 0
        && !"Guided Arrow".equalsIgnoreCase(skill.skill);
    return true;
  }

  private static short masteryStat(String element) {
    if (element == null) return 0;
    if ("fire".equalsIgnoreCase(element)) return Stat.passive_fire_mastery;
    if ("ltng".equalsIgnoreCase(element)
        || "lightning".equalsIgnoreCase(element)) return Stat.passive_ltng_mastery;
    if ("cold".equalsIgnoreCase(element)
        || "freeze".equalsIgnoreCase(element)
        || "frze".equalsIgnoreCase(element)) return Stat.passive_cold_mastery;
    return 0;
  }

  /** Replaces the source A1 profile with the A2 profile selected by Actioneer. */
  public static void applySourceAttackProfile(Missile projectile,
      int sourceMin, int sourceMax, int attackRating) {
    if (projectile == null || projectile.missile == null) return;
    Missiles.Entry row = projectile.missile;
    int level = Math.max(1, projectile.damageLevel);
    int min = shiftedDamage(row.MinDamage, row.MinLevDam, level, row.HitShift)
        + scaleSource(sourceMin, Math.max(0, row.SrcDamage));
    int max = shiftedDamage(row.MaxDamage, row.MaxLevDam, level, row.HitShift)
        + scaleSource(sourceMax, Math.max(0, row.SrcDamage));
    projectile.attackMinDamage = Math.max(0, min);
    projectile.attackMaxDamage = Math.max(projectile.attackMinDamage, max);
    projectile.attackRating = Math.max(0, attackRating);
    if (projectile.damageSnapshot) {
      StatListRef base = projectile.damage.base();
      base.put(Stat.mindamage, projectile.attackMinDamage);
      base.put(Stat.maxdamage, projectile.attackMaxDamage);
      base.put(Stat.tohit, projectile.attackRating);
      projectile.damage.reset();
    }
  }

  private static void writeSnapshot(Missile projectile, Attributes ownerAttrs,
      boolean copySourceBonuses, int level, int physicalMin, int physicalMax,
      int attackRating, int[] elementalMin, int[] elementalMax,
      int coldLength, int poisonLength) {
    StatListRef base = projectile.damage.base();
    base.clear();
    base.put(Stat.level, level);
    if (ownerAttrs != null) {
      base.put(Stat.strength, statInt(ownerAttrs, Stat.strength));
      base.put(Stat.dexterity, statInt(ownerAttrs, Stat.dexterity));
      // SUNITDMG applies these from the skill owner, independently of the
      // damage packet stored on the missile. Preserve their cast-time value
      // in the Java projectile snapshot so Cold Mastery and item pierce are
      // not lost when CombatSystem receives the snapshot as its attacker.
      copyStat(ownerAttrs, base, Stat.item_pierce_fire);
      copyStat(ownerAttrs, base, Stat.passive_fire_pierce);
      copyStat(ownerAttrs, base, Stat.item_pierce_ltng);
      copyStat(ownerAttrs, base, Stat.passive_ltng_pierce);
      copyStat(ownerAttrs, base, Stat.item_pierce_cold);
      copyStat(ownerAttrs, base, Stat.passive_cold_pierce);
      copyStat(ownerAttrs, base, Stat.item_pierce_pois);
      copyStat(ownerAttrs, base, Stat.passive_pois_pierce);
      copyStat(ownerAttrs, base, Stat.passive_mag_pierce);
    }
    base.put(Stat.mindamage, physicalMin);
    base.put(Stat.maxdamage, physicalMax);
    base.put(Stat.tohit, Math.max(0, attackRating));
    putPair(base, Stat.firemindam, Stat.firemaxdam, elementalMin[FIRE], elementalMax[FIRE]);
    putPair(base, Stat.lightmindam, Stat.lightmaxdam,
        elementalMin[LIGHTNING], elementalMax[LIGHTNING]);
    putPair(base, Stat.coldmindam, Stat.coldmaxdam, elementalMin[COLD], elementalMax[COLD]);
    putPair(base, Stat.poisonmindam, Stat.poisonmaxdam,
        elementalMin[POISON], elementalMax[POISON]);
    putPair(base, Stat.magicmindam, Stat.magicmaxdam, elementalMin[MAGIC], elementalMax[MAGIC]);
    if (coldLength > 0) base.put(Stat.coldlength, coldLength);
    if (poisonLength > 0) base.put(Stat.poisonlength, poisonLength);
    if (copySourceBonuses) {
      copyStat(ownerAttrs, base, Stat.damagepercent);
      copyStat(ownerAttrs, base, Stat.item_tohit_percent);
      copyStat(ownerAttrs, base, Stat.item_deadlystrike);
      copyStat(ownerAttrs, base, Stat.passive_critical_strike);
      copyStat(ownerAttrs, base, Stat.item_crushingblow);
      copyStat(ownerAttrs, base, Stat.lifedrainmindam);
      copyStat(ownerAttrs, base, Stat.manadrainmindam);
      copyStat(ownerAttrs, base, Stat.item_ignoretargetac);
    }
    projectile.damage.reset();
    projectile.damageSnapshot = true;
    projectile.attackMinDamage = physicalMin;
    projectile.attackMaxDamage = physicalMax;
    projectile.attackRating = Math.max(0, attackRating);
  }

  private static void addOwnerElemental(Attributes attrs, int scale,
      int[] min, int[] max) {
    addScaledPair(attrs, Stat.firemindam, Stat.firemaxdam, scale, min, max, FIRE);
    addScaledPair(attrs, Stat.lightmindam, Stat.lightmaxdam, scale, min, max, LIGHTNING);
    addScaledPair(attrs, Stat.coldmindam, Stat.coldmaxdam, scale, min, max, COLD);
    addScaledPair(attrs, Stat.poisonmindam, Stat.poisonmaxdam, scale, min, max, POISON);
    addScaledPair(attrs, Stat.magicmindam, Stat.magicmaxdam, scale, min, max, MAGIC);
  }

  private static void addMonsterElemental(MonStats.Entry monster, int mode,
      int level, int difficulty, int sourceScale, int[] min, int[] max,
      int[] duration) {
    String[] modes = {monster.El1Mode, monster.El2Mode, monster.El3Mode};
    String[] types = {monster.El1Type, monster.El2Type, monster.El3Type};
    int[][] chances = {monster.El1Pct, monster.El2Pct, monster.El3Pct};
    for (int i = 0; i < modes.length; i++) {
      if (!hasText(modes[i]) || Riiablo.files.MonMode.index(modes[i]) != mode) continue;
      int chance = arrayValue(chances[i], difficulty);
      if (chance <= 0 || (chance < 100 && MathUtils.random(99) >= chance)) continue;
      MonsterStatsCalculator.MonsterStatsInit stats =
          new MonsterStatsCalculator.MonsterStatsInit();
      if (!MonsterStatsCalculator.calculateMonsterStatsByLevel(monster.hcIdx, 1,
          difficulty, level, (short) (0x40 << i), stats)) continue;
      int type = damageType(types[i]);
      if (type <= PHYSICAL) continue;
      int elementMin = stats.ElMinD;
      int elementMax = stats.ElMaxD;
      int elementLength = stats.ElDur;
      if (type == POISON) {
        // MonsterMode.cpp stores monster poison as per-frame poison stats.
        elementMin *= 10;
        elementMax *= 10;
        elementLength *= 2;
      }
      min[type] += scaleSource(elementMin, sourceScale);
      max[type] += scaleSource(elementMax, sourceScale);
      if (type == COLD) duration[0] = Math.max(duration[0],
          scaleSource(elementLength, sourceScale));
      if (type == POISON) duration[1] = Math.max(duration[1],
          scaleSource(elementLength, sourceScale));
    }
  }

  private static int resolveAttackMode(MonStats.Entry monster, Missiles.Entry missile,
      int currentMode) {
    if (currentMode >= 0) return currentMode;
    if (hasText(monster.MissA2) && monster.MissA2.equalsIgnoreCase(missile.Missile)) {
      return Riiablo.files.MonMode.index("A2");
    }
    return Riiablo.files.MonMode.index("A1");
  }

  static int damageBonusByLevel(int level, int[] values) {
    if (level <= 1 || values == null || values.length == 0) return 0;
    int l1 = arrayValue(values, 0);
    int l2 = arrayValue(values, 1);
    int l3 = arrayValue(values, 2);
    int l4 = arrayValue(values, 3);
    int l5 = arrayValue(values, 4);
    if (level > 28) return 7 * l1 + 8 * l2 + 6 * (l3 + l4) + (level - 28) * l5;
    if (level > 22) return 7 * l1 + 8 * l2 + 6 * l3 + (level - 22) * l4;
    if (level > 16) return 7 * l1 + 8 * l2 + (level - 16) * l3;
    if (level > 8) return 7 * l1 + (level - 8) * l2;
    return (level - 1) * l1;
  }

  private static int shiftedDamage(int base, int[] perLevel, int level, int hitShift) {
    long value = Math.max(0L, (long) base + damageBonusByLevel(level, perLevel));
    int shift = hitShift - 8;
    if (shift > 0) value <<= Math.min(shift, 30);
    else if (shift < 0) value >>= Math.min(-shift, 30);
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }

  private static int skillElementalDamageFixed(Skills.Entry skill, int level,
      boolean minimum, ToIntFunction<String> baseSkillLevel) {
    int base = minimum ? skill.EMin : skill.EMax;
    int[] perLevel = minimum ? skill.EMinLev : skill.EMaxLev;
    long damage = Math.max(0L, (long) base + damageBonusByLevel(level, perLevel));
    damage <<= Math.max(0, Math.min(30, skill.HitShift));
    boolean applySynergy = !minimum || damage > 256L || arrayValue(perLevel, 0) != 0;
    if (applySynergy) {
      int synergy = Math.max(0, SkillFormula.evaluate(
          skill.EDmgSymPerCalc, skill, level,
          baseSkillLevel == null ? name -> 0 : baseSkillLevel));
      damage += damage * synergy / 100L;
    }
    return damage >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) damage;
  }

  private static int percentage(int value, int percent) {
    long result = (long) Math.max(0, value) * Math.max(0, percent) / 100L;
    return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
  }

  private static int elementalLength(Missiles.Entry row, int level) {
    if (level <= 1) return Math.max(0, row.ELen);
    int l1 = arrayValue(row.ELevLen, 0);
    int l2 = arrayValue(row.ELevLen, 1);
    int l3 = arrayValue(row.ELevLen, 2);
    if (level > 16) return Math.max(0, row.ELen + 7 * l1 + 8 * l2 + (level - 16) * l3);
    if (level > 8) return Math.max(0, row.ELen + 7 * l1 + (level - 8) * l2);
    return Math.max(0, row.ELen + (level - 1) * l1);
  }

  private static int damageType(String type) {
    if (!hasText(type)) return PHYSICAL;
    if ("fire".equalsIgnoreCase(type)) return FIRE;
    if ("ltng".equalsIgnoreCase(type) || "lightning".equalsIgnoreCase(type)) return LIGHTNING;
    if ("cold".equalsIgnoreCase(type) || "freeze".equalsIgnoreCase(type)
        || "frze".equalsIgnoreCase(type)) return COLD;
    if ("pois".equalsIgnoreCase(type) || "poison".equalsIgnoreCase(type)) return POISON;
    if ("mag".equalsIgnoreCase(type) || "magic".equalsIgnoreCase(type)) return MAGIC;
    return PHYSICAL;
  }

  private static void putPair(StatListRef base, short minStat, short maxStat,
      int min, int max) {
    if (max <= 0) return;
    base.put(minStat, Math.max(0, min));
    base.put(maxStat, Math.max(min, max));
  }

  private static void addScaledPair(Attributes attrs, short minStat, short maxStat,
      int scale, int[] min, int[] max, int type) {
    min[type] += scaleSource(statInt(attrs, minStat), scale);
    max[type] += scaleSource(statInt(attrs, maxStat), scale);
  }

  private static void copyStat(Attributes source, StatListRef target, short stat) {
    int value = statInt(source, stat);
    if (value != 0) target.put(stat, value);
  }

  private static int statInt(Attributes attrs, short stat) {
    if (attrs == null) return 0;
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref != null ? ref.asInt() : 0;
  }

  private static int scaleSource(int value, int scale) {
    return (int) ((long) value * scale / 128L);
  }

  private static int arrayValue(int[] values, int index) {
    return values != null && index >= 0 && index < values.length ? values[index] : 0;
  }

  private static int damageConversionPercent(Missiles.Entry missile, int level) {
    int base = arrayValue(missile != null ? missile.dParam : null, 0);
    int perLevel = arrayValue(missile != null ? missile.dParam : null, 1);
    // The legacy bin reader used by some installations does not expose the
    // dParam columns, although DmgCalc1 survives. Native dl12 means
    // dParam1 + level * dParam2; all three Amazon conversion arrows use 1,1.
    if (base == 0 && perLevel == 0 && missile != null
        && "dl12".equalsIgnoreCase(missile.DmgCalc1)) {
      base = 1;
      perLevel = 1;
    }
    return base + Math.max(1, level) * perLevel;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
