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

  @Test
  void includesNativeSpiderSideAreas() {
    assertArrayEquals(new int[] {
        D2LevelIds.LEVEL_SPIDERCAVE,
        D2LevelIds.LEVEL_SPIDERCAVERN
    }, Act3MapBuilderD2MOD.ACT3_UNDERGROUND_PRIMARY);
  }

  @Test
  void includesAllRemainingAct3UndergroundLevelsInNativeOrder() {
    assertArrayEquals(new int[] {
        D2LevelIds.LEVEL_SWAMPYPITLVL1,
        D2LevelIds.LEVEL_SWAMPYPITLVL2,
        D2LevelIds.LEVEL_FLAYERDUNGEONLVL1,
        D2LevelIds.LEVEL_FLAYERDUNGEONLVL2,
        D2LevelIds.LEVEL_SWAMPYPITLVL3,
        D2LevelIds.LEVEL_FLAYERDUNGEONLVL3,
        D2LevelIds.LEVEL_SEWERSA3LEV1,
        D2LevelIds.LEVEL_SEWERSA3LEV2,
        D2LevelIds.LEVEL_RUINEDTEMPLE,
        D2LevelIds.LEVEL_DISUSEDFANE,
        D2LevelIds.LEVEL_FORGOTTENRELIQUARY,
        D2LevelIds.LEVEL_FORGOTTENTEMPLE,
        D2LevelIds.LEVEL_RUINEDFANE,
        D2LevelIds.LEVEL_DISUSEDRELIQUARY
    }, Act3MapBuilderD2MOD.ACT3_UNDERGROUND_SECONDARY);
  }

  @Test
  void uses110fSpiderLvlPrestDefValues() {
    assertEquals(659, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_SW);
    assertEquals(660, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_SE);
    assertEquals(661, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_NW);
    assertEquals(662, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_NE);
    assertEquals(663, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_CHEST_NW);
    assertEquals(664, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_CHEST_NE);
  }

  @Test
  void uses110fJungleTransitionPresetDefValues() {
    assertEquals(715, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_JUNGLE_HEAD);
    assertEquals(716, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_JUNGLE_TAIL);
  }

  @Test
  void uses110fKurastSpecialPresetDefValues() {
    assertEquals(771, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_SEWER);
    assertEquals(772, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_TEMPLE);
    assertEquals(773, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_WAYPOINT);
    assertEquals(788, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_METRO_SEWER);
    assertEquals(789, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_METROTEMPLE);
  }

  @Test
  void uses110fCausewayAndTravincalPresetDefValues() {
    assertEquals(794, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BRIDGE);
    assertEquals(795, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_NW);
    assertEquals(796, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_N);
    assertEquals(797, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_NE);
    assertEquals(798, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_SW);
    assertEquals(799, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_S);
    assertEquals(800, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_TRAVINCAL_SE);
  }

  @Test
  void uses110fKurastFillPresetDefValues() {
    assertEquals(760, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SLUMS_16X16);
    assertEquals(758, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SLUMS_08X16);
    assertEquals(759, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SLUMS_16X08);
    assertEquals(757, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SLUMS_08X08);
    assertEquals(777, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_BURBS_16X16);
    assertEquals(793, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_METRO_16X16);
  }

  @Test
  void preservesNativeAct3MazeSpecialDefValues() {
    assertEquals(647, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_TEMPLE_6);
    assertEquals(704, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_TREASURE_2);
    assertEquals(659, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_SW);
    assertEquals(664, com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SPIDER_CHEST_NE);
  }

  @Test
  void uses110fDungeonSewerAndDuranceTransitionDefs() {
    assertArrayEquals(new int[] {665, 666, 668, 672, 695, 696, 697, 698, 699, 700, 701, 702},
        new int[] {
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_N,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_PREV_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_PREV_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_PREV_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_PREV_N,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_NEXT_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_NEXT_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_NEXT_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_DUNGEON_NEXT_N
        });
    assertArrayEquals(new int[] {739, 740, 741, 742, 743, 744, 745, 746},
        new int[] {
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_DRAIN_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_DRAIN_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_DRAIN_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_DRAIN_N,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_CHEST_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_CHEST_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_CHEST_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_SEWER_CHEST_N
        });
    assertArrayEquals(new int[] {784, 785, 786, 787, 788, 789, 790, 791, 792, 793, 794, 795},
        new int[] {
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_PREV_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_PREV_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_PREV_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_PREV_N,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_NEXT_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_NEXT_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_NEXT_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_NEXT_N,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_WAYPOINT_W,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_WAYPOINT_E,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_WAYPOINT_S,
          com.d2moo.common.drlg.D2LvlPrestIds.LVLPREST_ACT3_MEPHISTO_WAYPOINT_N
        });
  }

  @Test
  void excludesDedicatedCausewayAndTravincalFromGenericBorders() {
    assertEquals(true, Act3MapBuilderD2MOD.usesGenericOutdoorBorders(
        D2LevelIds.LEVEL_SPIDERFOREST));
    assertEquals(true, Act3MapBuilderD2MOD.usesGenericOutdoorBorders(
        D2LevelIds.LEVEL_UPPERKURAST));
    assertEquals(false, Act3MapBuilderD2MOD.usesGenericOutdoorBorders(
        D2LevelIds.LEVEL_KURASTCAUSEWAY));
    assertEquals(false, Act3MapBuilderD2MOD.usesGenericOutdoorBorders(
        D2LevelIds.LEVEL_TRAVINCAL));
  }
}
