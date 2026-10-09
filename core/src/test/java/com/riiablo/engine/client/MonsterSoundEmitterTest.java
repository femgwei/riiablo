package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.audio.MonsterAudio;
import org.junit.jupiter.api.Test;

class MonsterSoundEmitterTest {
  @Test
  void distantMonstersAreSilent() {
    assertTrue(MonsterSoundEmitter.isAudible(0f, true));
    assertTrue(MonsterSoundEmitter.isAudible(
        MonsterSoundEmitter.AUDIBLE_RADIUS2, true));
    assertFalse(MonsterSoundEmitter.isAudible(
        MonsterSoundEmitter.AUDIBLE_RADIUS2 + 0.01f, true));
  }

  @Test
  void soundsDoNotCrossTownAndOutdoorZoneBoundary() {
    assertFalse(MonsterSoundEmitter.isAudible(1f, false));
  }

  @Test
  void monsterSoundsFadeWithDistanceBeforeTheAudibleBoundary() {
    assertEquals(1f, MonsterAudio.spatialGain(0f, true), 0.0001f);
    assertEquals(0.5f,
        MonsterAudio.spatialGain((MonsterAudio.AUDIBLE_RADIUS / 2f)
            * (MonsterAudio.AUDIBLE_RADIUS / 2f), true), 0.0001f);
    assertEquals(0f,
        MonsterAudio.spatialGain(MonsterAudio.AUDIBLE_RADIUS2, true), 0.0001f);
    assertEquals(0f, MonsterAudio.spatialGain(1f, false), 0.0001f);
  }
}
