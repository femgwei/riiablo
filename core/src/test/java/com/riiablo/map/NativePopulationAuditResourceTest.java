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
      int[] totalPresetChests = new int[5], totalRandomChests = new int[5], levels = new int[5];
      double[] totalExpected = new double[5], totalTerrainExpected = new double[5];
      for (int act = 0; act < 5; act++) {
        Map map = new Map(seed, 0);
        if (act == 0) map.act1Builder().generate(map, seed, 0);
        if (act == 1) map.act2Builder().generate(map, seed, 0);
        if (act == 2) map.act3Builder().generate(map, seed, 0);
        if (act == 3) map.act4Builder().generate(map, seed, 0);
        if (act == 4) map.act5Builder().generate(map, seed, 0);
        NativeObjectPopulation objects = new NativeObjectPopulation();
        for (Map.Zone zone : map.getZones()) {
          int id = zone.levelId();
          if (zone.isTown()) continue;
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
          java.util.Map<Integer, Integer> presetChests = chestCounts(zone, 0);
          double expectedChests = 0, terrainExpected = 0;
          int[] blockedPrefix = blockedPrefix(zone);
          java.util.Map<String, Double> acceptance = new java.util.HashMap<>();
          for (Map.RoomEx room : zone.getRoomsEx()) {
            if ((room.populationFlags() & (0x00800000 | 0x00010000 | 0x00020000)) != 0
                || room.populationOutdoorFlag80() != 0) continue;
            for (int slot = 0; slot < zone.level.ObjGrp.length; slot++) {
              int groupId = zone.level.ObjGrp[slot];
              if (groupId == 0) continue;
              ObjGroup.Entry group = groups.get(groupId);
              for (int member = 0; member < 8 && group.ID[member] != 0; member++) {
                com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(group.ID[member]);
                if (base.PopulateFn == 3 && isChest(base)) {
                  double budgetMean = Math.min(100, zone.level.ObjPrb[slot] + 1) / 100.0
                      * group.PROB[member] / 100.0
                      * NativeObjectPopulation.densityCount(group.DENSITY[member], room.width, room.height);
                  expectedChests += budgetMean;
                  String key = room.id + ":" + base.SizeX + ":" + base.SizeY;
                  double candidateChance = acceptance.computeIfAbsent(key,
                      ignored -> terrainAcceptance(zone, room, base, blockedPrefix));
                  // Independent-candidate estimate with five attempts and static
                  // terrain only, not an original-game population measurement.
                  // Themes and existing/new unit footprints are omitted.
                  terrainExpected += budgetMean * (1 - Math.pow(1 - candidateChance, 5));
                }
              }
            }
          }
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
          java.util.Map<Integer, Integer> randomChests = chestCounts(zone, presetObjects);
          int actualAct = zone.level.Act;
          levels[actualAct]++;
          totalPresetChests[actualAct] += presetChests.values().stream().mapToInt(Integer::intValue).sum();
          totalRandomChests[actualAct] += randomChests.values().stream().mapToInt(Integer::intValue).sum();
          totalExpected[actualAct] += expectedChests;
          totalTerrainExpected[actualAct] += terrainExpected;
          System.out.println("[CHEST_AUDIT] seed=" + seed + " act=" + (actualAct + 1) + " level=" + id
              + " preset=" + presetChests + " random=" + randomChests
              + " expectedFn3BeforeCollision=" + expectedChests
              + " terrainOnlyFn3Mean=" + terrainExpected
              + " groups=" + java.util.Arrays.toString(zone.level.ObjGrp)
              + " probabilities=" + java.util.Arrays.toString(zone.level.ObjPrb));
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
      for (int act = 0; act < 5; act++)
        System.out.println("[ACT_CHEST_AUDIT] seed=" + seed + " act=" + (act + 1) + " levels=" + levels[act]
            + " preset=" + totalPresetChests[act] + " random=" + totalRandomChests[act]
            + " expectedFn3BeforeCollision=" + totalExpected[act]
            + " terrainOnlyFn3Mean=" + totalTerrainExpected[act]);
    }
  }

  private static int[] blockedPrefix(Map.Zone zone) {
    int stride = zone.width + 1;
    int[] prefix = new int[stride * (zone.height + 1)];
    for (int y = 0; y < zone.height; y++) {
      int rowCount = 0;
      for (int x = 0; x < zone.width; x++) {
        // PLACEMENT's static low-byte bits: WALL | PRESET.
        if ((zone.staticFlags(x, y) & 0x11) != 0) rowCount++;
        prefix[(y + 1) * stride + x + 1] = prefix[y * stride + x + 1] + rowCount;
      }
    }
    return prefix;
  }

  private static double terrainAcceptance(Map.Zone zone, Map.RoomEx room,
      com.riiablo.codec.excel.Objects.Entry base, int[] prefix) {
    int maxX = room.width - base.SizeX - 1, maxY = room.height - base.SizeY - 1;
    if (maxX <= 0 || maxY <= 0) return 0;
    int width = base.SizeX + 6, height = base.SizeY + 6;
    int stride = zone.width + 1, accepted = 0;
    for (int y = 1; y < maxY; y++) for (int x = 1; x < maxX; x++) {
      int left = room.x + x - zone.x() - width / 2;
      int bottom = room.y + y - zone.y() - height / 2;
      int right = left + width, top = bottom + height;
      if (left < 0 || bottom < 0 || right > zone.width || top > zone.height) continue;
      int blocked = prefix[top * stride + right] - prefix[bottom * stride + right]
          - prefix[top * stride + left] + prefix[bottom * stride + left];
      if (blocked == 0) accepted++;
    }
    return accepted / ((double) maxX * maxY);
  }

  private static boolean isChest(com.riiablo.codec.excel.Objects.Entry base) {
    // Ordinary chest artwork/name, excluding urns, baskets and loose rocks
    // which also carry OBJSUBCLASS_CHEST in the original table.
    return base != null && base.InitFn == 3 && base.Name != null
        && base.Name.toLowerCase(java.util.Locale.ROOT).contains("chest");
  }

  private static java.util.Map<Integer, Integer> chestCounts(Map.Zone zone, int start) {
    java.util.Map<Integer, Integer> counts = new java.util.TreeMap<>();
    for (int index = start; index < zone.getNativeObjects().size; index++) {
      Map.NativeObject object = zone.getNativeObjects().get(index);
      int objectId = object.ds1Raw
          ? Riiablo.files.obj.getObjectId(zone.level.Act + 1, object.presetIndex) : object.presetIndex;
      NativePresetObjectResolver.Resolution resolution = NativePresetObjectResolver.resolve(
          zone.level.Act + 1, zone.levelId(), objectId, zone.map.seed(), object.x, object.y);
      if (object.externalEntity && !object.spawned && resolution.shouldCreate()
          && resolution.kind != NativePresetObjectResolver.Kind.SPECIAL_CHEST
          && isChest(Riiablo.files.objects.get(resolution.classId)))
        counts.merge(resolution.classId, 1, Integer::sum);
    }
    return counts;
  }
}
