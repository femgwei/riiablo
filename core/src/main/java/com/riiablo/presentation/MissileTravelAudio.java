package com.riiablo.presentation;

import com.artemis.World;
import com.badlogic.gdx.math.Interpolation;
import com.riiablo.audio.Audio;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.presentation.component.SoundEmitter;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Owns the initial travel playback and its positional lifetime binding. */
public final class MissileTravelAudio {
  // World-local entity IDs only: do not retain pooled Audio.Instance references.
  private static final Map<World, Map<Integer, Integer>> poisonEmitters = new WeakHashMap<>();

  private MissileTravelAudio() {}

  public static void play(World world, ClientSpatialAudioServices session, int entity, String sound) {
    if (session.audio() == null || sound == null || sound.isEmpty()) return;
    Missile missile = world.getMapper(Missile.class).get(entity);
    if (isStateOwnedLoop(missile == null ? null : missile.missile, sound)) return;
    boolean poisonCloud = missile != null && missile.missile != null
        && "poisonjavcloud".equals(missile.missile.Missile)
        && "object_poison_loop".equals(sound) && missile.ownerId >= 0;
    Map<Integer, Integer> owners = null;
    if (poisonCloud) {
      owners = poisonEmitters.computeIfAbsent(world, unused -> new HashMap<>());
      Integer previous = owners.get(missile.ownerId);
      if (previous != null && previous != entity) {
        Missile oldMissile = world.getMapper(Missile.class).get(previous);
        SoundEmitter oldEmitter = world.getMapper(SoundEmitter.class).get(previous);
        if (oldMissile != null && oldMissile.ownerId == missile.ownerId
            && oldMissile.missile != null
            && "poisonjavcloud".equals(oldMissile.missile.Missile)
            && oldEmitter != null && oldEmitter.sound != null) {
          // Transfer, never share: deleting the old cloud must not stop the loop.
          // Also covers deferred playback, whose isPlaying() is still false.
          Audio.Instance ongoing = oldEmitter.sound;
          oldEmitter.sound = null;
          world.getMapper(SoundEmitter.class).remove(previous);
          ongoing.setVolume(SpatialAudio.gain(world, session.listener(), entity));
          world.getMapper(SoundEmitter.class).create(entity).set(ongoing,
              Interpolation.pow2OutInverse, SoundEmitter.DEFAULT_RADIUS, true);
          owners.put(missile.ownerId, entity);
          return;
        }
      }
    }
    Audio.Instance travel = session.audio().play(sound, true);
    if (travel == null) return;
    // Apply distance/zone gain before the first emitter tick to avoid a full-volume burst.
    travel.setVolume(SpatialAudio.gain(world, session.listener(), entity));
    world.getMapper(SoundEmitter.class).create(entity).set(travel, Interpolation.pow2OutInverse,
        SoundEmitter.DEFAULT_RADIUS, true);
    if (owners != null) owners.put(missile.ownerId, entity);
  }

  static boolean isStateOwnedLoop(com.riiablo.codec.excel.Missiles.Entry row, String sound) {
    // The SrvDo124 carrier lasts two ticks. Restarting this native looping
    // sound on every carrier/particle produces repeated wind attacks/cuts.
    return "druid_hurricane".equals(sound) && row != null && row.Missile != null
        && row.Missile.startsWith("hurricane");
  }
}
