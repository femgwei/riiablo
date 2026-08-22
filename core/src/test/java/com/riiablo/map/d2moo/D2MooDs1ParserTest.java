package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2DrlgFileStrc;
import com.d2moo.common.drlg.D2DrlgSubstGroupStrc;
import com.d2moo.common.drlg.DrlgDrlgGrid;
import com.d2moo.common.drlg.DrlgPreset;
import com.d2moo.common.drlg.DrlgTileSub;
import com.d2moo.common.datatbls.D2LvlSubTxt;
import com.d2moo.common.util.D2FileReader;
import com.riiablo.RiiabloTest;

/** Verifies the real MPQ DS1 stream layout used by Act I LvlSub records. */
class D2MooDs1ParserTest extends RiiabloTest {
  @Test
  void parsesVersion17SubstitutionGroupsAndLayers() {
    D2DrlgFileStrc file = load("Act1/Outdoors/BorderMiddle.ds1");

    assertEquals(89, file.getNWidth());
    assertEquals(54, file.getNHeight());
    assertEquals(1, file.getNWallLayers());
    assertEquals(1, file.getNFloorLayers());
    assertEquals(9, file.getNSubstGroups());
    assertTrue(file.getPWallLayer(0) instanceof int[]);
    assertTrue(file.getPTileTypeLayer(0) instanceof int[]);
    assertTrue(file.getPFloorLayer(0) instanceof int[]);
    assertTrue(file.getPShadowLayer() instanceof int[]);
  }

  @Test
  void borderCliffsGroupsAndVariantsStayInsideDs1Layers() {
    D2DrlgFileStrc file = load("Act1/Outdoors/BorderCliffs.ds1");

    assertEquals(46, file.getNWidth());
    assertEquals(73, file.getNHeight());
    assertEquals(15, file.getNSubstGroups());
    for (D2DrlgSubstGroupStrc group : file.getPSubstGroups()) {
      assertNotNull(group);
      assertTrue(group.getTBox().getNWidth() > 0);
      assertTrue(group.getTBox().getNHeight() > 0);
      assertTrue(group.getField_14() > 0);
      int lastVariantX = group.getTBox().getNPosX()
          + group.getField_14() * (group.getTBox().getNWidth() + 1)
          + group.getTBox().getNWidth() - 1;
      int lastY = group.getTBox().getNPosY() + group.getTBox().getNHeight() - 1;
      assertTrue(lastVariantX <= file.getNWidth(), "variant exceeds DS1 width");
      assertTrue(lastY <= file.getNHeight(), "group exceeds DS1 height");
    }
  }

  @Test
  void initializesLvlSubWallTypeAndFloorGrids() {
    D2DrlgFileStrc file = load("Act1/Outdoors/BorderCliffs.ds1");
    D2LvlSubTxt record = new D2LvlSubTxt();
    record.setSzFile("Act1/Outdoors/BorderCliffs.ds1");
    D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;

    DrlgTileSub.initializeDrlgFile(archive, record);

    assertNotNull(record.getPWallGrid(0).getPCellsFlags());
    assertNotNull(record.getPTileTypeGrid(0).getPCellsFlags());
    assertNotNull(record.getPFloorGrid().getPCellsFlags());
    assertEquals(file.getNWidth() + 1, record.getPWallGrid(0).getNWidth());
    assertEquals(file.getNHeight() + 1, record.getPWallGrid(0).getNHeight());
    assertArrayEquals((int[]) file.getPWallLayer(0), record.getPWallGrid(0).getPCellsFlags());
    assertArrayEquals((int[]) file.getPTileTypeLayer(0),
        record.getPTileTypeGrid(0).getPCellsFlags());
    assertArrayEquals((int[]) file.getPFloorLayer(0), record.getPFloorGrid().getPCellsFlags());
    assertTrue(countFlag(record.getPWallGrid(0), 1) > 0,
        "BorderCliffs wall match layer must not be empty");
  }

  @Test
  void parsesVersion18HeaderAndGroupPadding() {
    D2DrlgFileStrc file = load("Act1/Outdoors/Waypoint.ds1");

    assertEquals(6, file.getNWidth());
    assertEquals(6, file.getNHeight());
    assertEquals(2, file.getNWallLayers());
    assertEquals(1, file.getNFloorLayers());
    assertEquals(1, file.getNSubstGroups());
    assertNotNull(file.getPSubstGroups()[0]);
  }

  @Test
  void zeroPadsRetailTreesFinalSubstitutionGroup() {
    D2DrlgFileStrc file = load("Act1/Outdoors/Trees.ds1");

    assertEquals(36, file.getNWidth());
    assertEquals(8, file.getNHeight());
    assertEquals(14, file.getNSubstGroups());
    assertNotNull(file.getPSubstGroups());
    assertEquals(14, file.getPSubstGroups().length);
    assertNotNull(file.getPSubstGroups()[13]);
    assertEquals(0, file.getPSubstGroups()[13].getTBox().getNPosX());
    assertEquals(0, file.getPSubstGroups()[13].getTBox().getNPosY());
    assertEquals(0, file.getPSubstGroups()[13].getTBox().getNWidth());
    assertEquals(0, file.getPSubstGroups()[13].getTBox().getNHeight());
    assertTrue(file.getPShadowLayer() instanceof int[]);
  }

  private static D2DrlgFileStrc load(String path) {
    Object[] result = new Object[1];
    D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
    DrlgPreset.loadDrlgFile(result, archive, path);
    assertTrue(result[0] instanceof D2DrlgFileStrc);
    return (D2DrlgFileStrc) result[0];
  }

  private static int countFlag(com.d2moo.common.drlg.D2DrlgGridStrc grid, int flag) {
    int count = 0;
    for (int y = 0; y < grid.getNHeight(); y++) {
      for (int x = 0; x < grid.getNWidth(); x++) {
        if ((DrlgDrlgGrid.getGridEntry(grid, x, y) & flag) != 0) count++;
      }
    }
    return count;
  }
}
