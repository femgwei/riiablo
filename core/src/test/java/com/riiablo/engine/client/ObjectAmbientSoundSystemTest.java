package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObjectAmbientSoundSystemTest {
  @Test
  void resolvesNativeInvisibleRiverMarkers() {
    assertEquals(ObjectAmbientSoundSystem.RIVER_SOUND,
        ObjectAmbientSoundSystem.soundId(ObjectAmbientSoundSystem.INVISIBLE_RIVER_SOUND_1));
    assertEquals(ObjectAmbientSoundSystem.RIVER_SOUND,
        ObjectAmbientSoundSystem.soundId(ObjectAmbientSoundSystem.INVISIBLE_RIVER_SOUND_2));
    assertEquals(-1, ObjectAmbientSoundSystem.soundId(39));
  }

  @Test
  void cullsSourcesOutsideTheirAudibleRadius() {
    assertTrue(ObjectAmbientSoundSystem.withinRadius(0f, 20f));
    assertTrue(ObjectAmbientSoundSystem.withinRadius(399f, 20f));
    assertFalse(ObjectAmbientSoundSystem.withinRadius(400f, 20f));
    assertFalse(ObjectAmbientSoundSystem.withinRadius(1f, 0f));
  }
}
