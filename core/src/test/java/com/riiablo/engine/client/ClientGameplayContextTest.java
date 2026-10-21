package com.riiablo.engine.client;

import com.riiablo.presentation.ClientGameplayContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.server.event.NativeActTransitionEvent;
import java.util.concurrent.atomic.AtomicInteger;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ClientGameplayContextTest {
  @Test
  void headlessHarnessRoutesActTransitionThroughExplicitContext() {
    int[] player = {41};
    AtomicInteger act = new AtomicInteger(-1);
    ClientGameplayContext context = new ClientGameplayContext(
        null, () -> player[0], () -> null, act::set,
        null, null, null, null, null, null, null, null, null, null);

    try (ClientSystemHarness harness =
        new ClientSystemHarness(context, new ActTransitionSystem())) {
      NativeActTransitionEvent event = NativeActTransitionEvent.obtain(
          player[0], com.d2moo.common.drlg.D2LevelIds.LEVEL_LUTGHOLEIN);
      harness.world().getSystem(EventSystem.class).dispatch(event);
      assertEquals(1, act.get());
      assertTrue(event.accepted);
      assertEquals(41, context.playerId());
      player[0] = 99;
      assertEquals(99, context.playerId());
    }
  }
}

