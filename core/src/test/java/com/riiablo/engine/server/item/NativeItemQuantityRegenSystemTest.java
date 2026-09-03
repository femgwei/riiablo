package com.riiablo.engine.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Player;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class NativeItemQuantityRegenSystemTest extends RiiabloTest {
  @Test
  void intervalMatchesNativeStatRegenFormula() {
    assertEquals(Integer.MAX_VALUE, NativeItemQuantityRegenSystem.intervalFrames(0));
    assertEquals(2501, NativeItemQuantityRegenSystem.intervalFrames(1));
    assertEquals(251, NativeItemQuantityRegenSystem.intervalFrames(10));
    assertEquals(125, NativeItemQuantityRegenSystem.intervalFrames(100));
  }

  @Test
  void replenishesOneQuantityAtNativeIntervalAndStopsAtMaximum() {
    CharData data = CharData.createRemote("amazon", (byte) Riiablo.AMAZON);
    Item arrows = new Item();
    arrows.reset();
    arrows.setBase(Riiablo.files.misc.get("aqv"));
    int maximum = arrows.base.maxstack;
    arrows.attrs.base().put(Stat.quantity, maximum - 1);
    arrows.attrs.base().put(Stat.item_replenish_quantity, 100);
    arrows.attrs.reset();
    data.getItems().equipItem(BodyLoc.LARM, data.getItems().add(arrows));

    World world = new World(new WorldConfigurationBuilder()
        .with(new NativeItemQuantityRegenSystem()).build());
    try {
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      for (int i = 0; i < 125; i++) world.process();
      assertEquals(maximum - 1, arrows.attrs.base().get(Stat.quantity).asInt());
      world.process();
      assertEquals(maximum, arrows.attrs.base().get(Stat.quantity).asInt());
      for (int i = 0; i < 250; i++) world.process();
      assertEquals(maximum, arrows.attrs.base().get(Stat.quantity).asInt());
    } finally {
      world.dispose();
    }
  }
}
