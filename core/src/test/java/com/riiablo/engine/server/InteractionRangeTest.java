package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Size;

class InteractionRangeTest {
  @Test
  void effectiveRangeIncludesTheSourceFootprint() {
    Interactable interactable = new Interactable().set(2f, null);
    Size sourceSize = new Size();
    sourceSize.size = Size.MEDIUM;

    assertEquals(4f, InteractionRange.effective(interactable, sourceSize));
  }

  @Test
  void rangeCheckToleratesSubtileIntegrationError() {
    Interactable interactable = new Interactable().set(2f, null);
    Size sourceSize = new Size();
    sourceSize.size = Size.INSIGNIFICANT;

    assertTrue(InteractionRange.contains(2.003776f, interactable, sourceSize));
    assertFalse(InteractionRange.contains(2.2f, interactable, sourceSize));
  }
}
