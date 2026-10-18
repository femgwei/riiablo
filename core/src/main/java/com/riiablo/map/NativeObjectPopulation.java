package com.riiablo.map;

import com.badlogic.gdx.utils.Array;
import com.riiablo.codec.excel.Excel;
import com.riiablo.codec.excel.ObjGroup;
import com.riiablo.codec.excel.Objects;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;

/** D2Game Objects.cpp object groups and placement; separate from DS1 presets. */
final class NativeObjectPopulation {
  private ObjGroup groups;
  private final java.util.IdentityHashMap<Map.Zone, Region> regions = new java.util.IdentityHashMap<>();
  private final java.util.IdentityHashMap<Map, D2Seed> controls = new java.util.IdentityHashMap<>();
  private static final int[] SX = {-1,0,1,-1,1,-1,0,1};
  private static final int[] SY = {-1,-1,-1,0,0,1,1,1};

  int populate(Map.Zone zone, Map.RoomEx onlyRoom) {
    if (zone.isTown() || !zone.hasNativeRoomTopology()) return 0;
    if (groups == null) groups = Excel.load(ObjGroup.class,
        zone.map.mpqs().resolve("data\\global\\excel\\ObjGroup.txt"));
    Region region = regions.get(zone);
    if (region == null) {
      D2Seed control = controls.get(zone.map);
      if (control == null) {
        D2Seed game = new D2Seed(zone.map.seed(), 666);
        control = new D2Seed((int) Seed.rollRandomNumber(game), 666);
        controls.put(zone.map, control);
      }
      region = new Region(control, countPopulatedRooms(zone));
      regions.put(zone, region);
    }
    int before = zone.getNativeObjects().size;
    for (int i = 0; i < zone.getRoomsEx().size; i++) {
      Map.RoomEx room = zone.getRoomsEx().get(i);
      if (onlyRoom != null && room != onlyRoom || !room.claimObjectPopulation()) continue;
      // D2Game::PopulateRoom skips waypoint rooms and rooms marked
      // POPULATION_ZERO; all other native RoomEx rooms run ObjGroup rolls.
      if ((room.populationFlags() & (0x00800000 | 0x00010000 | 0x00020000)) != 0
          || room.populationOutdoorFlag80() != 0) continue;
      // D2Game runs the level theme handler before the ObjGrp rolls.  The
      // theme barrel pass is easy to miss because it is not represented by
      // an ObjGroup row; outdoor levels with a barrel theme otherwise lose
      // up to 32 containers per room.
      region.field4++;
      // D2Game returns as soon as a theme handler creates anything; the
      // ObjGroup dispatcher is not run a second time for that room.
      if (populateTheme(zone, room, region)) continue;
      D2Seed roomSeed = room.runtimeSeed();
      for (int slot = 0; zone.level.ObjGrp != null && slot < zone.level.ObjGrp.length; slot++) {
        int groupId = zone.level.ObjGrp[slot];
        int probability = next(roomSeed, 100);
        if (groupId == 0 || probability > zone.level.ObjPrb[slot]) continue;
        ObjGroup.Entry group = groups.get(groupId);
        if (group == null) throw new IllegalStateException("Missing ObjGroup " + groupId);
        int roll = next(roomSeed, 100), cumulative = 0;
        for (int member = 0; member < 8 && group.ID[member] != 0; member++) {
          cumulative += group.PROB[member];
          Objects.Entry base = zone.map.files().objects.get(group.ID[member]);
          if (base == null) throw new IllegalStateException("Missing Objects row " + group.ID[member]);
          // The legacy table projects Gore to boolean; both 0 and 1 pass
          // D2Game's nGore <= 2 check. Do not reject all corpse objects.
          if (roll >= cumulative) continue;
          String source = "ObjGroup/" + groupId + "/slot/" + slot + "/member/" + member;
          switch (base.PopulateFn) {
            case 0: break;
            case 1: clusteredContainers(zone, room, base, group.DENSITY[member], region.seed, source); break;
            case 2: shrine(zone, room, base, roomSeed, region, source); break;
            case 3: case 6: case 9:
              if (base.PopulateFn == 9) next(roomSeed, 100);
              common(zone, room, base, group.DENSITY[member], region.seed, source, base.PopulateFn); break;
            case 4: barrels(zone, room, group.DENSITY[member], region.seed, source); break;
            case 5: crates(zone, room, base, group.DENSITY[member], region.seed, source); break;
            case 7: rogues(zone, room, base, region.seed, source); break;
            case 8: well(zone, room, base, roomSeed, region, source); break;
            default: throw new IllegalStateException("Unsupported PopulateFn " + base.PopulateFn);
          }
          break;
        }
      }
    }
    return zone.getNativeObjects().size - before;
  }

  private static int countPopulatedRooms(Map.Zone zone) {
    int count = 0;
    for (Map.RoomEx room : zone.getRoomsEx()) {
      if ((room.populationFlags() & (0x00800000 | 0x00010000 | 0x00020000)) == 0
          && room.populationOutdoorFlag80() == 0) count++;
    }
    return count;
  }

  private static boolean populateTheme(Map.Zone zone, Map.RoomEx room, Region region) {
    int themes = zone.level.Themes;
    if (themes == 0 || region.populatedRooms <= 0) return false;
    int probability = 12;
    if (region.field4 > region.populatedRooms / 2) probability += 5;
    if (region.field4 > region.populatedRooms - region.populatedRooms / 4) probability += 5;
    if (next(region.seed, 100) >= probability) return false;
    int[] indices = new int[7];
    int count = 0;
    for (int i = 0; i < 7; i++) if ((themes & (1 << i)) != 0) indices[count++] = i + 1;
    if (count == 0) return false;
    int selected = indices[next(region.seed, count)];
    // This mirrors the native table's active entries.  Theme 2 is the
    // populated barrel grid; the remaining handlers either do nothing or
    // place equipment that is outside this object population audit.
    // The native table is indexed by the one-based theme id.  Comparing the
    // id with the number of enabled themes made theme 2 unreachable whenever
    // it was the only enabled theme (the common outdoor barrel setup).
    return selected == 2 && themeBarrels(zone, room, region.seed) > 0;
  }

  private static int themeBarrels(Map.Zone zone, Map.RoomEx room, D2Seed seed) {
    Objects.Entry barrel = zone.map.files().objects.get(7);
    if (barrel == null) return 0;
    int created = 0;
    int xEnd = room.x + room.width + 1;
    int yEnd = room.y + room.height + 1;
    for (int x = room.x; x < xEnd && created < 32; x += 2) {
      for (int y = room.y; y < yEnd && created < 32; y += 2) {
        int classId = (next(seed, 4) == 0) ? 11 : 7;
        if (room.width < 2 || room.height < 2 || x <= room.x + 2 || y <= room.y + 2
            || x >= room.x + room.width - barrel.SizeX
            || y >= room.y + room.height - barrel.SizeY
            || !clear(zone, x, y, barrel.SizeX + 2, barrel.SizeY + 2)) continue;
        add(zone, room, classId, x, y, "Theme/2/barrels");
        created++;
      }
    }
    return created;
  }

  static int densityCount(int density, int width, int height) {
    return density * (width * height >> 7) >> 8;
  }
  private static int next(D2Seed seed, int maximum) {
    return Seed.rollLimitedRandomNumber(seed, maximum);
  }
  private static boolean bounds(Map.RoomEx room, int x, int y, int sizeX, int sizeY, int margin) {
    return room.width >= sizeX + 2 && room.height >= sizeY + 2
        && x > room.x + margin && y > room.y + margin
        && x < room.x + room.width - sizeX && y < room.y + room.height - sizeY;
  }
  /** Centered collision rectangle; reserve pending object footprints as native allocation does. */
  static boolean clear(Map.Zone zone, int x, int y, int width, int height) {
    return clear(zone, x, y, width, height,
        com.d2moo.common.collision.D2Collision.COLLIDE_MASK_PLACEMENT);
  }
  private static boolean clear(Map.Zone zone, int x, int y, int width, int height, int mask) {
    int minX = x - width / 2, minY = y - height / 2;
    for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) {
      int wx = minX + dx, wy = minY + dy;
      if (zone.nativeCollisionZone(wx, wy) == null
          || (zone.nativeCollisionFlagsWorld(wx, wy) & mask) != 0) return false;
    }
    for (Map.NativeObject other : zone.getNativeObjects()) {
      // A materialized object's current bits are already in the shared grid.
      // Re-projecting its owner here would resurrect cleared overlapping bits.
      if (other.entityId >= 0) continue;
      int classId = other.resolvedObjectId;
      if (classId < 0) classId = other.ds1Raw
          ? zone.map.files().obj.getObjectId(zone.level.Act + 1, other.presetIndex) : other.presetIndex;
      if (classId < 0) continue;
      Objects.Entry base = zone.map.files().objects.get(classId);
      if (base == null || !base.HasCollision[other.currentMode()]) continue;
      int objectMask = com.d2moo.common.collision.D2Collision.objectMask(
          base.IsDoor, base.BlocksVis, base.BlockMissile, base.SubClass);
      if ((objectMask & mask) == 0) continue;
      int ox = Math.round(zone.x() + other.x - base.SizeX / 2f);
      int oy = Math.round(zone.y() + other.y - base.SizeY / 2f);
      if (minX < ox + base.SizeX && minX + width > ox && minY < oy + base.SizeY && minY + height > oy) return false;
    }
    return true;
  }
  /** Objects.cpp Fn1 tests walls/objects/doors around the container, PRESET only underneath. */
  private static boolean containerClear(Map.Zone z, Map.RoomEx r, Objects.Entry b, int x, int y) {
    return bounds(r, x, y, b.SizeX, b.SizeY, 1)
        && x < r.x + r.width - b.SizeX - 2 && y < r.y + r.height - b.SizeY - 2
        && clear(z, x, y, b.SizeX + 7, b.SizeY + 7, 0xC01)
        && clear(z, x, y, b.SizeX, b.SizeY);
  }
  private static void add(Map.Zone zone, Map.RoomEx room, int classId, int x, int y, String source) {
    zone.getNativeObjects().add(new Map.NativeObject(room.id, classId, 0,
        x - zone.x(), y - zone.y(), false, false, source, true));
  }
  private static void common(Map.Zone z, Map.RoomEx r, Objects.Entry base, int density,
      D2Seed seed, String source, int function) {
    if (function != 9) next(seed, 100); // Fn9 rolls the room seed in the dispatcher.
    int count = densityCount(density, r.width, r.height);
    Map.NativeObject last = null;
    for (int n = 0; n < count; n++) for (int attempt = 0; attempt < 5; attempt++) {
      int x = r.x + next(seed, r.width - base.SizeX - 1);
      int y = r.y + next(seed, r.height - base.SizeY - 1);
      if (bounds(r, x, y, 1, 1, 0) && clear(z, x, y, base.SizeX + 6, base.SizeY + 6)) {
        add(z, r, base.Id, x, y, source);
        last = z.getNativeObjects().peek();
        if (function == 9) return;
        break;
      }
    }
    // PopulateFn6 adds flies only at the final successfully-created corpse.
    if (function == 6 && last != null && next(seed, 100) > 70)
      add(z, r, 103, z.x() + last.x, z.y() + last.y, source + "/flies");
  }
  private static boolean clusterClear(Map.Zone z, Map.RoomEx r, Objects.Entry b, int x, int y) {
    return bounds(r, x, y, b.SizeX, b.SizeY, 2) && clear(z, x, y, b.SizeX + 2, b.SizeY + 2);
  }
  private static void barrels(Map.Zone z, Map.RoomEx r, int density, D2Seed seed, String source) {
    Objects.Entry b = z.map.files().objects.get(7);
    next(seed, 100);
    int remaining = densityCount(density, r.width, r.height), attempts = 2 * remaining, count = 0;
    while (remaining > 0 && attempts-- > 0) {
      int classId = next(seed, 3) == 0 ? 11 : 7;
      int x = r.x + next(seed, r.width), y = r.y + next(seed, r.height);
      if (!clusterClear(z, r, b, x, y)) continue;
      add(z, r, classId, x, y, source);
      if (++count >= 8) return;
      int chain = 1;
      boolean spawned = true;
      while (next(seed, chain / 2) == 0 && spawned) {
        spawned = false;
        for (int attempt = 0; attempt < 15 && !spawned; attempt++) {
          int dir = next(seed, 8);
          x += b.Xspace * SX[dir]; y += b.Yspace * SY[dir];
          spawned = clusterClear(z, r, b, x, y);
        }
        if (spawned) {
          add(z, r, next(seed, 4) == 0 ? 11 : 7, x, y, source);
          chain++;
          if (++count >= 8) return;
        }
      }
      remaining--;
    }
  }
  private static final int[] URNS = {4,9,52,94,95};
  private static void crates(Map.Zone z, Map.RoomEx r, Objects.Entry b, int density, D2Seed seed, String source) {
    next(seed, 100);
    int remaining = densityCount(density, r.width, r.height), attempts = 2 * remaining;
    int search = 4 * remaining, count = 0;
    while (remaining > 0 && attempts-- > 0) {
      int classId = b.Id == 46 ? 46 : URNS[next(seed, URNS.length)];
      int x = r.x + next(seed, r.width), y = r.y + next(seed, r.height);
      if (!clusterClear(z, r, b, x, y)) continue;
      if (classId != 46) classId = URNS[next(seed, URNS.length)];
      add(z, r, classId, x, y, source);
      boolean spawned = true;
      int chain = 1;
      while (next(seed, chain / 2) == 0 || spawned) {
        spawned = false;
        for (int attempt = 0; attempt < Math.max(4, search) && !spawned; attempt++) {
          int dir = next(seed, 8);
          x += b.Xspace * SX[dir]; y += b.Yspace * SY[dir];
          spawned = clusterClear(z, r, b, x, y);
        }
        if (spawned) {
          add(z, r, classId, x, y, source);
          chain++;
          if (++count >= 8) return;
        } else break;
      }
      remaining--;
    }
  }
  private static void clusteredContainers(Map.Zone z, Map.RoomEx r, Objects.Entry b, int density,
      D2Seed seed, String source) {
    // Retail Fn1 rolls its chance before class dispatch, even for expansion
    // jar rows which it does not populate. Skipping this roll shifts the
    // shared object-control seed and changes subsequent rooms' population.
    next(seed, 100);
    int[] classes;
    int tries = 12, step = 1, stepRange = 0;
    boolean small = false;
    switch (b.Id) {
      case 3: classes = new int[]{3,28}; tries = 18; step = 5; stepRange = 5; break;
      case 79: case 1: classes = new int[]{79,53,1}; step = 5; stepRange = 5; break;
      case 4: classes = URNS; small = true; break;
      case 89: classes = new int[]{89,284}; step = 5; stepRange = 5; break;
      case 208: case 209: classes = new int[]{208,209}; small = true; break;
      default: return;
    }
    int remaining = densityCount(density, r.width, r.height);
    while (remaining > 0 && tries-- > 0) {
      int classId = classes[next(seed, classes.length)];
      int x = r.x + next(seed, r.width - b.SizeX - 1);
      int y = r.y + next(seed, r.height - b.SizeY - 1);
      if (!containerClear(z, r, b, x, y)) continue;
      add(z, r, classId, x, y, source);
      int chain = 1;
      boolean spawned = true;
      while (next(seed, chain >> 1) == 0 && spawned) {
        spawned = false;
        for (int attempt = 0; attempt < 3 * Math.max(remaining, 4) && !spawned; attempt++) {
          int dir = next(seed, 8);
          x += 2 * (step + next(seed, stepRange)) * SX[dir];
          y += 2 * (step + next(seed, stepRange)) * SY[dir];
          spawned = small ? clusterClear(z, r, b, x, y)
              : containerClear(z, r, b, x, y);
        }
        if (spawned) { add(z, r, classes[next(seed, classes.length)], x, y, source); chain++; }
      }
      remaining--;
    }
  }
  private static void shrine(Map.Zone z, Map.RoomEx r, Objects.Entry b, D2Seed seed, Region region, String source) {
    next(seed, 100);
    if (region.shrines.size >= 10 || region.shrines.size > z.getRoomsEx().size / 8) return;
    for (int attempt = 0; attempt < 15; attempt++) {
      int x, y;
      if (b.Orientation == 1) {
        x = r.x + r.width / 4 + next(seed, r.width / 2); y = r.y + 1;
      } else if (b.Orientation == 2) {
        x = r.x + 1; y = r.y + r.height / 4 + next(seed, r.height / 2);
      } else {
        x = r.x + next(seed, r.width - b.SizeX - 1); y = r.y + next(seed, r.height - b.SizeY - 1);
      }
      if (bounds(r, x, y, 0, 0, 0) && clear(z, x, y, b.SizeX + 6, b.SizeY + 6)
          && separated(region.shrines, x, y, 50)) {
        add(z, r, b.Id, x, y, source); region.shrines.add(new int[]{x,y}); return;
      }
    }
  }
  private static void well(Map.Zone z, Map.RoomEx r, Objects.Entry b, D2Seed seed, Region region, String source) {
    if (region.wells.size >= 4 || region.wells.size > z.getRoomsEx().size / 8) return;
    next(seed, 100);
    int width = 2 * b.SizeX + 1, height = 2 * b.SizeY + 1;
    for (int attempt = 0; attempt < 5; attempt++) {
      int x = r.x + next(seed, r.width - width - 1), y = r.y + next(seed, r.height - height - 1);
      if (bounds(r, x, y, 0, 0, 0) && clear(z, x, y, width + 6, height + 6)
          && separated(region.wells, x, y, 100)) {
        add(z, r, b.Id, x, y, source); region.wells.add(new int[]{x,y}); return;
      }
    }
  }
  private static boolean separated(Array<int[]> points, int x, int y, int spacing) {
    for (int[] p : points) if (Math.abs(p[0] - x) < spacing || Math.abs(p[1] - y) < spacing) return false;
    return true;
  }
  private static void rogues(Map.Zone z, Map.RoomEx r, Objects.Entry b, D2Seed seed, String source) {
    int[][][] patterns = {{{-4,0},{0,0},{4,0},{8,0},{0,4},{0,-4}},
        {{-8,0},{-4,0},{0,0},{4,0},{8,0}}, {{0,-8},{0,-4},{0,0},{0,4},{0,8}},
        {{-7,-5},{-5,-3},{-3,-1},{0,0},{3,-1},{5,-3},{7,-5}}};
    next(seed, 100);
    int[][] pattern = patterns[next(seed, 4)];
    for (int attempt = 0; attempt < 8; attempt++) {
      int x = r.x + next(seed, r.width - b.SizeX - 1), y = r.y + next(seed, r.height - b.SizeY - 1);
      if (!bounds(r, x, y, 7, 7, 1) || !clear(z, x, y, 12, 12)) continue;
      for (int[] offset : pattern) {
        int px = x + offset[0], py = y + offset[1];
        if (bounds(r, px, py, b.SizeX, b.SizeY, 1) && clear(z, px, py, b.SizeX + 7, b.SizeY + 7)) {
          add(z, r, 57 + next(seed, 2), px, py, source);
          if (next(seed, 100) > 70) add(z, r, 103, px, py, source + "/flies");
        }
      }
      return;
    }
  }
  private static final class Region {
    final D2Seed seed;
    int field4;
    int populatedRooms;
    final Array<int[]> shrines = new Array<>(), wells = new Array<>();
    Region(D2Seed control, int populatedRooms) {
      seed = control;
      this.populatedRooms = populatedRooms;
    }
  }
}
