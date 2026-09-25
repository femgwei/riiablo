package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;

class InventoryClassMappingTest {
  @Test
  void expansionPlayerClassesUseTheirInventoryRows() {
    Inventory inventory = new Inventory();
    inventory.put(15, entry("Druid", 320));
    inventory.put(16, entry("Assassin", 320));
    inventory.put(20, entry("Paladin2", 400));
    inventory.put(21, entry("Barbarian2", 400));

    assertEquals("Druid", inventory.getClass(Riiablo.DRUID)._class);
    assertEquals("Assassin", inventory.getClass(Riiablo.ASSASSIN)._class);
    assertEquals(320, inventory.getClass(Riiablo.DRUID).invLeft);
    assertEquals(320, inventory.getClass(Riiablo.ASSASSIN).invLeft);
    assertEquals(400, inventory.get(20).invLeft,
        "wide alternate rows must not be used as player-class layouts");
  }

  private static Inventory.Entry entry(String name, int invLeft) {
    Inventory.Entry entry = new Inventory.Entry();
    entry._class = name;
    entry.invLeft = invLeft;
    return entry;
  }
}
