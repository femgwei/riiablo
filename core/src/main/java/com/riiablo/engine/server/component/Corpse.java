package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;

/**
 * Component that marks an entity as a corpse and tracks its lifetime.
 * Corpses will be removed after a certain duration.
 */
@PooledWeaver
public class Corpse extends Component {
  /**
   * Default corpse duration in seconds (similar to original Diablo 2).
   * In D2, corpses typically last around 8-10 seconds before fading.
   */
  public static final float DEFAULT_DURATION = 10.0f;

  /**
   * Time remaining before the corpse is removed (in seconds).
   */
  public float timeRemaining = DEFAULT_DURATION;

  /**
   * Whether this corpse can be used by skills like Corpse Explosion, Raise Skeleton, etc.
   */
  public boolean usable = true;

  /**
   * Whether this corpse is currently fading out (for visual effect).
   */
  public boolean fading = false;

  /**
   * Fade duration in seconds.
   */
  public static final float FADE_DURATION = 1.0f;

  /**
   * Time spent fading (0 to FADE_DURATION).
   */
  public float fadeTime = 0f;

}
