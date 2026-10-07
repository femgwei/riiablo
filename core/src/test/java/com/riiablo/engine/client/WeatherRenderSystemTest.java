package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.SimulationClock;

class WeatherRenderSystemTest {
  @Test
  void selectsWeatherFromLevelsRainAndAct() {
    assertEquals(WeatherRenderSystem.Mode.NONE, WeatherRenderSystem.modeFor(null));

    Levels.Entry level = new Levels.Entry();
    assertEquals(WeatherRenderSystem.Mode.NONE, WeatherRenderSystem.modeFor(level));

    level.Rain = true;
    level.Act = 0;
    assertEquals(WeatherRenderSystem.Mode.RAIN, WeatherRenderSystem.modeFor(level));

    level.Act = 4;
    assertEquals(WeatherRenderSystem.Mode.SNOW, WeatherRenderSystem.modeFor(level));

    level.IsInside = true;
    assertEquals(WeatherRenderSystem.Mode.NONE, WeatherRenderSystem.modeFor(level));
  }

  @Test
  void initializationIsDeterministicForTheSameSeed() {
    WeatherRenderSystem.ParticleField first = field(1234L, WeatherRenderSystem.Mode.RAIN);
    WeatherRenderSystem.ParticleField second = field(1234L, WeatherRenderSystem.Mode.RAIN);

    assertArrayEquals(first.x, second.x);
    assertArrayEquals(first.y, second.y);
    assertArrayEquals(first.velocityX, second.velocityX);
    assertArrayEquals(first.velocityY, second.velocityY);
  }

  @Test
  void advancesOnlyOnNativeTwentyFiveHertzTicks() {
    WeatherRenderSystem.ParticleField particles = field(2L, WeatherRenderSystem.Mode.RAIN);
    float initialX = particles.x[0];

    assertEquals(0, particles.advance(SimulationClock.STEP_SECONDS * 0.5f));
    assertEquals(initialX, particles.x[0]);
    assertEquals(0.5f, particles.alpha(), 0.0001f);

    assertEquals(1, particles.advance(SimulationClock.STEP_SECONDS * 0.5f));
    assertNotEquals(initialX, particles.x[0]);
    assertEquals(0f, particles.alpha(), 0.0001f);
  }

  @Test
  void rainRespawnsAtTopWithoutInterpolatingAcrossTheViewport() {
    WeatherRenderSystem.ParticleField particles = field(3L, WeatherRenderSystem.Mode.RAIN);
    particles.x[0] = -100f;
    particles.y[0] = -100f;

    particles.step();

    assertTrue(particles.y[0] >= 480f);
    assertEquals(particles.x[0], particles.previousX[0]);
    assertEquals(particles.y[0], particles.previousY[0]);
  }

  @Test
  void snowUsesEightDeterministicTwoLineShapes() {
    assertEquals(WeatherRenderSystem.SNOW_SHAPE_COUNT,
        WeatherRenderSystem.SNOW_SEGMENTS.length);
    for (float[] segments : WeatherRenderSystem.SNOW_SEGMENTS) {
      assertEquals(WeatherRenderSystem.SNOW_SEGMENTS_PER_PARTICLE * 4, segments.length);
    }

    WeatherRenderSystem.ParticleField particles = field(4L, WeatherRenderSystem.Mode.SNOW);
    for (int shape : particles.shape) {
      assertTrue(shape >= 0 && shape < WeatherRenderSystem.SNOW_SHAPE_COUNT);
    }
  }

  private static WeatherRenderSystem.ParticleField field(
      long seed, WeatherRenderSystem.Mode mode) {
    WeatherRenderSystem.ParticleField particles =
        new WeatherRenderSystem.ParticleField(seed);
    particles.configure(mode, 640f, 480f);
    return particles;
  }
}
