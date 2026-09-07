package com.riiablo.engine.client;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks one authoritative snapshot baseline transaction.
 *
 * <p>BEGIN, entity frames and END may be duplicated or observed out of order
 * by a transport adapter. Only a matching, complete transaction can become
 * the new client baseline.</p>
 */
public final class SnapshotBaselineTransaction {
  public enum EndResult {
    IGNORED,
    INCOMPLETE,
    FAILED,
    COMPLETE
  }

  private final Set<Integer> entityIds = new HashSet<>();
  private long activeRequestId;
  private long activeBaselineId;
  private long expectedEntityCount;
  private long lastCompletedBaselineId;
  private boolean active;

  /** Starts a new transaction, or ignores a duplicate/stale BEGIN. */
  public boolean begin(long requestId, long baselineId, long entityCount) {
    if (requestId <= 0L || baselineId <= 0L || entityCount < 0L) return false;
    if (active) {
      if (baselineId < activeBaselineId
          || (baselineId == activeBaselineId && requestId != activeRequestId)) return false;
      if (requestId == activeRequestId && baselineId == activeBaselineId) return false;
    }
    if (baselineId <= lastCompletedBaselineId) return false;
    activeRequestId = requestId;
    activeBaselineId = baselineId;
    expectedEntityCount = entityCount;
    entityIds.clear();
    active = true;
    return true;
  }

  public boolean active() {
    return active;
  }

  public long activeRequestId() {
    return activeRequestId;
  }

  public long activeBaselineId() {
    return activeBaselineId;
  }

  public long expectedEntityCount() {
    return expectedEntityCount;
  }

  public int receivedEntityCount() {
    return entityIds.size();
  }

  public long lastCompletedBaselineId() {
    return lastCompletedBaselineId;
  }

  /** Records a unique entity frame while a baseline is active. */
  public boolean acceptEntity(int entityId) {
    return active && entityIds.add(entityId);
  }

  /**
   * Commits only a matching and complete END marker. An incomplete baseline
   * is aborted so the receiver can request a fresh transaction.
   */
  public EndResult end(long requestId, long baselineId, long entityCount, boolean success) {
    if (!active || requestId != activeRequestId || baselineId != activeBaselineId) {
      return EndResult.IGNORED;
    }
    if (!success) {
      active = false;
      entityIds.clear();
      return EndResult.FAILED;
    }
    if (entityCount != expectedEntityCount
        || entityIds.size() != expectedEntityCount) {
      active = false;
      entityIds.clear();
      return EndResult.INCOMPLETE;
    }
    active = false;
    lastCompletedBaselineId = baselineId;
    entityIds.clear();
    return EndResult.COMPLETE;
  }

  /** Aborts a transaction without changing the completed-baseline watermark. */
  public void abort() {
    active = false;
    entityIds.clear();
  }
}
