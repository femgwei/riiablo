package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class NativeOpenWoundsTest {
  @ParameterizedTest
  @CsvSource({"-10,40","0,40","1,40","2,49","15,166","16,184",
      "30,436","31,463","40,706","45,841","46,877","60,1381","61,1426","99,3136"})
  void bandEndpointsAreRawHpRegenNotWholeHp(int level,int rate) {
    assertEquals(rate,NativeOpenWounds.rate(level,false,false,false));
    assertEquals(rate/2,NativeOpenWounds.rate(level,false,true,true));
    assertEquals(rate/4,NativeOpenWounds.rate(level,true,false,false));
    assertEquals(rate/4/2,NativeOpenWounds.rate(level,true,true,true));
  }
  @Test void malformedHugeLevelsSaturateOnlyAfterNativeTargetReductions() {
    assertEquals(Integer.MAX_VALUE,NativeOpenWounds.rate(Integer.MAX_VALUE,false,false,false));
    assertEquals(Integer.MAX_VALUE,NativeOpenWounds.rate(Integer.MAX_VALUE,true,true,false));
    assertEquals(200,NativeOpenWounds.DURATION);
  }
}
