package com.riiablo.engine.client;

import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.SoundEnviron;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.map.EnvironmentCycle;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Host-owned bindings for one client's environmental audio, not server simulation state.
 * The listener is read live because the host assigns/replaces its player after assembly.
 * Audio may be absent in silent hosts; tables and the clock must always be supplied.
 * This binding does not own or dispose the shared audio service or table resources.
 */
public final class ClientAudioSession {
  private final EnvironmentCycle environment;
  private final IntSupplier listener;
  private final Audio audio;
  private final SoundEnviron soundEnviron;
  private final Sounds sounds;

  public ClientAudioSession(EnvironmentCycle environment, IntSupplier listener,
      Audio audio, SoundEnviron soundEnviron, Sounds sounds) {
    this.environment = Objects.requireNonNull(environment, "environment");
    this.listener = Objects.requireNonNull(listener, "listener");
    this.audio = audio;
    this.soundEnviron = Objects.requireNonNull(soundEnviron, "soundEnviron");
    this.sounds = Objects.requireNonNull(sounds, "sounds");
  }

  public EnvironmentCycle environment() { return environment; }
  public int listener() { return listener.getAsInt(); }
  public Audio audio() { return audio; }
  public SoundEnviron soundEnviron() { return soundEnviron; }
  public Sounds sounds() { return sounds; }
}
