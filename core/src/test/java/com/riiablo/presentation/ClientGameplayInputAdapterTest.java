package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.riiablo.screen.GameScreen;

class ClientGameplayInputAdapterTest {
  @Test
  void missingCharacterDataAndFilesRemainSafeForInputConsumers() {
    ClientGameplayContext context = new ClientGameplayContext(
        null, () -> 1, new AtomicReference<GameScreen>()::get, null,
        null, null, null, null, null, null, null, null, null, null);
    ClientGameplayInputAdapter adapter = new ClientGameplayInputAdapter(context);

    assertFalse(adapter.movementBlocked());
    assertEquals(-1, adapter.action(0));
    assertNull(adapter.skill(1));
    assertNull(adapter.equippedThrowableWeapon());
    assertNull(adapter.equippedRangedWeapon());
  }
}
