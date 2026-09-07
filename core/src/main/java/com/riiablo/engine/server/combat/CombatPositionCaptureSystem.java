package com.riiablo.engine.server.combat;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;

/** Captures one local authoritative position frame before input/combat systems run. */
@Wire(failOnNull = false)
public final class CombatPositionCaptureSystem extends BaseSystem {
  @Wire(name = "combatPositionHistory")
  CombatPositionHistory history;
  private long tick;

  @Override
  protected void processSystem() {
    history.capture(world, ++tick);
  }
}
