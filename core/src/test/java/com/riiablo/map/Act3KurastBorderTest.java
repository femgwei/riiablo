package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlPrest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;

class Act3KurastBorderTest {
  @Test void placesAllLowerKurastEdgesAndCorners() {
    assertBorders(D2LevelIds.LEVEL_LOWERKURAST, 605);
  }

  @Test void placesAllBazaarEdgesAndCorners() {
    assertBorders(D2LevelIds.LEVEL_KURASTBAZAAR, 619);
  }

  @Test void upperKurastUsesMetroNotBazaarPresets() {
    assertBorders(D2LevelIds.LEVEL_UPPERKURAST, 636);
  }

  @Test void gatesFollowBothNativeInterlinkLayouts() {
    for (int interlink = 0; interlink <= 1; interlink++) {
      for (int levelId : new int[] {D2LevelIds.LEVEL_LOWERKURAST,
          D2LevelIds.LEVEL_KURASTBAZAAR, D2LevelIds.LEVEL_UPPERKURAST}) {
        assertBorders(levelId, levelId == D2LevelIds.LEVEL_LOWERKURAST ? 605
            : levelId == D2LevelIds.LEVEL_KURASTBAZAAR ? 619 : 636, interlink);
      }
    }
  }

  @Test void interlinkReplaysNativeDrlgSeedRollsWithoutGlobalRandomState() {
    for (int gameSeed : new int[] {0, 1, 2, 3, 0x171A6100, -1, Integer.MIN_VALUE}) {
      D2Seed nativeSeed = new D2Seed();
      Seed.initLowSeed(nativeSeed, gameSeed);
      Seed.rollRandomNumber(nativeSeed); // dwStartSeed
      int expected = (int) (Seed.rollRandomNumber(nativeSeed) & 1L);
      assertEquals(expected, Act3MapBuilderD2MOD.jungleInterlink(gameSeed));
      assertEquals(expected, Act3MapBuilderD2MOD.jungleInterlink(gameSeed));
    }
  }

  @Test void preservesExistingPresetsAndUsesActualValidFileIndex() {
    Map.Zone zone = zone(D2LevelIds.LEVEL_LOWERKURAST);
    Map.Preset existing = new Map.Preset();
    zone.presets[0][0] = existing;
    Act3MapBuilderD2MOD.placeNativeKurastBorders(zone, def -> {
      LvlPrest.Entry entry = preset(def);
      entry.File = new String[] {"0", "", "border.ds1"};
      return entry;
    });
    assertSame(existing, zone.presets[0][0]);
    assertEquals("border.ds1", zone.presets[1][0].ds1Path);
    Map.Preset first = zone.presets[1][0];
    Act3MapBuilderD2MOD.placeNativeKurastBorders(zone, Act3KurastBorderTest::preset);
    assertSame(first, zone.presets[1][0]);
  }

  @Test void skipsMissingRowsAndFilesWithoutStoppingOtherEdges() {
    Map.Zone zone = zone(D2LevelIds.LEVEL_LOWERKURAST);
    Act3MapBuilderD2MOD.placeNativeKurastBorders(zone, def -> {
      if (def == 605) return null;
      LvlPrest.Entry entry = preset(def);
      if (def == 606) entry.File = new String[] {null, "0", ""};
      return entry;
    });
    assertNull(zone.presets[1][0]);
    assertNull(zone.presets[1][zone.gridsY - 1]);
    assertEquals(610, zone.presets[0][0].preset.Def);
  }

  @Test void leavesNativeTerrainAndUnrelatedLevelsUntouched() {
    Map.Zone nativeZone = zone(D2LevelIds.LEVEL_LOWERKURAST);
    nativeZone.setNativeTileGrid(new TileGrid(40, 32), 0);
    Map.Zone jungle = zone(D2LevelIds.LEVEL_SPIDERFOREST);
    for (Map.Zone zone : new Map.Zone[] {nativeZone, jungle}) {
      Act3MapBuilderD2MOD.placeNativeKurastBorders(zone, def -> {
        throw new AssertionError("unexpected compatibility preset lookup " + def);
      });
      assertNull(zone.presets[0][0]);
    }
    Act3MapBuilderD2MOD.placeNativeKurastBorders(null, Act3KurastBorderTest::preset);
  }

  private static void assertBorders(int levelId, int base) {
    assertBorders(levelId, base, 0);
  }

  private static void assertBorders(int levelId, int base, int interlink) {
    Map.Zone zone = zone(levelId);
    Act3MapBuilderD2MOD.placeNativeKurastBorders(zone, Act3KurastBorderTest::preset,
        interlink);
    int lastX = zone.gridsX - 1, lastY = zone.gridsY - 1;
    int north = levelId == D2LevelIds.LEVEL_LOWERKURAST ? (interlink != 0 ? 1 : lastX - 1)
        : levelId == D2LevelIds.LEVEL_KURASTBAZAAR ? (interlink != 0 ? lastX - 1 : 1)
        : lastX / 2;
    int south = levelId == D2LevelIds.LEVEL_LOWERKURAST ? lastX / 2
        : levelId == D2LevelIds.LEVEL_KURASTBAZAAR ? (interlink != 0 ? 1 : lastX - 1)
        : interlink != 0 ? lastX - 1 : 1;
    for (int x = 0; x <= lastX; x++) {
      for (int y = 0; y <= lastY; y++) {
        boolean doubleGateFollower = (levelId == D2LevelIds.LEVEL_LOWERKURAST
            && y == lastY && x == south + 1)
            || (levelId == D2LevelIds.LEVEL_UPPERKURAST
                && y == 0 && x == north + 1);
        if (doubleGateFollower) {
          assertNull(zone.presets[x][y], "double-width gate follower must remain unfilled");
          continue;
        }
        int offset;
        if (x == 0 && y == 0) offset = 5;
        else if (x == lastX && y == 0) offset = 4;
        else if (x == 0 && y == lastY) offset = 7;
        else if (x == lastX && y == lastY) offset = 6;
        else if (y == 0) offset = 0;
        else if (y == lastY) offset = 1;
        else if (x == lastX) offset = 2;
        else if (x == 0) offset = 3;
        else {
          assertNull(zone.presets[x][y], "interior must remain available");
          continue;
        }
        if (y == 0 && x == north) offset = 8;
        if (y == lastY && x == south) offset = 9;
        assertEquals(base + offset, zone.presets[x][y].preset.Def,
            "level=" + levelId + " grid=(" + x + "," + y + ")");
      }
    }
  }

  private static Map.Zone zone(int levelId) {
    Map.Zone zone = new Map.Zone();
    zone.level = new Levels.Entry();
    zone.level.Id = levelId;
    zone.gridsX = 5;
    zone.gridsY = 4;
    zone.presets = new Map.Preset[zone.gridsX][zone.gridsY];
    return zone;
  }

  private static LvlPrest.Entry preset(int def) {
    LvlPrest.Entry entry = new LvlPrest.Entry();
    entry.Def = def;
    entry.File = new String[] {"border-" + def + ".ds1"};
    return entry;
  }
}
