package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Literal source-derived samples, not original executable captures.
 * D2MOO 0c0bbd1: PlrModes.cpp EVENTS_HpRegen; MonsterMode.cpp ApplyStatRegen.
 * Expected outputs below are not calculated with production helpers.
 */
@Tag("monster-parity-audit")
class PoisonRegenOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"512,25600,0,0,60,511,0,false,true,256",
      "512,25600,0,0,60,511,0,false,false,61",
      "128,25600,0,0,60,60,0,false,true,128",
      "128,25600,0,0,59,60,0,false,true,256",
      "2560,25600,30,10,60,80,0,false,true,2580",
      "2560,25600,100,20,0,80,100,true,false,2500",
      "256,25600,100,0,0,300,100,true,false,0",
      "25590,25600,0,0,60,40,0,false,true,25600"})
  void nativeHpRegenBranch(int life, int maximum, int aggregate, int states, int potion,
      int poison, int base, boolean suppressBase, boolean player, int expected) {
    long net = NativePoisonRegenMath.netRate(aggregate, states, potion, poison, base, suppressBase);
    assertEquals(expected, NativePoisonRegenMath.nextLife(life, maximum, net, player));
  }
}
