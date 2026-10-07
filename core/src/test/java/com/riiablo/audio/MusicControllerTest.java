package com.riiablo.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MusicControllerTest {
  private Application previousApplication;

  @BeforeEach
  void setUpApplication() {
    previousApplication = Gdx.app;
    Gdx.app = mock(Application.class);
  }

  @AfterEach
  void restoreApplication() {
    Gdx.app = previousApplication;
  }

  @Test
  void identifiesOptionalMissingTrackFailure() {
    RuntimeException root = new IllegalArgumentException("file cannot be null");
    RuntimeException wrapped = new RuntimeException("Couldn't load dependencies of asset", root);
    assertTrue(MusicController.isMissingMusicFailure(wrapped));
  }

  @Test
  void retainsUnexpectedAudioFailures() {
    assertFalse(MusicController.isMissingMusicFailure(
        new RuntimeException("decoder rejected corrupted stream")));
  }

  @Test
  void backgroundPausesCurrentTrackAndDefersNewRequests() {
    AssetManager assets = mock(AssetManager.class);
    Music track = mock(Music.class);
    when(assets.get("first", Music.class)).thenReturn(track);
    when(track.isPlaying()).thenReturn(true);
    MusicController controller = new MusicController(assets);

    controller.play("first");
    controller.pauseForBackground();
    assertTrue(controller.isBackgroundPaused());
    verify(track).pause();

    controller.enqueue("queued");
    verify(assets, never()).load("queued", Music.class);

    controller.resumeFromBackground();
    assertFalse(controller.isBackgroundPaused());
    verify(track, times(2)).play();
  }

  @Test
  void explicitBackgroundPlayStartsOnForeground() {
    AssetManager assets = mock(AssetManager.class);
    Music first = mock(Music.class);
    Music queued = mock(Music.class);
    when(assets.get("first", Music.class)).thenReturn(first);
    when(assets.get("queued", Music.class)).thenReturn(queued);
    when(first.isPlaying()).thenReturn(true);
    MusicController controller = new MusicController(assets);

    controller.play("first");
    controller.pauseForBackground();
    controller.play("queued");
    verify(assets, never()).load("queued", Music.class);

    controller.resumeFromBackground();
    verify(assets).load("queued", Music.class);
    verify(queued).play();
  }

  @Test
  void queuedTrackStartsOnlyAfterReturningToForeground() {
    AssetManager assets = mock(AssetManager.class);
    Music track = mock(Music.class);
    when(assets.get("queued", Music.class)).thenReturn(track);
    MusicController controller = new MusicController(assets);

    controller.pauseForBackground();
    controller.enqueue("queued");
    verify(assets, never()).load("queued", Music.class);

    controller.resumeFromBackground();
    verify(assets).load("queued", Music.class);
    verify(track).play();
  }
}
