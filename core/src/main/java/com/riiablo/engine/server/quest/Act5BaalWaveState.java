package com.riiablo.engine.server.quest;

/** Deterministic timing state for D2MOO's BaalThrone AI. */
public final class Act5BaalWaveState {
  public static final int NONE = -1;
  public static final int SPAWN_BAAL = Act5BaalQuest.WAVE_COUNT;

  private enum Phase { IDLE, WAIT_CLEAR, PRE_WAVE_DELAY, POST_SPAWN_LOCK, DONE }

  private Phase phase = Phase.IDLE;
  /** Number of waves already spawned (0..5). */
  private int wave;
  private int delayTicks;

  /** Primitive-only game snapshot suitable for reconnect/room migration. */
  public static final class Snapshot {
    public final int phase;
    public final int wave;
    public final int delayTicks;

    public Snapshot(int phase, int wave, int delayTicks) {
      this.phase = phase;
      this.wave = wave;
      this.delayTicks = delayTicks;
    }
  }

  public int wave() {
    return wave;
  }

  public int delayTicks() {
    return delayTicks;
  }

  public boolean started() {
    return phase != Phase.IDLE;
  }

  public boolean finished() {
    return phase == Phase.DONE;
  }

  public boolean canStart() {
    return phase == Phase.IDLE;
  }

  public boolean start() {
    if (!canStart()) return false;
    phase = Phase.WAIT_CLEAR;
    return true;
  }

  public Snapshot snapshot() {
    return new Snapshot(phase.ordinal(), wave, delayTicks);
  }

  /** Restores one game-session snapshot without replaying an already spawned wave. */
  public void restore(Snapshot snapshot) {
    if (snapshot == null) return;
    if (snapshot.phase < 0 || snapshot.phase >= Phase.values().length) {
      throw new IllegalArgumentException("invalid Baal wave phase: " + snapshot.phase);
    }
    if (snapshot.wave < 0 || snapshot.wave > Act5BaalQuest.WAVE_COUNT) {
      throw new IllegalArgumentException("invalid Baal wave index: " + snapshot.wave);
    }
    if (snapshot.delayTicks < 0
        || snapshot.delayTicks > Math.max(Act5BaalQuest.PRE_WAVE_DELAY_TICKS,
            Act5BaalQuest.POST_SPAWN_LOCK_TICKS)) {
      throw new IllegalArgumentException("invalid Baal wave delay: " + snapshot.delayTicks);
    }
    Phase restoredPhase = Phase.values()[snapshot.phase];
    if ((restoredPhase == Phase.IDLE || restoredPhase == Phase.DONE)
        && snapshot.delayTicks != 0) {
      throw new IllegalArgumentException("idle/done Baal wave state cannot have a delay");
    }
    phase = restoredPhase;
    wave = snapshot.wave;
    delayTicks = snapshot.delayTicks;
  }

  /** Advances one authoritative 25 Hz frame. Returns wave index 0..4,
   * {@link #SPAWN_BAAL}, or {@link #NONE}. */
  public int tick(boolean throneClear) {
    if (phase == Phase.IDLE || phase == Phase.DONE) return NONE;
    if (phase == Phase.POST_SPAWN_LOCK) {
      if (--delayTicks > 0) return NONE;
      delayTicks = 0;
      phase = Phase.WAIT_CLEAR;
    }
    if (phase == Phase.WAIT_CLEAR) {
      if (!throneClear) return NONE;
      phase = Phase.PRE_WAVE_DELAY;
      delayTicks = Act5BaalQuest.PRE_WAVE_DELAY_TICKS;
      return NONE;
    }
    if (phase == Phase.PRE_WAVE_DELAY) {
      if (delayTicks > 0 && --delayTicks > 0) return NONE;
      delayTicks = 0;
      if (!throneClear) return NONE;
      if (wave >= Act5BaalQuest.WAVE_COUNT) {
        phase = Phase.DONE;
        return SPAWN_BAAL;
      }
      int result = wave++;
      phase = Phase.POST_SPAWN_LOCK;
      delayTicks = Act5BaalQuest.POST_SPAWN_LOCK_TICKS;
      return result;
    }
    return NONE;
  }
}
