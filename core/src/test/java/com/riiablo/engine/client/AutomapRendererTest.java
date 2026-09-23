package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.item.Item;

class AutomapRendererTest {
  @Test
  void groundGoldIsNotAddedAsAutomapItemMarker() {
    Item gold = new Item();
    gold.code = "gld";
    assertFalse(AutomapRenderer.shouldDisplayItemMarker(gold));
  }

  @Test
  void regularGroundItemsRemainAutomapMarkers() {
    Item potion = new Item();
    potion.code = "ポーション";
    assertTrue(AutomapRenderer.shouldDisplayItemMarker(potion));
    assertFalse(AutomapRenderer.shouldDisplayItemMarker(null));
  }
}
