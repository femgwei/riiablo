package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import org.junit.jupiter.api.Test;

class EquipmentMovementModifierTest {
  @Test
  void armorAndActiveShieldStackWithoutDuplicateSlotsOrAlternateShield() {
    ItemData data = new ItemData(null, null);
    addArmor(data, BodyLoc.TORS, 10);
    addArmor(data, BodyLoc.LARM, 5);
    addArmor(data, BodyLoc.LARM2, 10);
    data.equipped.put(BodyLoc.HEAD, 0);
    assertEquals(-15, data.getEquipmentMovementModifier());
    data.alternate = 1;
    assertEquals(-20, data.getEquipmentMovementModifier());
    data.itemData.get(2).flags |= Item.ITEMFLAG_BROKEN;
    assertEquals(-10, data.getEquipmentMovementModifier());
  }

  private static void addArmor(ItemData data, BodyLoc slot, int speed) {
    Item item = new Item();
    item.base = new Armor.Entry();
    item.base.speed = speed;
    item.bodyLoc = slot;
    data.equipped.put(slot, data.itemData.size);
    data.itemData.add(item);
  }

  @Test
  void onlyActiveUnbrokenEquippedArmorContributesWeightOnce() {
    ItemData data = new ItemData(null, null);
    Item armor = new Item();
    armor.base = new Armor.Entry();
    armor.base.speed = 10;
    armor.bodyLoc = BodyLoc.TORS;
    data.itemData.add(armor);
    data.equipped.put(BodyLoc.TORS, 0);
    assertEquals(-10, data.getEquipmentMovementModifier());
    Item weapon = new Item();
    weapon.base = new Weapons.Entry();
    weapon.base.speed = 20;
    weapon.bodyLoc = BodyLoc.RARM;
    data.itemData.add(weapon);
    data.equipped.put(BodyLoc.RARM, 1);
    assertEquals(-10, data.getEquipmentMovementModifier());
    armor.flags |= Item.ITEMFLAG_BROKEN;
    assertEquals(0, data.getEquipmentMovementModifier());
    armor.flags &= ~Item.ITEMFLAG_BROKEN;
    data.equipped.put(BodyLoc.TORS, ItemData.INVALID_ITEM);
    assertEquals(0, data.getEquipmentMovementModifier(), "carried armor has no movement penalty");
  }
}
