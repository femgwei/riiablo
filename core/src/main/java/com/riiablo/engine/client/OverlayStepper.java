package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IntervalIteratingSystem;

import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.client.component.Overlay;

@All(Overlay.class)
public class OverlayStepper extends IntervalIteratingSystem {
  protected ComponentMapper<Overlay> mOverlay;

  public OverlayStepper() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  @Override
  protected void process(int entityId) {
    mOverlay.get(entityId).animation.update(SimulationClock.STEP_SECONDS);
  }
}
