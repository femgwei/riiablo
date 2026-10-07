package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

class ObjectTorchOperationTest {
  @Test
  void ordinaryTorchMatchesD2MooModeBranches() {
    assertEquals(Engine.Object.MODE_OP,
        ObjectInteractor.torchModeAfterOperation(11, Engine.Object.MODE_NU));
    assertEquals(Engine.Object.MODE_NU,
        ObjectInteractor.torchModeAfterOperation(11, Engine.Object.MODE_OP));
    assertEquals(Engine.Object.MODE_NU,
        ObjectInteractor.torchModeAfterOperation(11, Engine.Object.MODE_ON));
    assertEquals(Engine.Object.MODE_S1,
        ObjectInteractor.torchModeAfterOperation(11, Engine.Object.MODE_S1));
  }

  @Test
  void tikiTorchMatchesD2MooModeBranches() {
    assertEquals(Engine.Object.MODE_OP,
        ObjectInteractor.torchModeAfterOperation(13, Engine.Object.MODE_NU));
    assertEquals(Engine.Object.MODE_NU,
        ObjectInteractor.torchModeAfterOperation(13, Engine.Object.MODE_OP));
    assertEquals(Engine.Object.MODE_ON,
        ObjectInteractor.torchModeAfterOperation(13, Engine.Object.MODE_ON));
  }
}
