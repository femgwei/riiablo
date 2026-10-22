package com.riiablo.engine;

import java.util.Objects;

/** Immutable local simulation command; transport packets are adapted into this shape later. */
public final class SimulationCommandEnvelope {
  public final long tick;
  public final long sequence;
  public final String source;
  public final String kind;
  public final Object payload;

  SimulationCommandEnvelope(long tick, long sequence, String source, String kind, Object payload) {
    this.tick = tick;
    this.sequence = sequence;
    this.source = source;
    this.kind = kind;
    this.payload = payload;
  }

  public static Builder builder(String source, String kind, Object payload) {
    return new Builder(source, kind, payload);
  }

  public static final class Builder {
    private final String source;
    private final String kind;
    private final Object payload;
    private long tick;

    private Builder(String source, String kind, Object payload) {
      this.source = requireText(source, "source");
      this.kind = requireText(kind, "kind");
      this.payload = Objects.requireNonNull(payload, "payload");
    }

    public Builder tick(long tick) {
      if (tick < 1L) throw new IllegalArgumentException("tick must be positive");
      this.tick = tick;
      return this;
    }

    SimulationCommandEnvelope build(long sequence) {
      if (tick < 1L) throw new IllegalStateException("tick must be set");
      return new SimulationCommandEnvelope(tick, sequence, source, kind, payload);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
    return value;
  }
}
