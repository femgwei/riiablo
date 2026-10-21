package com.riiablo.presentation;

import com.artemis.World;
import com.badlogic.gdx.math.Interpolation;
import com.riiablo.audio.Audio;
import com.riiablo.presentation.component.SoundEmitter;

/** Owns the initial travel playback and its positional lifetime binding. */
public final class MissileTravelAudio {
  private MissileTravelAudio() {}

  public static void play(World world, ClientSpatialAudioServices session, int entity, String sound) {
    if (session.audio() == null || sound == null || sound.isEmpty()) return;
    Audio.Instance travel = session.audio().play(sound, true);
    if (travel == null) return;
    // Apply distance/zone gain before the first emitter tick to avoid a full-volume burst.
    travel.setVolume(SpatialAudio.gain(world, session.listener(), entity));
    world.getMapper(SoundEmitter.class).create(entity).set(travel, Interpolation.pow2OutInverse,
        SoundEmitter.DEFAULT_RADIUS, true);
  }
}
