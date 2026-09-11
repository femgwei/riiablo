package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class PointerClickQueueTest {
  @Test
  void preservesCapturedCoordinatesAndTickAndConsumesOnce() {
    PointerClickQueue queue = new PointerClickQueue();
    queue.capture(120f, 80f, 1000L, 42L);
    queue.capture(300f, 200f, 1001L, 43L);

    assertTrue(queue.hasPending());
    PointerClickQueue.Click click = queue.poll();
    assertEquals(120f, click.screenX);
    assertEquals(80f, click.screenY);
    assertEquals(1000L, click.capturedAtMillis);
    assertEquals(42L, click.observedTick);
    assertFalse(queue.hasPending());
    assertNull(queue.poll());
  }

  @Test
  void restoresUnconsumedClickWithoutChangingCoordinates() {
    PointerClickQueue queue = new PointerClickQueue();
    queue.capture(12f, 34f, 56L, 78L);
    PointerClickQueue.Click click = queue.poll();
    queue.restore(click);

    PointerClickQueue.Click restored = queue.poll();
    assertNotNull(restored);
    assertEquals(12f, restored.screenX);
    assertEquals(34f, restored.screenY);
    assertEquals(56L, restored.capturedAtMillis);
    assertEquals(78L, restored.observedTick);
  }
}
