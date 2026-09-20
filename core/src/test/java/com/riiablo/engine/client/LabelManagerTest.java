package com.riiablo.engine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelManagerTest {
  @Test
  void hoveredLabelsRemainVisibleWithoutAlt() {
    assertTrue(LabelManager.shouldDisplayLabel(true, false, false));
    assertTrue(LabelManager.shouldDisplayLabel(true, true, false));
  }

  @Test
  void altShowsOnlyOtherwiseHiddenGroundItemLabels() {
    assertTrue(LabelManager.shouldDisplayLabel(false, true, true));
    assertFalse(LabelManager.shouldDisplayLabel(false, false, true));
    assertFalse(LabelManager.shouldDisplayLabel(false, true, false));
  }
}
