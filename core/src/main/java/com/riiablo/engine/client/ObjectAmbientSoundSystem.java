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
  // Native Sounds.txt: object_river / ESOUND_OBJECT_RIVER.
  static final int RIVER_SOUND = 2599;

  static final float AUDIBLE_RADIUS = SoundEmitter.DEFAULT_RADIUS;
  static final float STOP_RADIUS = AUDIBLE_RADIUS + 2f;

  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<SoundEmitter> mSoundEmitter;

  private boolean markerAudible;
  private boolean missingRiverSoundLogged;
  private Audio.Instance terrainRiver;

  @Override
  protected void begin() {
    markerAudible = false;
  }

  @Override
  protected void process(int entityId) {
    com.riiablo.engine.server.component.Object object = mObject.get(entityId);
    int soundId = object == null || object.base == null ? -1 : soundId(object.base.Id);
    if (soundId < 0) return;

    SoundEmitter emitter = mSoundEmitter.has(entityId) ? mSoundEmitter.get(entityId) : null;
    boolean audible = isAudible(entityId, emitter == null ? AUDIBLE_RADIUS : STOP_RADIUS);
    if (!audible) {
      if (emitter != null) mSoundEmitter.remove(entityId);
      return;
    }
    markerAudible = true;
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
        instance, Interpolation.linear, AUDIBLE_RADIUS, true);
    Vector2 source = mPosition.get(entityId).position;
    com.badlogic.gdx.Gdx.app.log(TAG, String.format(
        "[RIVER_AMBIENCE] phase=start source=object entity=%d object=%d position=(%.2f,%.2f)",
        entityId, object.base.Id, source.x, source.y));
  }

  @Override
  protected void end() {
    // Hand-authored DS1s place objects 65/66 and remain authoritative. The
    // D2MOO outdoor generator instead bakes the visible river into River.dt1
    // floor cells and marks its decorative objects as already spawned, so no
    // ECS marker exists for the object-based path above. Use those native
    // floor cells as a single continuous positional source rather than
    // starting one overlapping loop per generated river object.
    if (markerAudible) {
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
    float radius = terrainRiver == null ? AUDIBLE_RADIUS : STOP_RADIUS;
    float distance2 = nearestRiverDistance2(
        grid, listener.x - listenerMap.zone.x(), listener.y - listenerMap.zone.y(), radius);
    if (!withinRadius(distance2, radius)) {
      stopTerrainRiver();
      return;
    }

    if (terrainRiver == null) startTerrainRiver(listenerMap.zone, listener, distance2);
    if (terrainRiver != null) {
      terrainRiver.setVolume(SoundEmitterHandler.spatialGain(
          (float) Math.sqrt(distance2), AUDIBLE_RADIUS, Interpolation.linear));
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

  static int soundId(int objectId) {
    switch (objectId) {
      case INVISIBLE_RIVER_SOUND_1:
      case INVISIBLE_RIVER_SOUND_2:
        return RIVER_SOUND;
      default:
        return -1;
    }
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
