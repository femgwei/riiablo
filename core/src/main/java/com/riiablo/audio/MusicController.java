package com.riiablo.audio;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;

import org.apache.commons.lang3.Validate;

import java.util.Deque;
import java.util.LinkedList;

public class MusicController implements Music.OnCompletionListener {

  private static final String TAG = "MusicController";

  @NonNull
  private final AssetManager ASSETS;

  @NonNull
  private final Deque<String> PLAYLIST;

  @Nullable
  private Music track;

  @Nullable
  private String asset;

  public MusicController(@NonNull AssetManager assetManager) {
    this.ASSETS = Validate.notNull(assetManager, "The AssetManager cannot be null");
    this.PLAYLIST = new LinkedList<>();
  }

  public void enqueue(String asset) {
    PLAYLIST.add(asset);
    if (!isPlaying()) {
      next();
    }
  }

  public void enqueue(AssetDescriptor<Music> asset) {
    enqueue(asset.fileName);
  }

  public void stop() {
    final Music track = this.track;
    if (track == null) {
      return;
    }

    // assert asset != null;
    if (ASSETS.isLoaded(asset)) ASSETS.unload(asset);
    track.dispose();
  }

  public boolean isPlaying() {
    return track != null && track.isPlaying();
  }

  public void play() {
    next();
  }

  public void play(@NonNull String asset) {
    Validate.notNull(asset, "Asset cannot be null");
    PLAYLIST.addFirst(asset);
    next();
  }

  public void play(AssetDescriptor<Music> asset) {
    play(asset.fileName);
  }

  public void next() {
    stop();
    // Music entries are data-driven and may legitimately be absent from a
    // particular game version/MPQ set (1.10f does not ship every expansion
    // track).  Keep trying the remaining playlist instead of allowing an
    // optional track failure to abort the whole client during splash screen.
    while (!PLAYLIST.isEmpty()) {
      this.asset = PLAYLIST.removeFirst();
      try {
        ASSETS.load(asset, Music.class);
        ASSETS.finishLoadingAsset(asset);
        this.track = ASSETS.get(asset, Music.class);
        if (track == null) {
          throw new IllegalStateException("Asset manager returned null music");
        }
        track.setOnCompletionListener(this);
        track.play();
        if (Gdx.app.getLogLevel() >= Application.LOG_DEBUG) {
          Gdx.app.debug(TAG, "Now playing \"" + asset + "\"");
        }
        return;
      } catch (RuntimeException ex) {
        // Resolver/load failures are non-fatal for music.  Unload any partial
        // entry so a later enqueue can retry cleanly after resources change.
        try {
          ASSETS.unload(asset);
        } catch (RuntimeException ignored) {
          // Keep the original load failure as the diagnostic below.
        }
        this.track = null;
        Gdx.app.error(TAG, "Skipping unavailable music \"" + asset + "\"", ex);
      }
    }
    this.asset = null;
  }


  @Override
  public void onCompletion(@NonNull Music music) {
    next();
  }
}
