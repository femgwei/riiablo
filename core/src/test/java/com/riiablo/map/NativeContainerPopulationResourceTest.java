package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Excel;
import com.riiablo.codec.excel.ObjGroup;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.LvlSub;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Per-class containers, including outdoor preset and substitution sources. */
class NativeContainerPopulationResourceTest extends RiiabloTest {
  private void auditDropContainerSourcesInSubstitutionTemplates() {
    LvlSub subs = Excel.load(LvlSub.class,
        Riiablo.mpqs.resolve("data\\global\\excel\\LvlSub.txt"));
    int files = 0, units = 0, containers = 0;
    for (LvlSub.Entry sub : subs) {
      if (sub.File == null || sub.File.isEmpty()) continue;
      String path = sub.File.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
      int act = path.startsWith("act1/") ? 1 : path.startsWith("act2/") ? 2
          : path.startsWith("act3/") ? 3 : path.startsWith("act4/") ? 4 : 5;
      DS1 file = DS1.loadFromFile(Riiablo.mpqs.resolve("data\\global\\tiles\\" + sub.File));
      files++;
      for (DS1.Object object : file.objects) {
        if (object == null || object.type != DS1.Object.STATIC_TYPE) continue;
        units++;
        int id = Riiablo.files.obj.getObjectId(act, object.id);
        if (isContainer(id)) {
          containers++;
          System.out.println("[LVLSUB_CONTAINER_TEMPLATE] file="
              + sub.File + " act=" + act + " index=" + object.id + " class=" + id
              + " flags=" + object.flags);
        }
      }
    }
    assertTrue(files > 0, "real LvlSub source files must be audited");
    System.out.println("[LVLSUB_SOURCE_AUDIT] files=" + files + " staticUnits=" + units
        + " selectableDropContainers=" + containers);
  }

  @Test void containersOnRealTerrainAcrossAllActs() {
    // Population needs real DT1 headers/flags, not pixel buffers for every
    // tile in every level. Keep the process-wide switch scoped to this test.
    boolean loadData = DT1.loadData;
    DT1.loadData = false;
    try {
      auditContainers();
    } finally {
      DT1.loadData = loadData;
    }
  }

  private void auditContainers() {
    auditDropContainerSourcesInSubstitutionTemplates();
    ObjGroup groups = Excel.load(ObjGroup.class, Riiablo.mpqs.resolve("data\\global\\excel\\ObjGroup.txt"));
    for (int id : new int[]{4,7,9,11,52,94,95,155,174,175,418,443,444,445,467,468,469,470,471,518}) {
      Objects.Entry b = Riiablo.files.objects.get(id);
      System.out.println("[CONTAINER_TABLE] class=" + id + " fn=" + b.PopulateFn
          + " size=" + b.SizeX + "," + b.SizeY + " spacing=" + b.Xspace + "," + b.Yspace);
    }
    for (String seedText : System.getProperty("map.audit.seeds", "1").split(",")) {
      int seed = Integer.parseInt(seedText.trim());
      for (int act = 0; act < 5; act++) {
        Map map = new Map(seed, 0);
        if (act == 0) Act1MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 1) Act2MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 2) Act3MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 3) Act4MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
        if (act == 4) Act5MapBuilderD2MOD.INSTANCE.generate(map, seed, 0);
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
          for (int i = 0; i < before; i++) {
            Map.NativeObject object = zone.getNativeObjects().get(i);
            int id = object.ds1Raw ? Riiablo.files.obj.getObjectId(act + 1, object.presetIndex) : object.presetIndex;
            if (isContainer(id)) assertTrue(object.externalEntity,
                "drop container suppressed: level=" + zone.levelId() + " class=" + id
                    + " source=" + object.sourceFile);
          }
          java.util.Map<String, Integer> rooms = new java.util.TreeMap<>();
          for (Map.RoomEx room : zone.getRoomsEx()) {
            if (zone.level.DrlgType == 1) assertTrue(room.width <= 60 && room.height <= 60,
                "large maze presets must be partitioned before population: level=" + zone.levelId());
            if ((zone.levelId() >= 55 && zone.levelId() <= 61
                || zone.levelId() >= 66 && zone.levelId() <= 72))
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
              if (!isContainer(b.Id)) continue;
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
              + " act=" + (act + 1) + " drlg=" + zone.level.DrlgType
              + " roomCount=" + zone.getRoomsEx().size
              + " preset=" + preset + " random=" + counts(zone, before, zone.getNativeObjects().size)
              + " sources=" + sources(zone, before)
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
      if (isContainer(id))
        counts.merge(id, 1, Integer::sum);
    }
    return counts.toString();
  }

  private static boolean isContainer(int id) {
    Objects.Entry b = Riiablo.files.objects.get(id);
    return b != null && b.Selectable[0] && ((b.SubClass & 8) != 0 || b.OperateFn == 3
        || b.OperateFn == 5 || b.OperateFn == 7 || b.OperateFn == 14);
  }

  private static String sources(Map.Zone zone, int end) {
    java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
    for (int i = 0; i < end; i++) {
      Map.NativeObject o = zone.getNativeObjects().get(i);
      int id = o.ds1Raw ? Riiablo.files.obj.getObjectId(zone.level.Act + 1, o.presetIndex) : o.presetIndex;
      if (isContainer(id)) counts.merge(id + "/" + o.sourceFile + "/external=" + o.externalEntity
          + "/spawned=" + o.spawned, 1, Integer::sum);
    }
    return counts.toString();
  }
}
