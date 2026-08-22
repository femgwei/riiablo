package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2DrlgFileStrc;
import com.d2moo.common.drlg.DrlgPreset;
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
  void parsesVersion18HeaderAndGroupPadding() {
    D2DrlgFileStrc file = load("Act1/Outdoors/Waypoint.ds1");

    assertEquals(6, file.getNWidth());
    assertEquals(6, file.getNHeight());
    assertEquals(2, file.getNWallLayers());
    assertEquals(1, file.getNFloorLayers());
    assertEquals(1, file.getNSubstGroups());
    assertNotNull(file.getPSubstGroups()[0]);
  }

  private static D2DrlgFileStrc load(String path) {
    Object[] result = new Object[1];
    D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
    DrlgPreset.loadDrlgFile(result, archive, path);
    assertTrue(result[0] instanceof D2DrlgFileStrc);
    return (D2DrlgFileStrc) result[0];
  }
}
