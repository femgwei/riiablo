package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

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
}
