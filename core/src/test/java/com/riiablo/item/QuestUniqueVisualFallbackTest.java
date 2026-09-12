package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.riiablo.codec.excel.Misc;
import org.junit.jupiter.api.Test;

class QuestUniqueVisualFallbackTest {
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
