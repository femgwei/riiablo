package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Velocity;

class PathfinderTest {
  @Test
  void blockedRaycastFallbackStopsInsteadOfMovingDirectlyToTarget() {
    Velocity velocity = new Velocity();
    velocity.velocity.set(30f, -20f);

    Pathfinder.stopBlockedMovement(velocity);

    assertEquals(0f, velocity.velocity.x);
    assertEquals(0f, velocity.velocity.y);
  }

  @Test
  void adjacentMovementDirectionMustRemainStableBeforeChangingFacing() {
    Angle angle = new Angle().set(Vector2.X);
    Pathfind pathfind = new Pathfind();
    Vector2 adjacent = new Vector2(1f, 0f).setAngleRad(MathUtils.PI / 6f);

    assertFalse(Pathfinder.updateMovementFacing(angle, pathfind, adjacent));
    assertFalse(Pathfinder.updateMovementFacing(angle, pathfind, adjacent));
    assertTrue(Pathfinder.updateMovementFacing(angle, pathfind, adjacent));
  }

  @Test
  void sharpMovementTurnChangesFacingImmediately() {
    Angle angle = new Angle().set(Vector2.X);
    Pathfind pathfind = new Pathfind();
    Vector2 sharpTurn = new Vector2(0f, 1f);

    assertTrue(Pathfinder.updateMovementFacing(angle, pathfind, sharpTurn));
  }
}
