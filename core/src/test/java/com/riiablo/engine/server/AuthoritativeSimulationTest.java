package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.engine.server.component.UnitLifecycle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AuthoritativeSimulationTest {
  @Test
  void longRunKeepsExactFixedClockWithoutDrift() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      final int frames = 10_000;
      long previousTime = 0L;
      for (int i = 0; i < frames; i++) {
        simulation.tick(null, null);
        long currentTime = simulation.serverTimeMillis();
        assertEquals(previousTime == 0L ? currentTime : previousTime
            + com.riiablo.engine.SimulationClock.STEP_MILLIS, currentTime);
        assertEquals(AuthoritativeSimulation.STEP_SECONDS,
            simulation.lastStepSeconds(), 0f);
        previousTime = currentTime;
      }
      assertEquals(frames, simulation.tickNumber());
    } finally {
      world.dispose();
    }
  }

  @Test
  void highEntityPressurePreservesTickProgression() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      final int entities = 2_000;
      for (int i = 0; i < entities; i++) {
        int id = world.create();
        world.getMapper(UnitLifecycle.class).create(id).reset();
      }
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      for (int i = 0; i < 500; i++) simulation.tick(null, null);
      assertEquals(500, simulation.tickNumber());
      assertEquals(AuthoritativeSimulation.STEP_SECONDS,
          simulation.lastStepSeconds(), 0f);
      assertEquals(entities, world.getAspectSubscriptionManager()
          .get(com.artemis.Aspect.all(UnitLifecycle.class)).getEntities().size());
    } finally {
      world.dispose();
    }
  }

  @Test
  void renderDeltaUsesBoundedFixedStepAccumulator() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      assertEquals(0, simulation.advance(0.01f, null, null));
      assertEquals(1, simulation.advance(0.03f, null, null));
      assertEquals(1, simulation.tickNumber());
      assertEquals(8, simulation.advance(2f, null, null));
      assertEquals(9, simulation.tickNumber());
      assertEquals(0f, simulation.accumulatedSeconds(), 0.00001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void appliesInputThenOneFixedNativeFrameThenOutput() {
    List<String> phases = new ArrayList<>();
    RecordingSystem system = new RecordingSystem(phases);
    World world = new World(new WorldConfigurationBuilder().with(system).build());
    try {
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      world.setDelta(99f);
      long[] observedTime = new long[1];

      simulation.tick(() -> {
        assertSame(simulation, AuthoritativeSimulation.current());
        phases.add("input");
      }, () -> {
        observedTime[0] = simulation.serverTimeMillis();
        phases.add("output");
      });

      assertEquals(Arrays.asList("input", "simulation", "output"), phases);
      assertEquals(AuthoritativeSimulation.STEP_SECONDS, system.delta, 0f);
      assertEquals(AuthoritativeSimulation.STEP_SECONDS, simulation.lastStepSeconds(), 0f);
      assertEquals(1, simulation.tickNumber());
      assertTrue(observedTime[0] > 0L);
      assertNull(AuthoritativeSimulation.current());
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

  @Test
  void restoresClockOnlyAfterCheckpointAdapterHasAppliedState() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      simulation.restoreFromCheckpoint(17L);
      assertEquals(17L, simulation.tickNumber());
      assertEquals(17L, simulation.tickCoordinator().tick());
      assertSame(Thread.currentThread(), simulation.ownerThread());
      simulation.tick(null, null);
      assertEquals(18L, simulation.tickNumber());
    } finally {
      world.dispose();
    }
  }

  @Test
  void replayRestoreExplicitlyAllowsRewindButDefaultRemainsMonotonic() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);
      simulation.tick(null, null);
      simulation.tick(null, null);
      simulation.tick(null, null);

      assertThrows(IllegalStateException.class,
          () -> simulation.restoreFromCheckpoint(1L));

      simulation.restoreFromCheckpoint(1L, true);
      assertEquals(1L, simulation.tickNumber());
      assertEquals(1L, simulation.tickCoordinator().tick());
      simulation.tick(null, null);
      assertEquals(2L, simulation.tickNumber());
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
