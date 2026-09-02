package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import org.junit.jupiter.api.Test;

/** Pure data-layer half of the D2MOO regression gate. */
class NativeDataTablesTest {
  @Test
  void clampsDifficultyAndUsesExplicitFallbackForMissingColumns() {
    assertEquals(10, NativeDataTables.value(new int[] {10, 20, 30}, -1, 99));
    assertEquals(30, NativeDataTables.value(new int[] {10, 20, 30}, 99, 99));
    assertEquals(99, NativeDataTables.value(null, 1, 99));
    assertEquals(42, NativeDataTables.value(new int[] {42}, 2, 42));
  }

  @Test
  void normalizesMonsterGroupsToNativeMinimums() {
    MonStats.Entry monster = new MonStats.Entry();
    monster.MinGrp = 0;
    monster.MaxGrp = 0;
    monster.PartyMin = 4;
    monster.PartyMax = 2;
    assertEquals(1, NativeDataTables.minGroup(monster));
    assertEquals(1, NativeDataTables.maxGroup(monster));
    assertEquals(4, NativeDataTables.partyMin(monster));
    assertEquals(4, NativeDataTables.partyMax(monster));
  }

  @Test
  void levelAndAreaReadsNeverIndexPastDifficultyColumns() {
    Levels.Entry level = new Levels.Entry();
    level.SizeX = new int[] {64};
    level.SizeY = new int[] {48};
    level.MonLvl = new int[] {3};
    assertEquals(64, NativeDataTables.levelSizeX(level, 2, 1));
    assertEquals(48, NativeDataTables.levelSizeY(level, 2, 1));
    assertEquals(3, NativeDataTables.areaLevel(level, 2, false));
  }
}
