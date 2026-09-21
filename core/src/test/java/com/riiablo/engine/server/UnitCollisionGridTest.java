package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnitCollisionGridTest {
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
}
