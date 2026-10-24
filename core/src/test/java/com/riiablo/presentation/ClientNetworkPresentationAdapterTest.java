package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.screen.GameScreen;

class ClientNetworkPresentationAdapterTest {
  @Test
  void missingHostResourcesRemainSafeForReplicaConsumers() {
    ClientGameplayContext context = new ClientGameplayContext(
        null, () -> 7, new AtomicReference<GameScreen>()::get, null,
        null, null, null, null, null, null, null, null, null, null);
    ClientNetworkPresentationAdapter adapter = new ClientNetworkPresentationAdapter(context);

    assertEquals(7, adapter.playerId());
    assertNull(adapter.format(3641, "player"));
    assertNull(adapter.files());
    assertNull(adapter.charData());
    assertNull(adapter.cursor());
    assertDoesNotThrow(() -> adapter.presentNpcServiceResult(null));
    assertDoesNotThrow(() -> adapter.presentTradeState((ClientNetworkReceiver) null));
  }
}
