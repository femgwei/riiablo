package com.riiablo.engine.client;

import java.util.HashMap;
import java.util.Random;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.annotations.Wire;
import com.artemis.utils.IntBag;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;

import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Object;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;
import com.riiablo.map.RenderSystem;
import com.riiablo.map.DT1;
import com.riiablo.profiler.GpuSystem;

/** Draws the native screen-space rain and Act V snow precipitation layer. */
@GpuSystem
public final class WeatherRenderSystem extends BaseSystem {
  static final int PARTICLE_COUNT = 256;
  static final int SNOW_SHAPE_COUNT = 8;
  static final int SNOW_SEGMENTS_PER_PARTICLE = 2;
  static final int RAIN_ANGLE_UNITS = 512;
  static final int RAIN_MIN_ANGLE = 92;
  static final int RAIN_MAX_ANGLE = 162;
  static final int RAIN_MIN_LENGTH = 4;
  static final int RAIN_MAX_LENGTH = 12;
  static final int RAIN_MIN_SPEED_PER_TICK = 15;
  static final int RAIN_MAX_SPEED_PER_TICK = 30;
  static final int RAIN_MIN_WIND_TICKS = 125;
  static final int RAIN_MAX_WIND_TICKS = 499;
  static final int RAIN_SHADE_COUNT = 12;
  static final int RAIN_DEPTH_BUCKET_COUNT = RAIN_MAX_LENGTH - RAIN_MIN_LENGTH + 1;
  static final float RAIN_DENSITY_SCALE = 0.75f;
  // Keep one stable conversion between visible rain streaks and water
  // impacts. At full rain this is 50 ripple spawns/sec for 192 streaks.
  private static final float RIPPLE_RATE_PER_RAIN_PARTICLE =
      50f / activeRainParticles(1f);
  private static final int RAIN_THICK_SHADE_SLOTS = 2;
  private static final int RAIN_SHADE_BASE = 98;
  private static final int RAIN_SHADE_RANGE = 80;
  private static final int RAIN_GREEN_BIAS = 25;
  private static final int MAX_CATCH_UP_STEPS = 4;
  private static final int RAIN_AMBIENCE_ID = 64;
  private static final long RANDOM_SEED = 0xD2C11E17L;
  private static final long WEATHER_CYCLE_SEED = 0x6FAA7940L;

  enum Mode {
    NONE,
    RAIN,
    SNOW
  }

  public enum ControlMode {
    AUTO,
    OFF,
    RAIN,
    SNOW
  }

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Object> mObject;
  protected RenderSystem renderer;
  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "iso")
  protected IsometricCamera iso;

  @Wire(name = "shapes")
  protected ShapeRenderer shapes;

  private final Matrix4 projection = new Matrix4();
  private final ParticleField particles = new ParticleField(RANDOM_SEED);
  private final RainRippleField ripples = new RainRippleField(RANDOM_SEED ^ 0x4D2A7B19L);
  private WeatherCycles weatherCycles;
  private ControlMode controlMode = ControlMode.AUTO;
  private Audio.Instance rainAmbience;

  @Override
  protected void processSystem() {
    Map worldMap = renderer.getMap();
    int src = renderer.getSrc();
    Map.Zone zone = src >= 0 && mMapWrapper.has(src) ? mMapWrapper.get(src).zone : null;
    if (zone == null && worldMap != null && src >= 0 && mPosition.has(src)) {
      zone = worldMap.getZone(mPosition.get(src).position);
    }
    Levels.Entry level = zone == null ? null : zone.level;
    Mode eligibleMode = modeFor(level);
    Mode mode;
    float intensity;
    if (controlMode == ControlMode.AUTO) {
      if (weatherCycles == null) {
        weatherCycles = new WeatherCycles(
            WEATHER_CYCLE_SEED ^ (worldMap == null ? 0L : worldMap.seed()));
      }
      WeatherCycle weatherCycle = weatherCycles.forLevel(level, eligibleMode);
      if (weatherCycle == null) {
        mode = Mode.NONE;
        intensity = 0f;
      } else {
        weatherCycle.advance(world.getDelta());
        mode = weatherCycle.visibleMode();
        intensity = weatherCycle.intensity;
      }
    } else {
      mode = controlledModeFor(eligibleMode);
      intensity = mode == Mode.NONE ? 0f : 1f;
    }
    updateRainAmbience(mode, intensity);
    float width = iso.viewportWidth * iso.zoom;
    float height = iso.viewportHeight * iso.zoom;
    particles.configure(mode, width, height);
    if (mode == Mode.NONE || width <= 0f || height <= 0f) {
      ripples.clear();
      return;
    }

    particles.advance(world.getDelta());
    if (mode == Mode.RAIN) {
      ripples.advance(world.getDelta(), worldMap, zone, src, intensity);
    } else {
      ripples.clear();
    }
    draw(width, height, intensity);
  }

  private void updateRainAmbience(Mode mode, float intensity) {
    Audio audio = Riiablo.audio;
    if (audio == null || audio.isBackgroundPaused()) return;

    float volume = rainVolume(mode, intensity);
    if (volume <= 0f) {
      stopRainAmbience();
      return;
    }

    if (rainAmbience == null) {
      // D2Client's weather sound controller hard-codes Sounds.txt ID 64
      // (scene_rain), independently of the level's base SoundEnv ambience.
      rainAmbience = audio.play(RAIN_AMBIENCE_ID, true, Audio.Channel.ENVIRONMENT);
    }
    if (rainAmbience != null) rainAmbience.setVolume(volume);
  }

  private void stopRainAmbience() {
    if (rainAmbience == null) return;
    rainAmbience.stop();
    rainAmbience = null;
  }

  static float rainVolume(Mode mode, float intensity) {
    return mode == Mode.RAIN ? MathUtils.clamp(intensity, 0f, 1f) : 0f;
  }

  private void draw(float width, float height, float intensity) {
    projection.setToOrtho2D(0f, 0f, width, height);
    shapes.identity();
    shapes.setProjectionMatrix(projection);
    shapes.setAutoShapeType(false);

    Gdx.gl.glEnable(GL20.GL_BLEND);
    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    if (particles.mode == Mode.RAIN) {
      shapes.begin(ShapeRenderer.ShapeType.Filled);
      drawRain(intensity);
    } else {
      shapes.begin(ShapeRenderer.ShapeType.Line);
      shapes.setColor(0.88f, 0.91f, 0.94f, 0.78f);
      drawSnow(intensity);
    }
    shapes.end();
    Gdx.gl.glDisable(GL20.GL_BLEND);
  }

  private void drawRain(float intensity) {
    float alpha = particles.alpha();
    int activeParticles = activeRainParticles(intensity);
    for (int i = 0; i < activeParticles; i++) {
      float x = MathUtils.lerp(particles.previousX[i], particles.x[i], alpha);
      float y = MathUtils.lerp(particles.previousY[i], particles.y[i], alpha);
      float length = particles.size[i];
      float shade = particles.shade[i];
      float greenShade = (shade * RAIN_SHADE_BASE + RAIN_GREEN_BIAS)
          / (RAIN_SHADE_BASE + RAIN_GREEN_BIAS);
      shapes.setColor(0.70f * shade, 0.72f * greenShade, 0.74f * shade, 0.50f);
      // Native D2Gfx receives integer endpoints and has no line-width argument. Quantizing the
      // vector reproduces its stepped silhouettes; solid one- and two-pixel quads reproduce the
      // apparent thin and heavy footprints of its indexed software rasterizer without a gap.
      float deltaX = (int) (particles.windX * length);
      float deltaY = (int) (particles.windY * length);
      shapes.rectLine(x, y, x + deltaX, y + deltaY, rainWidth(shade));
    }
  }

  static float rainWidth(float shade) {
    return shade >= ParticleField.rainShade(RAIN_THICK_SHADE_SLOTS - 1) ? 2f : 1f;
  }

  private void drawSnow(float intensity) {
    float alpha = particles.alpha();
    int activeParticles = activeParticles(intensity);
    for (int i = 0; i < activeParticles; i++) {
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

  private static int activeParticles(float intensity) {
    return MathUtils.clamp(MathUtils.ceil(PARTICLE_COUNT * intensity), 0, PARTICLE_COUNT);
  }

  static int activeRainParticles(float intensity) {
    // The native client uses a hard-coded screen-space particle system. Keep
    // its 32..255 weather-strength cycle, but calibrate the OpenGL streak count
    // to the visibly lighter 640x480 legacy presentation.
    return activeParticles(intensity * RAIN_DENSITY_SCALE);
  }

  static Mode modeFor(Levels.Entry level) {
    if (level == null || !level.Rain || level.IsInside) return Mode.NONE;
    return level.Act == 4 ? Mode.SNOW : Mode.RAIN;
  }

  private Mode controlledModeFor(Mode automaticMode) {
    switch (controlMode) {
      case OFF:  return Mode.NONE;
      case RAIN: return Mode.RAIN;
      case SNOW: return Mode.SNOW;
      default:   return automaticMode;
    }
  }

  public ControlMode getControlMode() {
    return controlMode;
  }

  public void setControlMode(ControlMode controlMode) {
    if (controlMode == null) throw new NullPointerException("controlMode");
    this.controlMode = controlMode;
  }

  @Override
  protected void dispose() {
    stopRainAmbience();
    ripples.clear();
  }

  /** Shares one weather cycle across every Rain-enabled level in an act. */
  static final class WeatherCycles {
    private final long seed;
    private final HashMap<Integer, WeatherCycle> cycles = new HashMap<>();

    WeatherCycles(long seed) {
      this.seed = seed;
    }

    WeatherCycle forLevel(Levels.Entry level, Mode eligibleMode) {
      if (level == null || eligibleMode == Mode.NONE) return null;

      WeatherCycle cycle = cycles.get(level.Act);
      if (cycle == null) {
        cycle = new WeatherCycle(seedForAct(seed, level.Act));
        cycle.configure(eligibleMode);
        cycles.put(level.Act, cycle);
      }
      return cycle;
    }

    static long seedForAct(long seed, int act) {
      return seed ^ (act * 0x9E3779B97F4A7C15L);
    }
  }

  /** Native four-stage weather timing from D2Client's Env.cpp state machine. */
  static final class WeatherCycle {
    enum Phase {
      DRY(7500, 7500),
      FADE_IN(250, 250),
      STEADY(3000, 3000),
      FADE_OUT(125, 50);

      final int baseTicks;
      final int randomTicks;

      Phase(int baseTicks, int randomTicks) {
        this.baseTicks = baseTicks;
        this.randomTicks = randomTicks;
      }
    }

    private final FixedStepAccumulator accumulator =
        new FixedStepAccumulator(SimulationClock.STEP_SECONDS, MAX_CATCH_UP_STEPS);
    private final Random random;
    private Mode eligibleMode = Mode.NONE;
    Phase phase;
    int phaseTicks;
    int remainingTicks;
    float peakIntensity;
    float intensity;

    WeatherCycle(long seed) {
      random = new Random(seed);
    }

    void configure(Mode nextEligibleMode) {
      Mode previousEligibleMode = eligibleMode;
      eligibleMode = nextEligibleMode;
      if (nextEligibleMode == Mode.NONE) {
        // Hide precipitation without discarding this level's saved phase.
        intensity = 0f;
        return;
      }
      if (phase != null) {
        // Restore the saved visible intensity when this level becomes eligible again.
        if (previousEligibleMode == Mode.NONE) restoreIntensity();
        return;
      }

      // A newly created weather controller starts in its dry interval. Rain is
      // selected only when that interval expires, so entering a Rain-enabled
      // level does not guarantee an immediate storm.
      begin(Phase.DRY);
      intensity = 0f;
    }

    private void restoreIntensity() {
      switch (phase) {
        case FADE_IN:
          intensity = peakIntensity * (1f - remainingTicks / (float) phaseTicks);
          break;
        case STEADY:
          intensity = peakIntensity;
          break;
        case FADE_OUT:
          intensity = peakIntensity * remainingTicks / (float) phaseTicks;
          break;
        default:
          intensity = 0f;
          break;
      }
    }

    int advance(float delta) {
      if (eligibleMode == Mode.NONE || phase == null) return 0;
      return accumulator.advance(delta, ignored -> step());
    }

    void advanceTicks(int ticks) {
      for (int i = 0; i < ticks; i++) step();
    }

    private void step() {
      if (eligibleMode == Mode.NONE || phase == null) return;

      if (--remainingTicks <= 0) {
        begin(next(phase));
      } else if (phase == Phase.FADE_IN) {
        intensity = peakIntensity * (1f - remainingTicks / (float) phaseTicks);
      } else if (phase == Phase.FADE_OUT) {
        intensity = peakIntensity * remainingTicks / (float) phaseTicks;
      }
    }

    private void begin(Phase nextPhase) {
      phase = nextPhase;
      phaseTicks = nextPhase.baseTicks + random.nextInt(nextPhase.randomTicks);
      remainingTicks = phaseTicks;
      if (nextPhase == Phase.FADE_IN) {
        // Env.cpp chooses a per-storm particle target in the inclusive 32..255 range.
        peakIntensity = (32 + random.nextInt(224)) / (float) PARTICLE_COUNT;
      }
      intensity = nextPhase == Phase.STEADY || nextPhase == Phase.FADE_OUT
          ? peakIntensity
          : 0f;
    }

    private static Phase next(Phase phase) {
      switch (phase) {
        case DRY:     return Phase.FADE_IN;
        case FADE_IN: return Phase.STEADY;
        case STEADY:  return Phase.FADE_OUT;
        default:      return Phase.DRY;
      }
    }

    Mode visibleMode() {
      return eligibleMode != Mode.NONE && intensity > 0f ? eligibleMode : Mode.NONE;
    }
  }

  /**
   * Client-only native water-impact presentation. D2Client creates one of the
   * four Objects.txt Dummy-ripple rows (tokens 1R..4R), whose TR layer is the
   * animated {@code *TRLITNUHTH.dcc}; the server never receives these units.
   */
  final class RainRippleField {
    // OpenDiablo2 calls the same lookup records 217..220, but riiablo's
    // Objects.txt table is keyed by the concrete rows 67..70 (1R..4R).
    private static final int FIRST_CLASS_ID = 67;
    private static final int RIPPLE_VARIANTS = 4;
    private static final int MAX_RIPPLES = 48;
    // Community captures consistently show large ripples less often than the
    // small rings.  These are an empirical ClientFn=2 approximation; the
    // retail branch is hard-coded in D2Client rather than Objects.txt.
    private static final int RIPPLE_VARIANT_ROLL = 100;
    // WeatherRenderSystem is a GPU/render system and receives real frame
    // deltas, not the fixed 25 Hz simulation tick.  The old integer countdown
    // therefore expired a ripple in roughly 18/60 seconds, often before its
    // COF/DCC could finish loading on the first rain frame.
    private static final float RIPPLE_LIFETIME_SECONDS = 0.9f;
    private static final int WATER_SAMPLE_ATTEMPTS = 80;

    private final Random random;
    private final com.badlogic.gdx.utils.Array<Ripple> active = new com.badlogic.gdx.utils.Array<>();
    private final com.badlogic.gdx.math.Vector2 player = new com.badlogic.gdx.math.Vector2();
    private float spawnBudget;
    private boolean resourcesChecked;
    private boolean resourcesAvailable;

    RainRippleField(long seed) {
      random = new Random(seed);
    }

    void advance(float delta, Map map, Map.Zone zone, int source, float intensity) {
      if (map == null || zone == null || source < 0 || !mPosition.has(source)
          || !(factory instanceof ClientEntityFactory) || !hasResources()) {
        clear();
        return;
      }

      for (int i = active.size - 1; i >= 0; i--) {
        Ripple ripple = active.get(i);
        ripple.remainingSeconds -= Math.max(0f, delta);
        if (ripple.remainingSeconds <= 0f) {
          delete(ripple.entityId);
          active.removeIndex(i);
        }
      }

      player.set(mPosition.get(source).position);
      // Keep the native-looking emission deterministic at the 25 Hz clock.
      // ClientFn=2 runs considerably denser than the old material sampler,
      // but its density remains in a fixed ratio to the actual screen rain.
      // This keeps light rain visibly sparse and heavy rain visibly crowded.
      spawnBudget += Math.max(0f, delta) * activeRainParticles(intensity)
          * RIPPLE_RATE_PER_RAIN_PARTICLE;
      while (spawnBudget >= 1f && active.size < MAX_RIPPLES) {
        spawnBudget -= 1f;
        if (!spawn(map, zone)) break;
      }
    }

    private boolean spawn(Map map, Map.Zone expectedZone) {
      // D2Client's ClientFn=2 is attached to a water-surface object.  Prefer
      // that object's rectangle so pools and other finite water objects do not
      // emit ripples across the whole viewport.  River objects have zero-sized
      // bounds in Objects.txt, so the material sampler remains a deliberate
      // compatibility fallback for those legacy entries.
      if (spawnFromClientFunctionObject(map, expectedZone)) return true;

      float radius = Math.max(32f, Math.max(iso.viewportWidth, iso.viewportHeight)
          * iso.zoom / (DT1.Tile.SUBTILE_WIDTH * 2f));
      for (int attempt = 0; attempt < WATER_SAMPLE_ATTEMPTS; attempt++) {
        int x = MathUtils.round(player.x + (random.nextFloat() * 2f - 1f) * radius);
        int y = MathUtils.round(player.y + (random.nextFloat() * 2f - 1f) * radius);
        if (map.getZone(x, y) != expectedZone || !map.isWater(x, y)) continue;

        int classId = FIRST_CLASS_ID + randomRippleVariant();
        int entityId = factory.createStaticObjectByClassId(classId, x, y);
        if (entityId == com.riiablo.engine.Engine.INVALID_ENTITY) continue;
        active.add(new Ripple(entityId, RIPPLE_LIFETIME_SECONDS));
        return true;
      }
      return false;
    }

    private boolean spawnFromClientFunctionObject(Map map, Map.Zone expectedZone) {
      if (world == null || mObject == null || mPosition == null) return false;
      com.artemis.EntitySubscription subscription = world.getAspectSubscriptionManager().get(
          Aspect.all(Object.class, Position.class));
      IntBag entities = subscription.getEntities();
      if (entities.size() == 0) return false;

      // Start at a deterministic random entity to avoid favoring the first
      // room's object when several pools are visible at once.
      int start = random.nextInt(entities.size());
      float visibleRadius = Math.max(32f, Math.max(iso.viewportWidth, iso.viewportHeight)
          * iso.zoom / (DT1.Tile.SUBTILE_WIDTH * 2f));
      for (int offset = 0; offset < entities.size(); offset++) {
        int entityId = entities.get((start + offset) % entities.size());
        Object object = mObject.get(entityId);
        Position position = mPosition.get(entityId);
        if (object == null || position == null || object.base == null
            || !isClientRainRippleEmitter(object.base)
            || mMapWrapper == null || !mMapWrapper.has(entityId)
            || mMapWrapper.get(entityId).zone != expectedZone) continue;
        if (Math.abs(position.position.x - player.x) > visibleRadius
            || Math.abs(position.position.y - player.y) > visibleRadius) continue;

        com.riiablo.codec.excel.Objects.Entry base = object.base;
        float halfWidth = base.SizeX > 0 ? base.SizeX * 0.5f : 0f;
        float halfHeight = base.SizeY > 0 ? base.SizeY * 0.5f : 0f;
        // A zero-sized river marker is only a semantic emitter. Let the
        // material fallback locate a real water tile rather than placing the
        // ripple on the invisible marker itself.
        if (halfWidth <= 0f || halfHeight <= 0f) continue;
        for (int attempt = 0; attempt < 8; attempt++) {
          int x = MathUtils.round(position.position.x
              + (random.nextFloat() * 2f - 1f) * halfWidth);
          int y = MathUtils.round(position.position.y
              + (random.nextFloat() * 2f - 1f) * halfHeight);
          if (map.getZone(x, y) != expectedZone || !map.isWater(x, y)) continue;

          int entity = factory.createStaticObjectByClassId(
              FIRST_CLASS_ID + randomRippleVariant(), x, y);
          if (entity == com.riiablo.engine.Engine.INVALID_ENTITY) continue;
          active.add(new Ripple(entity, RIPPLE_LIFETIME_SECONDS));
          return true;
        }
      }
      return false;
    }

    private int randomRippleVariant() {
      // 1R=10%, 2R=20%, 3R=30%, 4R=40%.  The exact retail intervals are
      // embedded in D2Client; this follows the available long-run captures.
      int roll = random.nextInt(RIPPLE_VARIANT_ROLL);
      if (roll < 10) return 0;
      if (roll < 30) return 1;
      if (roll < 60) return 2;
      return 3;
    }

    private boolean hasResources() {
      if (resourcesChecked) return resourcesAvailable;
      resourcesChecked = true;
      resourcesAvailable = false;
      if (Riiablo.files == null || Riiablo.files.objects == null || Riiablo.mpqs == null) return false;
      for (int i = 0; i < RIPPLE_VARIANTS; i++) {
        com.riiablo.codec.excel.Objects.Entry object =
            Riiablo.files.objects.get(FIRST_CLASS_ID + i);
        if (object == null || object.Token == null || object.Token.isEmpty()) continue;
        String cofPath = "data\\global\\objects\\" + object.Token + "\\COF\\"
            + object.Token + "NUHTH.cof";
        String path = "data\\global\\objects\\" + object.Token + "\\TR\\"
            + object.Token + "TRLITNUHTH.dcc";
        if (Riiablo.mpqs.contains(path)) {
          resourcesAvailable = true;
          // Queue the native ripple resources before the first object is
          // created.  CofLayerLoader still owns the per-entity references;
          // this only removes the first-frame async loading race.
          if (Riiablo.assets != null) {
            if (Riiablo.mpqs.contains(cofPath)) {
              Riiablo.assets.load(new AssetDescriptor<>(cofPath,
                  com.riiablo.codec.COF.class));
            }
            Riiablo.assets.load(new AssetDescriptor<>(path,
                com.riiablo.codec.DCC.class));
          }
        }
      }
      return resourcesAvailable;
    }

    void clear() {
      for (int i = 0; i < active.size; i++) delete(active.get(i).entityId);
      active.clear();
      spawnBudget = 0f;
    }

    private void delete(int entityId) {
      if (entityId >= 0 && world != null && world.getEntityManager().isActive(entityId)) {
        world.delete(entityId);
      }
    }
  }

  /**
   * Returns whether an Objects.txt row is a client-side water ripple emitter.
   * Ripple rows (67..70) are the visual products, not emitters themselves;
   * treating them as emitters would recursively multiply every spawned ripple.
   */
  static boolean isClientRainRippleEmitter(com.riiablo.codec.excel.Objects.Entry base) {
    return base != null && base.ClientFn == 2
        && (base.Id < RainRippleField.FIRST_CLASS_ID
            || base.Id >= RainRippleField.FIRST_CLASS_ID + RainRippleField.RIPPLE_VARIANTS);
  }

  static final class Ripple {
    final int entityId;
    float remainingSeconds;

    Ripple(int entityId, float remainingSeconds) {
      this.entityId = entityId;
      this.remainingSeconds = remainingSeconds;
    }
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
    final float[] fallSpeed = new float[PARTICLE_COUNT];
    final float[] size = new float[PARTICLE_COUNT];
    final float[] shade = new float[PARTICLE_COUNT];
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
    int windAngle;
    int targetWindAngle;
    int windTicks;
    int rainShadeOffset;
    int rainDepthOffset;
    float windX;
    float windY;

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
      if (mode == Mode.RAIN) {
        // The renderer exposes only the first active particles. Stratify the
        // two native-looking rain families across that prefix so a light storm
        // does not randomly collapse into only the darkest/brightest streaks.
        // The offsets keep the ordering from looking fixed while preserving a
        // balanced sample for every active count.
        rainShadeOffset = random.nextInt(RAIN_SHADE_COUNT);
        rainDepthOffset = random.nextInt(RAIN_DEPTH_BUCKET_COUNT);
        windAngle = randomRainAngle();
        targetWindAngle = windAngle;
        windTicks = randomWindTicks();
        updateWindVector();
      }
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
      if (mode == Mode.RAIN) advanceWind();
      for (int i = 0; i < PARTICLE_COUNT; i++) {
        previousX[i] = x[i];
        previousY[i] = y[i];
        if (mode == Mode.RAIN) {
          velocityX[i] = windX * fallSpeed[i];
          velocityY[i] = windY * fallSpeed[i];
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
        // Native rain assigns a shared direction but a per-drop perspective depth.
        // That depth correlates its 4..12 pixel streak with its 15..30 pixel/tick fall speed.
        int depthSlot = (i + rainDepthOffset) % RAIN_DEPTH_BUCKET_COUNT;
        float depth = (depthSlot + random.nextFloat()) / RAIN_DEPTH_BUCKET_COUNT;
        size[i] = RAIN_MIN_LENGTH
            + depthSlot;
        float speedPerTick = MathUtils.lerp(
            RAIN_MIN_SPEED_PER_TICK, RAIN_MAX_SPEED_PER_TICK, depth);
        fallSpeed[i] = speedPerTick / SimulationClock.STEP_SECONDS;
        velocityY[i] = windY * fallSpeed[i];
        velocityX[i] = windX * fallSpeed[i];
        int shadeSlot = (i + rainShadeOffset) % RAIN_SHADE_COUNT;
        shade[i] = rainShade(shadeSlot);
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

    private void advanceWind() {
      if (--windTicks <= 0) {
        targetWindAngle = randomRainAngle();
        windTicks = randomWindTicks();
      }

      if (windAngle < targetWindAngle) {
        windAngle = Math.min(windAngle + 2, targetWindAngle);
      } else if (windAngle > targetWindAngle) {
        windAngle = Math.max(windAngle - 2, targetWindAngle);
      }
      updateWindVector();
    }

    private int randomRainAngle() {
      return RAIN_MIN_ANGLE + random.nextInt(RAIN_MAX_ANGLE - RAIN_MIN_ANGLE + 1);
    }

    private int randomWindTicks() {
      return RAIN_MIN_WIND_TICKS
          + random.nextInt(RAIN_MAX_WIND_TICKS - RAIN_MIN_WIND_TICKS + 1);
    }

    static float rainShade(int slot) {
      int nativeValue = RAIN_SHADE_BASE - RAIN_SHADE_RANGE * slot / RAIN_SHADE_COUNT;
      return nativeValue / (float) RAIN_SHADE_BASE;
    }

    private void updateWindVector() {
      float radians = windAngle * MathUtils.PI2 / RAIN_ANGLE_UNITS;
      windX = MathUtils.cos(radians);
      windY = -MathUtils.sin(radians);
    }
  }
}
