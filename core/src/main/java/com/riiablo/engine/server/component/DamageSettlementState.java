package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.Transient;

/** Server-local death publication guard, reset when a living unit takes a new hit. */
@Transient
public final class DamageSettlementState extends PooledComponent {
  public boolean deathPublished;

  @Override protected void reset() { deathPublished = false; }
}
