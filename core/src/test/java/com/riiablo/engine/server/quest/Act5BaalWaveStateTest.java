package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5BaalWaveStateTest {
  @Test
  void fiveWavesAdvanceOnlyAfterAllMembersDie() {
    Act5BaalWaveState state = new Act5BaalWaveState();
    assertEquals(1, state.startWave());
    assertFalse(state.canAdvance());
    for (int wave = 1; wave <= Act5BaalQuest.WAVE_COUNT; wave++) {
      while (state.defeatOne()) { }
      if (wave < Act5BaalQuest.WAVE_COUNT) {
        assertTrue(state.canAdvance());
        assertEquals(wave + 1, state.advanceWave());
      }
    }
    assertTrue(state.finished());
    assertEquals(Act5BaalQuest.WAVE_COUNT, state.startWave());
  }
}
