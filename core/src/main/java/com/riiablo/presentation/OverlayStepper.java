package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IntervalIteratingSystem;

import com.riiablo.engine.SimulationClock;
import com.riiablo.presentation.component.Overlay;

@All(Overlay.class)
public class OverlayStepper extends IntervalIteratingSystem {
  protected ComponentMapper<Overlay> mOverlay;

  public OverlayStepper() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  @Override
  protected void process(int entityId) {
    Overlay root = mOverlay.get(entityId);
    if (root.entry != null) root.animation.update(SimulationClock.STEP_SECONDS);
    for (Overlay layer : root.layers) layer.animation.update(SimulationClock.STEP_SECONDS);
  }
}
