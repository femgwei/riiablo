package com.riiablo.engine;

import java.util.Objects;

/** Immutable local simulation command; transport packets are adapted into this shape later. */
public final class SimulationCommandEnvelope {
  public enum Authority { LOCAL_INPUT, NETWORK_INPUT, SYSTEM }

  public final long tick;
  public final long sequence;
  /** Stable actor identity used by authoritative command admission. */
  public final int actorId;
  /** Client supplied idempotency key; zero means local non-replayable input. */
  public final long requestId;
  /** Stable target identity, or {@code -1} when the command has no target. */
  public final int targetEntityId;
  /** Source trust level used by the admission boundary. */
  public final Authority authority;
  public final String source;
  public final String kind;
  public final Object payload;

  SimulationCommandEnvelope(long tick, long sequence, int actorId, long requestId,
      int targetEntityId, Authority authority, String source, String kind, Object payload) {
    this.tick = tick;
    this.sequence = sequence;
    this.actorId = actorId;
    this.requestId = requestId;
    this.targetEntityId = targetEntityId;
    this.authority = authority;
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
    private int actorId = -1;
    private long requestId;
    private int targetEntityId = -1;
    private Authority authority = Authority.LOCAL_INPUT;

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

    public Builder actor(int actorId) {
      if (actorId < 0) throw new IllegalArgumentException("actorId must be non-negative");
      this.actorId = actorId;
      return this;
    }

    public Builder request(long requestId) {
      if (requestId <= 0L) throw new IllegalArgumentException("requestId must be positive");
      this.requestId = requestId;
      return this;
    }

    public Builder target(int targetEntityId) {
      if (targetEntityId < 0) throw new IllegalArgumentException("targetEntityId must be non-negative");
      this.targetEntityId = targetEntityId;
      return this;
    }

    public Builder authority(Authority authority) {
      this.authority = Objects.requireNonNull(authority, "authority");
      return this;
    }

    /** Builds an envelope with a caller-owned stable sequence for protocol adapters. */
    public SimulationCommandEnvelope build(long sequence) {
      if (tick < 1L) throw new IllegalStateException("tick must be set");
      return new SimulationCommandEnvelope(tick, sequence, actorId, requestId,
          targetEntityId, authority, source, kind, payload);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
    return value;
  }
}
