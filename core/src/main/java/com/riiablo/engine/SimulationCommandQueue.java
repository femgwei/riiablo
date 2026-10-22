package com.riiablo.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Deterministic tick admission for local input and server-adapted commands. */
public final class SimulationCommandQueue {
  private static final Comparator<SimulationCommandEnvelope> ORDER =
      Comparator.comparingLong((SimulationCommandEnvelope c) -> c.tick)
          .thenComparingLong(c -> c.sequence);
  private final List<SimulationCommandEnvelope> pending = new ArrayList<>();
  private long sequence;
  private long lastDrainedTick;

  public SimulationCommandEnvelope submit(SimulationCommandEnvelope.Builder builder) {
    if (builder == null) throw new NullPointerException("builder");
    SimulationCommandEnvelope command = builder.build(++sequence);
    if (command.tick <= lastDrainedTick) return null;
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
  public void clear() { pending.clear(); }
}
