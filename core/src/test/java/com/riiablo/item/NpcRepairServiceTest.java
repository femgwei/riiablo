package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Npc;
import com.riiablo.engine.server.npc.NpcRepairService;
import com.riiablo.save.CharData;

class NpcRepairServiceTest extends RiiabloTest {
  @Test void repairItemValidatesIdentityAndCommitsWalletAndDurabilityTogether() {
    CharData character = characterWithGold(100);
    Item item = damagedItem(0x1234, 100, 10, 5);
    assertTrue(character.getItems().addToInventory(item));
    int index = character.getItems().indexOf(item);
    Npc.Entry npc = new Npc.Entry();
    npc.repMult = 2048;

    NpcRepairService.Result result =
        NpcRepairService.repairItem(character, npc, index, item.id);

    assertTrue(result.success);
    assertTrue(result.cost > 0);
    assertEquals(item.id, result.itemId);
    assertEquals(10, item.attrs.base().get(Stat.durability).asInt());
    assertEquals(100 - result.cost, character.getStats().get(Stat.gold).asInt());
  }

  @Test void reusedIndexWithDifferentItemIdCannotRepairOrCharge() {
    CharData character = characterWithGold(100);
    Item item = damagedItem(7, 100, 10, 5);
    assertTrue(character.getItems().addToInventory(item));

    NpcRepairService.Result result = NpcRepairService.repairItem(
        character, new Npc.Entry(), character.getItems().indexOf(item), 8);

    assertFalse(result.success);
    assertEquals("ITEM_NOT_OWNED", result.reason);
    assertEquals(5, item.attrs.base().get(Stat.durability).asInt());
    assertEquals(100, character.getStats().get(Stat.gold).asInt());
  }

  @Test void repairAllIsAllOrNothingWhenGoldIsInsufficient() {
    CharData character = characterWithGold(1);
    Item first = damagedItem(1, 100, 10, 5);
    Item second = damagedItem(2, 100, 10, 5);
    assertTrue(character.getItems().addToInventory(first));
    assertTrue(character.getItems().addToInventory(second));
    character.getItems().equipItem(BodyLoc.TORS, first);
    character.getItems().equipItem(BodyLoc.HEAD, second);

    NpcRepairService.Result result =
        NpcRepairService.repairAll(character, new Npc.Entry());

    assertFalse(result.success);
    assertEquals("INSUFFICIENT_GOLD", result.reason);
    assertEquals(5, first.attrs.base().get(Stat.durability).asInt());
    assertEquals(5, second.attrs.base().get(Stat.durability).asInt());
    assertEquals(1, character.getStats().get(Stat.gold).asInt());
  }

  @Test void repairAllCommitsEveryEquippedItemOnce() {
    CharData character = characterWithGold(100);
    Item first = damagedItem(1, 100, 10, 5);
    Item second = damagedItem(2, 100, 10, 5);
    assertTrue(character.getItems().addToInventory(first));
    assertTrue(character.getItems().addToInventory(second));
    character.getItems().equipItem(BodyLoc.TORS, first);
    character.getItems().equipItem(BodyLoc.HEAD, second);

    NpcRepairService.Result result =
        NpcRepairService.repairAll(character, new Npc.Entry());

    assertTrue(result.success);
    assertTrue(result.cost > 0);
    assertEquals(10, first.attrs.base().get(Stat.durability).asInt());
    assertEquals(10, second.attrs.base().get(Stat.durability).asInt());
    assertEquals(100 - result.cost, character.getStats().get(Stat.gold).asInt());
  }

  private static CharData characterWithGold(int gold) {
    CharData character = CharData.obtain().clear().set(
        Riiablo.NORMAL, false, "RepairHero", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, gold);
    character.getStats().aggregate().put(Stat.gold, gold);
    character.getStats().base().put(Stat.goldbank, 0);
    character.getStats().aggregate().put(Stat.goldbank, 0);
    return character;
  }

  private static Item damagedItem(int id, int baseCost, int max, int current) {
    Item item = new Item();
    item.reset();
    item.setBase("cap");
    item.base.cost = baseCost;
    item.id = id;
    item.location = Location.STORED;
    item.quality = Quality.NORMAL;
    item.attrs.base().put(Stat.maxdurability, max);
    item.attrs.base().put(Stat.durability, current);
    return item;
  }
}
