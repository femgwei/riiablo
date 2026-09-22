package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
