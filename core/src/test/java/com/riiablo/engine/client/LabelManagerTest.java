package com.riiablo.engine.client;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

  @Test
  void groundLabelKeepsPreferredPositionWhenThereIsNoCollision() {
    Rectangle placed = LabelManager.findGroundLabelPosition(
        100, 80, 40, 12, new Array<>(), 0, 0, 320, 200);

    assertEquals(100, placed.x);
    assertEquals(80, placed.y);
  }

  @Test
  void groundLabelStacksAwayFromExistingLabel() {
    Array<Rectangle> occupied = new Array<>();
    occupied.add(new Rectangle(100, 80, 40, 12));

    Rectangle placed = LabelManager.findGroundLabelPosition(
        100, 80, 40, 12, occupied, 0, 0, 320, 200);

    assertFalse(placed.overlaps(occupied.first()));
    assertEquals(14, Math.abs(placed.y - occupied.first().y));
  }
}
