package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.artemis.annotations.Wire;
import org.junit.jupiter.api.Test;

class Act1QuestDialogControllerTest {
  @Test
  void permitsMissingNetworkSynchronizerInSinglePlayerWorld() {
    Wire wire = Act1QuestDialogController.class.getAnnotation(Wire.class);
    assertNotNull(wire);
    assertFalse(wire.failOnNull());
  }
}
