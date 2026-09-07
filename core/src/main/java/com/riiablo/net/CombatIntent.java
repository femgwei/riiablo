package com.riiablo.net;

/** Immutable client combat command scheduled against an authoritative tick. */
public final class CombatIntent {
  public final long sequence;
  public final long observedServerTick;
  public final long targetTick;
  public final int skillId;
  public final int targetEntityId;
  public final float targetX;
  public final float targetY;

  public CombatIntent(long sequence, long observedServerTick, long targetTick,
      int skillId, int targetEntityId, float targetX, float targetY) {
    this.sequence = sequence;
    this.observedServerTick = observedServerTick;
    this.targetTick = targetTick;
    this.skillId = skillId;
    this.targetEntityId = targetEntityId;
    this.targetX = targetX;
    this.targetY = targetY;
  }

  long replayFingerprint() {
    long hash = 0xcbf29ce484222325L;
    hash = mix(hash, observedServerTick);
    hash = mix(hash, targetTick);
    hash = mix(hash, skillId);
    hash = mix(hash, targetEntityId);
    hash = mix(hash, Float.floatToIntBits(targetX));
    return mix(hash, Float.floatToIntBits(targetY));
  }

  private static long mix(long hash, long value) {
    hash ^= value;
    return hash * 0x100000001b3L;
  }
}
