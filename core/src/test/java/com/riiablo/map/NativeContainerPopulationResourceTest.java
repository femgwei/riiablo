package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Excel;
import com.riiablo.codec.excel.ObjGroup;
import com.riiablo.codec.excel.Objects;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Barrel/urn audit kept separate from ordinary chest counts. */
class NativeContainerPopulationResourceTest extends RiiabloTest {
  @Test void barrelsAndUrnsOnRealTerrain() {
    ObjGroup groups = Excel.load(ObjGroup.class, Riiablo.mpqs.resolve("data\\global\\excel\\ObjGroup.txt"));
    for (int id : new int[]{4,7,9,11,52,94,95}) {
      Objects.Entry b = Riiablo.files.objects.get(id);
      System.out.println("[CONTAINER_TABLE] class=" + id + " fn=" + b.PopulateFn
          + " size=" + b.SizeX + "," + b.SizeY + " spacing=" + b.Xspace + "," + b.Yspace);
    }
    for (String seedText : System.getProperty("map.audit.seeds", "1").split(",")) {
      int seed = Integer.parseInt(seedText.trim());
      for (int act = 0; act < 2; act++) {
        Map map = new Map(seed, 0);
        if (act == 0) Act1MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        else Act2MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        NativeObjectPopulation population = new NativeObjectPopulation();
        for (Map.Zone zone : map.getZones()) {
          if (zone.isTown()) continue;
          TileGrid grid = zone.nativeTileGrid();
          assertNotNull(grid);
          zone.dt1s = new DT1s();
          for (int index = 1; index <= 255; index++) {
            String source = grid.sourceFile((byte) index);
            if (source != null) zone.dt1s.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
          }
          Act1MapBuilderD2MOD.applyExportedTileGrid(zone, grid);
          int before = zone.getNativeObjects().size;
          String preset = counts(zone, 0, before);
          java.util.Map<String, Integer> rooms = new java.util.TreeMap<>();
          for (Map.RoomEx room : zone.getRoomsEx()) {
            if (zone.levelId() >= 55 && zone.levelId() <= 61
                || zone.levelId() >= 66 && zone.levelId() <= 72)
              assertTrue(room.width <= 40 && room.height <= 40,
                  "A2 tomb maps must be partitioned into 8x8-tile population rooms: level=" + zone.levelId());
            String key = room.width + "x" + room.height + "/" + Integer.toHexString(room.populationFlags())
                + "/" + room.populationOutdoorFlag80();
            rooms.merge(key, 1, Integer::sum);
          }
          StringBuilder inputs = new StringBuilder();
          for (int slot = 0; slot < zone.level.ObjGrp.length; slot++) {
            int id = zone.level.ObjGrp[slot];
            if (id == 0) continue;
            ObjGroup.Entry g = groups.get(id);
            for (int m = 0; m < 8 && g.ID[m] != 0; m++) {
              Objects.Entry b = Riiablo.files.objects.get(g.ID[m]);
              if (b.PopulateFn != 1 && b.PopulateFn != 4 && b.PopulateFn != 5) continue;
              int eligible = 0, budget = 0;
              for (Map.RoomEx room : zone.getRoomsEx()) {
                if ((room.populationFlags() & 0x830000) != 0 || room.populationOutdoorFlag80() != 0) continue;
                eligible++;
                budget += NativeObjectPopulation.densityCount(g.DENSITY[m], room.width, room.height);
              }
              inputs.append(" g").append(id).append("/class").append(b.Id).append("/fn").append(b.PopulateFn)
                  .append("/p").append(zone.level.ObjPrb[slot]).append("/weight").append(g.PROB[m])
                  .append("/density").append(g.DENSITY[m]).append("/rooms").append(eligible).append("/budget").append(budget);
            }
          }
          population.populate(zone, null);
          System.out.println("[CONTAINER_AUDIT] seed=" + seed + " level=" + zone.levelId()
              + " name=" + zone.level.LevelName
              + " preset=" + preset + " random=" + counts(zone, before, zone.getNativeObjects().size)
              + " inputs=" + inputs + " rooms=" + rooms);
          assertEquals(0, population.populate(zone, null));
        }
      }
    }
  }

  private static String counts(Map.Zone zone, int start, int end) {
    java.util.Map<Integer, Integer> counts = new java.util.TreeMap<>();
    for (int i = start; i < end; i++) {
      Map.NativeObject o = zone.getNativeObjects().get(i);
      int id = o.ds1Raw ? Riiablo.files.obj.getObjectId(zone.level.Act + 1, o.presetIndex) : o.presetIndex;
      if (id == 7 || id == 11 || id == 4 || id == 9 || id == 52 || id == 94 || id == 95)
        counts.merge(id, 1, Integer::sum);
    }
    return counts.toString();
  }
}
