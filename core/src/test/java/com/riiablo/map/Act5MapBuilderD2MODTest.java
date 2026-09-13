package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2LvlPrestIds;

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
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV3,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[12]);
    assertEquals(D2LevelIds.LEVEL_THRONEOFDESTRUCTION,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[13]);
    assertEquals(D2LevelIds.LEVEL_WORLDSTONECHAMBER,
        Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN[14]);
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
    int summit = Act5MapBuilderD2MOD.ACT5_MAIN_CHAIN.length - 6;
    assertEquals(D2LevelIds.LEVEL_ARREATSUMMIT,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit][0]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV1,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit][1]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV2,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit + 1][1]);
    assertEquals(D2LevelIds.LEVEL_THEWORLDSTONEKEEPLEV3,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit + 2][1]);
    assertEquals(D2LevelIds.LEVEL_THRONEOFDESTRUCTION,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit + 3][1]);
    assertEquals(D2LevelIds.LEVEL_WORLDSTONECHAMBER,
        Act5MapBuilderD2MOD.ACT5_MAIN_LINKS[summit + 4][1]);
  }

  @Test
  void nihlathakBranchUsesIndependentNativeTempleChain() {
    assertEquals(4, Act5MapBuilderD2MOD.ACT5_NIHLATHAK_CHAIN.length);
    assertEquals(D2LevelIds.LEVEL_NIHLATHAKSTEMPLE,
        Act5MapBuilderD2MOD.ACT5_NIHLATHAK_CHAIN[0]);
    assertEquals(D2LevelIds.LEVEL_HALLSOFVAUGHT,
        Act5MapBuilderD2MOD.ACT5_NIHLATHAK_CHAIN[3]);
    assertEquals(Act5MapBuilderD2MOD.ACT5_NIHLATHAK_CHAIN.length - 1,
        Act5MapBuilderD2MOD.ACT5_NIHLATHAK_LINKS.length);
    for (int i = 0; i < Act5MapBuilderD2MOD.ACT5_NIHLATHAK_LINKS.length; i++) {
      assertEquals(Act5MapBuilderD2MOD.ACT5_NIHLATHAK_CHAIN[i],
          Act5MapBuilderD2MOD.ACT5_NIHLATHAK_LINKS[i][0]);
      assertEquals(Act5MapBuilderD2MOD.ACT5_NIHLATHAK_CHAIN[i + 1],
          Act5MapBuilderD2MOD.ACT5_NIHLATHAK_LINKS[i][1]);
    }
  }

  @Test
  void worldstonePresetIdsMatch110fLvlPrestDefs() {
    assertEquals(1066, D2LvlPrestIds.LVLPREST_ACT5_BAAL_N);
    assertEquals(1060, D2LvlPrestIds.LVLPREST_ACT5_BAAL_E);
    assertEquals(1062, D2LvlPrestIds.LVLPREST_ACT5_BAAL_S);
    assertEquals(1059, D2LvlPrestIds.LVLPREST_ACT5_BAAL_W);
    assertEquals(1078, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_N);
    assertEquals(1079, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_S);
    assertEquals(1080, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_E);
    assertEquals(1081, D2LvlPrestIds.LVLPREST_ACT5_BAAL_NEXT_W);
    assertEquals(1082, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_N);
    assertEquals(1083, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_S);
    assertEquals(1084, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_E);
    assertEquals(1085, D2LvlPrestIds.LVLPREST_ACT5_BAAL_WAYPOINT_W);
    assertEquals(1086, D2LvlPrestIds.LVLPREST_ACT5_THRONEROOM);
    assertEquals(1087, D2LvlPrestIds.LVLPREST_ACT5_WORLDSTONE);
  }
}
