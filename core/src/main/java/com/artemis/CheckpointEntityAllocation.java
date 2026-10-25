package com.artemis;

import com.artemis.utils.IntBag;
import java.util.TreeSet;

/**
 * Exact-id allocation for the pinned Artemis 2.3 checkpoint boundary.
 * Uses the normal allocator, never edits its private free-list or runs systems.
 * Skipped ids have no components and are never published to subscriptions.
 * Keep one scope for a restore batch so sparse gaps are scanned only once.
 * Owner-thread only: do not process the world or edit unused reservations.
 * This is not an allocator/free-list snapshot: subsequent allocation order may differ.
 */
public final class CheckpointEntityAllocation implements AutoCloseable {
  public static final int MAX_ENTITY_ID = 999_999;

  private final World world;
  private final TreeSet<Integer> skipped = new TreeSet<>();
  private boolean closed;

  public CheckpointEntityAllocation(World world) {
    if (world == null) throw new NullPointerException("world");
    this.world = world;
  }

  public boolean isReserved(int entityId) { return !closed && skipped.contains(entityId); }

  public int create(int expectedId) {
    if (closed) throw new IllegalStateException("allocation scope is closed");
    if (expectedId < 0 || expectedId > MAX_ENTITY_ID) {
      throw new IllegalArgumentException("checkpoint entity id exceeds allocation bound");
    }
    EntityManager manager = world.getEntityManager();
    if (!skipped.remove(expectedId)) {
      if (expectedId < manager.entities.size() && manager.entities.get(expectedId) != null
          && (manager.isActive(expectedId) || world.batchProcessor.isDeleted(expectedId))) {
        throw new IllegalStateException("checkpoint id is occupied or pending deletion");
      }
      int allocated;
      // An inactive allocated id is in limbo; an unallocated id is reached by
      // nextId. Each skipped id stays reserved until used or this scope closes.
      while ((allocated = manager.create()) != expectedId) skipped.add(allocated);
    }
    world.batchProcessor.changed.unsafeSet(expectedId);
    return expectedId;
  }

  @Override public void close() {
    if (closed) return;
    closed = true;
    IntBag unused = new IntBag(skipped.size());
    for (int id : skipped) unused.add(id);
    // Raw reservations were never edited/published, so there are no component
    // removals or subscription notifications to flush.
    world.getEntityManager().clean(unused);
    skipped.clear();
  }
}
