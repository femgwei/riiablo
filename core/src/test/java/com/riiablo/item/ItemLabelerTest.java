package com.riiablo.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ItemLabelerTest {
  @Test
  void goldHeaderIncludesGroundQuantity() {
    assertEquals("125 Gold", ItemLabeler.formatGoldHeader("Gold", 125));
    assertEquals("125黄金", ItemLabeler.formatGoldHeader("黄金", 125));
  }

  @Test
  void nonPositiveGoldQuantityFallsBackToName() {
    assertEquals("Gold", ItemLabeler.formatGoldHeader("Gold", 0));
    assertEquals("Gold", ItemLabeler.formatGoldHeader("Gold", -1));
  }

  @Test
  void salePriceUsesTheRequestedTopLineText() {
    assertEquals("出售价格：1234", ItemLabeler.formatSellPrice(1234));
    assertEquals("出售价格：0", ItemLabeler.formatSellPrice(-1));
  }
}
