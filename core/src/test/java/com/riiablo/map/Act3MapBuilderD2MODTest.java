package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.d2moo.common.drlg.D2LevelIds;
import org.junit.jupiter.api.Test;

class Act3MapBuilderD2MODTest {
  @Test
  void usesNative110fAct3LevelIds() {
    assertEquals(D2LevelIds.LEVEL_ARCANESANCTUARY + 1,
        Act3MapBuilderD2MOD.LEVEL_KURASTDOCKTOWN);
    assertEquals(D2LevelIds.LEVEL_KURASTDOCKTOWN,
        Act3MapBuilderD2MOD.LEVEL_KURASTDOCKTOWN);
    assertEquals(D2LevelIds.LEVEL_TRAVINCAL,
        Act3MapBuilderD2MOD.LEVEL_TRAVINCAL);
  }

  @Test
  void preservesNativeAct3OutdoorBuildOrder() {
    assertArrayEquals(new int[] {
        D2LevelIds.LEVEL_KURASTDOCKTOWN,
        D2LevelIds.LEVEL_SPIDERFOREST,
        D2LevelIds.LEVEL_GREATMARSH,
        D2LevelIds.LEVEL_FLAYERJUNGLE,
        D2LevelIds.LEVEL_LOWERKURAST,
        D2LevelIds.LEVEL_KURASTBAZAAR,
        D2LevelIds.LEVEL_UPPERKURAST,
        D2LevelIds.LEVEL_KURASTCAUSEWAY,
        D2LevelIds.LEVEL_TRAVINCAL
    }, Act3MapBuilderD2MOD.ACT3_OUTDOOR_CHAIN);
  }
}
