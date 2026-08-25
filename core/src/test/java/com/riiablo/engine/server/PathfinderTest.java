package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import com.riiablo.engine.Direction;
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
    assertTrue(angle.target.epsilonEquals(adjacent.nor(), 0.0001f));
  }

  @Test
  void sharpMovementTurnChangesFacingImmediately() {
    Angle angle = new Angle().set(Vector2.X);
    Pathfind pathfind = new Pathfind();
    Vector2 sharpTurn = new Vector2(0f, 1f);

    assertTrue(Pathfinder.updateMovementFacing(angle, pathfind, sharpTurn));
    assertTrue(angle.target.epsilonEquals(sharpTurn, 0.0001f));
  }

  @Test
  void d2DirectionIdDoesNotChangeTheAuthoritativeMovementAngle() {
    Angle angle = new Angle().set(Vector2.X);
    Pathfind pathfind = new Pathfind();
    Vector2 movement = new Vector2(Vector2.X).setAngleRad(2.15f);

    // This angle quantizes to a D2 direction id whose value is not the
    // radians-table index. The target must still follow the actual movement.
    assertEquals(9, Direction.radiansToDirection(movement.angleRad(), 16));
    assertTrue(Pathfinder.updateMovementFacing(angle, pathfind, movement));
    assertTrue(angle.target.epsilonEquals(movement, 0.0001f));
  }

  @Test
  void pooledAngleResetRestoresDefaultFacingInsteadOfAccumulatingRotation() {
    Angle angle = new Angle();
    Vector2 expected = new Vector2(Vector2.X).setAngleRad(
        Direction.direction8ToRadians(Direction.DOWN));

    angle.reset();
    assertTrue(angle.angle.epsilonEquals(expected, 0.0001f));
    angle.angle.setAngleRad(1.1f);
    angle.target.set(angle.angle);
    angle.reset();
    assertTrue(angle.angle.epsilonEquals(expected, 0.0001f));
    assertTrue(angle.target.epsilonEquals(expected, 0.0001f));
  }
}
