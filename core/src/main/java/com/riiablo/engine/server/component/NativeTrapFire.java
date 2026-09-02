package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/** Runtime lifetime and tick state for a native D2Game fire object (5/7 trap). */
@Transient
@PooledWeaver
public class NativeTrapFire extends Component {
  public static final float DEFAULT_DURATION = 2.0f;
  public static final int MIN_TICK_FRAMES = 15;
  public static final int TICK_FRAME_RANGE = 35;

  public float remaining;
  public float untilDamageTick;
  public float radius;
  /** Objects.txt Damage percentage used by OBJEVAL_ApplyTrapObjectDamage. */
  public int damagePercent;
  private int seedLow;
  private int seedHigh;

  public NativeTrapFire reset(float duration, float radius, int damagePercent, int seed) {
    remaining = Math.max(0f, duration);
    untilDamageTick = 0f;
    this.radius = Math.max(0f, radius);
    this.damagePercent = Math.max(0, damagePercent);
    seedLow = seed == 0 ? 1 : seed;
    seedHigh = 666;
    return this;
  }

  /** D2Common seed roll used by ITEMS_RollLimitedRandomNumber. */
  public int nextInt(int bound) {
    if (bound <= 1) return 0;
    long next = Integer.toUnsignedLong(seedHigh)
        + 0x6AC690C5L * Integer.toUnsignedLong(seedLow);
    seedLow = (int) next;
    seedHigh = (int) (next >>> 32);
    return (int) (Integer.toUnsignedLong(seedLow) % bound);
  }

  protected void reset() {
    remaining = 0f;
    untilDamageTick = 0f;
    radius = 0f;
    damagePercent = 0;
    seedLow = 0;
    seedHigh = 0;
  }
}
