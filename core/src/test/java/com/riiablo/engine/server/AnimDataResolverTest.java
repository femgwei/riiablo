package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnimDataResolverTest {
  @Test
  void usesHitReactionFallbackForMissingBlockMode() {
    assertArrayEquals(new String[] {"GH", "A1"}, AnimDataResolver.fallbackModes("BL"));
  }

  @Test
  void usesAttackFallbackForMissingSpecialMode() {
    assertArrayEquals(new String[] {"A1", "S1"}, AnimDataResolver.fallbackModes("XX"));
  }

  @Test
  void ordinaryModesDoNotInventAnimationFallbacks() {
    assertEquals(0, AnimDataResolver.fallbackModes("DT").length);
  }
}
