package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.codec.excel.ItemEntry;

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
  void durabilityKeepsYellowUntilTheItemIsBroken() {
    assertEquals(0, InventoryWarning.durabilitySeverity(1));
    assertEquals(2, InventoryWarning.durabilitySeverity(0));
  }

  @Test
  void armorSlotsNeverFallBackToTheWeaponFrame() {
    Item helmet = new Item();
    helmet.bodyLoc = BodyLoc.HEAD;
    assertEquals(7, InventoryWarning.durabilityGroup(helmet));

    Item gloves = new Item();
    gloves.bodyLoc = BodyLoc.GLOV;
    assertEquals(6, InventoryWarning.durabilityGroup(gloves));

    Item boots = new Item();
    boots.bodyLoc = BodyLoc.FEET;
    assertEquals(6, InventoryWarning.durabilityGroup(boots));
  }

  @Test
  void meleeWeaponWithoutAQuantityTypeCannotProduceAnAmmoWarning() {
    Item meleeWeapon = new Item();
    meleeWeapon.bodyLoc = BodyLoc.RARM;
    meleeWeapon.base = new ItemEntry();
    assertEquals(false, InventoryWarning.supportsQuantityWarning(meleeWeapon));
    assertEquals(true, InventoryWarning.supportsDurabilityWarning(meleeWeapon));
    assertEquals(4, InventoryWarning.durabilityGroup(meleeWeapon));
  }

  @Test
  void noDurabilityItemsIgnoreStaleDurabilityStats() {
    Item javelin = new Item();
    javelin.bodyLoc = BodyLoc.RARM;
    javelin.base = new ItemEntry();
    javelin.base.nodurability = true;
    assertEquals(false, InventoryWarning.supportsDurabilityWarning(javelin));
  }

  @Test
  void quantityAndDurabilityUseFixedUpperAndLowerSlots() {
    assertEquals(61f, InventoryWarning.slotOffsetY(
        InventoryWarning.Kind.QUANTITY, 41f, 20f));
    assertEquals(0f, InventoryWarning.slotOffsetY(
        InventoryWarning.Kind.DURABILITY, 41f, 20f));
  }
}
