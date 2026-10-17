package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;

/**
 * Component that marks an entity as a corpse and tracks its presentation state.
 */
@PooledWeaver
public class Corpse extends Component {
  private static long nextInsertionOrder;

  /**
   * Native monster corpses remain server units while their RoomEx is active;
   * D2MOO does not apply the old port's fixed ten-second removal timer.
   */
  public static final float DEFAULT_DURATION = Float.POSITIVE_INFINITY;

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

  /**
   * Monotonic approximation of D2UnitStrc room-list insertion order. Native
   * room lists insert a newly created unit at pUnitFirst, so corpse searches
   * observe newer corpses before older corpses in the same RoomEx.
   */
  public long insertionOrder;

  /** Fully initializes a pooled corpse marker for a new lifecycle. */
  public Corpse reset(float duration, boolean usable) {
    timeRemaining = duration;
    this.usable = usable;
    fading = false;
    fadeTime = 0f;
    insertionOrder = ++nextInsertionOrder;
    return this;
  }

}
