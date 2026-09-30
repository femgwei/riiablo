package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.item.NativeItemQuantityRegenSystem;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** D2MOO zero-quantity/replenish behavior for Amazon bow quivers. */
class NativeAmazonAmmoReplenishTest extends RiiabloTest {
  @Test
  void replenishingQuiverSurvivesZeroAndRecovers() {
    CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
    data.getItems().unequipItem(BodyLoc.RARM);
    data.getItems().unequipItem(BodyLoc.LARM);

    Item bow = new Item();
    bow.reset();
    bow.setBase(Riiablo.files.weapons.get("sbw"));
    data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(bow));

    Item arrows = new Item();
    arrows.reset();
    arrows.setBase(Riiablo.files.misc.get("aqv"));
    arrows.attrs.base().put(Stat.quantity, 1);
    arrows.attrs.base().put(Stat.item_replenish_quantity, 100);
    arrows.attrs.reset();
    data.getItems().equipItem(BodyLoc.LARM, data.getItems().add(arrows));

    assertTrue(ServerSkillSystem.consumeRangedAmmo(data.getItems(), bow));
    assertEquals(0, arrows.attrs.base().get(Stat.quantity).asInt());
    assertTrue(data.getItems().contains(arrows));
    assertEquals(arrows, data.getItems().getSlot(BodyLoc.LARM));

    World world = new World(new WorldConfigurationBuilder()
        .with(new NativeItemQuantityRegenSystem()).build());
    try {
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      for (int i = 0; i < 125; i++) world.process();
      assertEquals(0, arrows.attrs.base().get(Stat.quantity).asInt());
      world.process();
      assertEquals(1, arrows.attrs.base().get(Stat.quantity).asInt());
      assertEquals(arrows, data.getItems().getEquippedAmmo(bow));
    } finally {
      world.dispose();
    }
  }
}
