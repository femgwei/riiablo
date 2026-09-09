package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;

/**
 * Authoritative lifecycle marker for a server unit.  The marker is deliberately
 * data-only; systems advance it at the native boundaries instead of inferring
 * lifecycle from a render mode or a nullable component.
 */
@PooledWeaver
public class UnitLifecycle extends Component {
  public enum Phase { SPAWN, INSERTED, ACTIVE, DEATH, REMOVED, DESTROYED }

  public Phase phase = Phase.SPAWN;
  public int deathKiller = -1;
  public long deathTick = -1L;
  public boolean deathHandled;

  public UnitLifecycle reset() {
    phase = Phase.SPAWN;
    deathKiller = -1;
    deathTick = -1L;
    deathHandled = false;
    return this;
  }

  public UnitLifecycle transition(Phase next) {
    if (next != null && canTransition(phase, next)) phase = next;
    return this;
  }

  /**
   * Returns whether a lifecycle transition is valid at a native boundary.
   * Spawn/insert/active are strictly forward-only; death may be observed from
   * any live phase, and removal/destruction are terminal. Invalid transitions
   * are ignored by {@link #transition(Phase)} so a duplicate or late event
   * cannot resurrect an entity or re-run its initialization path.
   */
  public static boolean canTransition(Phase current, Phase next) {
    if (current == null || next == null || current == next) return true;
    switch (current) {
      case SPAWN:
        return next == Phase.INSERTED || next == Phase.DEATH || next == Phase.REMOVED;
      case INSERTED:
        return next == Phase.ACTIVE || next == Phase.DEATH || next == Phase.REMOVED;
      case ACTIVE:
        return next == Phase.DEATH || next == Phase.REMOVED;
      case DEATH:
        return next == Phase.REMOVED || next == Phase.DESTROYED;
      case REMOVED:
        return next == Phase.DESTROYED;
      case DESTROYED:
      default:
        return false;
    }
  }

  public boolean isDead() {
    return phase == Phase.DEATH || phase == Phase.REMOVED || phase == Phase.DESTROYED;
  }
}
