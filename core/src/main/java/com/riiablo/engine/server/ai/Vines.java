package com.riiablo.engine.server.ai;

/**
 * Native AI entry point for Druid vine summons (SrvDo115).
 *
 * <p>Vines are rooted in the native data ({@code MonStats.Velocity == 0}).
 * The shared fallback loop already preserves that invariant while still
 * allowing a configured projectile skill to fire, so keep the data-driven
 * class name distinct instead of reporting an {@code AI_FALLBACK} warning.
 * This class is deliberately small until the remaining vine-specific native
 * targeting/attack cadence is ported.</p>
 */
public final class Vines extends GenericMonster {
  public Vines(int entityId) {
    super(entityId, null);
  }
}
