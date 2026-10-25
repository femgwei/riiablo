package com.artemis;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class CheckpointEntityAllocationTest {
  @Test public void sparseOutOfOrderAllocationNeverPublishesGapsOrRunsSystems() {
    Counter counter = new Counter();
    World world = new World(new WorldConfigurationBuilder().with(counter).build());
    EntitySubscription all = world.getAspectSubscriptionManager().get(Aspect.all());
    try {
      try (CheckpointEntityAllocation allocation = new CheckpointEntityAllocation(world)) {
        assertEquals(300, allocation.create(300));
        assertTrue(all.getEntities().isEmpty());
        assertEquals(5, allocation.create(5));
        assertEquals(1000, allocation.create(1000));
        assertEquals(0, counter.ticks);
      }
      world.process();
      assertEquals(1, counter.ticks);
      assertArrayEquals(new int[] {5, 300, 1000}, ids(all));
      // Gaps were recycled, not leaked as empty active entities.
      assertEquals(0, world.create());
      world.process();
      assertArrayEquals(new int[] {0, 5, 300, 1000}, ids(all));
    } finally { world.dispose(); }
  }

  @Test public void recycledQueueOrderDoesNotRemapOrOverwriteLiveEntities() {
    World world = new World();
    try {
      for (int i = 0; i < 10; i++) world.create();
      world.process();
      world.delete(8);
      world.process();
      world.delete(2);
      world.process();
      try (CheckpointEntityAllocation allocation = new CheckpointEntityAllocation(world)) {
        assertEquals(2, allocation.create(2));
        assertTrue(allocation.isReserved(8));
        assertEquals(8, allocation.create(8));
        assertRejected(() -> allocation.create(1));
        assertRejected(() -> allocation.create(2));
      }
      world.process();
      assertEquals(10, world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
    } finally { world.dispose(); }
  }

  @Test public void pendingDeletionAndUnboundedIdsAreRejectedWithoutConsumingIds() {
    World world = new World();
    try {
      assertEquals(0, world.create());
      world.process();
      world.delete(0);
      try (CheckpointEntityAllocation allocation = new CheckpointEntityAllocation(world)) {
        assertRejected(() -> allocation.create(0));
        assertRejected(() -> allocation.create(-1));
        assertRejected(() -> allocation.create(Integer.MAX_VALUE));
      }
      assertEquals(1, world.create());
      world.process();
      assertEquals(0, world.create());
    } finally { world.dispose(); }
  }

  @Test public void abortedScopeRecyclesOnlyUnusedReservationsAndCannotBeReused() {
    World world = new World();
    try {
      CheckpointEntityAllocation allocation = new CheckpointEntityAllocation(world);
      try (CheckpointEntityAllocation ignored = allocation) {
        allocation.create(9);
        world.delete(9);
      }
      allocation.close();
      assertRejected(() -> allocation.create(1));
      world.process();
      assertTrue(world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().isEmpty());
      for (int i = 0; i < 10; i++) assertEquals(i, world.create());
    } finally { world.dispose(); }
  }

  private static int[] ids(EntitySubscription subscription) {
    com.artemis.utils.IntBag entities = subscription.getEntities();
    return java.util.Arrays.copyOf(entities.getData(), entities.size());
  }

  private static void assertRejected(Runnable action) {
    try { action.run(); fail("invalid checkpoint allocation was accepted"); }
    catch (IllegalArgumentException | IllegalStateException expected) {}
  }

  private static final class Counter extends BaseSystem {
    int ticks;
    @Override protected void processSystem() { ticks++; }
  }
}
