package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.Transient;
import com.d2moo.common.drlg.D2Seed;

/** Server-only pSeed for units without a Monster/Missile seed (not saved or replicated). */
@Transient
public class UnitRandomSeed extends PooledComponent {
  public D2Seed seed;
  public int low;
  @Override protected void reset() { seed = null; low = 0; }
}
