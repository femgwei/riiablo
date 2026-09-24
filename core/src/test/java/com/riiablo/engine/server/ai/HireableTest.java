package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HireableTest {
  @Test
  void reflectionLoadsDedicatedHireableAiWithoutHostileFallback() {
    AI ai = AI.findAI(7, "Hireable");

    assertTrue(ai instanceof Hireable);
    assertEquals("HIREABLE", ai.getState());
  }
}
