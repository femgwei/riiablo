package com.riiablo.engine.client;

import java.util.Random;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;

import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.GpuSystem;

/** Draws the native screen-space rain and Act V snow precipitation layer. */
@GpuSystem
public final class WeatherRenderSystem extends BaseSystem {
  static final int PARTICLE_COUNT = 256;
  static final int SNOW_SHAPE_COUNT = 8;
  static final int SNOW_SEGMENTS_PER_PARTICLE = 2;
  private static final int MAX_CATCH_UP_STEPS = 4;
  private static final long RANDOM_SEED = 0xD2C11E17L;

  enum Mode {
    NONE,
    RAIN,
    SNOW
  }

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected RenderSystem renderer;
  protected Map map;

  @Wire(name = "iso")
  protected IsometricCamera iso;

  @Wire(name = "shapes")
  protected ShapeRenderer shapes;

  private final Matrix4 projection = new Matrix4();
  private final ParticleField particles = new ParticleField(RANDOM_SEED);

  @Override
  protected void processSystem() {
    int src = renderer.getSrc();
    Map.Zone zone = src >= 0 && mMapWrapper.has(src) ? mMapWrapper.get(src).zone : null;
    if (zone == null && src >= 0 && mPosition.has(src)) {
      zone = map.getZone(mPosition.get(src).position);
    }
    Mode mode = modeFor(zone == null ? null : zone.level);
    float width = iso.viewportWidth * iso.zoom;
    float height = iso.viewportHeight * iso.zoom;
    particles.configure(mode, width, height);
    if (mode == Mode.NONE || width <= 0f || height <= 0f) return;

    particles.advance(world.getDelta());
    draw(width, height);
  }

  private void draw(float width, float height) {
    projection.setToOrtho2D(0f, 0f, width, height);
    shapes.identity();
    shapes.setProjectionMatrix(projection);
    shapes.setAutoShapeType(false);

    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    shapes.begin(ShapeRenderer.ShapeType.Line);
    if (particles.mode == Mode.RAIN) {
      shapes.setColor(0.55f, 0.63f, 0.72f, 0.52f);
      drawRain();
    } else {
      shapes.setColor(0.88f, 0.91f, 0.94f, 0.78f);
      drawSnow();
    }
    shapes.end();
    Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private void drawRain() {
    float alpha = particles.alpha();
    for (int i = 0; i < PARTICLE_COUNT; i++) {
      float x = MathUtils.lerp(particles.previousX[i], particles.x[i], alpha);
      float y = MathUtils.lerp(particles.previousY[i], particles.y[i], alpha);
      float length = particles.size[i];
      shapes.line(x, y, x + length * 0.34f, y + length);
    }
  }

  private void drawSnow() {
    float alpha = particles.alpha();
    for (int i = 0; i < PARTICLE_COUNT; i++) {
      float x = MathUtils.lerp(particles.previousX[i], particles.x[i], alpha);
      float y = MathUtils.lerp(particles.previousY[i], particles.y[i], alpha);
      float scale = particles.size[i];
      float[] segments = SNOW_SEGMENTS[particles.shape[i]];
      shapes.line(
          x + segments[0] * scale, y + segments[1] * scale,
          x + segments[2] * scale, y + segments[3] * scale);
      shapes.line(
          x + segments[4] * scale, y + segments[5] * scale,
          x + segments[6] * scale, y + segments[7] * scale);
    }
  }

  static Mode modeFor(Levels.Entry level) {
    if (level == null || !level.Rain || level.IsInside) return Mode.NONE;
    return level.Act == 4 ? Mode.SNOW : Mode.RAIN;
  }

  /** Two short line segments for each of the eight native snow shape slots. */
  static final float[][] SNOW_SEGMENTS = {
      {-1f,  0f,  1f,  0f,  0f, -1f,  0f,  1f},
      {-1f, -1f,  1f,  1f, -1f,  1f,  1f, -1f},
      {-1f,  0f,  1f,  0f, -0.5f, -1f,  0.5f,  1f},
      { 0f, -1f,  0f,  1f, -1f, -0.5f,  1f,  0.5f},
      {-1f, -0.5f, 1f, 0.5f, -0.5f, 1f, 0.5f, -1f},
      {-1f,  0.5f, 1f,-0.5f, -0.5f,-1f, 0.5f,  1f},
      {-1f,  0f,  1f,  0f, -0.7f,-0.7f, 0.7f, 0.7f},
      { 0f, -1f,  0f,  1f, -0.7f, 0.7f, 0.7f,-0.7f}
  };

  static final class ParticleField {
    final float[] previousX = new float[PARTICLE_COUNT];
    final float[] previousY = new float[PARTICLE_COUNT];
    final float[] x = new float[PARTICLE_COUNT];
    final float[] y = new float[PARTICLE_COUNT];
    final float[] velocityX = new float[PARTICLE_COUNT];
    final float[] velocityY = new float[PARTICLE_COUNT];
    final float[] size = new float[PARTICLE_COUNT];
    final float[] phase = new float[PARTICLE_COUNT];
    final float[] phaseSpeed = new float[PARTICLE_COUNT];
    final int[] shape = new int[PARTICLE_COUNT];

    private final long seed;
    private final FixedStepAccumulator accumulator =
        new FixedStepAccumulator(SimulationClock.STEP_SECONDS, MAX_CATCH_UP_STEPS);
    private Random random;
    private Mode mode = Mode.NONE;
    private float width;
    private float height;

    ParticleField(long seed) {
      this.seed = seed;
    }

    void configure(Mode nextMode, float nextWidth, float nextHeight) {
      if (nextMode == mode
          && MathUtils.isEqual(nextWidth, width)
          && MathUtils.isEqual(nextHeight, height)) return;

      mode = nextMode;
      width = Math.max(0f, nextWidth);
      height = Math.max(0f, nextHeight);
      accumulator.reset();
      if (mode == Mode.NONE || width == 0f || height == 0f) return;

      random = new Random(seed ^ (mode.ordinal() * 0x9E3779B97F4A7C15L));
      for (int i = 0; i < PARTICLE_COUNT; i++) initialize(i, true);
    }

    int advance(float delta) {
      if (mode == Mode.NONE) return 0;
      return accumulator.advance(delta, ignored -> step());
    }

    float alpha() {
      return accumulator.getAccumulated() / accumulator.getStepSeconds();
    }

    void step() {
      for (int i = 0; i < PARTICLE_COUNT; i++) {
        previousX[i] = x[i];
        previousY[i] = y[i];
        if (mode == Mode.RAIN) {
          x[i] += velocityX[i] * SimulationClock.STEP_SECONDS;
          y[i] += velocityY[i] * SimulationClock.STEP_SECONDS;
          if (x[i] < -48f || y[i] < -48f) respawnAtTop(i);
        } else if (mode == Mode.SNOW) {
          phase[i] += phaseSpeed[i] * SimulationClock.STEP_SECONDS;
          x[i] += (velocityX[i] + (float) Math.sin(phase[i]) * 11f)
              * SimulationClock.STEP_SECONDS;
          y[i] += velocityY[i] * SimulationClock.STEP_SECONDS;
          if (x[i] < -24f) {
            x[i] = width + 24f;
            previousX[i] = x[i];
          } else if (x[i] > width + 24f) {
            x[i] = -24f;
            previousX[i] = x[i];
          }
          if (y[i] < -24f) respawnAtTop(i);
        }
      }
    }

    private void initialize(int i, boolean anywhere) {
      if (mode == Mode.RAIN) {
        velocityX[i] = -70f - random.nextFloat() * 55f;
        velocityY[i] = -430f - random.nextFloat() * 210f;
        size[i] = 12f + random.nextFloat() * 13f;
      } else {
        velocityX[i] = -8f + random.nextFloat() * 16f;
        velocityY[i] = -42f - random.nextFloat() * 45f;
        size[i] = 1.4f + random.nextFloat() * 1.8f;
      }
      phase[i] = random.nextFloat() * MathUtils.PI2;
      phaseSpeed[i] = 1.2f + random.nextFloat() * 2.1f;
      shape[i] = random.nextInt(SNOW_SHAPE_COUNT);
      x[i] = random.nextFloat() * (width + 64f) - 16f;
      y[i] = anywhere
          ? random.nextFloat() * (height + 64f) - 32f
          : height + random.nextFloat() * 48f;
      previousX[i] = x[i];
      previousY[i] = y[i];
    }

    private void respawnAtTop(int i) {
      initialize(i, false);
    }
  }
}
