package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.MathUtils;

import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.SoundEnviron;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;

/** Plays the ambience and periodic event authored by the player's current level. */
public final class SoundEnvironmentSystem extends BaseSystem {
  private static final int NO_SOUND = 0;

  protected ComponentMapper<MapWrapper> mMapWrapper;

  @Wire(name = "environment")
  protected EnvironmentCycle environment;

  private int environmentId = -1;
  private boolean day = true;
  private SoundEnviron.Entry soundEnvironment;

  private int ambienceId = NO_SOUND;
  private Sounds.Entry ambienceSound;
  private Audio.Instance ambience;

  private int eventFramesRemaining;
  private Sounds.Entry eventSound;
  private Audio.Instance event;
  private int eventDurationFrames;
  private boolean eventFading;

  @Override
  protected void processSystem() {
    Audio audio = Riiablo.audio;
    if (audio == null || audio.isBackgroundPaused()) return;

    Map.Zone zone = playerZone();
    int nextEnvironmentId = zone == null || zone.level == null ? 0 : zone.level.SoundEnv;
    boolean nextDay = environment == null || environment.isDay();
    if (nextEnvironmentId != environmentId || nextDay != day) {
      changeEnvironment(nextEnvironmentId, nextDay);
    }

    tickEventDuration();
    if (soundEnvironment == null || eventFramesRemaining <= 0) return;
    if (--eventFramesRemaining == 0) {
      playEvent(eventId(soundEnvironment, day));
      eventFramesRemaining = Math.max(0, soundEnvironment.Event_Delay);
    }
  }

  private Map.Zone playerZone() {
    if (Riiablo.game == null) return null;
    int player = Riiablo.game.player;
    if (player == Engine.INVALID_ENTITY || !mMapWrapper.has(player)) return null;
    MapWrapper mapWrapper = mMapWrapper.get(player);
    return mapWrapper == null ? null : mapWrapper.zone;
  }

  private void changeEnvironment(int nextEnvironmentId, boolean nextDay) {
    SoundEnviron.Entry next = Riiablo.files == null || Riiablo.files.SoundEnviron == null
        ? null
        : Riiablo.files.SoundEnviron.get(nextEnvironmentId);

    stopEvent(true);
    soundEnvironment = next;
    environmentId = nextEnvironmentId;
    day = nextDay;
    eventFramesRemaining = next == null ? 0 : Math.max(0, next.Event_Delay);

    int nextAmbienceId = ambienceId(next, nextDay);
    if (nextAmbienceId == ambienceId && ambience != null) return;

    if (ambience != null) {
      ambience.fadeOutAndStop(framesToSeconds(
          ambienceSound == null ? 0 : ambienceSound.Fade_Out));
    }

    ambienceId = nextAmbienceId;
    ambienceSound = sound(nextAmbienceId, false);
    ambience = ambienceSound == null ? null
        : Riiablo.audio.play(ambienceSound, true, Audio.Channel.ENVIRONMENT);
    if (ambience != null && ambienceSound.Fade_In > 0) {
      ambience.fadeIn(framesToSeconds(ambienceSound.Fade_In));
    }
  }

  private void playEvent(int soundId) {
    stopEvent(false);
    eventSound = sound(soundId, true);
    event = eventSound == null ? null
        : Riiablo.audio.play(eventSound, true, Audio.Channel.ENVIRONMENT);
    eventDurationFrames = eventSound == null || !eventSound.Loop
        ? 0
        : Math.max(0, eventSound.Duration);
    eventFading = false;
    if (event != null && eventSound.Fade_In > 0) {
      event.fadeIn(framesToSeconds(eventSound.Fade_In));
    }
  }

  private void tickEventDuration() {
    if (event == null || eventDurationFrames <= 0) return;
    eventDurationFrames--;
    int fadeOutFrames = Math.max(0, eventSound.Fade_Out);
    if (!eventFading && eventDurationFrames <= fadeOutFrames) {
      eventFading = true;
      if (fadeOutFrames == 0) {
        event.stop();
      } else {
        event.fadeOutAndStop(framesToSeconds(fadeOutFrames));
      }
    }
    if (eventDurationFrames == 0) clearEvent();
  }

  private void stopEvent(boolean fade) {
    if (event != null) {
      int fadeOutFrames = eventSound == null ? 0 : Math.max(0, eventSound.Fade_Out);
      if (fade && fadeOutFrames > 0) {
        event.fadeOutAndStop(framesToSeconds(fadeOutFrames));
      } else {
        event.stop();
      }
    }
    clearEvent();
  }

  private void clearEvent() {
    event = null;
    eventSound = null;
    eventDurationFrames = 0;
    eventFading = false;
  }

  private static Sounds.Entry sound(int id, boolean randomizeGroup) {
    if (id <= NO_SOUND || Riiablo.files == null || Riiablo.files.Sounds == null) return null;
    Sounds.Entry sound = Riiablo.files.Sounds.get(id);
    if (sound == null) return null;
    if (randomizeGroup && sound.Group_Size > 0) {
      sound = Riiablo.files.Sounds.get(sound.Index + MathUtils.random.nextInt(sound.Group_Size));
    }
    return sound == null || sound.FileName == null || sound.FileName.isEmpty() ? null : sound;
  }

  static int ambienceId(SoundEnviron.Entry environment, boolean day) {
    if (environment == null) return NO_SOUND;
    return day ? environment.Day_Ambience : environment.Night_Ambience;
  }

  static int eventId(SoundEnviron.Entry environment, boolean day) {
    if (environment == null) return NO_SOUND;
    return day ? environment.Day_Event : environment.Night_Event;
  }

  static float framesToSeconds(int frames) {
    return Math.max(0, frames) / (float) SimulationClock.TICKS_PER_SECOND;
  }

  @Override
  protected void dispose() {
    if (ambience != null) ambience.stop();
    stopEvent(false);
    ambience = null;
    ambienceSound = null;
    ambienceId = NO_SOUND;
  }
}
