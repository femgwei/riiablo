package com.riiablo.engine.server.combat;

import com.artemis.BaseSystem;
import com.artemis.annotations.Wire;

/** Captures one local authoritative position frame before input/combat systems run. */
@Wire(failOnNull = false)
public final class CombatPositionCaptureSystem extends BaseSystem {
  @Wire(name = "combatPositionHistory")
  CombatPositionHistory history;
  private long tick;

  public CombatPositionHistory checkpointHistory() { return history; }
  public long checkpointTick() { return tick; }
  public void restoreCheckpointTick(long value) {
    if (value < 0) throw new IllegalArgumentException("negative position capture tick");
    tick = value;
  }

  @Override
  protected void processSystem() {
    if (tick == Long.MAX_VALUE) throw new IllegalStateException("position capture tick exhausted");
    history.capture(world, ++tick);
  }
}
