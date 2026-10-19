package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NativePoisonRegenMathTest {
  @ParameterizedTest
  @CsvSource({"512,25600,-511,true,256", "512,25600,-511,false,1",
      "128,25600,-1,true,256", "128,25600,-1,false,127",
      "128,25600,0,true,128", "30000,25600,0,true,30000",
      "25599,25600,1,true,25600", "25599,25600,2,true,25600",
      "256,25600,-512,false,0", "0,25600,512,true,0",
      "1024,25600,-1,true,1023", "256,128,-1,true,256"})
  void encodedLifeBoundaries(int life, int maximum, long net, boolean player, int expected) {
    assertEquals(expected, NativePoisonRegenMath.nextLife(life, maximum, net, player));
  }

  @Test
  void combinedRateDoesNotRescaleRawHpRegenAndSuppressesOnlyBase() {
    assertEquals(45, NativePoisonRegenMath.netRate(100, 5, 60, 120, 90, false));
    assertEquals(-45, NativePoisonRegenMath.netRate(100, 5, 60, 120, 90, true));
    assertEquals(4294967294L, NativePoisonRegenMath.netRate(
        Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 0, false));
    assertEquals(Integer.MAX_VALUE, NativePoisonRegenMath.nextLife(
        256, Integer.MAX_VALUE, 4294967294L, true));
  }

  @Test
  void potionRemovalUsesStrictCombinedOvershootNotEqualityOrZeroRate() {
    assertFalse(NativePoisonRegenMath.exceedsMaximum(25599, 25600, 1));
    assertTrue(NativePoisonRegenMath.exceedsMaximum(25599, 25600, 2));
    assertFalse(NativePoisonRegenMath.exceedsMaximum(30000, 25600, 0));
    assertFalse(NativePoisonRegenMath.exceedsMaximum(0, 25600, 30000));
  }
}
