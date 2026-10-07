package com.riiablo.audio;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Pools;
import com.riiablo.codec.excel.Sounds;
import org.junit.jupiter.api.Test;

/** Regression coverage for sounds cancelled while their asset is still loading. */
class AudioInstanceTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void unloadedInstanceCanBeStoppedAndUpdatedSafely() {
    Audio.Instance instance = Audio.Instance.obtain(null, null, -1);
    try {
      assertFalse(instance.isLoaded());
      assertDoesNotThrow(() -> {
        instance.stop();
        instance.play();
        instance.setVolume(0.5f);
      });
    } finally {
      Pools.free(instance);
    }
  }

  @Test
  void busAndInstanceGainPreserveSoundsBaseVolume() {
    AssetManager assets = loadedAssets();
    Sound sound = mock(Sound.class);
    doReturn(sound).when(assets).get(any(AssetDescriptor.class));
    when(sound.play(anyFloat())).thenReturn(7L);
    Audio audio = new Audio(assets);
    Sounds.Entry entry = sound("test.wav", 128, false, false);

    Audio.Instance instance = audio.play(entry, true, Audio.Channel.ENVIRONMENT);
    float base = 128f / 255f;
    verify(sound).play(base);

    instance.setVolume(0.5f);
    verify(sound).setVolume(7L, base * 0.5f);

    audio.setChannelVolume(Audio.Channel.ENVIRONMENT, 0.5f);
    // A non-persistent one-shot is intentionally not retained forever by the
    // active registry. Calling setVolume still uses the current bus value.
    instance.setVolume(0.5f);
    verify(sound).setVolume(7L, base * 0.5f * 0.5f);
    assertEquals(0.5f, audio.getChannelVolume(Audio.Channel.ENVIRONMENT), EPSILON);
  }

  @Test
  void fadeAdvancesUsingSuppliedDeltaAndCanStop() {
    AssetManager assets = loadedAssets();
    Sound sound = mock(Sound.class);
    doReturn(sound).when(assets).get(any(AssetDescriptor.class));
    when(sound.loop(anyFloat())).thenReturn(42L);
    Audio audio = new Audio(assets);
    Sounds.Entry entry = sound("loop.wav", 255, true, false);

    Audio.Instance instance = audio.play(entry, true, Audio.Channel.ENVIRONMENT);
    instance.fadeTo(0f, 2f);
    audio.update(1f);
    verify(sound).setVolume(42L, 0.5f);

    instance.fadeOutAndStop(1f);
    audio.update(1f);
    verify(sound).stop(42L);
    assertFalse(instance.isPlaying());
  }

  @Test
  void fadeInStartsSilentAndRestoresAuthoredVolume() {
    AssetManager assets = loadedAssets();
    Sound sound = mock(Sound.class);
    doReturn(sound).when(assets).get(any(AssetDescriptor.class));
    when(sound.loop(anyFloat())).thenReturn(43L);
    Audio audio = new Audio(assets);
    Sounds.Entry entry = sound("loop.wav", 128, true, false);

    Audio.Instance instance = audio.play(entry, true, Audio.Channel.ENVIRONMENT);
    instance.fadeIn(2f);
    verify(sound).setVolume(43L, 0f);

    audio.update(1f);
    verify(sound).setVolume(43L, (128f / 255f) * 0.5f);
    audio.update(1f);
    verify(sound).setVolume(43L, 128f / 255f);
  }

  @Test
  void backgroundStopsOneShotsAndDoesNotReplayThem() {
    AssetManager assets = loadedAssets();
    Sound sound = mock(Sound.class);
    doReturn(sound).when(assets).get(any(AssetDescriptor.class));
    when(sound.play(anyFloat())).thenReturn(3L);
    Audio audio = new Audio(assets);
    Sounds.Entry entry = sound("one-shot.wav", 255, false, false);

    audio.play(entry, true);
    audio.pauseForBackground();
    assertTrue(audio.isBackgroundPaused());
    verify(sound).stop();
    assertNull(audio.play(entry, true));

    audio.resumeFromBackground();
    assertFalse(audio.isBackgroundPaused());
    verify(sound, times(1)).play(anyFloat());
  }

  @Test
  void backgroundRestartsOnlyPreviouslyPlayingSoundLoops() {
    AssetManager assets = loadedAssets();
    Sound sound = mock(Sound.class);
    doReturn(sound).when(assets).get(any(AssetDescriptor.class));
    when(sound.loop(anyFloat())).thenReturn(10L, 11L);
    Audio audio = new Audio(assets);
    Sounds.Entry entry = sound("loop.wav", 255, true, false);

    Audio.Instance instance = audio.play(entry, true, Audio.Channel.ENVIRONMENT);
    audio.pauseForBackground();
    assertTrue(instance.isPaused());
    verify(sound).stop();

    audio.resumeFromBackground();
    verify(sound, times(2)).loop(anyFloat());
    assertTrue(instance.isPlaying());
  }

  @Test
  void backgroundPausesAndResumesStreamsAtTheirCursor() {
    AssetManager assets = loadedAssets();
    Music music = mock(Music.class);
    doReturn(music).when(assets).get(any(AssetDescriptor.class));
    Audio audio = new Audio(assets);
    Sounds.Entry entry = sound("stream.wav", 255, false, true);

    Audio.Instance instance = audio.play(entry, true, Audio.Channel.SPEECH);
    audio.pauseForBackground();
    verify(music).pause();
    assertTrue(instance.isPaused());

    audio.resumeFromBackground();
    verify(music, times(2)).play();
    assertTrue(instance.isPlaying());
    verify(music, never()).stop();
  }

  @Test
  void backgroundCancelsDeferredOneShotBeforeItCanLoad() {
    AssetManager assets = mock(AssetManager.class);
    Sound sound = mock(Sound.class);
    doReturn(sound).when(assets).get(any(AssetDescriptor.class));
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(false, false, true);
    Audio audio = new Audio(assets);

    Audio.Instance instance = audio.play(sound("late.wav", 255, false, false), true);
    assertFalse(instance.isLoaded());
    audio.pauseForBackground();
    audio.resumeFromBackground();
    audio.update(1f);

    verify(sound, never()).play(anyFloat());
    assertFalse(instance.isPlaying());
  }

  private static AssetManager loadedAssets() {
    AssetManager assets = mock(AssetManager.class);
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    return assets;
  }

  private static Sounds.Entry sound(String fileName, int volume, boolean loop, boolean stream) {
    Sounds.Entry entry = new Sounds.Entry();
    entry.FileName = fileName;
    entry.Volume = volume;
    entry.Loop = loop;
    entry.Stream = stream;
    return entry;
  }
}
