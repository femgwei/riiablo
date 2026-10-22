package com.riiablo.engine;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Consumer;

/** Defers entity/component structure mutation until the end of a simulation tick. */
public final class StructuralChangeQueue {
  @FunctionalInterface public interface Change { void apply(); }
  private ArrayDeque<Change> pending = new ArrayDeque<>();

  public void enqueue(Change change) { pending.add(Objects.requireNonNull(change, "change")); }

  /** Flushes a stable batch; changes enqueued while flushing wait for the next barrier. */
  public int flush() {
    ArrayDeque<Change> batch = pending;
    pending = new ArrayDeque<>();
    int applied = 0;
    while (!batch.isEmpty()) { batch.removeFirst().apply(); applied++; }
    return applied;
  }

  public int flush(Consumer<Change> consumer) {
    Objects.requireNonNull(consumer, "consumer");
    ArrayDeque<Change> batch = pending;
    pending = new ArrayDeque<>();
    int applied = 0;
    while (!batch.isEmpty()) { consumer.accept(batch.removeFirst()); applied++; }
    return applied;
  }

  public int pendingCount() { return pending.size(); }
  public void clear() { pending.clear(); }
}
