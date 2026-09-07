package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AIWarCryControlTest {
  @Test
  void idleSentinelNeverQueriesComponentsWithInvalidEntityId() {
    assertFalse(AI.IDLE.updateWarCryControl(0.04f));
  }
}
