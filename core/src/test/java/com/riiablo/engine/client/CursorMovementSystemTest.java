package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.codec.util.BBox;

class CursorMovementSystemTest {
  @Test
  void interactableTargetWinsOverOverlappingNonInteractableEntity() {
    assertTrue(CursorMovementSystem.shouldReplaceHoveredTarget(
        true, 100f, false, 1f));
    assertFalse(CursorMovementSystem.shouldReplaceHoveredTarget(
        false, 1f, true, 100f));
  }

  @Test
  void nearestEntityWinsWhenTargetsHaveTheSameInteractionPriority() {
    assertTrue(CursorMovementSystem.shouldReplaceHoveredTarget(
        true, 4f, true, 9f));
    assertFalse(CursorMovementSystem.shouldReplaceHoveredTarget(
        true, 9f, true, 4f));
    assertTrue(CursorMovementSystem.shouldReplaceHoveredTarget(
        false, 4f, false, 9f));
  }

  @Test
  void synchronousWaypointHitTestUsesTheRenderedBoundingBox() {
    BBox box = new BBox();
    box.xMin = -70;
    box.yMin = -30;
    box.xMax = 70;
    box.yMax = 30;
    box.width = 140;
    box.height = 60;
    Vector2 waypointScreen = new Vector2(500, 300);

    assertTrue(CursorMovementSystem.containsScreenPoint(
        box, waypointScreen, new Vector2(500, 300)));
    assertTrue(CursorMovementSystem.containsScreenPoint(
        box, waypointScreen, new Vector2(430, 270)));
    assertFalse(CursorMovementSystem.containsScreenPoint(
        box, waypointScreen, new Vector2(429, 300)));
    assertFalse(CursorMovementSystem.containsScreenPoint(
        box, waypointScreen, new Vector2(500, 331)));
  }
}
