package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Act5BaalWaveStateTest {
  @Test
  void nativeDelaysAndRoomClearGateAllFiveWaves() {
    Act5BaalWaveState state = new Act5BaalWaveState();
    assertTrue(state.start());
    assertFalse(state.start());
    assertEquals(Act5BaalWaveState.NONE, state.tick(false));
    for (int wave = 0; wave < Act5BaalQuest.WAVE_COUNT; wave++) {
      assertEquals(Act5BaalWaveState.NONE, state.tick(true));
      assertEquals(Act5BaalQuest.PRE_WAVE_DELAY_TICKS, state.delayTicks());
      for (int tick = 1; tick < Act5BaalQuest.PRE_WAVE_DELAY_TICKS; tick++) {
        assertEquals(Act5BaalWaveState.NONE, state.tick(true));
      }
      assertEquals(wave, state.tick(true));
      assertEquals(wave + 1, state.wave());
      assertEquals(wave, state.activeWaveIndex());

      // The 100-frame post-spawn lock expires, but a surviving hostile keeps
      // the state waiting without starting the next 250-frame delay.
      for (int tick = 1; tick < Act5BaalQuest.POST_SPAWN_LOCK_TICKS; tick++) {
        assertEquals(Act5BaalWaveState.NONE, state.tick(false));
      }
      assertEquals(Act5BaalWaveState.NONE, state.tick(false));
      assertEquals(0, state.delayTicks());
    }

    assertEquals(Act5BaalWaveState.NONE, state.tick(true));
    for (int tick = 1; tick < Act5BaalQuest.PRE_WAVE_DELAY_TICKS; tick++) {
      assertEquals(Act5BaalWaveState.NONE, state.tick(true));
    }
    assertEquals(Act5BaalWaveState.SPAWN_BAAL, state.tick(true));
    assertTrue(state.finished());
    assertEquals(-1, state.activeWaveIndex());
    assertEquals(Act5BaalWaveState.NONE, state.tick(true));
  }

  @Test
  void usesAllFiveNativeSuperUniqueSubjectsAndBoundedGroupRolls() {
    assertArrayEquals(new int[] {61, 62, 63, 64, 65},
        Act5BaalQuest.WAVE_SUPER_UNIQUES);
    assertArrayEquals(new int[] {5, 3, 5, 8, 5}, Act5BaalQuest.WAVE_MINIONS);
    for (int wave = 0; wave < Act5BaalQuest.WAVE_COUNT; wave++) {
      int count = Act5BaalQuest.minionCount(3, 6, 0xBaa1, wave);
      assertTrue(count >= 3 && count <= 6);
      assertEquals(count, Act5BaalQuest.minionCount(3, 6, 0xBaa1, wave));
    }
  }

  @Test
  void baalHighPriestWaveLeaderIsNotFinalBaal() {
    assertFalse(Act5BaalQuest.isBaalMonster(557, "baalhighpriest"));
    assertTrue(Act5BaalQuest.isBaalMonster(Act5BaalQuest.BAAL_CLASS, "BaalCrab"));
    assertTrue(Act5BaalQuest.isBaalThroneMonster(
        Act5BaalQuest.BAAL_THRONE_CLASS, "BaalThrone"));
    assertFalse(Act5BaalQuest.isBaalMonster(
        Act5BaalQuest.BAAL_THRONE_CLASS, "BaalThrone"));
  }

  @Test
  void mapsFixedBaalSubjectModsToRuntimeAffixes() {
    assertEquals(com.riiablo.engine.server.monster.MonsterAffix.FIRE_ENCHANTED,
        Act5BaalQuest.nativeSuperUniqueAffixes(new int[] {9, 0, 0}));
    assertEquals(com.riiablo.engine.server.monster.MonsterAffix.POISON_ENCHANTED,
        Act5BaalQuest.nativeSuperUniqueAffixes(new int[] {23, 0, 0}));
    assertEquals(com.riiablo.engine.server.monster.MonsterAffix.SPECTRAL_HIT,
        Act5BaalQuest.nativeSuperUniqueAffixes(new int[] {27, 0, 0}));
  }

  @Test
  void reconnectSnapshotResumesDelayWithoutReplayingWave() {
    Act5BaalWaveState original = new Act5BaalWaveState();
    assertTrue(original.start());
    assertEquals(Act5BaalWaveState.NONE, original.tick(true));
    for (int i = 0; i < 37; i++) {
      assertEquals(Act5BaalWaveState.NONE, original.tick(true));
    }

    Act5BaalWaveState restored = new Act5BaalWaveState();
    restored.restore(original.snapshot());
    assertEquals(original.wave(), restored.wave());
    assertEquals(original.delayTicks(), restored.delayTicks());
    while (restored.delayTicks() > 1) {
      assertEquals(Act5BaalWaveState.NONE, restored.tick(true));
    }
    assertEquals(0, restored.tick(true));
    assertEquals(1, restored.wave());

    Act5BaalWaveState afterSpawn = new Act5BaalWaveState();
    afterSpawn.restore(restored.snapshot());
    for (int tick = 0; tick < Act5BaalQuest.POST_SPAWN_LOCK_TICKS; tick++) {
      assertEquals(Act5BaalWaveState.NONE, afterSpawn.tick(true));
    }
    assertEquals(1, afterSpawn.wave(), "restoring post-spawn state must not replay wave zero");
    assertEquals(Act5BaalQuest.PRE_WAVE_DELAY_TICKS, afterSpawn.delayTicks());
  }
}
