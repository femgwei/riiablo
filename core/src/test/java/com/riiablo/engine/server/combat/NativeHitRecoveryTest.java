package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class NativeHitRecoveryTest {
  @Test void fractionsUseHitClassWithoutDiscardingCriticalLayers() {
    for (int type : new int[] {2,6,10,11}) assertEquals(8, NativeHitRecovery.divisor(type));
    assertEquals(64, NativeHitRecovery.divisor(5));
    assertEquals(32, NativeHitRecovery.divisor(4));
    assertEquals(32, NativeHitRecovery.divisor(8));
    assertEquals(16, NativeHitRecovery.divisor(3));
    assertEquals(16, NativeHitRecovery.divisor(2 | 0x10));
  }

  @Test void smallFrozenAndPurePoisonPacketsNeverConsumeRecoveryRandomness() {
    IntUnaryOperator unused = mask -> { fail("no draw before a deterministic rejection"); return 0; };
    assertTrue(NativeHitRecovery.shouldSkip(255,0,2304,3,false,true,unused));
    assertTrue(NativeHitRecovery.shouldSkip(500,0,2560000,3,false,true,unused));
    assertTrue(NativeHitRecovery.shouldSkip(1000,0,2304,3,true,true,unused));
    assertTrue(NativeHitRecovery.shouldSkip(1000,1000,2304,3,false,true,unused));
    assertFalse(NativeHitRecovery.shouldSkip(874,0,2304,3,false,true,unused));
  }

  @Test void bothRandomGatesUseNativeMasksInOrderAndStrictThresholds() {
    List<Integer> calls = new ArrayList<>();
    assertFalse(NativeHitRecovery.shouldSkip(256,0,4096,3,false,true,
        mask -> { calls.add(mask); return mask; }));
    assertEquals(java.util.Arrays.asList(1,3), calls);
    assertTrue(NativeHitRecovery.shouldSkip(256,0,4096,3,false,true, mask -> 0));
    assertTrue(NativeHitRecovery.shouldSkip(512,0,4096,3,false,true,
        mask -> { assertEquals(3,mask); return 0; }));
    assertFalse(NativeHitRecovery.shouldSkip(1024,0,4096,3,false,true,
        mask -> { fail("upper equality must draw nothing"); return 0; }));
    assertTrue(NativeHitRecovery.shouldSkip(1024,0,4096,3,false,false, mask -> 0));
  }
}
