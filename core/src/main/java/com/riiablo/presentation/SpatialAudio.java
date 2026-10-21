package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.riiablo.audio.Audio;
import com.riiablo.audio.MonsterAudio;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;

/** Entity audio resolved against the owning world and its live listener. */
final class SpatialAudio {
  private SpatialAudio() {}

  static float gain(World world, int listener, int source) {
    return MonsterAudio.spatialGain(distance2(world, listener, source), true);
  }

  static boolean isAudible(World world, int listener, int source) {
    return MonsterAudio.isAudible(distance2(world, listener, source), true);
  }

  private static float distance2(World world, int listener, int source) {
    if (world == null || listener < 0 || source < 0) return -1f;
    ComponentMapper<Position> positions = world.getMapper(Position.class);
    if (!positions.has(listener) || !positions.has(source)) return -1f;
    ComponentMapper<MapWrapper> maps = world.getMapper(MapWrapper.class);
    if (maps.has(listener) && maps.has(source)) {
      MapWrapper a = maps.get(listener), b = maps.get(source);
      if (a != null && b != null && a.zone != null && b.zone != null && a.zone != b.zone) return -1f;
    }
    return positions.get(listener).position.dst2(positions.get(source).position);
  }

  static Audio.Instance play(World world, ClientSpatialAudioServices session,
      int source, String sound, boolean global) {
    if (sound == null || sound.isEmpty() || session.audio() == null) return null;
    float gain = gain(world, session.listener(), source);
    if (gain <= 0f) return null;
    Audio.Instance instance = session.audio().play(sound, global);
    if (instance != null) instance.setVolume(gain);
    return instance;
  }
}
