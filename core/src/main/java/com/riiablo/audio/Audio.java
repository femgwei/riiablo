package com.riiablo.audio;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.riiablo.Cvars;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Sounds;

import java.util.Arrays;
import java.util.Iterator;

public class Audio {
  private static final String GLOBAL = "data\\global\\sfx\\";
  private static final String LOCAL  = "data\\local\\sfx\\";

  public enum Channel {
    SFX, MUSIC, ENVIRONMENT, SPEECH
  }

  private final AssetManager assets;
  private final ObjectMap<Sounds.Entry, AssetDescriptor<?>> descriptors = new ObjectMap<>();
  private final Array<Instance> deferred = new Array<>();
  private final Array<Instance> active = new Array<>();
  private final float[] channelVolumes = new float[Channel.values().length];

  private float masterVolume = 1f;
  private boolean background;

  public Audio(AssetManager assets) {
    this.assets = assets;
    Arrays.fill(channelVolumes, 1f);
  }

  public float getMasterVolume() {
    return masterVolume;
  }

  public void setMasterVolume(float volume) {
    masterVolume = MathUtils.clamp(volume, 0f, 1f);
    refreshActiveVolumes();
  }

  public float getChannelVolume(Channel channel) {
    if (channel == null) throw new IllegalArgumentException("channel cannot be null");
    return channelVolumes[channel.ordinal()];
  }

  public void setChannelVolume(Channel channel, float volume) {
    if (channel == null) throw new IllegalArgumentException("channel cannot be null");
    channelVolumes[channel.ordinal()] = MathUtils.clamp(volume, 0f, 1f);
    refreshActiveVolumes();
  }

  public boolean isBackgroundPaused() {
    return background;
  }

  public void update() {
    update(0f);
  }

  public void update(float delta) {
    // Fade clocks and deferred starts are both gameplay audio state. Freeze
    // them while unfocused so returning to the game does not skip a fade or
    // emit a sound whose event happened in the background.
    if (background) return;
    for (Iterator<Instance> it = active.iterator(); it.hasNext();) {
      Instance instance = it.next();
      if (instance.stopped) {
        it.remove();
        continue;
      }

      instance.update(Math.max(0f, delta));
      // Music volume controllers can update the shared Music object when a
      // CVar changes. Reapply streamed instance/bus gain every frame so an
      // environmental stream is not accidentally left at the music volume.
      if (instance.stream && instance.playing && !instance.paused) {
        instance.applyVolume();
      }
      if (!instance.persistent && !instance.fading) it.remove();
    }

    for (Iterator<Instance> it = deferred.iterator(); it.hasNext();) {
      Instance instance = it.next();
      if (instance.stopped) {
        it.remove();
        continue;
      }
      if (assets.isLoaded(instance.descriptor)) {
        instance.attach(assets.get(instance.descriptor));
        boolean played = instance.play();
        if (played) it.remove();
      }
    }
  }

  public synchronized void pauseForBackground() {
    if (background) return;
    background = true;

    // A deferred sound has not actually started, so it must not be replayed
    // as a surprise when the window regains focus.
    for (Instance instance : deferred) instance.stop();
    deferred.clear();

    for (Instance instance : active) {
      if (instance.persistent) {
        instance.pauseForBackground();
      } else {
        instance.stop();
      }
    }

    // libGDX Sound has no per-instance playback query or completion callback.
    // Stop each loaded Sound bank to catch unmanaged one-shots. Persistent
    // Sound loops are restarted on resume; Music streams retain their cursor.
    for (AssetDescriptor<?> descriptor : descriptors.values()) {
      if (descriptor.type == Sound.class && assets.isLoaded(descriptor)) {
        ((Sound) assets.get(descriptor)).stop();
      }
    }
  }

  public synchronized void resumeFromBackground() {
    if (!background) return;
    background = false;
    for (Instance instance : active) instance.resumeFromBackground();
  }

  private void refreshActiveVolumes() {
    for (Instance instance : active) instance.applyVolume();
  }

  private void manage(Instance instance) {
    if (!active.contains(instance, true)) active.add(instance);
  }

  private float effectiveVolume(Instance instance) {
    float volume = instance.baseVolume
        * instance.instanceVolume
        * instance.fadeVolume
        * masterVolume
        * channelVolumes[instance.channel.ordinal()];
    if (instance.stream) volume *= streamedUserVolume(instance.channel);
    return MathUtils.clamp(volume, 0f, 1f);
  }

  private static float streamedUserVolume(Channel channel) {
    if (!Boolean.TRUE.equals(Cvars.Client.Sound.Enabled.get())) return 0f;
    if (channel == Channel.MUSIC) {
      if (!Boolean.TRUE.equals(Cvars.Client.Sound.Music.Enabled.get())) return 0f;
      Float volume = Cvars.Client.Sound.Music.Volume.get();
      return volume == null ? 0f : volume;
    } else {
      if (!Boolean.TRUE.equals(Cvars.Client.Sound.Effects.Enabled.get())) return 0f;
      Float volume = Cvars.Client.Sound.Effects.Volume.get();
      return volume == null ? 0f : volume;
    }
  }

  public synchronized Instance play(final Sounds.Entry sound, boolean global) {
    return play(sound, global, Channel.SFX);
  }

  public synchronized Instance play(
      final Sounds.Entry sound, boolean global, Channel channel) {
    if (sound == null || sound.FileName == null || sound.FileName.isEmpty() || background) {
      return null;
    }
    if (channel == null) throw new IllegalArgumentException("channel cannot be null");

    if (sound.Stream) {
      Music stream;
      AssetDescriptor<Music> descriptor = (AssetDescriptor<Music>) descriptors.get(sound);
      if (descriptor == null) {
        descriptor = new AssetDescriptor<>((global ? GLOBAL : LOCAL) + sound.FileName, Music.class);
        descriptors.put(sound, descriptor);
        assets.load(descriptor);
        assets.finishLoadingAsset(descriptor);
      }
      stream = assets.get(descriptor);

      if (sound.Defer_Inst && stream.isPlaying()) return null;

      Instance instance = Instance.obtain(this, sound, descriptor, stream, -1, channel);
      if (!instance.play()) return instance;
      manage(instance);
      return instance;
    } else {
      AssetDescriptor<Sound> descriptor = (AssetDescriptor<Sound>) descriptors.get(sound);
      if (descriptor == null) {
        descriptor = new AssetDescriptor<>((global ? GLOBAL : LOCAL) + sound.FileName, Sound.class);
        descriptors.put(sound, descriptor);
        assets.load(descriptor);
      }

      if (assets.isLoaded(descriptor)) {
        final Sound sfx = assets.get(descriptor);
        Instance instance = Instance.obtain(this, sound, descriptor, sfx, -1, channel);
        if (!instance.play()) deferred.add(instance);
        return instance;
      } else {
        Instance instance = Instance.obtain(this, sound, descriptor, null, -1, channel);
        deferred.add(instance);
        return instance;
      }
    }
  }

  public Instance play(int id, boolean global) {
    return play(id, global, Channel.SFX);
  }

  public Instance play(int id, boolean global, Channel channel) {
    Sounds.Entry sound = Riiablo.files.Sounds.get(id);
    return play(sound, global, channel);
  }

  public Instance play(String id, boolean global) {
    return play(id, global, Channel.SFX);
  }

  public Instance play(String id, boolean global, Channel channel) {
    if (id == null || id.isEmpty()) return null;
    Sounds.Entry sound = Riiablo.files.Sounds.get(id);
    if (sound == null) return null;
    if (sound.Group_Size > 0) {
      int randomId = sound.Index + MathUtils.random.nextInt(sound.Group_Size);
      sound = Riiablo.files.Sounds.get(randomId);
    }

    return play(sound, global, channel);
  }

  public static class Instance implements Pool.Poolable, Music.OnCompletionListener {
    Audio owner;
    AssetDescriptor descriptor;
    boolean stream;
    Object delegate;
    long id;
    Channel channel;
    float baseVolume;
    float instanceVolume;
    float fadeVolume;
    float fadeStart;
    float fadeTarget;
    float fadeDuration;
    float fadeElapsed;
    boolean loop;
    boolean persistent;
    boolean playing;
    boolean paused;
    boolean backgroundPaused;
    boolean restartAfterBackground;
    boolean fading;
    boolean stopAfterFade;
    boolean stopped;

    static Instance obtain(
        Audio owner, Sounds.Entry sound, AssetDescriptor descriptor,
        Object delegate, long id, Channel channel) {
      Instance instance = Pools.obtain(Instance.class);
      instance.owner = owner;
      instance.descriptor = descriptor;
      instance.stream = descriptor != null
          ? descriptor.type == Music.class
          : delegate instanceof Music;
      instance.delegate = delegate;
      instance.id = id;
      instance.channel = channel == null ? Channel.SFX : channel;
      instance.baseVolume = sound == null
          ? 1f : MathUtils.clamp(sound.Volume / 255f, 0f, 1f);
      instance.instanceVolume = 1f;
      instance.fadeVolume = 1f;
      instance.loop = sound != null && sound.Loop;
      instance.persistent = instance.stream || instance.loop;
      instance.stopped = false;
      return instance;
    }

    static Instance obtain(AssetDescriptor descriptor, Object delegate, long id) {
      return obtain(null, null, descriptor, delegate, id, Channel.SFX);
    }

    void attach(Object delegate) {
      this.delegate = delegate;
      this.stream = delegate instanceof Music;
    }

    @Override
    public void reset() {
      owner = null;
      descriptor = null;
      delegate = null;
      id = -1;
      channel = Channel.SFX;
      baseVolume = 1f;
      instanceVolume = 1f;
      fadeVolume = 1f;
      fadeStart = 0f;
      fadeTarget = 0f;
      fadeDuration = 0f;
      fadeElapsed = 0f;
      stream = false;
      loop = false;
      persistent = false;
      playing = false;
      paused = false;
      backgroundPaused = false;
      restartAfterBackground = false;
      fading = false;
      stopAfterFade = false;
      stopped = false;
    }

    public boolean isLoaded() {
      return delegate != null;
    }

    public boolean isPlaying() {
      return playing;
    }

    public boolean isPaused() {
      return paused;
    }

    public Channel getChannel() {
      return channel;
    }

    public boolean play() {
      if (stopped || delegate == null || owner != null && owner.background) return false;
      if (paused) {
        resume();
        return playing;
      }

      if (stream) {
        Music music = (Music) delegate;
        music.setLooping(loop);
        music.setOnCompletionListener(this);
        applyVolume();
        music.play();
        playing = true;
      } else {
        Sound sound = (Sound) delegate;
        float volume = effectiveVolume();
        id = loop ? sound.loop(volume) : sound.play(volume);
        playing = id != -1;
      }
      if (playing && owner != null && persistent) owner.manage(this);
      return playing;
    }

    public void pause() {
      if (stopped || paused || delegate == null || !playing) return;
      if (stream) {
        ((Music) delegate).pause();
      } else if (id != -1) {
        ((Sound) delegate).pause(id);
      }
      playing = false;
      paused = true;
    }

    public void resume() {
      if (stopped || !paused || delegate == null || owner != null && owner.background) return;
      if (restartAfterBackground) {
        restartAfterBackground = false;
        paused = false;
        play();
        return;
      }
      if (stream) {
        ((Music) delegate).play();
        playing = true;
      } else if (id != -1) {
        ((Sound) delegate).resume(id);
        playing = true;
      }
      paused = false;
      applyVolume();
    }

    void pauseForBackground() {
      if (stopped) return;
      if (stream) {
        if (playing) {
          backgroundPaused = true;
          pause();
        }
      } else {
        // The Sound bank is stopped after this pass to silence unmanaged
        // one-shots too. A playing loop is restarted on foreground; an
        // explicitly paused loop stays paused and restarts only on resume().
        if (playing || paused) {
          backgroundPaused = playing;
          playing = false;
          paused = true;
          restartAfterBackground = true;
        }
      }
    }

    void resumeFromBackground() {
      if (stopped || !backgroundPaused) return;
      backgroundPaused = false;
      resume();
    }

    public void stop() {
      stopped = true;
      playing = false;
      paused = false;
      fading = false;
      backgroundPaused = false;
      restartAfterBackground = false;
      if (delegate == null) return;
      if (stream) {
        ((Music) delegate).stop();
      } else if (id != -1) {
        ((Sound) delegate).stop(id);
      }
    }

    /** Sets this instance's relative/spatial gain without losing Sounds.txt volume. */
    public void setVolume(float volume) {
      if (stopped) return;
      instanceVolume = MathUtils.clamp(volume, 0f, 1f);
      applyVolume();
    }

    public void fadeTo(float volume, float seconds) {
      if (stopped) return;
      fadeStart = fadeVolume;
      fadeTarget = MathUtils.clamp(volume, 0f, 1f);
      fadeDuration = Math.max(0f, seconds);
      fadeElapsed = 0f;
      stopAfterFade = false;
      fading = fadeDuration > 0f;
      if (!fading) {
        fadeVolume = fadeTarget;
        applyVolume();
      } else if (owner != null) {
        owner.manage(this);
      }
    }

    public void fadeOutAndStop(float seconds) {
      fadeTo(0f, seconds);
      stopAfterFade = true;
      if (!fading) stop();
    }

    void update(float delta) {
      if (!fading || stopped) return;
      fadeElapsed = Math.min(fadeDuration, fadeElapsed + delta);
      float alpha = fadeDuration == 0f ? 1f : fadeElapsed / fadeDuration;
      fadeVolume = fadeStart + (fadeTarget - fadeStart) * alpha;
      applyVolume();
      if (fadeElapsed >= fadeDuration) {
        fading = false;
        if (stopAfterFade) stop();
      }
    }

    void applyVolume() {
      if (stopped || delegate == null) return;
      float volume = effectiveVolume();
      if (stream) {
        ((Music) delegate).setVolume(volume);
      } else if (id != -1) {
        ((Sound) delegate).setVolume(id, volume);
      }
    }

    private float effectiveVolume() {
      if (owner != null) return owner.effectiveVolume(this);
      return MathUtils.clamp(baseVolume * instanceVolume * fadeVolume, 0f, 1f);
    }

    @Override
    public void onCompletion(Music music) {
      if (music != delegate || loop) return;
      playing = false;
      stopped = true;
    }
  }
}
