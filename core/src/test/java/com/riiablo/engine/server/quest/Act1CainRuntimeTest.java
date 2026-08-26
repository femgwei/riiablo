package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Act1CainRuntimeTest {
  @Test
  void createsAStableCompleteNativeStonePermutation() {
    Act1CainRuntime first = new Act1CainRuntime();
    Act1CainRuntime second = new Act1CainRuntime();
    first.initialize(0x12345678L);
    second.initialize(0x12345678L);

    assertArrayEquals(first.stoneOrder(), second.stoneOrder());
    int[] sorted = first.stoneOrder();
    Arrays.sort(sorted);
    assertArrayEquals(new int[] {17, 18, 19, 20, 21}, sorted);
  }

  @Test
  void wrongStoneDoesNotAdvanceAndLastStoneWaitsForPortal() {
    Act1CainRuntime runtime = new Act1CainRuntime();
    runtime.initialize(7L);
    int[] order = runtime.stoneOrder();

    int wrong = order[0] == 17 ? 18 : 17;
    assertEquals(Act1CainRuntime.StoneResult.WRONG, runtime.inspect(wrong));
    assertEquals(0, runtime.operated());
    for (int i = 0; i < order.length - 1; i++) {
      assertEquals(Act1CainRuntime.StoneResult.ADVANCED, runtime.inspect(order[i]));
      runtime.advance();
    }
    assertEquals(Act1CainRuntime.StoneResult.LAST_STONE,
        runtime.inspect(order[order.length - 1]));
    assertFalse(runtime.portalOpened());
    runtime.markPortalOpened();
    assertTrue(runtime.portalOpened());
    assertEquals(Act1CainRuntime.StoneResult.COMPLETE,
        runtime.inspect(order[order.length - 1]));
  }
}
