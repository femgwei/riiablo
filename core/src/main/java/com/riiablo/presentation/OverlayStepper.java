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
    if (root.entry != null) step(root);
    for (Overlay layer : root.layers) step(layer);
  }

  private void step(Overlay overlay) {
    if (NativeArcticCastOverlay.handles(overlay)) return; // OverlayManager owns its independent clock.
    if (NativeBoneArmorPresentation.handles(overlay)) return;
    if (overlay.entry != null && "enchant".equalsIgnoreCase(overlay.entry.overlay)) {
      overlay.nativeFrame++;
      int frames = overlay.animation.getNumFramesPerDir();
      if (overlay.isLoaded && frames > 0) {
        int delta = Math.max(0, overlay.entry.AnimRate) << 4;
        overlay.animation.setFrameDelta(delta);
        overlay.animation.setFrame((int) Math.min(frames - 1, (long) overlay.nativeFrame * delta >> 8));
      }
    } else overlay.animation.update(SimulationClock.STEP_SECONDS);
  }
}
