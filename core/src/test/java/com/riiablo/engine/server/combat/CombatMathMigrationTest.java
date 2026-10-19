package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.riiablo.engine.server.combat.CombatSystem.*;
import com.riiablo.engine.server.NativeRng;
import org.junit.jupiter.api.Test;

/** Migration oracle frozen from b0ff5c994, not a second native-mechanics specification. */
class CombatMathMigrationTest {
  @Test
  void defenseCalculatorUsesTheSameScalarBlockKernelWithoutDrawingRandomness() {
    DefenseCalculator defense = new DefenseCalculator(bound -> {
      throw new AssertionError("Block chance calculation must not draw random values");
    });
    for (int base : new int[] {-5, 0, 1, 40, 75, 150}) {
      for (int dex : new int[] {0, 14, 15, 16, 100, 315}) {
        for (int level : new int[] {0, 1, 50, 99}) {
          int previous = base <= 0 ? 0 : Math.max(0, Math.min(75,
              base * Math.max(0, dex - 15) / (Math.max(level, 1) * 2)));
          assertEquals(previous, defense.calculateBlockChance(base, dex, level));
        }
      }
    }
  }

  @Test
  void scalarKernelMatchesPreviousProductionArithmeticAcrossSeededSnapshots() {
    java.util.Random inputs = new java.util.Random(0x110F);
    CombatSystem host = new CombatSystem(new NativeRng(17));
    for (int i = 0; i < 2000; i++) {
      AttackerData a = new AttackerData();
      DefenderData d = new DefenderData();
      a.attackRating = inputs.nextInt(2001) - 1000;
      a.isPlayer = inputs.nextBoolean();
      a.dexterity = inputs.nextInt(200);
      a.attackRatingPercent = inputs.nextInt(301) - 150;
      a.demonToHit = inputs.nextInt(100);
      a.undeadToHit = inputs.nextInt(100);
      a.level = inputs.nextInt(101) - 1;
      a.isMissile = inputs.nextBoolean();
      a.ignoreTargetDefense = inputs.nextBoolean();
      a.lifeLeech = inputs.nextInt(100) - 10;
      a.manaLeech = inputs.nextInt(100) - 10;
      d.isDemon = inputs.nextBoolean();
      d.isUndead = inputs.nextBoolean();
      d.isPlayer = inputs.nextBoolean();
      d.ignoreTargetDefenseAllowed = inputs.nextBoolean();
      d.defense = inputs.nextInt(2001) - 1000;
      d.defenseVsMissile = inputs.nextInt(501) - 250;
      d.defenseVsMelee = inputs.nextInt(501) - 250;
      d.level = inputs.nextInt(101) - 1;
      d.canBlock = inputs.nextBoolean();
      d.hasEquippedShield = inputs.nextBoolean();
      d.blockFactor = inputs.nextInt(100);
      d.blockChance = inputs.nextInt(100);
      d.dexterity = inputs.nextInt(500);
      d.isRunning = inputs.nextBoolean();
      d.immunePhysical = inputs.nextBoolean();
      d.damageReducedPercent = inputs.nextInt(251) - 100;
      d.damageReduced = inputs.nextInt(100);
      d.magicDamageReduced = inputs.nextInt(100);
      int damage = inputs.nextInt(100001);
      int scale = inputs.nextBoolean() ? 1 : 256;
      int pierce = inputs.nextInt(301) - 100;
      int type = inputs.nextInt(DAMAGE_TYPE_COUNT);
      d.immuneElemental[type] = inputs.nextBoolean();
      d.resistances[type] = inputs.nextInt(401) - 200;
      d.maxResistances[type] = inputs.nextInt(126) - 5;
      d.absorbPercent[type] = inputs.nextInt(151) - 25;
      d.absorbFlat[type] = inputs.nextInt(100) - 25;
      int rating = Previous.calculateEffectiveAttackRating(a, d);
      int defenseValue = Previous.calculateEffectiveDefense(a, d);
      if (a.ignoreTargetDefense && a.isPlayer && !d.isPlayer && d.ignoreTargetDefenseAllowed) defenseValue = 0;
      if (rating + defenseValue == 0) {
        // Intentional native correction: old 5% fallback is NOT the oracle here.
        // Keep the frozen implementation unchanged for all other snapshots.
        int alvl = Math.max(1, a.level), dlvl = Math.max(1, d.level);
        assertEquals(Math.max(5, Math.min(95, 200 * alvl / (alvl + dlvl))),
            host.calculateHitChance(a, d), "native zero divisor " + i);
      } else {
        assertEquals(Previous.calculateHitChance(a, d), host.calculateHitChance(a, d), "hit snapshot " + i);
      }
      assertEquals(Previous.calculateBlockChance(d), host.calculateBlockChance(d), "block snapshot " + i);
      assertEquals(Previous.applyPhysicalDamageReduction(damage, d, false),
          NativeCombatMath.physicalReduction(damage, d.immunePhysical, d.damageReducedPercent, d.damageReduced, false));
      assertEquals(Previous.applyPhysicalDamageReduction(damage, d, true),
          NativeCombatMath.physicalReduction(damage, d.immunePhysical, d.damageReducedPercent, d.damageReduced, true));
      int maximum = d.maxResistances[type] > 0 ? d.maxResistances[type] : 75;
      assertEquals(Previous.applyElementalResistance(damage, d, type, pierce, scale),
          NativeCombatMath.legacyElementalResistance(damage, d.immuneElemental[type], d.resistances[type],
              maximum, pierce, type != DAMAGE_POISON && type != DAMAGE_PHYSICAL, d.magicDamageReduced, scale));
      assertEquals(Previous.absorbElementalDamage(damage, d, type, scale),
          NativeCombatMath.elementalAbsorb(damage, d.absorbPercent[type], d.absorbFlat[type], scale));
      assertEquals(Previous.calculateLifeLeech(damage, a), NativeCombatMath.leech(damage, a.lifeLeech));
      assertEquals(Previous.calculateManaLeech(damage, a), NativeCombatMath.leech(damage, a.manaLeech));
    }
  }

  @Test
  void correctedBonusPacketKeepsRngStateAtTheHostBoundary() {
    NativeRng rng = new NativeRng(17);
    NativeRng expected = new NativeRng(17);
    CombatSystem host = new CombatSystem(rng);
    AttackerData a = new AttackerData();
    a.alwaysHit = true;
    a.level = 10;
    a.isPlayer = true;
    a.minDamage = 10;
    a.maxDamage = 20;
    a.strength = 50;
    a.enhancedDamagePercent = 25;
    a.lifeLeech = 10;
    a.manaLeech = 5;
    a.elementalMinDamage[DAMAGE_FIRE] = a.elementalMaxDamage[DAMAGE_FIRE] = 100;
    DefenderData d = new DefenderData();
    d.level = 10;
    d.damageReducedPercent = -50;
    d.damageReduced = 2;
    d.resistances[DAMAGE_FIRE] = 50;
    d.magicDamageReduced = 10;
    d.absorbPercent[DAMAGE_FIRE] = 25;
    d.absorbFlat[DAMAGE_FIRE] = 3;
    expected.nextInt(100); // Existing rollHit consumes a draw even with alwaysHit.
    int rolled = 10 + expected.nextInt(11);
    // Strength 50 + ED 25 are additive; deliberately not the frozen two-stage product.
    int base = rolled + rolled * 75 / 100;
    // Deliberate native correction: flat DR before resistance, no minimum 1.
    int physical = Math.max(0, base - 2) * 150 / 100;
    expected.nextInt(1); // Existing inclusive fixed-range fire roll also advances the stream.
    CombatResult result = host.calculateAttack(a, d);
    assertEquals(physical, result.physicalDamage);
    assertEquals(27, result.elementalDamage[DAMAGE_FIRE]); // (100 * 50% - 10) - (25% + 3).
    assertEquals(13, result.absorbedLife);
    assertEquals(physical + 27, result.totalDamage);
    assertEquals(physical / 10, result.lifeStolen);
    assertEquals(physical * 5 / 100, result.manaStolen);
    assertEquals(expected.state(), rng.state());
  }

  private static class Previous {
    static int calculateHitChance(AttackerData attacker, DefenderData defender) {
    // 计算有效攻击等级
    int attackRating = calculateEffectiveAttackRating(attacker, defender);

    // 计算有效防御等级
    int defense = calculateEffectiveDefense(attacker, defender);

    // 如果无视防御
    if (attacker.ignoreTargetDefense && attacker.isPlayer && !defender.isPlayer
        && defender.ignoreTargetDefenseAllowed) {
      defense = 0;
    }

    // 计算命中率
    int hitChance;
    if (attackRating + defense == 0) {
      hitChance = BASE_TO_HIT_CHANCE;
    } else {
      int attackerLevel = Math.max(1, attacker.level);
      int defenderLevel = Math.max(1, defender.level);
      int toHitFactor = 100 * attackRating / (attackRating + defense);
      hitChance = PVP_HIT_FACTOR * attackerLevel * toHitFactor
          / (attackerLevel + defenderLevel);
    }

    // 限制范围
    hitChance = Math.max(MIN_TO_HIT_CHANCE, Math.min(MAX_TO_HIT_CHANCE, hitChance));

    return hitChance;
  }

    static int calculateEffectiveAttackRating(AttackerData attacker, DefenderData defender) {
    int ar = attacker.attackRating;

    // 敏捷加成（怪物：5 * dex）
    if (!attacker.isPlayer) {
      ar += 5 * attacker.dexterity;
    }

    // 攻击等级百分比加成
    if (attacker.attackRatingPercent != 0) {
      ar = ar * (100 + attacker.attackRatingPercent) / 100;
    }

    // 对特殊怪物类型加成
    if (defender.isDemon && attacker.demonToHit > 0) {
      ar += attacker.demonToHit;
    }
    if (defender.isUndead && attacker.undeadToHit > 0) {
      ar += attacker.undeadToHit;
    }

    return Math.max(0, ar);
  }

    static int calculateEffectiveDefense(AttackerData attacker, DefenderData defender) {
    int defense;

    // 根据攻击类型选择防御值
    if (attacker.isMissile) {
      defense = defender.defense + defender.defenseVsMissile;
    } else {
      defense = defender.defense + defender.defenseVsMelee;
    }

    return Math.max(0, defense);
  }

    static int calculateBlockChance(DefenderData defender) {
    if (!defender.canBlock || (defender.isPlayer && !defender.hasEquippedShield)) {
      return 0;
    }

    int blockChance = defender.isPlayer
        ? defender.blockFactor + defender.blockChance : defender.blockChance;

    // D2Common UNITS_GetBlockRate applies the expansion player formula to
    // the combined class+shield block value. Monster ToBlock is already the
    // final chance and must not be scaled by player dexterity.
    if (defender.isPlayer) {
      int dexterityFactor = Math.max(0, defender.dexterity - 15);
      blockChance = blockChance * dexterityFactor
          / (BLOCK_DEXTERITY_FACTOR * Math.max(1, defender.level));
    }

    // UNITS_GetBlockRate caps at 75 before SUNITDMG_ApplyBlockOrDodge
    // applies the running penalty, so running block can never exceed 25%.
    blockChance = Math.max(MIN_BLOCK_CHANCE, Math.min(MAX_BLOCK_CHANCE, blockChance));
    if (defender.isPlayer && defender.isRunning) blockChance /= 3;

    return blockChance;
  }

    static int applyPhysicalDamageReduction(
      int damage, DefenderData defender, boolean bypassResistance) {
    if (damage <= 0) return 0;
    if (!bypassResistance && defender.immunePhysical) {
      return 0;
    }

    // 百分比减免
    if (!bypassResistance && defender.damageReducedPercent != 0) {
      damage = damage * (100 - defender.damageReducedPercent) / 100;
    }

    // 固定减免
    damage -= defender.damageReduced;

    return Math.max(1, damage);
  }

    static int applyElementalResistance(int damage, DefenderData defender, int damageType,
      int piercePercent, int scale) {
    if (defender.immuneElemental[damageType]) {
      return 0;
    }

    int resistance = defender.resistances[damageType]
        - Math.max(0, Math.min(100, piercePercent));

    // 抗性可以为负（增加伤害）
    // 抗性上限为 75%（或更高，如果有装备加成）
    int maxResist = defender.maxResistances != null
        && damageType < defender.maxResistances.length
        && defender.maxResistances[damageType] > 0
        ? defender.maxResistances[damageType] : 75;
    maxResist = Math.min(ABSOLUTE_MAX_RESISTANCE, maxResist);
    resistance = Math.max(MIN_RESISTANCE, resistance);
    resistance = Math.min(maxResist, resistance);

    damage = (int) Math.min(Integer.MAX_VALUE, (long) damage * (100 - resistance) / 100);

    // Preserve the legacy attack ordering here. Native MDR precedes
    // resistance; migrating that rule is separate from the precision opt-in.
    if (damageType != DAMAGE_POISON && damageType != DAMAGE_PHYSICAL) {
      damage = (int) Math.max(0L, damage - (long) defender.magicDamageReduced * scale);
    }

    return Math.max(0, damage);
  }

    static int absorbElementalDamage(int damage, DefenderData defender, int damageType, int scale) {
    if (damage <= 0 || defender.absorbPercent == null || damageType >= defender.absorbPercent.length) {
      return 0;
    }
    int percent = Math.max(0, Math.min(100, defender.absorbPercent[damageType]));
    int flat = defender.absorbFlat != null && damageType < defender.absorbFlat.length
        ? (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) defender.absorbFlat[damageType] * scale)) : 0;
    int absorbed = (int) ((long) damage * percent / 100);
    absorbed = Math.min(damage, absorbed + Math.min(flat, damage - absorbed));
    return absorbed;
  }

    static int calculateLifeLeech(int physicalDamage, AttackerData attacker) {
    if (attacker.lifeLeech <= 0) {
      return 0;
    }
    return physicalDamage * attacker.lifeLeech / 100;
  }

    static int calculateManaLeech(int physicalDamage, AttackerData attacker) {
    if (attacker.manaLeech <= 0) {
      return 0;
    }
    return physicalDamage * attacker.manaLeech / 100;
  }

  }
}
