package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SoundEmitter;

@All({SoundEmitter.class, Position.class})
public class SoundEmitterHandler extends IteratingSystem {
  protected ComponentMapper<SoundEmitter> mSoundEmitter;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  @Override
  protected void dispose() {
    IntBag entities = getEntityIds();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int id = entities.get(i);
      unload(id);
    }
  }

  @Override
  protected void process(int entityId) {
    SoundEmitter soundEmitter = mSoundEmitter.get(entityId);
    if (soundEmitter == null || soundEmitter.sound == null
        || !soundEmitter.sound.isLoaded() || Riiablo.game == null) return;
    int player = Riiablo.game.player;
    if (player == Engine.INVALID_ENTITY || !mPosition.has(player)) return;

    Vector2 position = mPosition.get(entityId).position;
    Vector2 src = mPosition.get(player).position;
    boolean sameZone = !soundEmitter.sameZoneOnly || sameZone(entityId, player);
    float volume = sameZone
        ? spatialGain(src.dst(position), soundEmitter.minRadius, soundEmitter.radius,
            soundEmitter.interpolator)
        : 0f;
    soundEmitter.sound.setVolume(volume);
  }

  private void unload(int id) {
    if (!mSoundEmitter.has(id)) return;
    SoundEmitter soundEmitter = mSoundEmitter.get(id);
    if (soundEmitter != null && soundEmitter.sound != null) soundEmitter.sound.stop();
  }

  private boolean sameZone(int source, int listener) {
    if (!mMapWrapper.has(source) || !mMapWrapper.has(listener)) return false;
    MapWrapper sourceMap = mMapWrapper.get(source);
    MapWrapper listenerMap = mMapWrapper.get(listener);
    return sourceMap != null && listenerMap != null
        && sourceMap.map == listenerMap.map
        && sourceMap.zone != null
        && sourceMap.zone == listenerMap.zone;
  }

  static float spatialGain(float distance, float radius, Interpolation interpolation) {
    return spatialGain(distance, 0f, radius, interpolation);
  }

  static float spatialGain(float distance, float minRadius, float radius,
      Interpolation interpolation) {
    if (radius <= 0f || distance >= radius) return 0f;
    float start = MathUtils.clamp(minRadius, 0f, radius);
    if (distance <= start) return 1f;
    float gain = MathUtils.clamp(1f - (distance - start) / (radius - start), 0f, 1f);
    return interpolation == null ? gain : interpolation.apply(gain);
  }
}
