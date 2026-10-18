package com.artemis;

/** Read-only adapter for the pinned Artemis 2.3 batch deletion boundary. */
public final class PendingEntityDeletion {
  private PendingEntityDeletion() {}

  public static boolean isDeleted(World world, int entityId) {
    return entityId < 0 || !world.getEntityManager().isActive(entityId)
        || world.batchProcessor.isDeleted(entityId);
  }
}
