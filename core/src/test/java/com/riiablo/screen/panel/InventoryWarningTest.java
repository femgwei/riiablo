package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InventoryWarningTest {
  @Test
  void severityUsesGoldOrangeRedStates() {
    assertEquals(0, InventoryWarning.severity(7, 7, 70));
    assertEquals(1, InventoryWarning.severity(3, 7, 70));
    assertEquals(2, InventoryWarning.severity(0, 7, 70));
    assertEquals(0, InventoryWarning.severity(1, 1, 10));
    assertEquals(2, InventoryWarning.severity(0, 1, 10));
  }

  @Test
  void frameClampsGroupAndColor() {
    assertEquals(0, InventoryWarning.frame(-1, -1));
    assertEquals(23, InventoryWarning.frame(99, 99));
    assertEquals(7, InventoryWarning.frame(2, 1));
  }
}
