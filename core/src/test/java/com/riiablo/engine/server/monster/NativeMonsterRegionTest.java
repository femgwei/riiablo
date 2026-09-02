package com.riiablo.engine.server.monster;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.Levels;
import org.junit.jupiter.api.Test;

class NativeMonsterRegionTest {
  @Test
  void prefersNightmareHellColumnsAndFallsBackWhenEmpty() {
    Levels.Entry level = new Levels.Entry();
    level.NumMon = 4;
    level.mon = new String[] {"fallen1", "quillrat1", "zombie1", ""};
    level.nmon = new String[] {"darkranger", "", "", ""};

    assertArrayEquals(level.nmon, NativeMonsterRegion.monsterColumns(level, 1));
    level.nmon[0] = "";
    assertArrayEquals(level.mon, NativeMonsterRegion.monsterColumns(level, 2));
  }

  @Test
  void capsEntriesLikeNativeMonsterRegionInitialization() {
    Levels.Entry level = new Levels.Entry();
    level.NumMon = 25;
    level.mon = new String[20];
    for (int i = 0; i < level.mon.length; i++) level.mon[i] = "monster" + i;
    assertEquals(13, NativeMonsterRegion.selectedEntryCount(level, 0));
  }

  @Test
  void ignoresEmptyDeclaredMonsterSlots() {
    Levels.Entry level = new Levels.Entry();
    level.NumMon = 5;
    level.mon = new String[] {"fallen1", "", null, "0", "zombie1"};
    assertEquals(2, NativeMonsterRegion.selectedEntryCount(level, 0));
  }

  @Test
  void densityRollUsesNativeInclusiveHundredThousandRange() {
    assertFalse(NativeMonsterRegion.densityRoll(0, 0));
    assertTrue(NativeMonsterRegion.densityRoll(100, 100));
    assertFalse(NativeMonsterRegion.densityRoll(100, 101));
    assertTrue(NativeMonsterRegion.densityRoll(100000, 99999));
  }
}
