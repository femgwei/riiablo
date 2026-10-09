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
  void groundLabelLayoutOnlyRunsForAltOverlayWithMultipleItems() {
    assertFalse(LabelManager.shouldLayoutGroundLabels(false, 2));
    assertFalse(LabelManager.shouldLayoutGroundLabels(true, 1));
    assertTrue(LabelManager.shouldLayoutGroundLabels(true, 2));
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
    assertEquals(12, Math.abs(placed.y - occupied.first().y));
  }

  @Test
  void longGroundLabelUsesItsFullWidthWhenAvoidingAnotherLabel() {
    Array<Rectangle> occupied = new Array<>();
    occupied.add(new Rectangle(100, 80, 180, 12));

    Rectangle placed = LabelManager.findGroundLabelPosition(
        100, 80, 180, 12, occupied, 0, 0, 640, 360);

    assertFalse(overlapsWithGap(placed, occupied.first()));
  }

  @Test
  void denseGroundLabelsKeepSeparatingBeyondTheInitialSearchRings() {
    Array<Rectangle> occupied = new Array<>();
    boolean overlapObserved = false;
    for (int i = 0; i < 18; i++) {
      Rectangle placed = LabelManager.findGroundLabelPosition(
          160, 100, 40, 12, occupied, 0, 0, 320, 200);
      for (Rectangle previous : occupied) {
        if (overlapsWithGap(placed, previous)) overlapObserved = true;
      }
      if (i < 8) assertFalse(overlapObserved,
          "the initial local nudge should separate nearby labels");
      occupied.add(placed);
    }
    assertTrue(overlapObserved,
        "a dense drop must eventually allow native-style visual overlap");
  }

  private static boolean overlapsWithGap(Rectangle a, Rectangle b) {
    return a.x < b.x + b.width + 2
        && a.x + a.width + 2 > b.x
        && a.y < b.y + b.height + 2
        && a.y + a.height + 2 > b.y;
  }
}
