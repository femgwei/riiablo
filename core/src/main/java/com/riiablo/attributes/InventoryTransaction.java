package com.riiablo.attributes;

import java.util.Arrays;

/** Immutable, replay-safe inventory transaction boundary for M6. */
public final class InventoryTransaction {
  private final long baseRevision;
  private final long requestId;
  private final long[] before;
  private final long[] after;

  private InventoryTransaction(long baseRevision, long requestId, long[] before, long[] after) {
    this.baseRevision = baseRevision;
    this.requestId = requestId;
    this.before = copy(before);
    this.after = copy(after);
  }

  public static InventoryTransaction prepare(long baseRevision, long requestId,
      long[] before, long[] after) {
    if (baseRevision < 0) throw new IllegalArgumentException("baseRevision");
    if (requestId < 0) throw new IllegalArgumentException("requestId");
    if (before == null || after == null) throw new NullPointerException("inventory state");
    return new InventoryTransaction(baseRevision, requestId, before, after);
  }

  public long baseRevision() { return baseRevision; }
  public long requestId() { return requestId; }
  public long[] before() { return copy(before); }
  public long[] after() { return copy(after); }

  /** Commits exactly once against the expected revision and request identity. */
  public Commit commit(long currentRevision, long appliedRequestId) {
    if (currentRevision != baseRevision) return Commit.CONFLICT;
    if (appliedRequestId == requestId) return Commit.DUPLICATE;
    return new Commit(CommitStatus.APPLIED, baseRevision + 1, requestId, after);
  }

  /** Returns the original state for an atomic rollback. */
  public long[] rollback() { return copy(before); }

  public static final class Commit {
    public static final Commit CONFLICT = new Commit(CommitStatus.CONFLICT, -1, -1, null);
    public static final Commit DUPLICATE = new Commit(CommitStatus.DUPLICATE, -1, -1, null);
    public final CommitStatus status;
    public final long revision, requestId;
    private final long[] state;
    private Commit(CommitStatus status, long revision, long requestId, long[] state) {
      this.status = status; this.revision = revision; this.requestId = requestId;
      this.state = copy(state);
    }
    private Commit(CommitStatus status, long revision, long requestId, long[] state, boolean ignored) {
      this(status, revision, requestId, state);
    }
    public long[] state() { return copy(state); }
  }

  public enum CommitStatus { APPLIED, CONFLICT, DUPLICATE }

  private static long[] copy(long[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
