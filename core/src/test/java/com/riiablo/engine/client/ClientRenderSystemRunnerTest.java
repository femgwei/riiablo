package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.SimulationClock;
import com.riiablo.profiler.GpuSystem;
import org.junit.jupiter.api.Test;

class ClientRenderSystemRunnerTest {
  @Test
  void simulationAndRenderingUseIndependentCadence() {
    SimulationSystem simulation = new SimulationSystem();
    RenderingSystem rendering = new RenderingSystem();
    World world = new World(new WorldConfigurationBuilder()
        .with(simulation, rendering)
        .build());
    try {
      ClientRenderSystemRunner runner = new ClientRenderSystemRunner(world);
      assertEquals(1, runner.size());

      runner.beginSimulation();
      world.setDelta(SimulationClock.STEP_SECONDS);
      world.process();
      runner.endSimulation();

      assertEquals(1, simulation.processed);
      assertEquals(0, rendering.processed);
      assertTrue(rendering.isEnabled());

      assertEquals(1, runner.render(1f / 60f));
      assertEquals(1, rendering.processed);
      assertEquals(1f / 60f, rendering.lastDelta, 0f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void preservesDisabledDebugRenderer() {
    RenderingSystem rendering = new RenderingSystem();
    World world = new World(new WorldConfigurationBuilder().with(rendering).build());
    try {
      rendering.setEnabled(false);
      ClientRenderSystemRunner runner = new ClientRenderSystemRunner(world);
      runner.beginSimulation();
      assertFalse(rendering.isEnabled());
      runner.endSimulation();
      assertFalse(rendering.isEnabled());
      assertEquals(0, runner.render(1f / 60f));
    } finally {
      world.dispose();
    }
  }

  @Test
  void rejectsRenderInsideSimulationBoundary() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new RenderingSystem())
        .build());
    try {
      ClientRenderSystemRunner runner = new ClientRenderSystemRunner(world);
      runner.beginSimulation();
      assertThrows(IllegalStateException.class, () -> runner.render(0.016f));
      runner.endSimulation();
    } finally {
      world.dispose();
    }
  }

  private static final class SimulationSystem extends BaseSystem {
    int processed;

    @Override
    protected void processSystem() {
      processed++;
    }
  }

  @GpuSystem
  private static final class RenderingSystem extends BaseSystem {
    int processed;
    float lastDelta;

    @Override
    protected void processSystem() {
      processed++;
      lastDelta = world.getDelta();
    }
  }
}
