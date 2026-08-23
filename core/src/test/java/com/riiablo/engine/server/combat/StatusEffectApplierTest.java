package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.state.StateId;

public class StatusEffectApplierTest {
  @Test
  public void poisonIsForwardedToTheRuntimeStateSink() {
    int[] values = new int[7];
    StatusEffectApplier.StateSink sink = (target, state, duration, level, source, damage, type) -> {
      values[0] = target;
      values[1] = state;
      values[2] = duration;
      values[3] = level;
      values[4] = source;
      values[5] = damage;
      values[6] = type;
    };

    StatusEffectApplier.INSTANCE.setStateSink(sink);
    try {
      StatusEffectApplier.INSTANCE.applyPoison(12, 7, 30, 4);
    } finally {
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }

    assertEquals(12, values[0]);
    assertEquals(StateId.POISON, values[1]);
    assertEquals(30, values[2]);
    assertEquals(1, values[3]);
    assertEquals(4, values[4]);
    assertEquals(7, values[5]);
    assertEquals(4, values[6]);
  }
}
