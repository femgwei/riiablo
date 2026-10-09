package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Interpolation;
import org.junit.jupiter.api.Test;

class SoundEmitterHandlerTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void attenuatesLinearlyWithinConfiguredRadius() {
    assertEquals(1f, SoundEmitterHandler.spatialGain(0f, 20f, Interpolation.linear), EPSILON);
    assertEquals(0.5f, SoundEmitterHandler.spatialGain(10f, 20f, Interpolation.linear), EPSILON);
    assertEquals(0f, SoundEmitterHandler.spatialGain(20f, 20f, Interpolation.linear), EPSILON);
    assertEquals(0f, SoundEmitterHandler.spatialGain(10f, 0f, Interpolation.linear), EPSILON);
  }

  @Test
  void preservesExistingEmitterInterpolation() {
    assertEquals(0.25f,
        SoundEmitterHandler.spatialGain(10f, 20f, Interpolation.pow2In), EPSILON);
  }

  @Test
  void keepsAmbientSoundsAtFullVolumeBeforeNativeFalloffStarts() {
    assertEquals(1f,
        SoundEmitterHandler.spatialGain(12.5f, 12.5f, 46.875f, Interpolation.linear), EPSILON);
    assertEquals(0.5f,
        SoundEmitterHandler.spatialGain(29.6875f, 12.5f, 46.875f, Interpolation.linear), EPSILON);
    assertEquals(0f,
        SoundEmitterHandler.spatialGain(46.875f, 12.5f, 46.875f, Interpolation.linear), EPSILON);
  }
}
