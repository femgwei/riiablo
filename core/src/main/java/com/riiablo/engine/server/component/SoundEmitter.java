package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.math.Interpolation;
import com.riiablo.audio.Audio;

@Transient
@PooledWeaver
public class SoundEmitter extends PooledComponent {
  public static final float DEFAULT_RADIUS = 20f;

  public Audio.Instance sound;
  public Interpolation interpolator = Interpolation.linear;
  public float radius = DEFAULT_RADIUS;
  public boolean sameZoneOnly;

  @Override
  protected void reset() {
    if (sound != null) sound.stop();
    sound = null;
    interpolator = Interpolation.linear;
    radius = DEFAULT_RADIUS;
    sameZoneOnly = false;
  }

  public SoundEmitter set(Audio.Instance sound, Interpolation interpolator) {
    return set(sound, interpolator, DEFAULT_RADIUS, false);
  }

  public SoundEmitter set(Audio.Instance sound, Interpolation interpolator,
      float radius, boolean sameZoneOnly) {
    this.sound = sound;
    this.interpolator = interpolator;
    this.radius = Math.max(0f, radius);
    this.sameZoneOnly = sameZoneOnly;
    return this;
  }
}
