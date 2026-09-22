package com.riiablo.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemLabelerTest {
  @Test
  void goldHeaderIncludesGroundQuantity() {
    assertEquals("Gold: 125", ItemLabeler.formatGoldHeader("Gold", 125));
  }

  @Test
  void nonPositiveGoldQuantityFallsBackToName() {
    assertEquals("Gold", ItemLabeler.formatGoldHeader("Gold", 0));
    assertEquals("Gold", ItemLabeler.formatGoldHeader("Gold", -1));
  }
}
