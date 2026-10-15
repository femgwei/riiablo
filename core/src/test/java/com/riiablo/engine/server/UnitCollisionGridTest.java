package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitCollisionGridTest {
  @Test
  void invalidPresenceSizesUseSmallPatternForWritesQueriesAndDiagnostics() {
    for (int size : new int[] {Integer.MIN_VALUE, -1, 4, Integer.MAX_VALUE}) {
      UnitCollisionGrid grid = new UnitCollisionGrid();
      grid.put(1, 10, 10, size);
      assertFalse(grid.isFree(2, -1, 11, 10, 1), "small presence must still block cardinal cells");
      assertTrue(grid.isFree(2, -1, 11, 11, 1), "invalid size must not lay a large marker");
      assertTrue(grid.isFree(2, -1, 11, 11, size), "invalid mover must test the small cross");
      assertEquals(0, grid.blockers(2, -1, 11, 11, size, null).size);
      assertTrue(grid.blockers(2, -1, 11, 10, size, null).contains(1));
      grid.remove(1);
      assertTrue(grid.isFree(2, -1, 10, 10, size), "remove must clear the fallback marker");
    }
  }

  @Test
  void failedInvalidSizeMoveRestoresTheSmallFallbackMarker() {
    UnitCollisionGrid grid = new UnitCollisionGrid();
    grid.put(1, 10, 10, 1);
    grid.put(2, 20, 20, 4);
    assertFalse(grid.move(2, -1, 10, 10, 4));
    assertFalse(grid.isFree(3, -1, 21, 20, 1));
    assertTrue(grid.isFree(3, -1, 21, 21, 1), "rollback must not expand the old marker");
    assertTrue(grid.move(2, -1, 11, 11, 4));
    assertTrue(grid.isFree(3, -1, 20, 20, 1));
    assertFalse(grid.isFree(3, -1, 11, 11, 1));
  }

  @Test
  void footprintsBlockOtherUnitsButCanIgnoreTarget() {
    UnitCollisionGrid grid = new UnitCollisionGrid();
    grid.put(1, 10, 10, 1);

    assertFalse(grid.isFree(2, -1, 10, 10, 1));
    assertTrue(grid.isFree(2, 1, 10, 10, 1));
    assertTrue(grid.move(2, 1, 10, 10, 1));
  }

  @Test
  void blockedMoveRestoresTheOriginalFootprint() {
    UnitCollisionGrid grid = new UnitCollisionGrid();
    grid.put(1, 10, 10, 1);
    grid.put(2, 12, 10, 1);

    assertFalse(grid.move(2, -1, 10, 10, 1));
    assertTrue(grid.isFree(3, -1, 12, 10, 1) == false);
    grid.remove(1);
    assertTrue(grid.move(2, -1, 10, 10, 1));
  }

  @Test
  void rearUnitCanRouteAroundFrontUnitButCannotFollowItsBlockedCell() {
    UnitCollisionGrid grid = new UnitCollisionGrid();
    grid.put(1, 10, 10, 1);
    grid.put(2, 8, 10, 1);

    // The path query and authoritative step must agree about the front unit.
    assertFalse(grid.isFree(2, -1, 10, 10, 1));
    assertTrue(grid.isFree(2, -1, 11, 11, 1));
    assertTrue(grid.move(2, -1, 11, 11, 1));
  }

  @Test
  void sizeTwoFootprintUsesNativeCrossInsteadOfFilledSquare() {
    UnitCollisionGrid grid = new UnitCollisionGrid();
    grid.put(1, 10, 10, 2);

    assertFalse(grid.isFree(2, -1, 10, 10, 1));
    assertFalse(grid.isFree(2, -1, 11, 10, 1));
    assertFalse(grid.isFree(2, -1, 10, 11, 1));
    assertTrue(grid.isFree(2, -1, 11, 11, 1));
  }

  @Test
  void ordinarySizeTwoUnitsCannotConvergeOnTheSameCenter() {
    UnitCollisionGrid grid = new UnitCollisionGrid();
    grid.put(1, 133, 234, 2);
    grid.put(2, 135, 234, 2);

    assertFalse(grid.move(2, -1, 133, 234, 2));
    assertFalse(grid.move(2, -1, 134, 234, 2));
    assertTrue(grid.move(2, -1, 134, 235, 2));
  }
}
