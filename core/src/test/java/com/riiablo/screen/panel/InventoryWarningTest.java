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

  @Test
  void layoutUsesScreenCenterAndRightEdge() {
    assertEquals(580f, InventoryWarning.rightInsetX(640f, 40f, 20f));
    float quantityY = InventoryWarning.screenSlotY(
        InventoryWarning.Kind.QUANTITY, 480f, 41f, 10f);
    float durabilityY = InventoryWarning.screenSlotY(
        InventoryWarning.Kind.DURABILITY, 480f, 41f, 10f);
    assertEquals(250f, quantityY); // bottom = center + 10
    assertEquals(189f, durabilityY); // top = center - 10
    assertEquals(230f, durabilityY + 41f);
  }

  @Test
  void ammunitionKeepsYellowUntilTheStackIsEmpty() {
    assertEquals(0, InventoryWarning.quantitySeverity(10, 10));
    assertEquals(0, InventoryWarning.quantitySeverity(1, 10));
    assertEquals(2, InventoryWarning.quantitySeverity(0, 10));
  }

  @Test
  void quantityAndDurabilityUseFixedUpperAndLowerSlots() {
    assertEquals(61f, InventoryWarning.slotOffsetY(
        InventoryWarning.Kind.QUANTITY, 41f, 20f));
    assertEquals(0f, InventoryWarning.slotOffsetY(
        InventoryWarning.Kind.DURABILITY, 41f, 20f));
  }
}
