package com.riiablo.engine.server.ai;

/**
 * Native AI slot for Death Sentry.
 *
 * <p>Corpse selection/explosion and the ordinary lightning fallback are
 * driven by {@code AssassinTrapSystem}; this concrete class only prevents the
 * missing-class GenericMonster fallback from competing with that system.</p>
 */
public final class DeathSentry extends AssassinSentry {
  public DeathSentry(int entityId) {
    super(entityId);
  }
}
