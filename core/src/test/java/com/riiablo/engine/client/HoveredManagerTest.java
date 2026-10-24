package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector2;

class HoveredManagerTest {
  @Test
  void interactableTargetWinsOverOverlappingNonInteractableEntity() {
    assertTrue(HoveredManager.shouldReplaceTarget(true, 100f, false, 1f));
    assertFalse(HoveredManager.shouldReplaceTarget(false, 1f, true, 100f));
  }

  @Test
  void nearestEntityWinsWhenTargetsHaveTheSameInteractionPriority() {
    assertTrue(HoveredManager.shouldReplaceTarget(true, 4f, true, 9f));
    assertFalse(HoveredManager.shouldReplaceTarget(true, 9f, true, 4f));
    assertTrue(HoveredManager.shouldReplaceTarget(false, 4f, false, 9f));
  }

  @Test
  void hitTestIncludesConfiguredPadding() {
    ClientHoverBounds box = new ClientHoverBounds(-16, 8, 32, 16);
    Vector2 entity = new Vector2(500, 300);

    assertTrue(HoveredManager.containsScreenPoint(
        box, entity, new Vector2(472, 300), 12f, 8f));
    assertFalse(HoveredManager.containsScreenPoint(
        box, entity, new Vector2(471, 300), 12f, 8f));
  }

}
