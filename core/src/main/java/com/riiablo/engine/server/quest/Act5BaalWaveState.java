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
  private boolean waveSpawnRequested;
  private boolean waveSpawned;
  private boolean waveCleared;

  /** Primitive-only game snapshot suitable for reconnect/room migration. */
  public static final class Snapshot {
    public final int phase;
    public final int wave;
    public final int delayTicks;
    public final boolean waveSpawnRequested;
    public final boolean waveSpawned;
    public final boolean waveCleared;

    public Snapshot(int phase, int wave, int delayTicks) {
      this(phase, wave, delayTicks, false, false, false);
    }

    public Snapshot(int phase, int wave, int delayTicks,
        boolean waveSpawnRequested, boolean waveSpawned, boolean waveCleared) {
      this.phase = phase;
      this.wave = wave;
      this.delayTicks = delayTicks;
      this.waveSpawnRequested = waveSpawnRequested;
      this.waveSpawned = waveSpawned;
      this.waveCleared = waveCleared;
    }
  }

  public int wave() {
    return wave;
  }

  /**
   * Returns the wave whose preset members may still be present in the Throne.
   * The counter is incremented at the same tick that a wave is requested, so
   * the active preset is always {@code wave - 1}.  Once the state reaches DONE
   * there is no wave to rebuild; Baal is spawned instead.
   */
  public int activeWaveIndex() {
    return phase == Phase.IDLE || phase == Phase.DONE || wave <= 0
        ? -1 : wave - 1;
  }

  public int delayTicks() {
    return delayTicks;
  }

  public boolean waveSpawnRequested() {
    return waveSpawnRequested;
  }

  public boolean waveSpawned() {
    return waveSpawned;
  }

  public boolean waveCleared() {
    return waveCleared;
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
    waveSpawnRequested = false;
    waveSpawned = false;
    waveCleared = false;
    return true;
  }

  public void markWaveSpawned() {
    if (activeWaveIndex() >= 0) waveSpawned = true;
  }

  public void markWaveCleared() {
    if (activeWaveIndex() >= 0) waveCleared = true;
  }

  /** True only for a requested wave that has not been explicitly cleared. */
  public boolean needsWaveRecovery() {
    return activeWaveIndex() >= 0 && waveSpawnRequested && !waveCleared;
  }

  public Snapshot snapshot() {
    return new Snapshot(phase.ordinal(), wave, delayTicks,
        waveSpawnRequested, waveSpawned, waveCleared);
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
    waveSpawnRequested = snapshot.waveSpawnRequested;
    waveSpawned = snapshot.waveSpawned;
    waveCleared = snapshot.waveCleared;
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
      waveSpawnRequested = true;
      waveSpawned = false;
      waveCleared = false;
      phase = Phase.POST_SPAWN_LOCK;
      delayTicks = Act5BaalQuest.POST_SPAWN_LOCK_TICKS;
      return result;
    }
    return NONE;
  }
}
