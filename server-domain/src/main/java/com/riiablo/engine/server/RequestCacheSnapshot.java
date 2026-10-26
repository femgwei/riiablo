package com.riiablo.engine.server;

/** Immutable portable view of one completed request-cache entry. */
public final class RequestCacheSnapshot {
  public final int connectionId;
  public final long requestId;
  public final String kind;
  public final String intent;
  private final byte[] response;

  public RequestCacheSnapshot(int connectionId, long requestId, String kind,
      String intent, byte[] response) {
    if (connectionId < 0 || requestId <= 0 || kind == null || kind.isEmpty()
        || intent == null || response == null) throw new IllegalArgumentException("request cache snapshot");
    this.connectionId = connectionId;
    this.requestId = requestId;
    this.kind = kind;
    this.intent = intent;
    this.response = response.clone();
  }

  public byte[] response() { return response.clone(); }
}
