package com.riiablo.engine.server.combat;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class NativeResourceMathTest {
  @Test void preservesBothHistoricalRoundingOrders() {
    Random random = new Random(110);
    for (int i = 0; i < 10000; i++) {
      float current = random.nextFloat() * 1000000f;
      float maximum = random.nextFloat() * 1000000f;
      float requested = random.nextFloat() * 1000f;
      assertEquals(Math.max(0f, Math.min(requested, maximum - current)),
          NativeResourceMath.cappedRestore(current, maximum, requested));
      float after = Math.min(maximum, current + requested);
      assertEquals(after <= current ? 0f : after - current,
          NativeResourceMath.restoreAfterAddition(current, maximum, requested));
    }
    assertEquals(0.01f, NativeResourceMath.cappedRestore(1000000f, 2000000f, 0.01f));
    assertEquals(0f, NativeResourceMath.restoreAfterAddition(1000000f, 2000000f, 0.01f));
  }
}
