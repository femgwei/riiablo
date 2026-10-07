package com.riiablo.engine.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabelManagerTest {
  @Test
  void hoveredLabelsRemainVisibleWithoutAlt() {
    assertTrue(LabelManager.shouldDisplayLabel(true, false, false, false, false));
    assertTrue(LabelManager.shouldDisplayLabel(true, true, false, false, false));
  }

  @Test
  void altShowsOnlyOtherwiseHiddenGroundItemLabels() {
    assertTrue(LabelManager.shouldDisplayLabel(false, true, true, false, false));
    assertFalse(LabelManager.shouldDisplayLabel(false, false, true, false, false));
    assertFalse(LabelManager.shouldDisplayLabel(false, true, false, false, false));
  }

  @Test
  void hoveredDecorativeObjectsDoNotShowNames() {
    assertFalse(LabelManager.shouldDisplayLabel(true, false, false, true, false));
    assertTrue(LabelManager.shouldDisplayLabel(true, false, false, true, true));
  }
}
