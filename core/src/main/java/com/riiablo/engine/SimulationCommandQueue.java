package com.riiablo.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;

/** Deterministic tick admission for local input and server-adapted commands. */
public final class SimulationCommandQueue {
  private static final Comparator<SimulationCommandEnvelope> ORDER =
      Comparator.comparingLong((SimulationCommandEnvelope c) -> c.tick)
          .thenComparingLong(c -> c.sequence);
  private final List<SimulationCommandEnvelope> pending = new ArrayList<>();
  private final SimulationCommandAdmission admission;
  private final Set<RequestKey> acceptedRequests = new HashSet<>();
  private long sequence;
  private long lastDrainedTick;
  private SimulationCommandAdmission.Rejection lastRejection =
      SimulationCommandAdmission.Rejection.NONE;

  public SimulationCommandQueue() {
    this(SimulationCommandAdmission.local());
  }

  public SimulationCommandQueue(SimulationCommandAdmission admission) {
    this.admission = Objects.requireNonNull(admission, "admission");
  }

  public SimulationCommandEnvelope submit(SimulationCommandEnvelope.Builder builder) {
    if (builder == null) throw new NullPointerException("builder");
    SimulationCommandEnvelope command = builder.build(++sequence);
    lastRejection = admission.validate(command, lastDrainedTick);
    if (lastRejection != SimulationCommandAdmission.Rejection.NONE) return null;
    if (command.requestId > 0L) {
      RequestKey key = new RequestKey(command.source, command.actorId, command.requestId);
      if (!acceptedRequests.add(key)) {
        lastRejection = SimulationCommandAdmission.Rejection.DUPLICATE_REQUEST;
        return null;
      }
    }
    pending.add(command);
    return command;
  }

  public int drain(long tick, Consumer<SimulationCommandEnvelope> consumer) {
    if (tick < 1L) throw new IllegalArgumentException("tick must be positive");
    if (consumer == null) throw new NullPointerException("consumer");
    pending.sort(ORDER);
    int applied = 0;
    for (int i = 0; i < pending.size();) {
      SimulationCommandEnvelope command = pending.get(i);
      if (command.tick > tick) break;
      pending.remove(i);
      if (command.tick == tick) {
        consumer.accept(command);
        applied++;
      }
    }
    if (tick > lastDrainedTick) lastDrainedTick = tick;
    return applied;
  }

  public int pendingCount() { return pending.size(); }
  public long lastDrainedTick() { return lastDrainedTick; }
  public SimulationCommandAdmission.Rejection lastRejection() { return lastRejection; }
  public void clear() { pending.clear(); acceptedRequests.clear(); lastRejection =
      SimulationCommandAdmission.Rejection.NONE; }

  private static final class RequestKey {
    final String source;
    final int actorId;
    final long requestId;
    RequestKey(String source, int actorId, long requestId) {
      this.source = source; this.actorId = actorId; this.requestId = requestId;
    }
    @Override public int hashCode() {
      int result = source.hashCode();
      result = 31 * result + actorId;
      return 31 * result + Long.hashCode(requestId);
    }
    @Override public boolean equals(Object other) {
      if (!(other instanceof RequestKey)) return false;
      RequestKey key = (RequestKey) other;
      return actorId == key.actorId && requestId == key.requestId && source.equals(key.source);
    }
  }
}
