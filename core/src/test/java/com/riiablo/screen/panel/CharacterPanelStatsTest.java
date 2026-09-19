package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CharacterPanelStatsTest {
  @Test
  void attackRatingUsesNativePlayerFormula() {
    assertEquals(95, CharacterPanel.calculateAttackRating(25, 0, 5));
    assertEquals(65, CharacterPanel.calculateAttackRating(20, 0, 0));
  }

  @Test
  void damageRangeAppliesWeaponAttributeScaling() {
    assertArrayEquals(new int[] {1, 6},
        CharacterPanel.calculateDamageRange(1, 5, 20, 25, 0, 80, 0));
    assertArrayEquals(new int[] {2, 7},
        CharacterPanel.calculateDamageRange(2, 6, 20, 0, 100, 0, 0));
  }
}
