package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AuthoritativeSimulationTest {
  @Test
  void appliesInputThenOneFixedNativeFrameThenOutput() {
    List<String> phases = new ArrayList<>();
    RecordingSystem system = new RecordingSystem(phases);
    World world = new World(new WorldConfigurationBuilder().with(system).build());
    try {
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      world.setDelta(99f);

      simulation.tick(() -> phases.add("input"), () -> phases.add("output"));

      assertEquals(Arrays.asList("input", "simulation", "output"), phases);
      assertEquals(AuthoritativeSimulation.STEP_SECONDS, system.delta, 0f);
      assertEquals(AuthoritativeSimulation.STEP_SECONDS, simulation.lastStepSeconds(), 0f);
      assertEquals(1, simulation.tickNumber());
      assertSame(Thread.currentThread(), simulation.ownerThread());
    } finally {
      world.dispose();
    }
  }

  @Test
  void rejectsASecondWriterThread() throws InterruptedException {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      simulation.tick(null, null);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread second = new Thread(() -> {
        try {
          simulation.tick(null, null);
        } catch (Throwable t) {
          failure.set(t);
        }
      }, "unauthorized-sim-writer");
      second.start();
      second.join();

      assertTrue(failure.get() instanceof IllegalStateException);
      assertEquals(1, simulation.tickNumber());
    } finally {
      world.dispose();
    }
  }

  private static final class RecordingSystem extends BaseSystem {
    private final List<String> phases;
    float delta;

    RecordingSystem(List<String> phases) {
      this.phases = phases;
    }

    @Override
    protected void processSystem() {
      delta = world.getDelta();
      phases.add("simulation");
    }
  }
}
