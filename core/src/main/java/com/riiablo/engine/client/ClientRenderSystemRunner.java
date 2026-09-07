package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.riiablo.profiler.GpuSystem;
import com.riiablo.profiler.ProfilerManager;
import com.riiablo.profiler.SystemProfiler;

/**
 * Runs GPU-only Artemis systems once per visible frame instead of once per
 * simulation tick.
 *
 * <p>The systems are discovered through {@link GpuSystem}; their previous
 * enabled state is preserved so disabled debug renderers remain disabled.
 */
public final class ClientRenderSystemRunner {
  private final World world;
  private final ProfilerManager profilerManager;
  private final Array<BaseSystem> renderSystems = new Array<>();
  private boolean[] enabledBeforeSimulation;
  private boolean simulationActive;

  public ClientRenderSystemRunner(World world) {
    if (world == null) throw new NullPointerException("world");
    this.world = world;
    profilerManager = world.getSystem(ProfilerManager.class);
    for (BaseSystem system : world.getSystems()) {
      if (system.getClass().isAnnotationPresent(GpuSystem.class)) {
        renderSystems.add(system);
      }
    }
    enabledBeforeSimulation = new boolean[renderSystems.size];
  }

  /** Temporarily removes GPU systems from {@link World#process()}. */
  public void beginSimulation() {
    if (simulationActive) throw new IllegalStateException("simulation already active");
    simulationActive = true;
    for (int i = 0; i < renderSystems.size; i++) {
      BaseSystem system = renderSystems.get(i);
      enabledBeforeSimulation[i] = system.isEnabled();
      system.setEnabled(false);
    }
  }

  /** Restores every renderer to the state it had before simulation. */
  public void endSimulation() {
    if (!simulationActive) return;
    for (int i = 0; i < renderSystems.size; i++) {
      renderSystems.get(i).setEnabled(enabledBeforeSimulation[i]);
    }
    simulationActive = false;
  }

  /** Draws enabled GPU systems once, in their configured Artemis order. */
  public int render(float renderDelta) {
    if (simulationActive) {
      throw new IllegalStateException("cannot render during simulation");
    }
    world.setDelta(renderDelta);
    int rendered = 0;
    for (int i = 0; i < renderSystems.size; i++) {
      BaseSystem system = renderSystems.get(i);
      if (!system.isEnabled()) continue;
      SystemProfiler profiler = profilerManager == null ? null : profilerManager.getFor(system);
      if (profiler != null) profiler.start();
      try {
        system.process();
      } finally {
        if (profiler != null) profiler.stop();
      }
      rendered++;
    }
    return rendered;
  }

  public int size() {
    return renderSystems.size;
  }
}
