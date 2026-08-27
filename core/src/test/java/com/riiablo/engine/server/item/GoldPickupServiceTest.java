package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.item.Item;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.save.CharData;

class GoldPickupServiceTest {
  @Test
  void goldIsCreditedInsteadOfPlacedOnCursor() {
    CharData character = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "GoldHero", Riiablo.AMAZON);
    character.getStats().base().put(Stat.gold, 10);
    character.getStats().aggregate().put(Stat.gold, 10);

    Item gold = new Item();
    gold.id = 42;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, 25);
    ItemMoveIntent intent = new ItemMoveIntent(1, 0, ItemMoveOperation.GROUND_TO_CURSOR,
        42, 99, -1, -1, -1, -1, false);

    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    AuthoritativeItemMoveService.Outcome result = service.pickup(7, character, intent, gold);
    assertTrue(result.success);
    assertEquals(ItemMoveFailure.NONE, result.failure);
    assertEquals(35, character.getStats().get(Stat.gold).asInt());
    assertNull(character.getItems().getCursor());
    assertEquals(1, service.revision(7));
  }
}
