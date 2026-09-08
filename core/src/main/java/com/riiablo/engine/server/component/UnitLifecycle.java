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
    if (next != null) phase = next;
    return this;
  }

  public boolean isDead() {
    return phase == Phase.DEATH || phase == Phase.REMOVED || phase == Phase.DESTROYED;
  }
}
