package com.riiablo.map;

import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2LevelTypes;
import com.d2moo.common.drlg.D2LvlPrestIds;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** D2MOO DataTbls/LevelsIds.h enum values used by the 1.10f resources. */
class NativeExpansionIdsTest {
  @Test void actTwoEndsBeforeKurastDocks() {
    assertEquals(65, D2LevelIds.LEVEL_ANCIENTTUNNELS);
    assertEquals(66, D2LevelIds.LEVEL_TALRASHASTOMB1);
    assertEquals(72, D2LevelIds.LEVEL_TALRASHASTOMB7);
    assertEquals(73, D2LevelIds.LEVEL_DURIELSLAIR);
    assertEquals(74, D2LevelIds.LEVEL_ARCANESANCTUARY);
  }
  @Test void outdoorTypesMatchNativeEnum() {
    assertEquals(16, D2LevelTypes.LVLTYPE_ACT2_DESERT);
    assertEquals(27, D2LevelTypes.LVLTYPE_ACT4_MESA);
    assertEquals(28, D2LevelTypes.LVLTYPE_ACT4_LAVA);
    assertEquals(30, D2LevelTypes.LVLTYPE_ACT5_SIEGE);
    assertEquals(31, D2LevelTypes.LVLTYPE_ACT5_BARRICADE);
  }

  @Test void expansionMazeAndBorderPresetsMatchNativeEnum() {
    assertEquals(301, D2LvlPrestIds.LVLPREST_ACT2_TOWN);
    assertEquals(388, D2LvlPrestIds.LVLPREST_ACT2_DESERT_TOMB_1);
    assertEquals(414, D2LvlPrestIds.LVLPREST_ACT2_TOMB_W);
    assertEquals(481, D2LvlPrestIds.LVLPREST_ACT2_DURIELS_LAIR);
    assertEquals(510, D2LvlPrestIds.LVLPREST_ACT2_ARCANE_W);
    assertEquals(529, D2LvlPrestIds.LVLPREST_ACT3_TOWN);
    assertEquals(605, D2LvlPrestIds.LVLPREST_ACT3_SLUMS_BORDER_N);
    assertEquals(652, D2LvlPrestIds.LVLPREST_ACT3_BRIDGE);
    assertEquals(799, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_1);
    assertEquals(810, D2LvlPrestIds.LVLPREST_ACT4_MESA_BORDER_12);
    assertEquals(836, D2LvlPrestIds.LVLPREST_ACT4_LAVA_X);
    assertEquals(862, D2LvlPrestIds.LVLPREST_ACT4_DIABLO_HEART);
    assertEquals(881, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_1);
    assertEquals(968, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_CLIFF_BORDER_12_SNOW);
    assertEquals(1002, D2LvlPrestIds.LVLPREST_ACT5_BARRICADE_16_SNOW);
  }

  @Test void displayAliasesDoNotSwapMainCavesAndSideBranches() {
    assertEquals(114, D2LevelIds.LEVEL_FROZENRIVER);
    assertEquals(115, D2LevelIds.LEVEL_GLACIALTRAIL);
    assertEquals(116, D2LevelIds.LEVEL_DRIFTERSCAVERN);
    assertEquals(118, D2LevelIds.LEVEL_ANCIENTSWAY);
    assertEquals(119, D2LevelIds.LEVEL_ICYCELLAR);
  }
}
