package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real integer allocation wrapper with a controlled legacy allocator. */
class IntegerMonsterAllocationGuardTest {
  @Test void changedFinalLandingDoesNotAcquireSelectionPrecision() {
    try (Fixture f = new Fixture()) {
      for (Vector2 landing : new Vector2[] {new Vector2(11, 20), new Vector2(10, 21)}) {
        f.position.position.set(landing);
        assertEquals(f.id, f.factory.createMonsterAtIntegerSelection(1, 10, 20, 2, 3L, 4, 5));
        assertEquals(landing, f.position.position);
        assertFalse(f.position.hasNativePrecision());
      }
      assertEquals(2, f.factory.rank);
      assertEquals(3L, f.factory.affixes);
      assertEquals(4, f.factory.championType);
      assertEquals(5, f.factory.uniqueId);
    }
  }

  @Test void failedAllocationDoesNotReadOrModifyPosition() {
    try (Fixture f = new Fixture()) {
      f.position.initializeNativeCell(10, 20);
      f.factory.result = Engine.INVALID_ENTITY;
      assertEquals(Engine.INVALID_ENTITY,
          f.factory.createMonsterAtIntegerSelection(1, 10, 20, 0, 0L, -1, -1));
      assertTrue(f.position.hasNativePrecision());
    }
  }

  @Test void successfulAllocatorWithoutPositionIsNotPromoted() {
    try (Fixture f = new Fixture()) {
      f.factory.mPosition.remove(f.id);
      f.world.process();
      assertEquals(f.id, f.factory.createMonsterAtIntegerSelection(1, 10, 20, 0, 0L, -1, -1));
      assertFalse(f.factory.mPosition.has(f.id));
    }
  }

  @Test void unsignedCellEndpointsPreserveExpectedRawBits() {
    try (Fixture f = new Fixture()) {
      for (int cell : new int[] {0, 65535}) {
        f.position.invalidateNativePrecision();
        f.position.position.set(cell, cell);
        f.factory.createMonsterAtIntegerSelection(1, cell, cell, 0, 0L, -1, -1);
        assertEquals((cell << 16) | 0x8000, f.position.nativePrecisionX());
        assertEquals((cell << 16) | 0x8000, f.position.nativePrecisionY());
      }
    }
  }

  @Test void eitherAxisOutsideRawDomainRetainsLegacyResultWithoutPromotion() {
    try (Fixture f = new Fixture()) {
      for (int[] cell : new int[][] {{-1, 20}, {65536, 20}, {10, -1}, {10, 65536}}) {
        f.position.position.set(cell[0], cell[1]);
        assertEquals(f.id,
            f.factory.createMonsterAtIntegerSelection(1, cell[0], cell[1], 0, 0L, -1, -1));
        assertFalse(f.position.hasNativePrecision());
      }
    }
  }

  private static final class ControlledFactory extends ServerEntityFactory {
    int result, rank, championType, uniqueId;
    long affixes;

    @Override public int createMonster(int monsterId, float x, float y,
        int rank, long affixes, int championType, int uniqueId) {
      this.rank = rank;
      this.affixes = affixes;
      this.championType = championType;
      this.uniqueId = uniqueId;
      return result;
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfiguration());
    final ControlledFactory factory = new ControlledFactory();
    final int id = world.create();
    final Position position = world.getMapper(Position.class).create(id);

    Fixture() {
      factory.mPosition = world.getMapper(Position.class);
      factory.result = id;
      world.process();
    }

    @Override public void close() { world.dispose(); }
  }
}
