package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
  void staffModCountMatchesNativeThresholds() {
    assertEquals(0, NativeItemGeneration.staffModCount(30, 0));
    assertEquals(1, NativeItemGeneration.staffModCount(31, 0));
    assertEquals(1, NativeItemGeneration.staffModCount(70, 0));
    assertEquals(2, NativeItemGeneration.staffModCount(71, 0));
    assertEquals(2, NativeItemGeneration.staffModCount(90, 0));
    assertEquals(3, NativeItemGeneration.staffModCount(91, 0));
    assertEquals(1, NativeItemGeneration.staffModCount(0, 1));
  }

  @Test
  void staffModTierAndValueMatchNativeBranches() {
    assertEquals(1, NativeItemGeneration.staffModBaseTier(11));
    assertEquals(2, NativeItemGeneration.staffModBaseTier(12));
    assertEquals(5, NativeItemGeneration.staffModBaseTier(37));
    assertEquals(3, NativeItemGeneration.staffModTier(5, 5, false));
    assertEquals(4, NativeItemGeneration.staffModTier(5, 20, false));
    assertEquals(5, NativeItemGeneration.staffModTier(5, 50, false));
    assertEquals(6, NativeItemGeneration.staffModTier(5, 81, false));
    assertEquals(4, NativeItemGeneration.staffModTier(5, 81, true));
    assertEquals(1, NativeItemGeneration.staffModValue(59, 0));
    assertEquals(2, NativeItemGeneration.staffModValue(60, 0));
    assertEquals(3, NativeItemGeneration.staffModValue(90, 0));
  }

  @Test
  void uniqueAndSetItemsDoNotRollRandomStaffMods() {
    assertTrue(NativeItemGeneration.canRollStaffMods(Quality.NORMAL));
    assertTrue(NativeItemGeneration.canRollStaffMods(Quality.MAGIC));
    assertTrue(NativeItemGeneration.canRollStaffMods(Quality.RARE));
    assertFalse(NativeItemGeneration.canRollStaffMods(Quality.SET));
    assertFalse(NativeItemGeneration.canRollStaffMods(Quality.UNIQUE));
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

  @Test
  void forcedEtherealityUsesPropertyFunc23Guard() {
    Item item = armor(4, 2, 2, 40);
    item.attrs.base().put(Stat.armorclass, 100);
    item.attrs.base().put(Stat.maxdurability, 40);
    item.attrs.base().put(Stat.durability, 30);

    assertTrue(NativeItemGeneration.applyEtherealIfNeeded(item));
    assertEquals(150, item.attrs.base().get(Stat.armorclass).asInt());
    assertEquals(21, item.attrs.base().get(Stat.maxdurability).asInt());
    assertFalse(NativeItemGeneration.applyEtherealIfNeeded(item));
    assertEquals(150, item.attrs.base().get(Stat.armorclass).asInt(),
        "PropertyFunc23 must not scale an already ethereal item twice");
  }

  @Test
  void vendorArmorReceivesDefenseAndFullDurabilityWithoutRerollingExistingDefense() {
    Item item = armor(0, 2, 2, 40);
    Armor.Entry armor = (Armor.Entry) item.base;
    armor.minac = 8;
    armor.maxac = 12;

    NativeItemGeneration.normalizeVendorBaseStats(item, bound -> 0);

    assertEquals(8, item.attrs.base().get(Stat.armorclass).asInt());
    assertEquals(40, item.attrs.base().get(Stat.maxdurability).asInt());
    assertEquals(40, item.attrs.base().get(Stat.durability).asInt());

    item.attrs.base().put(Stat.armorclass, 11);
    item.attrs.base().put(Stat.maxdurability, 25);
    item.attrs.base().put(Stat.durability, 3);
    NativeItemGeneration.normalizeVendorBaseStats(item, bound -> {
      throw new AssertionError("existing defense must not be rerolled");
    });

    assertNotNull(item.attrs.base().get(Stat.armorclass));
    assertEquals(11, item.attrs.base().get(Stat.armorclass).asInt());
    assertEquals(25, item.attrs.base().get(Stat.maxdurability).asInt());
    assertEquals(25, item.attrs.base().get(Stat.durability).asInt());
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
