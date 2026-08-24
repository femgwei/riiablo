package com.d2moo.common.datatbls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2LvlWarpTxt;

class DataTblsIndexTest {
  @AfterEach
  void clearCaches() {
    DataTbls.unloadLevelTypesTxt();
    DataTbls.unloadLevelDefsBin();
    DataTbls.unloadLvlPrestTxt();
    DataTbls.unloadLvlWarpTxt();
    DataTbls.unloadLvlSubTxt();
    DataTbls.unloadLvlMazeTxt();
  }

  @Test
  void indexesCommonDrlgTablesByNativeKeys() {
    D2LevelTypesTxt levelType = new D2LevelTypesTxt();
    levelType.setDwLevelType(2);
    D2LevelDefBin levelDef = new D2LevelDefBin();
    levelDef.setDwLevelId(3);
    D2LvlPrestTxt preset = new D2LvlPrestTxt();
    preset.setDwDef(4);
    preset.setDwLevelId(5);
    D2LvlWarpTxt warp = new D2LvlWarpTxt();
    warp.setDwLevelId(6);
    warp.setSzDirection("b");
    D2LvlSubTxt sub = new D2LvlSubTxt();
    sub.setDwType(7);
    D2LvlMazeTxt maze = new D2LvlMazeTxt();
    maze.setDwLevelId(8);

    DataTbls.setLevelTypesTxtCache(new D2LevelTypesTxt[] {levelType});
    DataTbls.setLevelDefBinCache(new D2LevelDefBin[] {levelDef});
    DataTbls.setLvlPrestTxtCache(new D2LvlPrestTxt[] {preset});
    DataTbls.setLvlWarpTxtCache(new D2LvlWarpTxt[] {warp});
    DataTbls.setLvlSubTxtCache(new D2LvlSubTxt[] {sub});
    DataTbls.setLvlMazeTxtCache(new D2LvlMazeTxt[] {maze});

    assertSame(levelType, DataTbls.getLevelTypesTxtRecord(2));
    assertSame(levelDef, DataTbls.getLevelDefRecord(3));
    assertSame(preset, DataTbls.getLvlPrestTxtRecord(4));
    assertSame(preset, DataTbls.getLvlPrestTxtRecordFromLevelId(5));
    assertSame(warp, DataTbls.getLvlWarpTxtRecordFromLevelIdAndDirection(6, 'b'));
    assertSame(sub, DataTbls.getLvlSubTxtRecord(7));
    assertSame(maze, DataTbls.getLvlMazeTxtRecordFromLevelId(8));
  }

  @Test
  void retainsFirstDuplicateLikePreviousLinearLookup() {
    D2LvlPrestTxt first = new D2LvlPrestTxt();
    first.setDwDef(10);
    first.setDwLevelId(20);
    D2LvlPrestTxt duplicate = new D2LvlPrestTxt();
    duplicate.setDwDef(10);
    duplicate.setDwLevelId(20);

    DataTbls.setLvlPrestTxtCache(new D2LvlPrestTxt[] {first, duplicate});

    assertSame(first, DataTbls.getLvlPrestTxtRecord(10));
    assertSame(first, DataTbls.getLvlPrestTxtRecordFromLevelId(20));
  }

  @Test
  void unloadClearsArrayAndIndexTogether() {
    D2LvlMazeTxt maze = new D2LvlMazeTxt();
    maze.setDwLevelId(8);
    DataTbls.setLvlMazeTxtCache(new D2LvlMazeTxt[] {maze});

    DataTbls.unloadLvlMazeTxt();

    assertNull(DataTbls.getLvlMazeTxtRecordFromLevelId(8));
  }

  @Test
  void buildsPortalLevelsInSourceTableOrderAndReturnsACopy() {
    D2LevelDefBin normal = levelDef(7, false);
    D2LevelDefBin firstPortal = levelDef(40, true);
    D2LevelDefBin secondPortal = levelDef(12, true);

    DataTbls.setLevelDefBinCache(
        new D2LevelDefBin[] {normal, firstPortal, null, secondPortal});

    int[] levels = DataTbls.getPortalLevels();
    assertArrayEquals(new int[] {40, 12}, levels);
    levels[0] = 999;
    assertArrayEquals(new int[] {40, 12}, DataTbls.getPortalLevels());

    DataTbls.unloadLevelDefsBin();
    assertArrayEquals(new int[0], DataTbls.getPortalLevels());
  }

  private static D2LevelDefBin levelDef(int levelId, boolean portal) {
    D2LevelDefBin levelDef = new D2LevelDefBin();
    levelDef.setDwLevelId(levelId);
    levelDef.setDwPortal(portal ? 1 : 0);
    return levelDef;
  }
}
