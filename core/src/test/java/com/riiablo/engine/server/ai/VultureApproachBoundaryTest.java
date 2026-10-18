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
  @Test void groundRadiusCallersRecoverOccupiedGoalsAndPreserveSuccessfulRequests() {
    for (String name : new String[] {"ReanimatedHorde", "SiegeBeast"}) {
      String row = null;
      for (com.riiablo.codec.excel.MonStats.Entry entry : com.riiablo.Riiablo.files.monstats) {
        if (name.equals(entry.AI)) { row = entry.Id; break; }
      }
      assertNotNull(row, name);
      com.riiablo.engine.server.DynamicUnitCollisionSystem collision =
          mock(com.riiablo.engine.server.DynamicUnitCollisionSystem.class);
      try (Fixture f = new Fixture(row, 0, 20, collision)) {
        NativeDecisionAI ai = (NativeDecisionAI) f.ai;
        com.riiablo.map.Map map = mock(com.riiablo.map.Map.class);
        com.riiablo.map.Map.Zone zone = mock(com.riiablo.map.Map.Zone.class);
        f.world.getMapper(com.riiablo.engine.server.component.MapWrapper.class)
            .get(f.actor).set(map, zone);
        when(zone.contains(anyInt(), anyInt())).thenReturn(true);
        when(zone.areRoomsAdjacent(anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenReturn(true);
        when(collision.isFreeForPath(eq(f.actor), eq(Engine.INVALID_ENTITY),
            anyInt(), anyInt(), anyInt())).thenAnswer(inv ->
                (int) inv.getArgument(2) != 79 || (int) inv.getArgument(3) != -47);
        f.world.getMapper(Position.class).get(f.actor).position.set(80, -50);
        f.world.getMapper(Size.class).get(f.actor).size = 2;
        f.targetPosition().set(78, -45);
        // Exact coordinate order fails; the recovered endpoint still goes through host A*.
        when(f.paths.findPath(eq(f.actor), any(), eq(false), eq(Engine.INVALID_ENTITY)))
            .thenReturn(false, true);
        assertTrue(ai.walkInRadius(f.target, name.equals("SiegeBeast") ? 12 : 4, 0, 37), name);
        ArgumentCaptor<Vector2> points = ArgumentCaptor.forClass(Vector2.class);
        verify(f.paths, times(2)).findPath(eq(f.actor), points.capture(), eq(false), eq(Engine.INVALID_ENTITY));
        assertEquals(new Vector2(79, -47), points.getAllValues().get(0));
        assertNotEquals(new Vector2(80, -50), points.getAllValues().get(1));
        assertNotEquals(new Vector2(79, -47), points.getAllValues().get(1));
        assertEquals(0.37f, f.world.getMapper(com.riiablo.engine.server.component.Velocity.class)
            .get(f.actor).modeSpeedBonusMultiplier, 0.0001f);
        clearInvocations(f.paths, collision);
        assertTrue(ai.walkInRadius(f.target, 4, 0, 0));
        verify(f.paths, times(1)).findPath(eq(f.actor), any(), eq(false), eq(Engine.INVALID_ENTITY));
        verifyNoInteractions(collision);
        // Fully blocked recovery preserves failure rather than walking through occupants.
        when(f.paths.findPath(eq(f.actor), any(), eq(false), eq(Engine.INVALID_ENTITY))).thenReturn(false);
        when(collision.isFreeForPath(eq(f.actor), eq(Engine.INVALID_ENTITY),
            anyInt(), anyInt(), anyInt())).thenReturn(false);
        assertFalse(ai.walkInRadius(f.target, 4, 0, 0), name);
      }
    }
  }
  @Test void occupiedApproachGoalAllowsPartialProgressInNegativeCoordinates() {
    Vector2 next = RadiusMovementRecovery.recoveryPoint(80, -50, 79, -47, 9,
        (x, y) -> Math.abs(x - 79) + Math.abs(y + 48) <= 1);
    assertNotNull(next, "an occupied goal must not discard all reachable progress");
    assertNotEquals(new Vector2(80, -50), next);
    assertTrue(Math.abs(next.x - 79) + Math.abs(next.y + 48) > 1);
    Vector2 shifted = RadiusMovementRecovery.recoveryPoint(180, 50, 179, 53, 9,
        (x, y) -> Math.abs(x - 179) + Math.abs(y - 52) <= 1);
    assertEquals(next.cpy().add(100, 100), shifted);
  }

  @Test void fullySurroundedBirdDoesNotRequestMovementThroughOccupants() {
    assertNull(RadiusMovementRecovery.recoveryPoint(80, -50, 79, -47, 9,
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
          goal = RadiusMovementRecovery.recoveryPoint((int) source.x, (int) source.y,
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
