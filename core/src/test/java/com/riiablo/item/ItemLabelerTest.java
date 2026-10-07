package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ItemLabelerTest {
  @Test
  void goldHeaderIncludesGroundQuantity() {
    assertEquals("125 Gold", ItemLabeler.formatGoldHeader("Gold", 125));
    assertEquals("125黄金", ItemLabeler.formatGoldHeader("黄金", 125));
  }

  @Test
  void nonPositiveGoldQuantityFallsBackToName() {
    assertEquals("Gold", ItemLabeler.formatGoldHeader("Gold", 0));
    assertEquals("Gold", ItemLabeler.formatGoldHeader("Gold", -1));
  }

  @Test
  void salePriceUsesTheRequestedTopLineText() {
    assertEquals("Sell Value: 1234", ItemLabeler.formatSellPrice(1234));
    assertEquals("Sell Value: 0", ItemLabeler.formatSellPrice(-1));
  }

  @Test
  void nativeWeaponAttackLengthIncludesBaseSpeedAndWeaponIas() {
    assertEquals(16, NativeWeaponSpeed.attackLength(16, 256, 0, 0));
    assertEquals(17, NativeWeaponSpeed.attackLength(16, 256, 10, 0));
    assertEquals(14, NativeWeaponSpeed.attackLength(16, 256, 10, 20));
  }

  @Test
  void nativeWeaponSpeedUsesClientBoundaryDescriptions() {
    assertEquals("WeaponAttackVeryFast",
        NativeWeaponSpeed.descriptionKey(0, false, 9));
    assertEquals("WeaponAttackVerySlow",
        NativeWeaponSpeed.descriptionKey(0, false, 28));
  }

  @Test
  void nativeWeaponSpeedDependsOnCharacterAndBowAnimationGroup() {
    assertEquals("WeaponAttackNormal",
        NativeWeaponSpeed.descriptionKey(0, false, 19));
    assertEquals("WeaponAttackFast",
        NativeWeaponSpeed.descriptionKey(1, false, 19));
    assertEquals("WeaponAttackNormal",
        NativeWeaponSpeed.descriptionKey(1, false, 20));
    assertEquals("WeaponAttackFast",
        NativeWeaponSpeed.descriptionKey(1, true, 20));
  }
}
