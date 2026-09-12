package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2LevelIds;

class Act5MapBuilderD2MODTest {
  @Test
  void mainChainUsesAct5LevelsInNativeProgressionOrder() {
    assertEquals(D2LevelIds.LEVEL_HARROGATH,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[0]);
    assertEquals(D2LevelIds.LEVEL_BLOODYFOOTHILLS,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[1]);
    assertEquals(D2LevelIds.LEVEL_FRIGIDHIGHLANDS,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[2]);
    assertEquals(D2LevelIds.LEVEL_ARREATSUMMIT,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[9]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[10]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[11]);
    assertEquals(D2LevelIds.LEVEL_WORLDSTONECHAMBER,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[12]);
    assertEquals(Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN.length - 1,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS.length);
  }

  @Test
  void linksAreAdjacentAndDoNotAliasBloodyFoothills() {
    assertTrue(D2LevelIds.LEVEL_ID_ACT5_BARRICADE_1
        != D2LevelIds.LEVEL_BLOODYFOOTHILLS);
    for (int i = 0; i < Act5MapBuilderD2MOD.ACT5_MAIN_LINKS.length; i++) {
      assertEquals(Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[i],
          Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[i][0]);
      assertEquals(Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[i + 1],
          Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[i][1]);
    }
  }

  @Test
  void worldstoneTailIsLinkedAfterArreatSummit() {
    int summit = Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN.length - 4;
    assertEquals(D2LevelIds.LEVEL_ARREATSUMMIT,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit][0]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit][1]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit + 1][1]);
    assertEquals(D2LevelIds.LEVEL_WORLDSTONECHAMBER,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit + 2][1]);
  }
}
