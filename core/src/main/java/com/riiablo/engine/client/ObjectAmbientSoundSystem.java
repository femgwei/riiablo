package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SoundEmitter;

/** Creates distance-attenuated environmental loops for native map sound markers. */
@All({com.riiablo.engine.server.component.Object.class, Position.class, MapWrapper.class})
public final class ObjectAmbientSoundSystem extends IteratingSystem {
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
}
