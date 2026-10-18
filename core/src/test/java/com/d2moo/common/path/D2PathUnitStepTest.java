package com.d2moo.common.path;

import com.d2moo.common.collision.D2Collision;
import com.d2moo.common.collision.D2CollisionGrid;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class D2PathUnitStepTest {
  private static int center(int cell) { return D2PathStep.toFp16Center(cell); }

  @Test void sameCellPreservesLowWordsWithoutMigratingCollision() {
    Position position = new Position().setNativePrecision(center(10) + 7, center(20) + 13);
    D2PathStep.StepResult step = position.stepNativeUnit(1024, -512, center(30), center(30),
        (x, y, nx, ny) -> { fail("same-cell precision must not rewrite collision"); return false; });
    assertEquals(center(10) + 1031, position.nativePrecisionX());
    assertEquals(center(20) - 499, position.nativePrecisionY());
    assertEquals(0, step.crossedCells);
    assertFalse(step.reachedTarget);
  }

  @Test void zeroVectorFitsCenterAndUnknownFloatSourceCannotBeStepped() {
    Position position = new Position().setNativePrecision(center(10) + 7, center(20) - 13);
    position.stepNativeUnit(0, 0, center(30), center(30), (x, y, nx, ny) -> false);
    assertEquals(center(10), position.nativePrecisionX());
    assertEquals(center(20), position.nativePrecisionY());
    position.invalidateNativePrecision();
    assertThrows(IllegalStateException.class,
        () -> position.stepNativeUnit(1, 0, center(30), center(30), (x, y, nx, ny) -> true));
  }

  @Test void fastMovementProbesIntermediateCellsAndStopsAtLastSafeCenter() {
    int[] calls = {0};
    D2PathStep.StepResult step = D2PathStep.stepUnit(center(10), center(20),
        4 << 16, 0, center(30), center(20), (x, y, nx, ny) -> {
          calls[0]++;
          assertEquals(x + 1, nx);
          return nx < 13;
        });
    assertEquals(3, calls[0]);
    assertTrue(step.blocked);
    assertFalse(step.reachedTarget);
    assertEquals(center(12), step.x);
    assertEquals(center(20), step.y);
    assertEquals(2, step.crossedCells);
  }

  @Test void nearWaypointClampsBothAxesUsingLargestVelocityComponent() {
    D2PathStep.StepResult step = D2PathStep.stepUnit(center(10), center(20),
        2 << 16, 1 << 16, center(11), center(22), (x, y, nx, ny) -> true);
    assertTrue(step.reachedTarget);
    assertEquals(center(11), step.x);
    assertEquals(center(22), step.y);
  }

  @Test void rawUnsignedCoordinateRangeAndNegativeMovementDoNotPassThroughFloat() {
    Position position = new Position().setNativePrecision(center(65534) + 1, center(40000) + 3);
    position.stepNativeUnit(-65536, 0, center(65000), center(40000), (x, y, nx, ny) -> true);
    assertEquals(center(65533) + 1, position.nativePrecisionX());
    assertEquals(center(40000) + 3, position.nativePrecisionY());
  }

  @Test void sharedGridCollisionRestoresFootprintAndDoesNotTunnelThroughWall() {
    D2CollisionGrid grid = new D2CollisionGrid(0, 0, 40, 40);
    int pattern = D2Collision.PATTERN_SMALL_UNIT_PRESENCE;
    grid.setMaskWithPattern(10, 20, pattern, D2Collision.COLLIDE_MONSTER);
    grid.setMask(14, 20, D2Collision.COLLIDE_WALL);
    Position position = new Position().initializeNativeCell(10, 20);
    D2PathStep.StepResult step = position.stepNativeUnit(5 << 16, 0, center(30), center(20),
        (x, y, nx, ny) -> grid.tryTeleportUnit(x, y, nx, ny, pattern,
            D2Collision.COLLIDE_MONSTER, D2Collision.COLLIDE_WALL) == 0);
    assertTrue(step.blocked);
    assertEquals(center(12), position.nativePrecisionX());
    assertNotEquals(0, grid.flagsAt(12, 20) & D2Collision.COLLIDE_MONSTER);
    assertEquals(0, grid.flagsAt(14, 20) & D2Collision.COLLIDE_MONSTER);
    assertNotEquals(0, grid.flagsAt(14, 20) & D2Collision.COLLIDE_WALL);
  }

  @Test void sharedGridResetClearsSharedBitsRatherThanReferenceCountingOwners() {
    D2CollisionGrid grid = new D2CollisionGrid(0, 0, 40, 40);
    int pattern = D2Collision.PATTERN_SMALL_UNIT_PRESENCE;
    grid.setMaskWithPattern(10, 20, pattern, D2Collision.COLLIDE_MONSTER);
    grid.setMaskWithPattern(12, 20, pattern, D2Collision.COLLIDE_MONSTER);
    assertNotEquals(0, grid.flagsAt(11, 20) & D2Collision.COLLIDE_MONSTER);
    grid.resetMaskWithPattern(10, 20, pattern, D2Collision.COLLIDE_MONSTER);
    assertEquals(0, grid.flagsAt(11, 20) & D2Collision.COLLIDE_MONSTER);
    assertNotEquals(0, grid.flagsAt(12, 20) & D2Collision.COLLIDE_MONSTER);
  }
}
