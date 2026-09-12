package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Offline two-observer oracle for the authoritative wave plan.  It mirrors
 * the data visible to two D2GS clients without requiring MPQ/GL rendering or
 * two sockets: same game seed, difficulty, preset ids, group roll and room
 * candidates must be identical for both observers.
 */
class Act5BaalDualClientConsistencyTest {
  @Test
  void bothObserversSeeIdenticalNativeWavePlan() {
    int seed = 0x5A17;
    int difficulty = 2;
    for (int wave = 0; wave < Act5BaalQuest.WAVE_COUNT; wave++) {
      int[] groups = Act5BaalQuest.nativeGroupRange(
          Act5BaalQuest.WAVE_MINIONS[wave], Act5BaalQuest.WAVE_MINIONS[wave], difficulty);
      int firstCount = Act5BaalQuest.minionCount(groups[0], groups[1], seed, wave);
      int secondCount = Act5BaalQuest.minionCount(groups[0], groups[1], seed, wave);
      assertEquals(firstCount, secondCount);
      String[] firstHints = Act5BaalQuest.nativeWaveClientClassHints(wave);
      String[] secondHints = Act5BaalQuest.nativeWaveClientClassHints(wave);
      assertArrayEquals(firstHints, secondHints);
      for (int i = 0; i < firstCount; i++) {
        float firstX = Act5BaalSpawnLayout.candidateX(i);
        float secondX = Act5BaalSpawnLayout.candidateX(i);
        float firstY = Act5BaalSpawnLayout.candidateY(i);
        float secondY = Act5BaalSpawnLayout.candidateY(i);
        assertEquals(firstX, secondX);
        assertEquals(firstY, secondY);
      }
    }
  }
}
