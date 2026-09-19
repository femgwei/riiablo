package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Size;
import org.junit.jupiter.api.Test;

class GameScreenPanelTest {
  @Test
  void controlTemporarilyRunsOnlyWhilePersistentModeIsWalk() {
    assertFalse(GameScreen.shouldUseTemporaryRun(false, false));
    assertTrue(GameScreen.shouldUseTemporaryRun(true, false));
    assertFalse(GameScreen.shouldUseTemporaryRun(true, true));
  }

  @Test
  void waygateRemainsOpenOnlyInsideItsInteractionRange() {
    Interactable waypoint = new Interactable();
    waypoint.range = 5f;
    Size playerSize = new Size();
    playerSize.size = Size.MEDIUM;

    assertTrue(GameScreen.isWaygateWithinRange(
        Vector2.Zero, new Vector2(7.1f, 0f), waypoint, playerSize));
    assertFalse(GameScreen.isWaygateWithinRange(
        Vector2.Zero, new Vector2(7.2f, 0f), waypoint, playerSize));
    assertFalse(GameScreen.isWaygateWithinRange(
        Vector2.Zero, null, waypoint, playerSize));
  }
}
