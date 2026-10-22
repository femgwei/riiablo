package com.riiablo.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Tick-ordered event handoff kept separate from the presentation EventSystem. */
public final class SimulationEventQueue {
  public static final class Event {
    public final long tick;
    public final long sequence;
    public final String kind;
    public final Object payload;

    private Event(long tick, long sequence, String kind, Object payload) {
      this.tick = tick; this.sequence = sequence; this.kind = kind; this.payload = payload;
    }
  }

  private static final Comparator<Event> ORDER =
      Comparator.comparingLong((Event e) -> e.tick).thenComparingLong(e -> e.sequence);
  private final List<Event> pending = new ArrayList<>();
  private long sequence;

  public Event publish(long tick, String kind, Object payload) {
    if (tick < 1L) throw new IllegalArgumentException("tick must be positive");
    if (kind == null || kind.trim().isEmpty()) throw new IllegalArgumentException("kind");
    Event event = new Event(tick, ++sequence, kind, Objects.requireNonNull(payload, "payload"));
    pending.add(event);
    return event;
  }

  public int drain(long tick, Consumer<Event> consumer) {
    if (tick < 1L) throw new IllegalArgumentException("tick must be positive");
    if (consumer == null) throw new NullPointerException("consumer");
    pending.sort(ORDER);
    int applied = 0;
    for (int i = 0; i < pending.size();) {
      Event event = pending.get(i);
      if (event.tick > tick) break;
      pending.remove(i);
      if (event.tick == tick) { consumer.accept(event); applied++; }
    }
    return applied;
  }

  public int pendingCount() { return pending.size(); }
  public void clear() { pending.clear(); }
}
