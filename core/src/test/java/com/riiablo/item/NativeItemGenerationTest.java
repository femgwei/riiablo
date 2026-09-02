package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Armor;
import com.riiablo.codec.excel.ItemTypes;
import org.junit.jupiter.api.Test;

class NativeItemGenerationTest extends RiiabloTest {
  @Test
  void computesNativeAffixLevelBranches() {
    assertEquals(1, NativeItemGeneration.affixLevel(1, 1, 0));
    assertEquals(45, NativeItemGeneration.affixLevel(50, 10, 0));
    assertEquals(75, NativeItemGeneration.affixLevel(70, 20, 5));
    assertEquals(99, NativeItemGeneration.affixLevel(99, 1, 20));
  }

  @Test
  void socketLimitUsesItemLevelTypeBaseAndDifficulty() {
    Item item = armor(6, 1, 1, 24);
    item.typeEntry.MaxSock = new int[] {2, 4, 6};
    assertEquals(2, NativeItemGeneration.maxSockets(item, 20, 0));
    assertEquals(4, NativeItemGeneration.maxSockets(item, 30, 1));
    assertEquals(6, NativeItemGeneration.maxSockets(item, 50, 2));
  }

  @Test
  void etherealAppliesDefenseAndHalfDurability() {
    Item item = armor(4, 2, 2, 40);
    item.attrs.base().put(Stat.armorclass, 100);
    item.attrs.base().put(Stat.maxdurability, 40);
    item.attrs.base().put(Stat.durability, 30);
    NativeItemGeneration.applyEthereal(item);
    assertTrue(item.isEthereal());
    assertEquals(150, item.attrs.base().get(Stat.armorclass).asInt());
    assertEquals(21, item.attrs.base().get(Stat.maxdurability).asInt());
    assertEquals(21, item.attrs.base().get(Stat.durability).asInt());
  }

  @Test
  void normalSocketRollUsesStartSeedAndNativeCaps() {
    Item item = armor(6, 2, 3, 24);
    item.typeEntry.MaxSock = new int[] {2, 4, 6};
    assertTrue(NativeItemGeneration.rollSockets(
        item, Quality.NORMAL, 50, 2, 8, bound -> 0));
    assertTrue(item.hasFlag(Item.ITEMFLAG_SOCKETED));
    assertEquals(3, item.attrs.base().get(Stat.item_numsockets).asInt());
    assertFalse(NativeItemGeneration.rollSockets(
        armor(6, 2, 3, 24), Quality.MAGIC, 50, 2, 8, bound -> 0));
  }

  @Test
  void lowSetQuestAndNoDurabilityItemsCannotBeEthereal() {
    Item item = armor(4, 2, 2, 40);
    item.attrs.base().put(Stat.maxdurability, 40);
    assertFalse(NativeItemGeneration.canBeEthereal(item, Quality.LOW));
    assertFalse(NativeItemGeneration.canBeEthereal(item, Quality.SET));
    item.base.quest = 1;
    assertFalse(NativeItemGeneration.canBeEthereal(item, Quality.NORMAL));
  }

  private static Item armor(int sockets, int width, int height, int durability) {
    Armor.Entry base = new Armor.Entry();
    base.gemsockets = sockets;
    base.invwidth = width;
    base.invheight = height;
    base.durability = durability;
    Item item = new Item();
    item.reset();
    item.base = base;
    item.typeEntry = new ItemTypes.Entry();
    item.attrs = Attributes.obtainStandard();
    item.attrs.base().clear();
    return item;
  }
}
