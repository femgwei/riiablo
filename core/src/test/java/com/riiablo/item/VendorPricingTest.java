package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Misc;
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

  private static Item item(String code, int width, int height) {
    Item item = new Item();
    item.reset();
    Misc.Entry base = new Misc.Entry();
    base.code = code;
    base.invwidth = width;
    base.invheight = height;
    base.level = 1;
    item.setBase(base);
    item.location = Location.STORED;
    item.storeLoc = StoreLoc.NONE;
    item.quality = Quality.NORMAL;
    return item;
  }
}
