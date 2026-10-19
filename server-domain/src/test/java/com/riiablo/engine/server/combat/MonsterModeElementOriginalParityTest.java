package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Literal source-derived samples, cross-checked against 1.10f DLL instructions;
 * NOT captures of a running original process. Native type and host channel differ.
 */
@Tag("monster-parity-audit")
class MonsterModeElementOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"1,15,31,17,8,0,1,15,32,0,0",
      "5,15,31,17,8,0,4,150,320,0,36",
      "10,10,20,0,128,2,5,20,40,0,0",
      "10,10,20,0,128,3,3,20,40,25,0",
      "10,10,20,0,128,4,4,200,400,0,50",
      "4,15,31,17,56,0,3,21,44,24,0",
      "3,20,10,0,0,0,5,20,10,0,0",
      "5,1,2,1,8,0,4,10,20,0,2"})
  void modeStatSamples(int nativeType, int min, int max, int length, int multiplier,
      int roll, int channel, int expectedMin, int expectedMax, int cold, int poison) {
    NativeMonsterModeElements p = new NativeMonsterModeElements();
    p.set(nativeType, min, max, length, multiplier, n -> roll);
    assertEquals(expectedMin, p.minimum[channel]); assertEquals(expectedMax, p.maximum[channel]);
    assertEquals(cold, p.coldLength); assertEquals(poison, p.poisonLength);
  }
}
