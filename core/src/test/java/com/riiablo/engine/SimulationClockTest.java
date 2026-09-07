package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.Aspect;
import com.artemis.systems.IntervalSystem;
import com.riiablo.codec.Animation;
import org.junit.jupiter.api.Test;

class SimulationClockTest {
  @Test
  void matchesNativeDiabloFrameRate() {
    assertEquals(25, SimulationClock.TICKS_PER_SECOND);
    assertEquals(Animation.FRAME_DURATION, SimulationClock.STEP_SECONDS, 0f);
    assertEquals(40, SimulationClock.STEP_MILLIS);
  }

  @Test
  void nativeIntervalRunsOncePerFixedWorldTickWithoutBacklog() {
    CountingIntervalSystem system = new CountingIntervalSystem();
    World world = new World(new WorldConfigurationBuilder().with(system).build());
    try {
      for (int i = 0; i < 250; i++) {
        world.setDelta(SimulationClock.STEP_SECONDS);
        world.process();
      }

      assertEquals(250, system.processed);
      assertEquals(0f, system.remainder(), 0.00001f);
    } finally {
      world.dispose();
    }
  }

  private static final class CountingIntervalSystem extends IntervalSystem {
    int processed;

    CountingIntervalSystem() {
      super(Aspect.all(), SimulationClock.STEP_SECONDS);
    }

    @Override
    protected void processSystem() {
      processed++;
    }

    float remainder() {
      return acc;
    }
  }
}
