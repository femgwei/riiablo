package com.riiablo.audio;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;

/** Spatial gate for monster-owned sounds emitted by local AI and clients. */
public final class MonsterAudio {
  /** Native-style listener radius shared with the client monster emitter. */
  public static final float AUDIBLE_RADIUS = 20f;
  public static final float AUDIBLE_RADIUS2 = AUDIBLE_RADIUS * AUDIBLE_RADIUS;

  private MonsterAudio() {}

  /** Plays a monster sound only when the local listener can hear its entity. */
  public static Audio.Instance play(int entityId, String sound) {
    return play(entityId, sound, true);
  }

  /** Compatibility overload for legacy AI call sites that passed {@code global}. */
  public static Audio.Instance play(int entityId, String sound, boolean global) {
    if (!isAudible(entityId) || sound == null || sound.isEmpty() || Riiablo.audio == null) {
      return null;
    }
    return Riiablo.audio.play(sound, global);
  }

  /** Applies the same local-player, Zone and distance policy to all callers. */
  public static boolean isAudible(int entityId) {
    if (Riiablo.game == null || Riiablo.game.player < 0 || Riiablo.engine == null) return false;
    ComponentMapper<Position> positions = Riiablo.engine.getMapper(Position.class);
    if (!positions.has(Riiablo.game.player) || !positions.has(entityId)) return false;

    ComponentMapper<MapWrapper> maps = Riiablo.engine.getMapper(MapWrapper.class);
    if (maps.has(Riiablo.game.player) && maps.has(entityId)) {
      MapWrapper listener = maps.get(Riiablo.game.player);
      MapWrapper emitter = maps.get(entityId);
      if (listener != null && emitter != null && listener.zone != null
          && emitter.zone != null && listener.zone != emitter.zone) return false;
    }

    Vector2 listener = positions.get(Riiablo.game.player).position;
    Vector2 emitter = positions.get(entityId).position;
    return isAudible(listener.dst2(emitter), true);
  }

  /** Pure predicate kept small enough for deterministic unit tests. */
  public static boolean isAudible(float distance2, boolean sameZone) {
    return sameZone && distance2 >= 0f && distance2 <= AUDIBLE_RADIUS2;
  }
}
