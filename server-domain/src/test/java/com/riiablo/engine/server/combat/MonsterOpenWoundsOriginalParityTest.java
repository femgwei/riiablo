package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SkillItem.cpp 0x6FD04C40/0x6FD04CF0, literal hand-derived, NOT executable captures. */
@Tag("monster-parity-audit")
class MonsterOpenWoundsOriginalParityTest {
  @ParameterizedTest
  @CsvSource({"1,false,false,false,40","15,false,false,false,166",
      "16,false,false,false,184","30,false,false,false,436",
      "31,false,false,false,463","45,false,false,false,841",
      "46,false,false,false,877","60,false,false,false,1381",
      "61,false,false,false,1426","99,false,false,false,3136",
      "99,true,false,false,784","99,true,true,false,392",
      "99,false,true,false,3136","99,false,false,true,1568",
      "15,true,true,true,20"})
  void independentOriginalRate(int level,boolean player,boolean ranged,boolean elite,int rate) {
    assertEquals(rate,NativeOpenWounds.rate(level,player,ranged,elite));
  }
}
