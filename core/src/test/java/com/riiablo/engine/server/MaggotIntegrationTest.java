package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

/** Headless regression tests for the native MaggotDown/MaggotLay helpers. */
class MaggotIntegrationTest extends RiiabloTest {
  @Test
  void maggotDownHealsTenPercentOfCurrentLifeAndClampsToMax() {
    assertEquals(110f, Actioneer.calculateMaggotHeal(100f, 200f, 10), 0.001f);
    assertEquals(200f, Actioneer.calculateMaggotHeal(195f, 200f, 10), 0.001f);
    assertEquals(0f, Actioneer.calculateMaggotHeal(0f, 200f, 10), 0.001f);
  }

  @Test
  void maggotLayUsesD2DirectionalOffsetIndices() {
    // D2Common_11053/11055 mapping used by SkillMonst.cpp's byte table.
    assertEquals(10, Actioneer.maggotDirectionIndex(0, -10)); // north
    assertEquals(14, Actioneer.maggotDirectionIndex(10, 0));  // east
    assertEquals(18, Actioneer.maggotDirectionIndex(0, 10));  // south
    assertEquals(22, Actioneer.maggotDirectionIndex(-10, 0)); // west
  }
}
