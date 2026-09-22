package com.riiablo.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
