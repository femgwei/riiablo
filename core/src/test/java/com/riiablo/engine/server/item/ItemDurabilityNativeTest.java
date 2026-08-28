package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Boundary checks for the native ITEMS_UpdateDurability chance contract. */
class ItemDurabilityNativeTest {
  @Test
  void usesNativeWeaponArmorAndThrowableProbabilities() {
    assertEquals(4, ItemDurabilityManager.nativeDurabilityChance(false, false, true));
    assertEquals(10, ItemDurabilityManager.nativeDurabilityChance(true, false, true));
    assertEquals(10, ItemDurabilityManager.nativeDurabilityChance(false, true, true));
    assertEquals(0, ItemDurabilityManager.nativeDurabilityChance(false, true, false));
  }
}
