package com.riiablo.net;

/** Immutable client movement command scheduled against the authoritative tick. */
public final class MovementIntent {
  public enum TargetKind { LOCATION, ENTITY }

  public final long sequence;
  public final long observedServerTick;
  public final long targetTick;
  /** Authoritative tick at which D2GS accepted this command; diagnostic only. */
  public final long receivedTick;
  public final boolean running;
  public final TargetKind targetKind;
  public final int x;
  public final int y;
  public final int targetType;
  public final int targetEntityId;

  private MovementIntent(long sequence, long observedServerTick, long targetTick,
      boolean running, TargetKind targetKind, int x, int y,
      int targetType, int targetEntityId, long receivedTick) {
    this.sequence = sequence;
    this.observedServerTick = observedServerTick;
    this.targetTick = targetTick;
    this.receivedTick = receivedTick;
    this.running = running;
    this.targetKind = targetKind;
    this.x = x;
    this.y = y;
    this.targetType = targetType;
    this.targetEntityId = targetEntityId;
  }

  public static MovementIntent location(long sequence, long observedServerTick,
      long targetTick, boolean running, int x, int y) {
    return new MovementIntent(sequence, observedServerTick, targetTick, running,
        TargetKind.LOCATION, x, y, 0, -1, 0L);
  }

  public static MovementIntent entity(long sequence, long observedServerTick,
      long targetTick, boolean running, int targetType, int targetEntityId) {
    return new MovementIntent(sequence, observedServerTick, targetTick, running,
        TargetKind.ENTITY, 0, 0, targetType, targetEntityId, 0L);
  }

  /** Returns an equivalent intent annotated with its server receive tick. */
  public MovementIntent withReceivedTick(long tick) {
    if (tick < 0L) throw new IllegalArgumentException("tick must be non-negative");
    return new MovementIntent(sequence, observedServerTick, targetTick, running,
        targetKind, x, y, targetType, targetEntityId, tick);
  }

  /** Fingerprint excludes sequence so unchanged commands need not repath each tick. */
  public long commandFingerprint() {
    long hash = 0xcbf29ce484222325L;
    hash = mix(hash, running ? 1 : 0);
    hash = mix(hash, targetKind.ordinal());
    hash = mix(hash, x);
    hash = mix(hash, y);
    hash = mix(hash, targetType);
    return mix(hash, targetEntityId);
  }

  /** Exact replay fingerprint includes scheduling metadata but not its own sequence. */
  long replayFingerprint() {
    long hash = commandFingerprint();
    hash = mix(hash, observedServerTick);
    return mix(hash, targetTick);
  }

  private static long mix(long hash, long value) {
    hash ^= value;
    return hash * 0x100000001b3L;
  }
}
