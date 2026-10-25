package com.artemis;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;

class EntityAllocatorCheckpointTest {
  @Test void fifoLimboAndUnallocatedTailResumeWithoutRunningSystems() {
    Counter counter = new Counter();
    try (Fixture source = new Fixture(new World()); Fixture target = new Fixture(
        new World(new WorldConfigurationBuilder().with(counter).build()))) {
      for (int i = 0; i < 12; i++) source.world.create();
      EntityAllocatorCheckpoint.flushStructure(source.world);
      for (int id : new int[] {8, 2, 11, 4, 7}) {
        source.world.delete(id); EntityAllocatorCheckpoint.flushStructure(source.world);
      }
      assertEquals(8, source.world.create()); // wrap logical FIFO head
      source.world.delete(8); EntityAllocatorCheckpoint.flushStructure(source.world);
      EntityAllocatorCheckpoint archive = EntityAllocatorCheckpoint.fromBytes(
          EntityAllocatorCheckpoint.capture(source.world).bytes());
      assertArrayEquals(new int[] {2, 11, 4, 7, 8}, archive.freeIds());
      try (CheckpointEntityAllocation allocation = new CheckpointEntityAllocation(target.world)) {
        for (int id : archive.activeIds()) allocation.create(id);
      }
      EntityAllocatorCheckpoint.flushStructure(target.world);
      archive.restore(target.world);
      assertArrayEquals(archive.bytes(), EntityAllocatorCheckpoint.capture(target.world).bytes());
      for (int id : new int[] {2, 11, 4, 7, 8, 12, 13}) {
        assertEquals(id, source.world.create()); assertEquals(id, target.world.create());
      }
      assertEquals(0, counter.ticks);
    }
  }

  @Test void freeOnlyHighTailIsMaterializedAndHigherTargetHistoryIsTruncated() {
    try (Fixture fixture = new Fixture(new World())) {
      EntityAllocatorCheckpoint expected = EntityAllocatorCheckpoint.of(6, new int[] {5, 1, 4, 0, 3, 2});
      expected.restore(fixture.world); // no live entity forces factory allocation to reach 5
      assertArrayEquals(expected.bytes(), EntityAllocatorCheckpoint.capture(fixture.world).bytes());
      for (int id : expected.freeIds()) assertEquals(id, fixture.world.create());
      EntityAllocatorCheckpoint.flushStructure(fixture.world);
      for (int id = 0; id < 6; id++) fixture.world.delete(id);
      EntityAllocatorCheckpoint.flushStructure(fixture.world);
      EntityAllocatorCheckpoint.of(0, new int[0]).restore(fixture.world);
      assertEquals(0, fixture.world.getEntityManager().entities.size());
      assertEquals(0, fixture.world.create());
      // Growth after rewind still honors component and registered bit stores.
      for (int id = 1; id < 400; id++) assertEquals(id, fixture.world.create());
      fixture.world.getMapper(Marker.class).create(399);
      EntityAllocatorCheckpoint.flushStructure(fixture.world);
      assertTrue(fixture.world.getMapper(Marker.class).has(399));
    }
  }

  @Test void pendingDeletionAndMismatchedLiveSetsAreRejectedWithoutMutation() {
    try (Fixture fixture = new Fixture(new World())) {
      fixture.world.create(); EntityAllocatorCheckpoint.flushStructure(fixture.world);
      EntityAllocatorCheckpoint before = EntityAllocatorCheckpoint.capture(fixture.world);
      assertThrows(IllegalStateException.class,
          () -> EntityAllocatorCheckpoint.of(0, new int[0]).restore(fixture.world));
      assertArrayEquals(before.bytes(), EntityAllocatorCheckpoint.capture(fixture.world).bytes());
      fixture.world.delete(0);
      assertThrows(IllegalStateException.class, () -> EntityAllocatorCheckpoint.capture(fixture.world));
      assertThrows(IllegalStateException.class, () -> before.restore(fixture.world));
      EntityAllocatorCheckpoint.flushStructure(fixture.world);
      assertArrayEquals(new int[] {0}, EntityAllocatorCheckpoint.capture(fixture.world).freeIds());
      assertEquals(0, fixture.world.create());
    }
  }

  @Test void localEntitySetsMustMatchAndFreeIdsCannotBePayloads() {
    try (Fixture fixture = new Fixture(new World())) {
      fixture.world.create();
      EntityAllocatorCheckpoint same = EntityAllocatorCheckpoint.capture(fixture.world);
      same.validateRestore(fixture.world, Collections.emptySet(), Collections.emptySet());
      assertThrows(IllegalStateException.class,
          () -> same.validateRestore(fixture.world, Collections.singleton(0), Collections.emptySet()));
      EntityAllocatorCheckpoint free = EntityAllocatorCheckpoint.of(1, new int[] {0});
      assertThrows(IllegalArgumentException.class,
          () -> free.validateRestore(fixture.world, Collections.singleton(0), Collections.emptySet()));
    }
  }

  @Test void codecRejectsDuplicatesBoundsVersionsTruncationAndTrailingBytes() {
    assertThrows(IllegalArgumentException.class, () -> EntityAllocatorCheckpoint.of(-1, new int[0]));
    assertThrows(IllegalArgumentException.class, () -> EntityAllocatorCheckpoint.of(1_000_001, new int[0]));
    assertThrows(IllegalArgumentException.class, () -> EntityAllocatorCheckpoint.of(2, new int[] {1, 1}));
    assertThrows(IllegalArgumentException.class, () -> EntityAllocatorCheckpoint.of(2, new int[] {2}));
    byte[] bytes = EntityAllocatorCheckpoint.of(2, new int[] {1, 0}).bytes();
    byte[] badVersion = bytes.clone(); ByteBuffer.wrap(badVersion).putInt(0, 2);
    assertThrows(IllegalArgumentException.class, () -> EntityAllocatorCheckpoint.fromBytes(badVersion));
    assertThrows(IllegalArgumentException.class, () -> EntityAllocatorCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length - 1)));
    assertThrows(IllegalArgumentException.class, () -> EntityAllocatorCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
    int[] detached = EntityAllocatorCheckpoint.fromBytes(bytes).freeIds(); detached[0] = 0;
    assertArrayEquals(new int[] {1, 0}, EntityAllocatorCheckpoint.fromBytes(bytes).freeIds());
  }

  public static class Marker extends Component {}
  private static final class Counter extends BaseSystem {
    int ticks;
    @Override protected void processSystem() { ticks++; }
  }
  private static final class Fixture implements AutoCloseable {
    final World world;
    Fixture(World world) { this.world = world; }
    @Override public void close() { world.dispose(); }
  }
}
