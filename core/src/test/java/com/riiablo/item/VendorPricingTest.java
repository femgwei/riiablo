package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Misc;
import com.riiablo.codec.excel.Npc;
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
