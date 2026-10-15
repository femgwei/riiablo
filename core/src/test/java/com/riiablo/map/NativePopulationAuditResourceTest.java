package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.ObjGroup;
import com.riiablo.codec.excel.Excel;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Counts population after installing real terrain; preset export counts alone miss it. */
class NativePopulationAuditResourceTest extends RiiabloTest {
  @Test void populationOnRealTerrain() {
    ObjGroup groups = Excel.load(ObjGroup.class, Riiablo.mpqs.resolve("data\\global\\excel\\ObjGroup.txt"));
    for (int index = 0; index < Riiablo.files.MonPreset.getSize(5); index++) {
      String place = Riiablo.files.MonPreset.getPlace(5, index);
      if (place.startsWith("place_")) System.out.println("[A5_PLACEMENT] index=" + index + " place=" + place);
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
        NativeObjectPopulation objects = new NativeObjectPopulation();
        for (Map.Zone zone : map.getZones()) {
          int id = zone.levelId();
          if (!java.util.Arrays.stream(new int[]{2,3,8,12,37,41,56,66,74,83,104,108,110,120,128,131})
              .anyMatch(value -> value == id)) continue;
          TileGrid grid = zone.nativeTileGrid();
          assertNotNull(grid, "native terrain level=" + id);
          zone.dt1s = new DT1s();
          for (int index = 1; index <= 255; index++) {
            String source = grid.sourceFile((byte) index);
            if (source != null) zone.dt1s.add(DT1.loadFromFile(Riiablo.mpqs.resolve(source)), source);
          }
          Act1MapBuilderD2MOD.applyExportedTileGrid(zone, grid);
          int eligible = 0, zeroBudgets = 0, budgets = 0;
          long area = 0;
          for (Map.RoomEx room : zone.getRoomsEx()) {
            area += (long) room.width * room.height;
            if ((room.populationFlags() & (0x00800000 | 0x00010000 | 0x00020000)) != 0
                || room.populationOutdoorFlag80() != 0) continue;
            eligible++;
            for (int groupId : zone.level.ObjGrp) {
              if (groupId == 0) continue;
              ObjGroup.Entry group = groups.get(groupId);
              for (int member = 0; member < 8 && group.ID[member] != 0; member++) {
                budgets++;
                if (NativeObjectPopulation.densityCount(group.DENSITY[member], room.width, room.height) == 0) zeroBudgets++;
              }
            }
          }
          int presetObjects = zone.getNativeObjects().size;
          for (Map.NativeObject object : zone.getNativeObjects()) {
            if (object.externalEntity) continue;
            int objectId = object.ds1Raw ? Riiablo.files.obj.getObjectId(act + 1, object.presetIndex) : object.presetIndex;
            com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(objectId);
            if (base != null && ((base.SubClass & 8) != 0 || base.PopulateFn == 1 || base.PopulateFn == 4 || base.PopulateFn == 5))
              System.out.println("[SUPPRESSED_CONTAINER] level=" + id + " id=" + objectId + " source=" + object.sourceFile);
          }
          int presetMonsters = 0;
          for (Map.RoomEx room : zone.getRoomsEx()) presetMonsters += room.getPendingMonsterSpawns().size;
          int populated = objects.populate(zone, null);
          int pending = 0;
          if (act > 0) { zone.generator.init(zone); zone.generator.finish(zone); }
          for (Map.RoomEx room : zone.getRoomsEx()) pending += room.getPendingMonsterSpawns().size;
          int groupMarkers = 0;
          for (Map.NativeMonster monster : zone.getNativeMonsters()) if (monster.populationGroupSize > 0) groupMarkers++;
          if (groupMarkers > 0) assertTrue(pending > presetMonsters,
              "MonPlace group markers must become real pending spawns level=" + id);
          System.out.println("[POPULATION_AUDIT] seed=" + seed + " level=" + id
              + " rooms=" + zone.getRoomsEx().size + " eligible=" + eligible + " roomArea=" + area
              + " zeroBudgets=" + zeroBudgets + "/" + budgets + " presets=" + presetObjects
              + " randomObjects=" + populated + " monsters=" + pending
              + " presetMonsters=" + presetMonsters + " groupMarkers=" + groupMarkers + " MonDen=" + zone.level.MonDen[0]);
          assertEquals(0, objects.populate(zone, null), "room population is consumed once");
        }
      }
    }
  }
}
