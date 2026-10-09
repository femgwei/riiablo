package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SoundEmitter;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.DT1;

/** Creates distance-attenuated environmental loops for native map sound markers. */
@All({com.riiablo.engine.server.component.Object.class, Position.class, MapWrapper.class})
public final class ObjectAmbientSoundSystem extends IteratingSystem {
  private static final String TAG = "ObjectAmbientSoundSystem";
  // D2MOO ObjectsIds.h: OBJECT_INVISIBLE_RIVER_SOUND1/2.
  static final int INVISIBLE_RIVER_SOUND_1 = 65;
  static final int INVISIBLE_RIVER_SOUND_2 = 66;
  // Native Objects.txt rows initialized as interactive torches/braziers.
  static final int BRAZIER = 29;
  static final int TORCH_TIKI = 37;
  static final int TORCH_WALL = 38;
  static final int BRAZIER_3 = 101;
  static final int FLOOR_BRAZIER = 102;
  static final int JUNGLE_TORCH = 117;
  // Native Sounds.txt loops for the matching object families.
  static final int BRAZIER_SOUND = 2574;
  static final int TORCH_SOUND = 2578;
  // Native Sounds.txt: object_river / ESOUND_OBJECT_RIVER.
  static final int RIVER_SOUND = 2599;

  static final float AUDIBLE_RADIUS = SoundEmitter.DEFAULT_RADIUS;
  static final float STOP_RADIUS = AUDIBLE_RADIUS + 2f;
  // Sounds.txt Falloff=3 (Ambient): 400..1500 authored pixels. A DT1
  // subtile is 32 pixels in the renderer, so keep river ambience audible
  // across the native 12.5..46.875 subtile range.
  static final float RIVER_MIN_RADIUS = 400f / DT1.Tile.SUBTILE_WIDTH;
  static final float RIVER_AUDIBLE_RADIUS = 1500f / DT1.Tile.SUBTILE_WIDTH;
  static final float RIVER_STOP_RADIUS = RIVER_AUDIBLE_RADIUS + 2f;

  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<SoundEmitter> mSoundEmitter;

  private boolean riverMarkerAudible;
  private boolean missingRiverSoundLogged;
  private Audio.Instance terrainRiver;

  @Override
  protected void begin() {
    riverMarkerAudible = false;
  }

  @Override
  protected void process(int entityId) {
    com.riiablo.engine.server.component.Object object = mObject.get(entityId);
    if (object == null || object.base == null || !isAmbientSource(object.base.Id)) return;

    SoundEmitter emitter = mSoundEmitter.has(entityId) ? mSoundEmitter.get(entityId) : null;
    int soundId = soundId(object.base.Id, object.mode);
    if (soundId < 0) {
      // A native torch operation switches a burning OP/ON object back to NU.
      // Only remove emitters for object classes owned by this system so other
      // object audio (portals, quest effects, and so on) remains untouched.
      if (emitter != null) mSoundEmitter.remove(entityId);
      return;
    }
    float audibleRadius = soundId == RIVER_SOUND ? RIVER_AUDIBLE_RADIUS : AUDIBLE_RADIUS;
    float stopRadius = soundId == RIVER_SOUND ? RIVER_STOP_RADIUS : STOP_RADIUS;
    boolean audible = isAudible(entityId, emitter == null ? audibleRadius : stopRadius);
    if (!audible) {
      if (emitter != null) mSoundEmitter.remove(entityId);
      return;
    }
    if (soundId == RIVER_SOUND) riverMarkerAudible = true;
    if (emitter != null || Riiablo.audio == null || Riiablo.audio.isBackgroundPaused()
        || Riiablo.files == null || Riiablo.files.Sounds == null) return;

    Sounds.Entry sound = Riiablo.files.Sounds.get(soundId);
    Audio.Instance instance = Riiablo.audio.play(sound, true, Audio.Channel.ENVIRONMENT);
    if (instance == null) return;

    if (sound.Fade_In > 0) {
      instance.fadeIn(sound.Fade_In / (float) SimulationClock.TICKS_PER_SECOND);
    } else {
      // Deferred Sound assets must also begin silent; SoundEmitterHandler sets
      // the first spatial gain after the asset becomes available.
      instance.setVolume(0f);
    }
    mSoundEmitter.create(entityId).set(
        instance, Interpolation.linear, audibleRadius, true,
        soundId == RIVER_SOUND ? RIVER_MIN_RADIUS : 0f);
    Vector2 source = mPosition.get(entityId).position;
    String event = soundId == RIVER_SOUND ? "RIVER_AMBIENCE" : "FIRE_AMBIENCE";
    com.badlogic.gdx.Gdx.app.log(TAG, String.format(
        "[%s] phase=start source=object entity=%d object=%d sound=%d position=(%.2f,%.2f)",
        event, entityId, object.base.Id, soundId, source.x, source.y));
  }

  @Override
  protected void end() {
    // Hand-authored DS1s place objects 65/66 and remain authoritative. The
    // D2MOO outdoor generator instead bakes the visible river into River.dt1
    // floor cells and marks its decorative objects as already spawned, so no
    // ECS marker exists for the object-based path above. Use those native
    // floor cells as a single continuous positional source rather than
    // starting one overlapping loop per generated river object.
    if (riverMarkerAudible) {
      stopTerrainRiver();
      return;
    }

    MapWrapper listenerMap = playerMap();
    if (listenerMap == null || listenerMap.zone == null || !mPosition.has(Riiablo.game.player)) {
      stopTerrainRiver();
      return;
    }

    TileGrid grid = listenerMap.zone.nativeTileGrid();
    Vector2 listener = mPosition.get(Riiablo.game.player).position;
    float radius = terrainRiver == null ? RIVER_AUDIBLE_RADIUS : RIVER_STOP_RADIUS;
    float distance2 = nearestRiverDistance2(
        grid, listener.x - listenerMap.zone.x(), listener.y - listenerMap.zone.y(), radius);
    if (!withinRadius(distance2, radius)) {
      stopTerrainRiver();
      return;
    }

    if (terrainRiver == null) startTerrainRiver(listenerMap.zone, listener, distance2);
    if (terrainRiver != null) {
      terrainRiver.setVolume(SoundEmitterHandler.spatialGain(
          (float) Math.sqrt(distance2), RIVER_MIN_RADIUS, RIVER_AUDIBLE_RADIUS,
          Interpolation.linear));
    }
  }

  private MapWrapper playerMap() {
    if (Riiablo.game == null) return null;
    int player = Riiablo.game.player;
    if (player == Engine.INVALID_ENTITY || !mMapWrapper.has(player)) return null;
    MapWrapper wrapper = mMapWrapper.get(player);
    return wrapper == null || wrapper.map == null ? null : wrapper;
  }

  private void startTerrainRiver(com.riiablo.map.Map.Zone zone, Vector2 listener,
      float distance2) {
    if (Riiablo.audio == null || Riiablo.audio.isBackgroundPaused()
        || Riiablo.files == null || Riiablo.files.Sounds == null) return;
    Sounds.Entry sound = Riiablo.files.Sounds.get(RIVER_SOUND);
    if (sound == null || sound.FileName == null || sound.FileName.isEmpty()) {
      if (!missingRiverSoundLogged) {
        missingRiverSoundLogged = true;
        com.badlogic.gdx.Gdx.app.error(TAG,
            "[RIVER_AMBIENCE] phase=missing_sound sound=" + RIVER_SOUND);
      }
      return;
    }
    missingRiverSoundLogged = false;

    terrainRiver = Riiablo.audio.play(sound, true, Audio.Channel.ENVIRONMENT);
    if (terrainRiver == null) return;
    terrainRiver.setVolume(0f);
    if (sound.Fade_In > 0) {
      terrainRiver.fadeIn(sound.Fade_In / (float) SimulationClock.TICKS_PER_SECOND);
    }
    com.badlogic.gdx.Gdx.app.log(TAG, String.format(
        "[RIVER_AMBIENCE] phase=start source=terrain level=%d position=(%.2f,%.2f) distance=%.2f",
        zone.levelId(), listener.x, listener.y, Math.sqrt(distance2)));
  }

  private void stopTerrainRiver() {
    if (terrainRiver == null) return;
    terrainRiver.stop();
    terrainRiver = null;
  }

  private boolean isAudible(int entityId, float radius) {
    if (Riiablo.game == null) return false;
    int player = Riiablo.game.player;
    if (player == Engine.INVALID_ENTITY || !mPosition.has(player)
        || !mMapWrapper.has(player)) return false;

    MapWrapper sourceMap = mMapWrapper.get(entityId);
    MapWrapper listenerMap = mMapWrapper.get(player);
    if (sourceMap == null || listenerMap == null || sourceMap.map != listenerMap.map
        || sourceMap.zone == null || sourceMap.zone != listenerMap.zone) return false;

    Vector2 source = mPosition.get(entityId).position;
    Vector2 listener = mPosition.get(player).position;
    return withinRadius(source.dst2(listener), radius);
  }

  static int soundId(int objectId, int mode) {
    switch (objectId) {
      case INVISIBLE_RIVER_SOUND_1:
      case INVISIBLE_RIVER_SOUND_2:
        return RIVER_SOUND;
      case TORCH_TIKI:
      case TORCH_WALL:
      case JUNGLE_TORCH:
        return isBurningMode(mode) ? TORCH_SOUND : -1;
      case BRAZIER:
      case BRAZIER_3:
      case FLOOR_BRAZIER:
        return isBurningMode(mode) ? BRAZIER_SOUND : -1;
      default:
        return -1;
    }
  }

  static boolean isAmbientSource(int objectId) {
    switch (objectId) {
      case INVISIBLE_RIVER_SOUND_1:
      case INVISIBLE_RIVER_SOUND_2:
      case TORCH_TIKI:
      case TORCH_WALL:
      case JUNGLE_TORCH:
      case BRAZIER:
      case BRAZIER_3:
      case FLOOR_BRAZIER:
        return true;
      default:
        return false;
    }
  }

  static boolean isBurningMode(int mode) {
    // D2MOO InitFunction08_Torch starts these objects in ON. Its native
    // operate functions use OP while lighting and NU after extinguishing.
    return mode == Engine.Object.MODE_OP || mode == Engine.Object.MODE_ON;
  }

  static boolean withinRadius(float distance2, float radius) {
    return radius > 0f && distance2 < radius * radius;
  }

  static float nearestRiverDistance2(
      TileGrid grid, float localX, float localY, float radius) {
    if (grid == null || radius <= 0f) return Float.POSITIVE_INFINITY;
    final float tileSize = DT1.Tile.SUBTILE_SIZE;
    int minX = Math.max(0, MathUtils.floor((localX - radius) / tileSize));
    int maxX = Math.min(grid.width - 1, MathUtils.floor((localX + radius) / tileSize));
    int minY = Math.max(0, MathUtils.floor((localY - radius) / tileSize));
    int maxY = Math.min(grid.height - 1, MathUtils.floor((localY + radius) / tileSize));
    if (minX > maxX || minY > maxY) return Float.POSITIVE_INFINITY;

    float nearest = Float.POSITIVE_INFINITY;
    for (int y = minY; y <= maxY; y++) {
      for (int x = minX; x <= maxX; x++) {
        if (!grid.exportedFloorCells[y][x]
            || !isRiverSource(grid.sourceFile(grid.floorSourceFiles[y][x]))) continue;
        float dx = axisDistance(localX, x * tileSize, (x + 1) * tileSize);
        float dy = axisDistance(localY, y * tileSize, (y + 1) * tileSize);
        nearest = Math.min(nearest, dx * dx + dy * dy);
      }
    }
    return nearest;
  }

  static boolean isRiverSource(String sourceFile) {
    if (sourceFile == null) return false;
    String normalized = sourceFile.replace('\\', '/');
    return normalized.equalsIgnoreCase("River.dt1")
        || normalized.regionMatches(true,
            Math.max(0, normalized.length() - "/River.dt1".length()),
            "/River.dt1", 0, "/River.dt1".length());
  }

  private static float axisDistance(float value, float min, float max) {
    if (value < min) return min - value;
    if (value > max) return value - max;
    return 0f;
  }

  @Override
  protected void dispose() {
    stopTerrainRiver();
  }
}
