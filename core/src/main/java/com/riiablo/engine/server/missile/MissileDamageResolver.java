package com.riiablo.engine.server.missile;

import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.MonsterStatsCalculator;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
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
    if ("cold".equalsIgnoreCase(type) || "freeze".equalsIgnoreCase(type)) return COLD;
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

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
