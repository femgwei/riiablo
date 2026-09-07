package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

class AuthoritativeTransformInterpolatorTest {
  private final Vector2 actual = new Vector2();

  @Test
  void interpolatesAtTheSameRateAtSixtyAndOneTwentyHertz() {
    AuthoritativeTransformInterpolator sixty = movingBuffer();
    AuthoritativeTransformInterpolator oneTwenty = movingBuffer();

    for (int i = 0; i < 3; i++) sixty.advance(1f / 60f);
    for (int i = 0; i < 6; i++) oneTwenty.advance(1f / 120f);

    assertEquals(4f, sixty.samplePosition(actual).x, 0.0001f);
    assertEquals(4f, oneTwenty.samplePosition(actual).x, 0.0001f);
  }

  @Test
  void usesServerTimeGapWhenAnIntermediatePacketIsMissing() {
    AuthoritativeTransformInterpolator buffer = movingBuffer();
    buffer.advance(0.04f);
    assertTrue(buffer.record(4, 1120, true, 12f, 0f,
        false, 0f, 0f));

    buffer.advance(0.04f);
    assertEquals(8f, buffer.samplePosition(actual).x, 0.0001f);
    buffer.advance(0.04f);
    assertEquals(12f, buffer.samplePosition(actual).x, 0.0001f);
  }

  @Test
  void rejectsOldSnapshotsWithoutRollingPresentationBack() {
    AuthoritativeTransformInterpolator buffer = movingBuffer();
    buffer.advance(0.02f);
    assertFalse(buffer.record(1, 1000, true, -10f, 0f,
        false, 0f, 0f));
    assertEquals(2f, buffer.samplePosition(actual).x, 0.0001f);
    assertEquals(2, buffer.tick());
  }

  @Test
  void snapsLargeWarpInsteadOfCrossingTheMap() {
    AuthoritativeTransformInterpolator buffer = movingBuffer();
    buffer.advance(0.04f);
    buffer.record(3, 1080, true, 20f, 0f,
        true, 0f, 1f);

    assertEquals(20f, buffer.samplePosition(actual).x, 0f);
    assertEquals(0f, buffer.sampleAngle(actual).x, 0.0001f);
    assertEquals(1f, actual.y, 0.0001f);
  }

  @Test
  void explicitWarpStateSnapsEvenForShortTeleport() {
    AuthoritativeTransformInterpolator buffer = movingBuffer();
    buffer.advance(0.04f);
    buffer.record(3, 1080, true, 6f, 0f,
        false, 0f, 0f, true);

    assertEquals(6f, buffer.samplePosition(actual).x, 0f);
  }

  @Test
  void legacyZeroTickSnapshotSnapsForProtocolCompatibility() {
    AuthoritativeTransformInterpolator buffer = movingBuffer();
    buffer.record(0, 0, true, 6f, 7f, false, 0f, 0f);

    assertEquals(new Vector2(6f, 7f), buffer.samplePosition(actual));
  }

  @Test
  void interpolatesAndNormalizesFacingVector() {
    AuthoritativeTransformInterpolator buffer = new AuthoritativeTransformInterpolator();
    buffer.record(1, 1000, false, 0f, 0f, true, 1f, 0f);
    buffer.record(2, 1040, false, 0f, 0f, true, 0f, 1f);
    buffer.advance(0.02f);

    buffer.sampleAngle(actual);
    assertEquals(0.7071f, actual.x, 0.001f);
    assertEquals(0.7071f, actual.y, 0.001f);
  }

  private static AuthoritativeTransformInterpolator movingBuffer() {
    AuthoritativeTransformInterpolator buffer = new AuthoritativeTransformInterpolator();
    buffer.record(1, 1000, true, 0f, 0f, true, 1f, 0f);
    buffer.record(2, 1040, true, 4f, 0f, true, 1f, 0f);
    return buffer;
  }
}
