package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Executes complete attacks without core, ECS, MPQs or global RNG. */
class NativeAttackResolverTest {
  @Test
  void snapshotsOwnTheirArraysAndContainNoRuntimeReferences() {
    for (Class<?> type : new Class<?>[] {NativeAttackData.class, NativeDefenseData.class}) {
      for (java.lang.reflect.Field field : type.getFields()) {
        assertTrue(field.getType().isPrimitive() || field.getType() == int[].class
            || field.getType() == boolean[].class
            || field.getType() == NativeMissilePhysicalDamage.class, field.getName());
      }
    }
    // The sole nested value is immutable and scalar-only, never an Attributes,
    // ECS, state-list or shared mutable runtime reference.
    assertTrue(java.lang.reflect.Modifier.isFinal(NativeMissilePhysicalDamage.class.getModifiers()));
    for (java.lang.reflect.Field field : NativeMissilePhysicalDamage.class.getDeclaredFields()) {
      assertTrue(field.getType().isPrimitive(), field.getName());
      assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()), field.getName());
    }
    NativeAttackData a = new NativeAttackData(), b = new NativeAttackData();
    assertNotSame(a.elementalMinDamage, b.elementalMinDamage);
    assertNotSame(a.elementalMaxDamage, b.elementalMaxDamage);
    assertNotSame(a.elementalPierce, b.elementalPierce);
    NativeDefenseData d = new NativeDefenseData(), e = new NativeDefenseData();
    assertNotSame(d.resistances, e.resistances);
    assertNotSame(d.maxResistances, e.maxResistances);
    assertNotSame(d.immuneElemental, e.immuneElemental);
    assertNotSame(d.absorbPercent, e.absorbPercent);
    assertNotSame(d.absorbFlat, e.absorbFlat);
  }

  @Test
  void missStopsBeforeDefenseAndDamage() {
    List<Integer> bounds = new ArrayList<>();
    NativeAttackResolver resolver = new NativeAttackResolver(new NativeCombatRolls(bound -> {
      bounds.add(bound);
      return 99;
    }));
    NativeAttackData a = new NativeAttackData();
    a.criticalStrike = a.deadlyStrike = a.crushingBlow = 100;
    NativeDefenseData d = new NativeDefenseData();
    d.canBlock = true;
    d.blockChance = 75;
    NativeDamagePacket result = resolver.resolve(a, d);
    assertFalse(result.hit);
    assertFalse(result.blocked);
    assertEquals(0, result.totalDamage);
    assertEquals(Arrays.asList(100), bounds);
  }

  @Test
  void shieldPrecedesGuaranteedPassivesAndStopsDamage() {
    List<Integer> bounds = new ArrayList<>();
    NativeAttackResolver resolver = zeroRolls(bounds);
    NativeAttackData a = guaranteedAttack(10);
    a.criticalStrike = a.deadlyStrike = 100;
    NativeDefenseData d = new NativeDefenseData();
    d.canBlock = true;
    d.blockChance = 75;
    d.passiveWeaponBlock = d.passiveDodge = 100;
    d.currentFrame = 16;
    NativeDamagePacket result = resolver.resolve(a, d);
    assertTrue(result.hit && result.blocked && result.blockAnimationAllowed);
    assertEquals(DEFENSE_BLOCK, result.defenseType);
    assertEquals(16, d.lastBlockFrame);
    assertFalse(result.critical);
    assertEquals(0, result.totalDamage);
    assertEquals(Arrays.asList(100, 100), bounds);
  }

  @Test
  void elementalMissileBypassesShieldButStillChecksMovingEvade() {
    List<Integer> bounds = new ArrayList<>();
    NativeAttackResolver resolver = zeroRolls(bounds);
    NativeAttackData a = guaranteedAttack(0);
    a.isMissile = true;
    a.elementalMinDamage[DAMAGE_FIRE] = a.elementalMaxDamage[DAMAGE_FIRE] = 10;
    NativeDefenseData d = new NativeDefenseData();
    d.canBlock = true;
    d.blockChance = 75;
    d.isMoving = true;
    d.passiveEvade = d.passiveWeaponBlock = 100;
    NativeDamagePacket result = resolver.resolve(a, d);
    assertEquals(DEFENSE_EVADE, result.defenseType);
    assertTrue(result.blocked);
    assertFalse(result.blockAnimationAllowed);
    assertEquals(0, result.totalDamage);
    assertEquals(Arrays.asList(100), bounds);
  }

  @Test
  void criticalSkipsDeadlyThenCrushingIsAddedBeforeConversion() {
    List<Integer> bounds = new ArrayList<>();
    NativeAttackData a = guaranteedAttack(10);
    a.criticalStrike = a.deadlyStrike = a.crushingBlow = 100;
    a.physicalConversionPercent = 50;
    a.physicalConversionType = DAMAGE_MAGIC;
    a.lifeLeech = a.manaLeech = 10;
    NativeDefenseData d = new NativeDefenseData();
    d.currentLife = 80;
    NativeDamagePacket result = zeroRolls(bounds).resolve(a, d);
    assertTrue(result.critical && result.crushingBlow);
    assertFalse(result.deadlyStrike);
    assertEquals(20, result.physicalDamage);
    assertEquals(20, result.elementalDamage[DAMAGE_MAGIC]);
    assertEquals(40, result.totalDamage);
    assertEquals(2, result.lifeStolen);
    assertEquals(2, result.manaStolen);
    assertEquals(Arrays.asList(100, 1, 100, 100), bounds);
  }

  @Test
  void mixedPvpPacketKeepsNativeLightningSeparateAndPoisonDeferred() {
    NativeAttackData a = guaranteedAttack(100);
    a.isPlayer = true;
    a.isMissile = true;
    a.fixedPointElements = true;
    a.physicalConversionPercent = 50;
    a.physicalConversionType = DAMAGE_MAGIC;
    a.magicTargetBonusPercent = 100;
    a.poisonLength = 20;
    a.elementalMinDamage[DAMAGE_FIRE] = a.elementalMaxDamage[DAMAGE_FIRE] = 20;
    a.elementalMinDamage[DAMAGE_LIGHTNING] = a.elementalMaxDamage[DAMAGE_LIGHTNING] = 40;
    a.elementalMinDamage[DAMAGE_POISON] = a.elementalMaxDamage[DAMAGE_POISON] = 10;
    NativeDefenseData d = new NativeDefenseData();
    d.isPlayer = true;
    d.magicDamageReduced = 2;
    NativeDamagePacket result = zeroRolls(new ArrayList<>()).resolve(a, d);
    assertEquals(8, result.physicalDamage);
    assertEquals(783, result.elementalDamage[DAMAGE_FIRE]); // (20 - 2) * 256 * 17 / 100
    assertEquals(1228, result.elementalDamage[DAMAGE_LIGHTNING]); // 40 * 256 * 17 / 100 - 2 * 256
    assertEquals(4264, result.elementalDamage[DAMAGE_MAGIC]); // (100 - 2) * 256 * 17 / 100
    assertEquals(435, result.elementalDamage[DAMAGE_POISON]); // poison does not pay MDR
    assertEquals(31, result.totalDamage); // per-channel integer truncation, no immediate poison
    assertEquals(8 + (783 + 1228 + 4264) / 256f, result.totalDamageHp());
  }

  @Test
  void deferredColdScalingAndPoisonDoNotChangeImmediateHitProjection() {
    NativeAttackData a = guaranteedAttack(1);
    a.coldLength = 81;
    a.difficulty = 2;
    a.poisonLength = 20;
    a.elementalMinDamage[DAMAGE_POISON] = a.elementalMaxDamage[DAMAGE_POISON] = 10;
    NativeDefenseData d = new NativeDefenseData();
    d.isMonster = true;
    NativeAttackResolver resolver = zeroRolls(new ArrayList<>());
    NativeDamagePacket legacy = resolver.resolve(a, d);
    assertEquals(20, legacy.coldDuration);
    assertEquals(1, legacy.totalDamage);
    a.stateDurationsDeferred = true;
    assertEquals(81, resolver.resolve(a, d).coldDuration);
    d.halfFreezeDuration = true;
    assertEquals(40, resolver.resolve(a, d).coldDuration);
    d.cannotBeFrozen = true;
    assertEquals(0, resolver.resolve(a, d).coldDuration);
  }

  @Test
  void outputReuseClearsEarlierDamageAndFlagsBeforeAnEarlyReturn() {
    NativeAttackResolver resolver = zeroRolls(new ArrayList<>());
    NativeAttackData a = guaranteedAttack(10);
    a.criticalStrike = 100;
    NativeDefenseData d = new NativeDefenseData();
    NativeDamagePacket out = new NativeDamagePacket();
    assertSame(out, resolver.resolve(a, d, out, null));
    assertTrue(out.critical);
    d.passiveWeaponBlock = 100;
    resolver.resolve(a, d, out, null);
    assertTrue(out.blocked);
    assertFalse(out.critical);
    assertEquals(0, out.physicalDamage);
    assertEquals(0, out.totalDamage);
  }

  @Test
  void blockFrameGateUsesStrictIntervalAndLongSubtraction() {
    NativeDefenseData d = new NativeDefenseData();
    d.fasterBlockRate = 80;
    d.currentFrame = 25;
    assertFalse(NativeAttackResolver.advanceBlockAnimation(d));
    assertEquals(0, d.lastBlockFrame);
    d.currentFrame = 26;
    assertTrue(NativeAttackResolver.advanceBlockAnimation(d));
    assertEquals(26, d.lastBlockFrame);
    d.lastBlockFrame = Integer.MIN_VALUE;
    d.currentFrame = Integer.MAX_VALUE;
    assertTrue(NativeAttackResolver.advanceBlockAnimation(d));
    assertEquals(Integer.MAX_VALUE, d.lastBlockFrame);
  }

  private static NativeAttackData guaranteedAttack(int damage) {
    NativeAttackData a = new NativeAttackData();
    a.alwaysHit = true;
    a.minDamage = a.maxDamage = damage;
    return a;
  }

  private static NativeAttackResolver zeroRolls(List<Integer> bounds) {
    return new NativeAttackResolver(new NativeCombatRolls(bound -> {
      bounds.add(bound);
      return 0;
    }));
  }
}
