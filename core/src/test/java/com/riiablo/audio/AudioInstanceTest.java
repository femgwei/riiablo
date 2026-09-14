package com.riiablo.audio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.badlogic.gdx.utils.Pools;
import org.junit.jupiter.api.Test;

/** Regression coverage for sounds cancelled while their asset is still loading. */
class AudioInstanceTest {
  @Test
  void unloadedInstanceCanBeStoppedAndUpdatedSafely() {
    Audio.Instance instance = Audio.Instance.obtain(null, null, -1);
    try {
      assertFalse(instance.isLoaded());
      assertDoesNotThrow(() -> {
        instance.stop();
        instance.play();
        instance.setVolume(0.5f);
      });
    } finally {
      Pools.free(instance);
    }
  }
}
