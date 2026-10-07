package com.riiablo.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnvironmentCycleTest {
  @Test
  public void startsAtNativeNoonState() {
    EnvironmentCycle environment = new EnvironmentCycle();
    assertEquals(EnvironmentCycle.CYCLE_NOON, environment.cycleIndex());
    assertEquals(EnvironmentCycle.PERIOD_DAY, environment.periodOfDay());
    assertEquals(0, environment.ticks());
    assertEquals(128, environment.intensity(1, 0));
    assertTrue(environment.isDay());
  }

  @Test
  public void entersDuskAndNightAtNativeBoundaries() {
    EnvironmentCycle environment = new EnvironmentCycle();
    environment.initialize(EnvironmentCycle.CYCLE_NOON,
        160 * EnvironmentCycle.NORMAL_TIME_RATE, false);
    assertTrue(environment.advance(0));
    assertEquals(EnvironmentCycle.CYCLE_AFTERNOON, environment.cycleIndex());
    assertEquals(EnvironmentCycle.PERIOD_DUSK, environment.periodOfDay());

    environment.initialize(EnvironmentCycle.CYCLE_SUNSET,
        200 * EnvironmentCycle.NORMAL_TIME_RATE, false);
    assertTrue(environment.advance(0));
    assertEquals(EnvironmentCycle.CYCLE_NIGHT, environment.cycleIndex());
    assertEquals(EnvironmentCycle.PERIOD_NIGHT, environment.periodOfDay());
    assertFalse(environment.isDay());
  }

  @Test
  public void nightAdvancesTwiceAsFastInActOne() {
    EnvironmentCycle environment = new EnvironmentCycle();
    environment.initialize(EnvironmentCycle.CYCLE_NIGHT,
        200 * EnvironmentCycle.NORMAL_TIME_RATE, false);
    int before = environment.ticks();
    environment.advance(0);
    assertEquals(before + 2, environment.ticks());
  }

  @Test
  public void synchronizedStateRestoresDerivedLight() {
    EnvironmentCycle environment = new EnvironmentCycle();
    environment.initialize(EnvironmentCycle.CYCLE_NIGHT,
        270 * EnvironmentCycle.NORMAL_TIME_RATE, false);
    assertEquals(64, environment.intensity(1, 0));
    assertEquals(125, environment.red(1, 0));
    assertEquals(144, environment.green(1, 0));
    assertEquals(243, environment.blue(1, 0));
  }

  @Test
  public void verificationPresetCanJumpDirectlyToNight() {
    EnvironmentCycle environment = new EnvironmentCycle();
    environment.setPeriodOfDay(EnvironmentCycle.PERIOD_NIGHT);
    assertEquals(EnvironmentCycle.CYCLE_NIGHT, environment.cycleIndex());
    assertEquals(EnvironmentCycle.PERIOD_NIGHT, environment.periodOfDay());
    assertEquals(270 * EnvironmentCycle.NORMAL_TIME_RATE, environment.ticks());
    assertEquals("night", EnvironmentCycle.periodName(environment.periodOfDay()));
  }
}
