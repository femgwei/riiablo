package com.riiablo.engine.server.quest;

/** Small deterministic state machine for the five native Throne waves. */
public final class Act5BaalWaveState {
  private int wave;
  private int alive;

  public int wave() {
    return wave;
  }

  public int alive() {
    return alive;
  }

  public boolean started() {
    return wave > 0;
  }

  public boolean finished() {
    return wave == Act5BaalQuest.WAVE_COUNT && alive == 0;
  }

  public boolean canStart() {
    return wave == 0;
  }

  public boolean canAdvance() {
    return wave > 0 && alive == 0 && wave < Act5BaalQuest.WAVE_COUNT;
  }

  public int startWave() {
    if (!canStart()) return wave;
    wave = 1;
    alive = Act5BaalQuest.MONSTERS_PER_WAVE;
    return wave;
  }

  public int advanceWave() {
    if (!canAdvance()) return wave;
    wave++;
    alive = Act5BaalQuest.MONSTERS_PER_WAVE;
    return wave;
  }

  public boolean defeatOne() {
    if (alive <= 0) return false;
    alive--;
    return true;
  }
}
