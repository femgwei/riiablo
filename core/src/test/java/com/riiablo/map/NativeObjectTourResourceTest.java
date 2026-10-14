package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.codec.DC;
import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeObjectTourResourceTest extends RiiabloTest {
  @Test void countessCellarMustInstallItsNativeRoomsAndPresetObjects() {
    for (String seedText : System.getProperty("map.audit.seeds", "1").split(",")) {
      int seed = Integer.parseInt(seedText.trim());
      Map map = new Map(seed, 0);
      Act1MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
      Map.Zone zone = map.findZone(Riiablo.files.Levels.get(25));
      assertNotNull(zone);
      assertTrue(zone.hasNativeRoomTopology(), "level25 cannot pass with a local fallback and zero native objects");
      assertFalse(zone.getNativeObjects().isEmpty());
      boolean countess = false, chest = false;
      for (Map.NativeMonster monster : zone.getNativeMonsters())
        if ("The Countess".equals(monster.superUniqueKey)) countess = true;
      for (Map.NativeObject object : zone.getNativeObjects()) {
        int id = object.ds1Raw ? Riiablo.files.obj.getObjectId(1, object.presetIndex) : object.presetIndex;
        com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(id);
        if (base != null && (base.SubClass & 8) != 0) chest = true;
      }
      assertTrue(countess, "Countess preset must be installed");
      assertTrue(chest, "native chests must be installed");
      TileGrid grid = zone.nativeTileGrid();
      assertNotNull(grid);
      DT1s library = new DT1s();
      for (int i = 1; i <= 255; i++) {
        String source = grid.sourceFile((byte) i);
        if (source != null) library.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
      }
      DT1.Tile[][] layers = new DT1.Tile[Map.MAX_LAYERS][];
      layers[Map.FLOOR_OFFSET] = new DT1.Tile[grid.width * grid.height];
      Act1MapBuilderD2MOD.LayerApplyCounts counts = Act1MapBuilderD2MOD.applyTileGridLayers(
          grid, library, layers, grid.width, grid.width, grid.height, null);
      assertEquals(0, counts.failedResolve);
      assertTrue(counts.floors > 0);
      assertTrue(counts.walls > 0);
    }
  }

  @Test void kurastStairsUseNativeObjTypeVisualsAndAllEnabledModesResolve() {
    assertEquals("sl", Riiablo.files.objects.get(194).Token);
    assertEquals("9c", Riiablo.files.ObjType.get(194).Token);
    assertEquals("2v", Riiablo.files.ObjType.get(449).Token.toLowerCase(java.util.Locale.ROOT));
    for (byte mode = 0; mode < 8; mode++) {
      if (!Riiablo.files.objects.get(194).Mode[mode]) continue;
      String token = Riiablo.files.ObjType.get(194).Token;
      String name = Class.Type.OBJ.getMode(mode);
      String root = Class.Type.OBJ.PATH + "\\" + token + "\\";
      String path = root + "cof\\" + token + name + "hth.cof";
      assertTrue(Riiablo.mpqs.contains(path), path);
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(path));
      assertTrue(cof.getNumFramesPerDir() > 0);
      for (int i = 0; i < cof.getNumLayers(); i++) {
        COF.Layer layer = cof.getLayer(i);
        String component = Engine.getComposite(layer.component);
        String stem = root + component + "\\" + token + component + "LIT";
        String sprite = stem + name + layer.weaponClass + ".dcc";
        if (!Riiablo.mpqs.contains(sprite) && cof.getNumFramesPerDir() == 1)
          sprite = stem + "OP" + layer.weaponClass + ".dcc";
        assertTrue(Riiablo.mpqs.contains(sprite), sprite);
        DC dc = DC.loadFromFile(Riiablo.mpqs.resolve(sprite));
        try { assertTrue(dc.getNumDirections() > 0); assertTrue(dc.getNumFramesPerDir() > 0); }
        finally { dc.dispose(); }
      }
    }
  }

  @Test void monasterySeamPreservesNativeCrossLevelRoomNeighbors() {
    Map map = new Map(1, 0);
    Act1MapBuilderD2MOD.INSTANCE.generate(map, 1, 0);
    Map.Zone first = map.findZone(Riiablo.files.Levels.get(27));
    Map.Zone second = map.findZone(Riiablo.files.Levels.get(26));
    assertTrue(map.isNativeSeamConnection(first, second));
    boolean found = false;
    for (Map.RoomEx a : first.getRoomsEx()) for (Map.RoomEx b : second.getRoomsEx()) {
      if (!a.isNativeNeighbor(26, b.id)) continue;
      found = true;
      assertTrue(map.areNativeRoomsVisible(first, a.x + 1, a.y + 1, second, b.x + 1, b.y + 1));
      assertTrue(map.areNativeRoomsVisible(second, b.x + 1, b.y + 1, first, a.x + 1, a.y + 1));
    }
    assertTrue(found, "native pRoomsNear must include 27/26 seam neighbors");
  }
}
