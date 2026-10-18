package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VultureApproachBoundaryTest extends RiiabloTest {
  @Test void occupiedApproachGoalAllowsPartialProgressInNegativeCoordinates() {
    Vector2 next = Vulture.radiusRecoveryPoint(80, -50, 79, -47, 9,
        (x, y) -> Math.abs(x - 79) + Math.abs(y + 48) <= 1);
    assertNotNull(next, "an occupied goal must not discard all reachable progress");
    assertNotEquals(new Vector2(80, -50), next);
    assertTrue(Math.abs(next.x - 79) + Math.abs(next.y + 48) > 1);
    Vector2 shifted = Vulture.radiusRecoveryPoint(180, 50, 179, 53, 9,
        (x, y) -> Math.abs(x - 179) + Math.abs(y - 52) <= 1);
    assertEquals(next.cpy().add(100, 100), shifted);
  }

  @Test void fullySurroundedBirdDoesNotRequestMovementThroughOccupants() {
    assertNull(Vulture.radiusRecoveryPoint(80, -50, 79, -47, 9,
        (x, y) -> x != 80 || y != -50));
  }

  @Test void repeatedRecoveryCanReachMeleeBesideAnAttackingBird() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      f.world.getMapper(Size.class).get(f.actor).size = 2;
      f.world.getMapper(Size.class).get(f.target).size = 2;
      Vector2 source = f.world.getMapper(Position.class).get(f.actor).position;
      source.set(80, -50);
      f.targetPosition().set(78, -45);
      com.d2moo.common.path.D2Toward.Collision occupied = (x, y) ->
          Math.abs(x - 79) + Math.abs(y + 48) <= 1
              || Math.abs(x - 78) + Math.abs(y + 45) <= 1;
      for (int i = 0; i < 12 && !ai.isInNativeMeleeRange(f.target); i++) {
        clearInvocations(f.paths);
        ai.walkInRadius(f.target, 9, 0, 0);
        ArgumentCaptor<Vector2> point = ArgumentCaptor.forClass(Vector2.class);
        verify(f.paths).findPath(eq(f.actor), point.capture(), eq(false), eq(Engine.INVALID_ENTITY));
        Vector2 goal = point.getValue();
        if (occupied.blocked((int) goal.x, (int) goal.y)) {
          goal = Vulture.radiusRecoveryPoint((int) source.x, (int) source.y,
              (int) goal.x, (int) goal.y, 9, occupied);
        }
        if (goal == null) break;
        assertFalse(occupied.blocked((int) goal.x, (int) goal.y));
        source.set(goal);
      }
      assertTrue(ai.isInNativeMeleeRange(f.target), "must reach a free attacking position");
    }
  }

  @Test void negativeFractionalCoordinatesDoNotProduceZeroApproachOutsideMelee() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      f.world.getMapper(Size.class).get(f.actor).size = 2;
      f.world.getMapper(Size.class).get(f.target).size = 2;
      f.world.getMapper(Position.class).get(f.actor).position.set(83f, -45.9f);
      f.targetPosition().set(86.99f, -45.03f);
      assertFalse(ai.isInNativeMeleeRange(f.target));
      assertTrue(ai.walkInRadius(f.target, 9, 0, 0));
      ArgumentCaptor<Vector2> point = ArgumentCaptor.forClass(Vector2.class);
      verify(f.paths).findPath(eq(f.actor), point.capture(), eq(false), eq(Engine.INVALID_ENTITY));
      assertEquals(new Vector2(85, -45), point.getValue());
      f.world.getMapper(Position.class).get(f.actor).position.set(point.getValue());
      assertTrue(ai.isInNativeMeleeRange(f.target));
    }
  }

  @Test void fullUnitSizeDistanceIsNotTheCombatDistanceTable() {
    try (Fixture f = new Fixture("vulture1", 0, 20)) {
      Vulture ai = (Vulture) f.ai;
      f.world.getMapper(Size.class).get(f.actor).size = 2;
      f.world.getMapper(Size.class).get(f.target).size = 2;
      f.world.getMapper(Position.class).get(f.actor).position.set(80, -45);
      f.targetPosition().set(84, -45);
      ai.walkInRadius(f.target, 9, 0, 0);
      ArgumentCaptor<Vector2> point = ArgumentCaptor.forClass(Vector2.class);
      verify(f.paths).findPath(eq(f.actor), point.capture(), eq(false), eq(Engine.INVALID_ENTITY));
      // AIUTIL full-size: abs(4-2)=2, abs(0-2)=2 -> distance 3.
      assertEquals(new Vector2(83, -45), point.getValue());
    }
  }
}
