package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual 1.10f DS1/table regression for the fortress stair braziers. */
class Act4FortressResourceTest extends RiiabloTest {
  @Test void fortressRoofPopSelectsAllMatchingStylesWithoutExteriorHoles() {
    Map map = new Map(127897359, 0);
    map.act4Builder().generate(map, 127897359, 0);
    com.riiablo.drlg.TileGrid grid = null;
    for (Map.Zone zone : map.getZones()) {
      if (zone.levelId() == 103) grid = zone.nativeTileGrid();
    }
    assertNotNull(grid);
    assertFalse(grid.nativePops.isEmpty());
    for (int i = 0; i < grid.nativePops.size(); i++) {
      com.riiablo.drlg.TileGrid.NativePop pop = grid.nativePops.get(i);
      System.out.println("[FORTRESS_POP] index=" + pop.logicalIndex + " style="
          + pop.subIndex + " bounds=" + pop.x + "," + pop.y + ","
          + pop.width + "," + pop.height);
    }
    int affected = 0;
    int roofCount = 0;
    java.util.Map<String, Integer> roofKinds = new java.util.TreeMap<>();
    java.util.Map<String, Integer> wallKinds = new java.util.TreeMap<>();
    for (int slot = 0; slot < com.riiablo.drlg.TileGrid.MAX_WALL_LAYERS; slot++) {
      for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
        int id = grid.wallIds[slot][y][x];
        if (id == -1) continue;
        int flags = grid.wallNativeFlags[slot][y][x];
        int style = DT1.Tile.Index.mainIndex(id);
        if (!Orientation.isRoof(DT1.Tile.Index.orientation(id))) {
          String source = grid.sourceFile(grid.wallSourceFiles[slot][y][x]);
          if (style == 4) System.out.println("[FORTRESS_COLUMN] pos=" + x + "," + y
              + " flags=" + Integer.toHexString(flags));
          wallKinds.merge(source + " style=" + style + " flags=" + Integer.toHexString(flags)
              + " group=" + grid.wallLogicalGroups[slot][y][x], 1, Integer::sum);
        }
        if (Orientation.isRoof(DT1.Tile.Index.orientation(id))) {
          roofCount++;
          roofKinds.merge("style=" + style + " flags=" + Integer.toHexString(flags), 1, Integer::sum);
          assertFalse(RenderSystem.roofContextOccludes(grid, 7, 7, -1, x, y));
          assertFalse(RenderSystem.nativePopOccludes(grid, -1, x, y, style, flags),
              "outside the temple all roof artwork must remain visible");
          for (int p = 0; p < grid.nativePops.size(); p++) {
            if (RenderSystem.nativePopOccludes(grid, p, x, y, style, flags)) affected++;
          }
        }
      }
    }
    assertTrue(affected > 0, "native fortress Pops must control actual roof tiles");
    assertEquals(roofCount, affected, "entering the temple must hide every roof tile");
    System.out.println("[FORTRESS_POP] affectedRoofTiles=" + affected);
    System.out.println("[FORTRESS_POP] roofKinds=" + roofKinds);
    System.out.println("[FORTRESS_POP] wallKinds=" + wallKinds);
  }

  @Test void fortressBraziersUseNativeObjectClasses() {
    DS1 ds1 = DS1.loadFromFile(Riiablo.mpqs.resolve(
        "data/global/tiles/act4/fort/fortress.ds1"));
    assertEquals(4, ds1.getAct());
    int braziers = 0;
    for (DS1.Object object : ds1.objects) {
      if (object.type != DS1.Object.STATIC_TYPE) continue;
      int classId = Riiablo.files.obj.getObjectId(ds1.getAct(), object.id);
      com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(classId);
      if (object.id != 64 && object.id != 65) continue;
      braziers++;
      assertEquals(object.id == 64 ? "98" : "99", base.Token);
      assertEquals(classId, base.Id);
      assertTrue(base.Description.startsWith("fortress brazier"));
    }
    assertEquals(408, Riiablo.files.obj.getObjectId(4, 64));
    assertEquals(409, Riiablo.files.obj.getObjectId(4, 65));
    assertTrue(braziers > 0);
  }
}
