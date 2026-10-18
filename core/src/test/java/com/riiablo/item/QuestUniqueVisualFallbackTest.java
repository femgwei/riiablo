package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.riiablo.codec.excel.Misc;
import com.riiablo.codec.excel.ItemTypes;
import org.junit.jupiter.api.Test;

class QuestUniqueVisualFallbackTest {
  @Test
  void variableInventoryGraphicsHonorExplicitIndexAndMissingIndexFallback() {
    Item item = new Item();
    item.base = new Misc.Entry(); item.base.invfile = "placeholder";
    item.quality = Quality.NORMAL;
    item.typeEntry = new ItemTypes.Entry();
    item.typeEntry.VarInvGfx = 3;
    item.typeEntry.InvGfx = new String[] {"invch1", "invch4", "invch7"};
    for (int i = 0; i < 3; i++) {
      item.pictureId = (byte) i;
      assertEquals(item.typeEntry.InvGfx[i], item.getInvFileName());
    }
    item.pictureId = Item.NO_PICTURE_ID;
    assertEquals("invch1", item.getInvFileName());
    NativeItemGeneration.rollInventoryGraphic(item, bound -> bound - 1);
    assertEquals(2, item.pictureId);
    assertEquals("invch7", item.getInvFileName());
    item.pictureId = 7;
    assertEquals("invch7", item.getInvFileName());
  }

  @Test
  void uniqueQuestItemWithoutUniqueItemsRowUsesBaseVisuals() {
    Misc.Entry base = new Misc.Entry();
    base.flippyfile = "flpbase";
    base.invfile = "invbase";
    base.dropsound = "dropbase";
    base.usesound = "usebase";
    base.dropsfxframe = 7;

    Item item = new Item();
    item.base = base;
    item.quality = Quality.UNIQUE;
    item.qualityId = Item.NO_UNIQUE_ID;
    item.qualityData = null;
    item.flags = Item.ITEMFLAG_IDENTIFIED;
    item.pictureId = Item.NO_PICTURE_ID;

    assertEquals("flpbase", item.getFlippyFile());
    assertEquals("invbase", item.getInvFileName());
    assertEquals("dropbase", item.getDropSound());
    assertEquals("usebase", item.getUseSound());
    assertEquals(7, item.getDropFxFrame());
    assertNull(item.getInvColor());
    assertNull(item.getCharColor());
  }
}
