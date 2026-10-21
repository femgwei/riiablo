package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.codec.excel.SoundEnviron;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.engine.Engine;
import com.riiablo.map.EnvironmentCycle;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientAudioSessionTest {
  @Test
  void bindsHostResourcesAndReadsTheListenerLive() {
    EnvironmentCycle environment = new EnvironmentCycle();
    AtomicInteger listener = new AtomicInteger(Engine.INVALID_ENTITY);
    SoundEnviron environs = new SoundEnviron();
    Sounds sounds = new Sounds();
    ClientAudioSession session = new ClientAudioSession(
        environment, listener::get, null, environs, sounds);
    assertSame(environment, session.environment());
    assertSame(environs, session.soundEnviron());
    assertSame(sounds, session.sounds());
    assertNull(session.audio(), "silent hosts are supported explicitly");
    assertEquals(Engine.INVALID_ENTITY, session.listener());
    listener.set(17);
    assertEquals(17, session.listener(), "player assignment happens after world assembly");
  }

  @Test
  void rejectsMissingRequiredBindings() {
    EnvironmentCycle environment = new EnvironmentCycle();
    SoundEnviron environs = new SoundEnviron();
    Sounds sounds = new Sounds();
    assertAll(
        () -> assertThrows(NullPointerException.class,
            () -> new ClientAudioSession(null, () -> -1, null, environs, sounds)),
        () -> assertThrows(NullPointerException.class,
            () -> new ClientAudioSession(environment, null, null, environs, sounds)),
        () -> assertThrows(NullPointerException.class,
            () -> new ClientAudioSession(environment, () -> -1, null, null, sounds)),
        () -> assertThrows(NullPointerException.class,
            () -> new ClientAudioSession(environment, () -> -1, null, environs, null)));
  }

  @Test
  void systemsRequireAnExplicitSession() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> new SoundEnvironmentSystem(null)),
        () -> assertThrows(NullPointerException.class, () -> new ObjectAmbientSoundSystem(null)),
        () -> assertThrows(NullPointerException.class, () -> new SoundEmitterHandler(null)),
        () -> assertThrows(NullPointerException.class, () -> new SoundEventSystem(null)));
  }
}
