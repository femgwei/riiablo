package com.riiablo.engine.server.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeAiCommandTest {
  @Test
  void attackCommandRetainsNativeOwnerAndCurrentTarget() {
    NativeAiCommand command = new NativeAiCommand().set(
        10, NativeAiCommand.ATTACK, 20);

    assertTrue(command.isAttack());
    assertEquals(10, command.ownerId);
    assertEquals(20, command.targetId);
  }
}
