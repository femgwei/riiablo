package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.badlogic.gdx.utils.Array;
import com.riiablo.codec.excel.Misc;
import com.riiablo.codec.excel.Npc;
import com.riiablo.codec.excel.ItemTypes;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.engine.server.npc.NpcVendorSessionManager;
import com.riiablo.save.CharData;

class VendorPricingTest extends RiiabloTest {
  @Test
  void purchaseConsumesCarriedThenBankGoldAndClearsStoreFlag() {
    CharData character = CharData.obtain().clear().set(Riiablo.NORMAL, false, "VendorHero", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 20);
    character.getStats().base().put(Stat.goldbank, 100);
    character.getStats().aggregate().put(Stat.gold, 20);
    character.getStats().aggregate().put(Stat.goldbank, 100);

    Item item = item("hp1", 1, 1);
    item.flags2 |= Item.ITEMFLAG2_INSTORE;
    int price = VendorPricing.buyPrice(item);
    assertTrue(price > 0);
    assertTrue(VendorPricing.buy(character, item));
    assertEquals(20 - Math.min(20, price), character.getStats().get(Stat.gold).asInt());
    assertEquals(100 - Math.max(0, price - 20), character.getStats().get(Stat.goldbank).asInt());
    assertTrue(!item.hasFlag2(Item.ITEMFLAG2_INSTORE));
    assertTrue(character.getItems().contains(item));
  }

  @Test
  void purchaseAutoEquipsUsableItemIntoEmptyBodySlot() {
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "EquipBuyer", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 100000);
    character.getStats().aggregate().put(Stat.gold, 100000);

    Item weapon = item("sw1", 2, 3);
    weapon.base = new Weapons.Entry();
    weapon.base.code = "sw1";
    weapon.base.invwidth = 2;
    weapon.base.invheight = 3;
    weapon.typeEntry = new ItemTypes.Entry();
    weapon.typeEntry.BodyLoc = new String[] {"rarm"};
    weapon.type = Type.get("swor");
    weapon.flags2 |= Item.ITEMFLAG2_INSTORE;

    assertTrue(VendorPricing.buy(character, weapon));
    assertEquals(weapon, character.getItems().getSlot(BodyLoc.RARM));
    assertEquals(Location.EQUIPPED, weapon.location);
    assertTrue(!character.getItems().getStore(StoreLoc.INVENTORY).contains(
        character.getItems().indexOf(weapon)));
  }

  @Test
  void pickupAutoFillsBeltThenTomeBeforeInventory() {
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "ConsumableBuyer", Riiablo.AMAZON);
    Item potion = item("hp1", 1, 1);
    potion.typeEntry = new ItemTypes.Entry();
    potion.typeEntry.Beltable = true;
    potion.type = Type.get("hpot");
    assertTrue(character.getItems().addGroundPickup(potion, character));
    assertEquals(Location.BELT, potion.location);

    Item tome = item("tbk", 1, 1);
    tome.base.maxstack = 20;
    tome.attrs.base().put(Stat.quantity, 19);
    tome.typeEntry = new ItemTypes.Entry();
    tome.type = Type.get("book");
    assertTrue(character.getItems().addToInventory(tome));

    Item scroll = item("tsc", 1, 1);
    scroll.attrs.base().put(Stat.quantity, 1);
    scroll.typeEntry = new ItemTypes.Entry();
    scroll.type = Type.get("scro");
    assertTrue(character.getItems().addGroundPickup(scroll, character));
    assertEquals(20, tome.attrs.base().get(Stat.quantity).asInt());
    assertEquals(Location.STORED, tome.location);
    assertTrue(!character.getItems().contains(scroll));
  }

  @Test
  void leftClickPurchaseUsesCursorAndRejectsAnOccupiedCursorAtomically() {
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "CursorBuyer", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 1000);
    character.getStats().aggregate().put(Stat.gold, 1000);

    Item first = item("hp1", 1, 1);
    first.flags2 |= Item.ITEMFLAG2_INSTORE;
    int firstPrice = VendorPricing.buyPrice(first);
    assertTrue(VendorPricing.buyToCursor(character, first, null));
    assertEquals(first, character.getItems().getCursor());
    assertEquals(Location.CURSOR, first.location);
    assertEquals(1000 - firstPrice, character.getStats().get(Stat.gold).asInt());

    Item second = item("mp1", 1, 1);
    second.flags2 |= Item.ITEMFLAG2_INSTORE;
    int goldBeforeRejectedPurchase = character.getStats().get(Stat.gold).asInt();
    assertTrue(!VendorPricing.buyToCursor(character, second, null));
    assertEquals(goldBeforeRejectedPurchase, character.getStats().get(Stat.gold).asInt());
    assertTrue(second.hasFlag2(Item.ITEMFLAG2_INSTORE));
    assertTrue(!character.getItems().contains(second));
  }

  @Test
  void sellingAnInventoryItemAddsQuarterValue() {
    CharData character = CharData.obtain().clear().set(Riiablo.NORMAL, false, "VendorHero", Riiablo.AMAZON);
    Item item = item("hp1", 1, 1);
    assertTrue(character.getItems().addToInventory(item));
    int value = VendorPricing.sellPrice(item);
    assertTrue(VendorPricing.sell(character, character.getItems().indexOf(item)));
    assertEquals(value, character.getStats().get(Stat.gold).asInt());
    assertTrue(!character.getItems().contains(item));
  }

  @Test
  void nativeCostAndNpcMultiplierAreApplied() {
    Item item = item("hp1", 1, 1);
    item.base.cost = 400;
    Npc.Entry npc = new Npc.Entry();
    npc.sellMult = 2048;
    npc.buyMult = 512;
    assertEquals(800, VendorPricing.transactionCost(item, npc, VendorPricing.Transaction.BUY, 0));
    assertEquals(200, VendorPricing.transactionCost(item, npc, VendorPricing.Transaction.SELL, 0));
  }

  @Test
  void quiverUsesNativeFractionalStackCost() {
    Item item = item("cqv", 1, 1);
    item.base.cost = 80;
    item.base.stackable = true;
    item.typeEntry = new ItemTypes.Entry();
    item.typeEntry.Quiver = "xbow";
    item.attrs.base().put(Stat.quantity, 250);
    Npc.Entry npc = new Npc.Entry();
    npc.buyMult = VendorPricing.MULTIPLIER_SCALE;

    assertEquals(19, VendorPricing.transactionCost(
        item, npc, VendorPricing.Transaction.SELL, 0));
  }

  @Test
  void nativeConsumablesAreInfiniteStockButThrowingWeaponsAreNot() {
    Item arrows = item("aqv", 1, 1);
    arrows.type = Type.get("bowq");
    Item healthPotion = item("hp1", 1, 1);
    healthPotion.type = Type.get("hpot");
    Item manaPotion = item("mp5", 1, 1);
    manaPotion.type = Type.get("mpot");
    Item javelin = new ItemGenerator().generate("jav");

    assertTrue(VendorPricing.isInfiniteStockItem(arrows));
    assertTrue(VendorPricing.isInfiniteStockItem(healthPotion));
    assertTrue(VendorPricing.isInfiniteStockItem(manaPotion));
    assertFalse(VendorPricing.isInfiniteStockItem(javelin));
  }

  @Test
  void serverPurchaseReplacesInfiniteStockWithANewItem() throws Exception {
    Item potion = item("hp1", 1, 1);
    potion.id = 41;
    potion.type = Type.get("hpot");
    potion.flags2 |= Item.ITEMFLAG2_INSTORE;
    Item replacement = item("hp1", 1, 1);
    replacement.id = 42;
    replacement.type = Type.get("hpot");
    replacement.flags2 |= Item.ITEMFLAG2_INSTORE;
    VendorGenerator generator = new VendorGenerator() {
      @Override public Array<Item> generate(String vendor) {
        Array<Item> stock = new Array<>(true, 1, Item.class);
        stock.add(potion);
        return stock;
      }
      @Override public Item restock(Item purchased) { return replacement; }
    };
    NpcVendorSessionManager manager = new NpcVendorSessionManager();
    NpcVendorSessionManager.Session session =
        manager.open(10, "akara", generator, false, null, 0);
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "PotionBuyer", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 1000);
    character.getStats().aggregate().put(Stat.gold, 1000);

    assertTrue(manager.buy(session, character, potion.id) > 0);
    assertTrue(character.getItems().contains(potion));
    assertEquals(1, session.stock.size);
    assertSame(replacement, session.stock.first());
    assertNotEquals(potion.id, replacement.id);
  }

  @Test
  void reducedPriceAndRepairUseNativeScaling() {
    Item item = item("hp1", 1, 1);
    item.base.cost = 1000;
    item.attrs.base().put(Stat.maxdurability, 100);
    item.attrs.base().put(Stat.durability, 25);
    assertEquals(750, VendorPricing.transactionCost(item, null, VendorPricing.Transaction.REPAIR, 0));
    assertEquals(500, VendorPricing.transactionCost(item, null, VendorPricing.Transaction.BUY, 50));
  }

  @Test
  void gambleUsesGambleCostAndEtherealItemsCannotBeRepaired() {
    Item item = item("hp1", 1, 1);
    item.base.cost = 100;
    item.base.gambleCost = 750;
    item.attrs.base().put(Stat.maxdurability, 10);
    item.attrs.base().put(Stat.durability, 5);
    assertEquals(750, VendorPricing.gamblePrice(item));
    item.flags |= Item.ITEMFLAG_ETHEREAL;
    assertEquals(0, VendorPricing.repairPrice(item, null, null));
  }

  @Test
  void nativeRepairLeavesOnePointForReplenishingDurability() {
    Item item = item("hp1", 1, 1);
    item.base.cost = 1000;
    item.attrs.base().put(Stat.maxdurability, 10);
    item.attrs.base().put(Stat.durability, 9);
    item.attrs.base().put(Stat.item_replenish_durability, 1);
    assertEquals(0, VendorPricing.transactionCost(
        item, null, VendorPricing.Transaction.REPAIR, 0));
  }

  @Test
  void indestructiblePropertyCannotBeRepaired() {
    Item item = item("hp1", 1, 1);
    item.attrs.base().put(Stat.maxdurability, 10);
    item.attrs.base().put(Stat.durability, 1);
    item.attrs.base().put(Stat.item_indesctructible, 1);
    assertEquals(0, VendorPricing.repairPrice(item, null, null));
  }

  @Test
  void gamblePurchaseAtomicallyUsesGamblePrice() {
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "GambleHero", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 1000);
    character.getStats().aggregate().put(Stat.gold, 1000);
    Item item = item("hp1", 1, 1);
    item.base.gambleCost = 750;
    item.flags2 |= Item.ITEMFLAG2_INSTORE;

    assertTrue(VendorPricing.gamble(character, item));
    assertEquals(250, character.getStats().get(Stat.gold).asInt());
    assertTrue(character.getItems().contains(item));
    assertTrue(!item.hasFlag2(Item.ITEMFLAG2_INSTORE));
  }

  @Test
  void gambleSessionUsesGambleTransactionInsteadOfTradeBuy() throws Exception {
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "GambleHero", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 1000);
    character.getStats().aggregate().put(Stat.gold, 1000);
    Item item = item("hp1", 1, 1);
    item.base.cost = 100;
    item.base.gambleCost = 750;
    item.flags2 |= Item.ITEMFLAG2_INSTORE;
    item.id = 99;
    NpcVendorSessionManager manager = new NpcVendorSessionManager();
    NpcVendorSessionManager.Session session =
        manager.open(10, "gheed", null, true, null, 0, 1, false);
    session.stock.add(item);

    assertEquals(750, manager.buy(session, character, item.id));
    assertEquals(250, character.getStats().get(Stat.gold).asInt());
    assertEquals(0, session.stock.size);
    assertEquals(2, session.revision);
  }

  @Test
  void goldDropAndStashTransfersAreAtomicAndRespectCaps() {
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "GoldHero", Riiablo.AMAZON);
    character.level = 10;
    character.getStats().base().put(Stat.gold, 500);
    character.getStats().base().put(Stat.goldbank, 700);
    character.getStats().aggregate().put(Stat.gold, 500);
    character.getStats().aggregate().put(Stat.goldbank, 700);

    assertTrue(VendorPricing.dropCarriedGold(character, 125));
    assertEquals(375, character.getStats().get(Stat.gold).asInt());
    assertEquals(700, character.getStats().get(Stat.goldbank).asInt());
    assertTrue(VendorPricing.depositGold(character, 75));
    assertEquals(300, character.getStats().get(Stat.gold).asInt());
    assertEquals(775, character.getStats().get(Stat.goldbank).asInt());
    assertTrue(VendorPricing.withdrawGold(character, 200));
    assertEquals(500, character.getStats().get(Stat.gold).asInt());
    assertEquals(575, character.getStats().get(Stat.goldbank).asInt());
    assertTrue(!VendorPricing.withdrawGold(character, 20_000));
    assertEquals(500, character.getStats().get(Stat.gold).asInt());
    assertEquals(575, character.getStats().get(Stat.goldbank).asInt());
  }

  private static Item item(String code, int width, int height) {
    Item item = new Item();
    item.reset();
    Misc.Entry base = new Misc.Entry();
    base.code = code;
    base.invwidth = width;
    base.invheight = height;
    base.level = 1;
    item.code = code;
    item.base = base;
    item.attrs = Attributes.obtainStandard();
    item.location = Location.STORED;
    item.storeLoc = StoreLoc.NONE;
    item.quality = Quality.NORMAL;
    return item;
  }
}
