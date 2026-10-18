package com.riiablo.engine.server.combat;

import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NativeSkillAttackProfileTest {
  @Test
  void missingItemMaximumKeepsEachRecipesDifferentFallback() {
    NativeSkillAttackProfile.Source source = new NativeSkillAttackProfile.Source();
    source.itemMin[DAMAGE_FIRE] = 10;
    int[] skillMin = new int[DAMAGE_TYPE_COUNT], skillMax = new int[DAMAGE_TYPE_COUNT];
    skillMin[DAMAGE_FIRE] = 2;
    skillMax[DAMAGE_FIRE] = 20;
    NativeSkillAttackProfile melee = NativeSkillAttackProfile.meleeElements(source,
        1, 2, 3, skillMin, skillMax, 0, 0, 0, DAMAGE_PHYSICAL);
    assertEquals(12, melee.elementalMin[DAMAGE_FIRE]);
    assertEquals(20, melee.elementalMax[DAMAGE_FIRE]);
    NativeSkillAttackProfile lightning = NativeSkillAttackProfile.lightningStrike(source, 0, 0, 0, 0, 0);
    assertEquals(10, lightning.elementalMin[DAMAGE_FIRE]);
    assertEquals(10, lightning.elementalMax[DAMAGE_FIRE]);
    NativeSkillAttackProfile blade = NativeSkillAttackProfile.bladeShield(source, 0, 0, 64, 0, false);
    assertEquals(5, blade.elementalMin[DAMAGE_FIRE]);
    assertEquals(5, blade.elementalMax[DAMAGE_FIRE]);
  }

  @Test
  void venomAndStateOverlaysAreNotUnifiedAcrossRecipes() {
    NativeSkillAttackProfile.Source source = new NativeSkillAttackProfile.Source();
    source.itemMin[DAMAGE_POISON] = 2;
    source.itemMax[DAMAGE_POISON] = 4;
    source.itemMaxPresent[DAMAGE_POISON] = true;
    source.stateMin[DAMAGE_POISON] = 100;
    source.stateMax[DAMAGE_POISON] = 200;
    source.venomActive = true;
    source.venomMin = 8;
    source.venomMax = 10;
    source.venomLength = 12;
    source.poisonLength = 90;
    NativeSkillAttackProfile melee = NativeSkillAttackProfile.meleeElements(source,
        1, 2, 3, null, null, 0, 30, 0, DAMAGE_PHYSICAL);
    assertEquals(10, melee.elementalMin[DAMAGE_POISON]);
    assertEquals(14, melee.elementalMax[DAMAGE_POISON]);
    assertEquals(30, melee.poisonLength); // explicit skill length wins in this recipe
    NativeSkillAttackProfile fallback = NativeSkillAttackProfile.meleeElements(source,
        1, 2, 3, null, null, 0, 0, 0, DAMAGE_PHYSICAL);
    assertEquals(12, fallback.poisonLength);
    NativeSkillAttackProfile lightning = NativeSkillAttackProfile.lightningStrike(source, 0, 0, 0, 0, 0);
    assertEquals(102, lightning.elementalMin[DAMAGE_POISON]);
    assertEquals(204, lightning.elementalMax[DAMAGE_POISON]);
    assertEquals(90, lightning.poisonLength);
    NativeSkillAttackProfile blade = NativeSkillAttackProfile.bladeShield(source, 0, 0, 64, 0, false);
    assertEquals(5, blade.elementalMin[DAMAGE_POISON]);
    assertEquals(7, blade.elementalMax[DAMAGE_POISON]);
    assertEquals(12, blade.poisonLength); // not SrcDam-scaled
  }

  @Test
  void bladeScalesCompletedSkillAndWeaponBeforeIntegerTruncation() {
    NativeSkillAttackProfile.Source source = new NativeSkillAttackProfile.Source();
    source.weaponMin = 11;
    source.weaponMax = 17;
    source.attackRating = 101;
    source.coldLength = 81;
    NativeSkillAttackProfile blade = NativeSkillAttackProfile.bladeShield(source, 7, 13, 64, 25, true);
    assertEquals(9, blade.physicalMin);
    assertEquals(15, blade.physicalMax);
    assertEquals(126, blade.attackRating);
    assertEquals(40, blade.coldLength);
    assertTrue(blade.alwaysHit && blade.precomputedPhysical);
  }

  @Test
  void precomputedKeepsRawPhysicalAndConversionValuesForCommonSnapshotAdapter() {
    NativeSkillAttackProfile profile = NativeSkillAttackProfile.precomputed(-2, -1, 0, 150, DAMAGE_MAGIC);
    assertEquals(-2, profile.physicalMin);
    assertEquals(-1, profile.physicalMax);
    assertEquals(150, profile.conversionPercent);
    assertNull(profile.elementalMin);
    assertNull(profile.elementalMax);
    assertTrue(profile.precomputedPhysical);
    NativeSkillAttackProfile lightning = NativeSkillAttackProfile.lightningStrike(
        new NativeSkillAttackProfile.Source(), -2, -1, -5, 150, DAMAGE_MAGIC);
    assertEquals(0, lightning.enhancedDamageBonus);
    assertEquals(100, lightning.conversionPercent);
    assertFalse(lightning.precomputedPhysical);
  }

  @Test
  void shortSkillArraysUseSkillMinimumFallbackWithoutAliasing() {
    NativeSkillAttackProfile.Source source = new NativeSkillAttackProfile.Source();
    int[] min = {0, 9, 7};
    int[] max = {0};
    NativeSkillAttackProfile profile = NativeSkillAttackProfile.meleeElements(
        source, 1, 2, 3, min, max, 0, 0, 0, DAMAGE_PHYSICAL);
    min[DAMAGE_FIRE] = 999;
    source.itemMin[DAMAGE_FIRE] = 999;
    assertEquals(9, profile.elementalMin[DAMAGE_FIRE]);
    assertEquals(9, profile.elementalMax[DAMAGE_FIRE]);
    assertEquals(7, profile.elementalMax[DAMAGE_LIGHTNING]);
    assertEquals(0, profile.elementalMax[DAMAGE_MAGIC]);
  }

  @Test
  void zeroSourceScaleDoesNotEraseVenomLengthOrAlwaysHit() {
    NativeSkillAttackProfile.Source source = new NativeSkillAttackProfile.Source();
    source.weaponMin = source.weaponMax = 100;
    source.venomActive = true;
    source.venomLength = 10;
    NativeSkillAttackProfile profile = NativeSkillAttackProfile.bladeShield(source, 20, 30, -1, 0, true);
    assertEquals(0, profile.physicalMin);
    assertEquals(0, profile.physicalMax);
    assertEquals(10, profile.poisonLength);
    assertTrue(profile.alwaysHit);
  }
}
