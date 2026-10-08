package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
  void rainAmbienceTracksOnlyVisibleRainIntensity() {
    assertEquals(0f,
        WeatherRenderSystem.rainVolume(WeatherRenderSystem.Mode.RAIN, 0f));
    assertEquals(0.5f,
        WeatherRenderSystem.rainVolume(WeatherRenderSystem.Mode.RAIN, 0.5f));
    assertEquals(1f,
        WeatherRenderSystem.rainVolume(WeatherRenderSystem.Mode.RAIN, 2f));
    assertEquals(0f,
        WeatherRenderSystem.rainVolume(WeatherRenderSystem.Mode.SNOW, 1f));
    assertEquals(0f,
        WeatherRenderSystem.rainVolume(WeatherRenderSystem.Mode.NONE, 1f));
  }

  @Test
  void areaWeatherTransitionFadesRainOutAndBackIn() {
    WeatherRenderSystem.WeatherTransition transition =
        new WeatherRenderSystem.WeatherTransition();

    assertEquals(WeatherRenderSystem.Mode.RAIN,
        transition.update(WeatherRenderSystem.Mode.RAIN, 1f, 0.5f));
    assertEquals(1f, transition.intensity, 0.0001f);

    assertEquals(WeatherRenderSystem.Mode.RAIN,
        transition.update(WeatherRenderSystem.Mode.NONE, 0f, 0.25f));
    assertEquals(0.5f, transition.intensity, 0.0001f);

    assertEquals(WeatherRenderSystem.Mode.NONE,
        transition.update(WeatherRenderSystem.Mode.NONE, 0f, 0.25f));
    assertEquals(0f, transition.intensity, 0.0001f);

    assertEquals(WeatherRenderSystem.Mode.RAIN,
        transition.update(WeatherRenderSystem.Mode.RAIN, 1f, 0.25f));
    assertEquals(0.5f, transition.intensity, 0.0001f);
    assertEquals(WeatherRenderSystem.Mode.RAIN,
        transition.update(WeatherRenderSystem.Mode.RAIN, 1f, 0.25f));
    assertEquals(1f, transition.intensity, 0.0001f);
  }

  @Test
  void dryWeatherCycleDoesNotCreateAHiddenParticleField() {
    WeatherRenderSystem.WeatherTransition transition =
        new WeatherRenderSystem.WeatherTransition();

    assertEquals(WeatherRenderSystem.Mode.NONE,
        transition.update(WeatherRenderSystem.Mode.RAIN, 0f, 0.25f));
    assertEquals(0f, transition.intensity, 0.0001f);
  }

  @Test
  void initializationIsDeterministicForTheSameSeed() {
    WeatherRenderSystem.ParticleField first = field(1234L, WeatherRenderSystem.Mode.RAIN);
    WeatherRenderSystem.ParticleField second = field(1234L, WeatherRenderSystem.Mode.RAIN);

    assertArrayEquals(first.x, second.x);
    assertArrayEquals(first.y, second.y);
    assertArrayEquals(first.velocityX, second.velocityX);
    assertArrayEquals(first.velocityY, second.velocityY);
    assertArrayEquals(first.fallSpeed, second.fallSpeed);
    assertArrayEquals(first.size, second.size);
    assertArrayEquals(first.shade, second.shade);
  }

  @Test
  void rainUsesNativeStreakLengthSpeedAndWindRanges() {
    WeatherRenderSystem.ParticleField particles = field(8L, WeatherRenderSystem.Mode.RAIN);

    assertTrue(particles.windAngle >= WeatherRenderSystem.RAIN_MIN_ANGLE);
    assertTrue(particles.windAngle <= WeatherRenderSystem.RAIN_MAX_ANGLE);
    assertTrue(particles.windTicks >= WeatherRenderSystem.RAIN_MIN_WIND_TICKS);
    assertTrue(particles.windTicks <= WeatherRenderSystem.RAIN_MAX_WIND_TICKS);
    assertTrue(particles.windY < 0f);
    for (int i = 0; i < WeatherRenderSystem.PARTICLE_COUNT; i++) {
      assertTrue(particles.size[i] >= WeatherRenderSystem.RAIN_MIN_LENGTH);
      assertTrue(particles.size[i] <= WeatherRenderSystem.RAIN_MAX_LENGTH);
      float speedPerTick = particles.fallSpeed[i] * SimulationClock.STEP_SECONDS;
      assertTrue(speedPerTick >= WeatherRenderSystem.RAIN_MIN_SPEED_PER_TICK - 0.001f);
      assertTrue(speedPerTick <= WeatherRenderSystem.RAIN_MAX_SPEED_PER_TICK + 0.001f);
    }
  }

  @Test
  void rainUsesTheNativeActOneTwelveShadeRamp() {
    float brightest = WeatherRenderSystem.ParticleField.rainShade(0);
    float darkest = WeatherRenderSystem.ParticleField.rainShade(
        WeatherRenderSystem.RAIN_SHADE_COUNT - 1);

    assertEquals(1f, brightest);
    assertTrue(darkest < 0.3f);
    assertTrue(brightest / darkest > 3f);

    WeatherRenderSystem.ParticleField particles = field(10L, WeatherRenderSystem.Mode.RAIN);
    for (float shade : particles.shade) {
      boolean nativeShade = false;
      for (int slot = 0; slot < WeatherRenderSystem.RAIN_SHADE_COUNT; slot++) {
        if (shade == WeatherRenderSystem.ParticleField.rainShade(slot)) {
          nativeShade = true;
          break;
        }
      }
      assertTrue(nativeShade);
    }
  }

  @Test
  void onlyBrightRainUsesTheHeavyRasterWidth() {
    assertEquals(2f, WeatherRenderSystem.rainWidth(
        WeatherRenderSystem.ParticleField.rainShade(0)));
    assertEquals(2f, WeatherRenderSystem.rainWidth(
        WeatherRenderSystem.ParticleField.rainShade(1)));
    assertEquals(1f, WeatherRenderSystem.rainWidth(
        WeatherRenderSystem.ParticleField.rainShade(2)));
    assertEquals(1f, WeatherRenderSystem.rainWidth(
        WeatherRenderSystem.ParticleField.rainShade(
            WeatherRenderSystem.RAIN_SHADE_COUNT - 1)));
  }

  @Test
  void rainWindChangesDirectionGradually() {
    WeatherRenderSystem.ParticleField particles = field(9L, WeatherRenderSystem.Mode.RAIN);
    particles.windAngle = WeatherRenderSystem.RAIN_MIN_ANGLE;
    particles.targetWindAngle = WeatherRenderSystem.RAIN_MAX_ANGLE;
    particles.windTicks = 10;

    particles.step();

    assertEquals(WeatherRenderSystem.RAIN_MIN_ANGLE + 2, particles.windAngle);
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

  @Test
  void nativeWeatherCycleFadesThenAlternatesRainAndDryPeriods() {
    WeatherRenderSystem.WeatherCycle cycle =
        new WeatherRenderSystem.WeatherCycle(5L);
    cycle.configure(WeatherRenderSystem.Mode.RAIN);

    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.DRY, cycle.phase);
    assertEquals(0f, cycle.intensity);
    assertEquals(WeatherRenderSystem.Mode.NONE, cycle.visibleMode());
    assertTrue(cycle.phaseTicks >= 7500 && cycle.phaseTicks < 15000);

    cycle.advanceTicks(cycle.phaseTicks);
    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.FADE_IN, cycle.phase);
    assertEquals(0f, cycle.intensity);
    assertEquals(WeatherRenderSystem.Mode.NONE, cycle.visibleMode());
    assertTrue(cycle.phaseTicks >= 250 && cycle.phaseTicks < 500);
    assertTrue(cycle.peakIntensity >= 32f / WeatherRenderSystem.PARTICLE_COUNT);
    assertTrue(cycle.peakIntensity <= 255f / WeatherRenderSystem.PARTICLE_COUNT);

    cycle.advanceTicks(cycle.phaseTicks);
    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.STEADY, cycle.phase);
    assertEquals(cycle.peakIntensity, cycle.intensity);
    assertEquals(WeatherRenderSystem.Mode.RAIN, cycle.visibleMode());
    assertTrue(cycle.phaseTicks >= 3000 && cycle.phaseTicks < 6000);

    cycle.advanceTicks(cycle.phaseTicks);
    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.FADE_OUT, cycle.phase);
    assertEquals(cycle.peakIntensity, cycle.intensity);
    assertTrue(cycle.phaseTicks >= 125 && cycle.phaseTicks < 175);

    cycle.advanceTicks(cycle.phaseTicks);
    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.DRY, cycle.phase);
    assertEquals(0f, cycle.intensity);
    assertEquals(WeatherRenderSystem.Mode.NONE, cycle.visibleMode());
    assertTrue(cycle.phaseTicks >= 7500 && cycle.phaseTicks < 15000);
  }

  @Test
  void levelsKeepIndependentWeatherCyclesWhileAway() {
    WeatherRenderSystem.WeatherCycles cycles =
        new WeatherRenderSystem.WeatherCycles(10L);
    Levels.Entry town = rainyLevel(1);
    Levels.Entry stonyField = rainyLevel(4);

    WeatherRenderSystem.WeatherCycle townCycle =
        cycles.forLevel(town, WeatherRenderSystem.Mode.RAIN);
    WeatherRenderSystem.WeatherCycle fieldCycle =
        cycles.forLevel(stonyField, WeatherRenderSystem.Mode.RAIN);
    assertNotSame(townCycle, fieldCycle);
    assertNotEquals(
        WeatherRenderSystem.WeatherCycles.seedForLevel(10L, town.Id),
        WeatherRenderSystem.WeatherCycles.seedForLevel(10L, stonyField.Id));

    int fieldTicks = fieldCycle.remainingTicks;
    townCycle.advanceTicks(townCycle.remainingTicks + 1);

    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.FADE_IN, townCycle.phase);
    assertTrue(townCycle.intensity > 0f);
    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.DRY, fieldCycle.phase);
    assertEquals(fieldTicks, fieldCycle.remainingTicks);
    assertEquals(0f, fieldCycle.intensity);
    assertSame(townCycle, cycles.forLevel(town, WeatherRenderSystem.Mode.RAIN));
  }

  @Test
  void unsupportedLevelClearsVisibleWeatherButPausesTheCycle() {
    WeatherRenderSystem.WeatherCycle cycle =
        new WeatherRenderSystem.WeatherCycle(6L);
    cycle.configure(WeatherRenderSystem.Mode.RAIN);
    cycle.advanceTicks(20);
    int remainingTicks = cycle.remainingTicks;

    cycle.configure(WeatherRenderSystem.Mode.NONE);
    cycle.advanceTicks(100);
    assertEquals(remainingTicks, cycle.remainingTicks);
    assertEquals(0f, cycle.intensity);
    assertEquals(WeatherRenderSystem.Mode.NONE, cycle.visibleMode());

    cycle.configure(WeatherRenderSystem.Mode.RAIN);
    assertEquals(WeatherRenderSystem.Mode.NONE, cycle.visibleMode());
    assertEquals(remainingTicks, cycle.remainingTicks);
    assertEquals(0f, cycle.intensity);
  }

  @Test
  void steadyRainRestoresImmediatelyAfterUnsupportedLevel() {
    WeatherRenderSystem.WeatherCycle cycle =
        new WeatherRenderSystem.WeatherCycle(7L);
    cycle.configure(WeatherRenderSystem.Mode.RAIN);
    cycle.advanceTicks(cycle.phaseTicks);
    cycle.advanceTicks(cycle.phaseTicks);
    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.STEADY, cycle.phase);
    assertTrue(cycle.intensity > 0f);

    cycle.configure(WeatherRenderSystem.Mode.NONE);
    int remainingTicks = cycle.remainingTicks;
    cycle.configure(WeatherRenderSystem.Mode.RAIN);

    assertEquals(WeatherRenderSystem.WeatherCycle.Phase.STEADY, cycle.phase);
    assertEquals(remainingTicks, cycle.remainingTicks);
    assertEquals(cycle.peakIntensity, cycle.intensity);
    assertEquals(WeatherRenderSystem.Mode.RAIN, cycle.visibleMode());
  }

  private static WeatherRenderSystem.ParticleField field(
      long seed, WeatherRenderSystem.Mode mode) {
    WeatherRenderSystem.ParticleField particles =
        new WeatherRenderSystem.ParticleField(seed);
    particles.configure(mode, 640f, 480f);
    return particles;
  }

  private static Levels.Entry rainyLevel(int id) {
    Levels.Entry level = new Levels.Entry();
    level.Id = id;
    level.Rain = true;
    return level;
  }
}
