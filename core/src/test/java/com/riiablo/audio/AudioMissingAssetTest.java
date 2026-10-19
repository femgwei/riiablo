package com.riiablo.audio;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.mpq.MPQFileHandleResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AudioMissingAssetTest {
  private static final String MISSING = "monster/regurgitator/neutral1.wav";
  private final Application previousApp = Gdx.app;
  private final com.badlogic.gdx.Audio previousAudio = Gdx.audio;

  @AfterEach
  void restoreGlobals() {
    Gdx.app = previousApp;
    Gdx.audio = previousAudio;
  }

  @Test
  void missingSoundIsNotQueuedOrRetriedAndOtherSoundsStillPlay() {
    Gdx.app = mock(Application.class);
    Gdx.audio = mock(com.badlogic.gdx.Audio.class);
    FileHandle present = mock(FileHandle.class);
    when(present.exists()).thenReturn(true);
    FileHandleResolver resolver = mock(FileHandleResolver.class);
    when(resolver.resolve("data/global/sfx/present.wav")).thenReturn(present);
    Sound sound = mock(Sound.class);
    when(Gdx.audio.newSound(present)).thenReturn(sound);
    when(sound.play(anyFloat(), anyFloat(), anyFloat())).thenReturn(7L);
    AssetManager assets = assets(resolver);
    try {
      Audio audio = new Audio(assets);
      for (int i = 0; i < 3; i++) assertNull(audio.play(entry(MISSING, false), true));
      assertEquals(0, assets.getQueuedAssets());
      assertTrue(assets.update());
      verify(resolver, times(1)).resolve("data/global/sfx/" + MISSING);
      verify(Gdx.app, times(1)).log("Audio",
          "[MISSING_AUDIO] Skipping unavailable audio: data/global/sfx/" + MISSING);
      verify(Gdx.audio, never()).newSound(any());

      Audio.Instance instance = audio.play(entry("present.wav", false), true);
      assertNotNull(instance);
      assets.finishLoading();
      audio.update();
      assertTrue(instance.isPlaying());
      verify(sound).play(1f, 1f, 0f);
    } finally {
      assets.dispose();
    }
  }

  @Test
  void nonexistentLocalFileAndMissingStreamAreSkippedBeforeLoading() {
    Gdx.audio = mock(com.badlogic.gdx.Audio.class);
    FileHandle missingFile = mock(FileHandle.class);
    when(missingFile.exists()).thenReturn(false);
    AssetManager assets = assets(path -> missingFile);
    try {
      Audio audio = new Audio(assets);
      assertNull(audio.play(entry("missing.wav", false), false));
      assertNull(audio.play(entry("missing-stream.wav", true), true));
      assertEquals(0, assets.getQueuedAssets());
      assertDoesNotThrow(() -> {
        assets.finishLoading();
        audio.update();
      });
      verifyNoInteractions(Gdx.audio);
    } finally {
      assets.dispose();
    }
  }

  @Test
  void availableStreamStillLoadsAndPlays() {
    Gdx.audio = mock(com.badlogic.gdx.Audio.class);
    FileHandle file = mock(FileHandle.class);
    when(file.exists()).thenReturn(true);
    Music music = mock(Music.class);
    when(Gdx.audio.newMusic(file)).thenReturn(music);
    AssetManager assets = assets(path -> file);
    try {
      Audio.Instance instance = new Audio(assets).play(entry("stream.wav", true), true);
      assertNotNull(instance);
      assertTrue(instance.isPlaying());
      verify(music).play();
    } finally {
      assets.dispose();
    }
  }

  @Test
  void original110fRegurgitatorMissingNeutralDoesNotCrashAssetUpdates() {
    String home = System.getenv("D2_HOME");
    assumeTrue(home != null && new FileHandle(home).child("d2sfx.mpq").exists(),
        "D2_HOME with original 1.10f MPQs required");
    Gdx.app = mock(Application.class);
    Gdx.audio = mock(com.badlogic.gdx.Audio.class);
    MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(home));
    assertNull(resolver.resolve("data/global/sfx/" + MISSING));
    assertNotNull(resolver.resolve("data/global/sfx/monster/regurgitator/attack1.wav"));
    AssetManager assets = assets(resolver);
    try {
      Audio audio = new Audio(assets);
      assertNull(audio.play(entry(MISSING, false), true));
      assertTrue(assets.update());
      audio.update();
      assertEquals(0, assets.getQueuedAssets());
      verifyNoInteractions(Gdx.audio);
    } finally {
      assets.dispose();
    }
  }

  private static AssetManager assets(FileHandleResolver resolver) {
    // Match Client: the manager's default resolver differs from its MPQ audio loaders.
    AssetManager assets = new AssetManager();
    VolumeController<Sound> soundVolume = mock(VolumeController.class);
    when(soundVolume.getVolume()).thenReturn(1f);
    VolumeController<Music> musicVolume = mock(VolumeController.class);
    when(musicVolume.getVolume()).thenReturn(1f);
    assets.setLoader(Sound.class,
        new VolumeControlledSoundLoader(resolver, soundVolume));
    assets.setLoader(Music.class,
        new VolumeControlledMusicLoader(resolver, musicVolume));
    return assets;
  }

  private static Sounds.Entry entry(String path, boolean stream) {
    Sounds.Entry entry = new Sounds.Entry();
    entry.FileName = path;
    entry.Stream = stream;
    entry.Volume = 255;
    return entry;
  }
}
