package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2C_Acts;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.D2DrlgTypes;
import com.d2moo.common.drlg.D2UnitTypes;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.map.Map;
import com.riiablo.map.Map.Zone;
import com.riiablo.map.Act1MapBuilderD2MOD;
import com.riiablo.drlg.TileGrid;

import java.util.IdentityHashMap;

/** Minimal native bridge for Arcane Sanctuary RoomEx and preset metadata. */
public final class Act2ArcaneD2MOOLayoutBridge {
  private static final String TAG = "Act2ArcaneD2MOOLayoutBridge";
  private static final int LEVEL_ARCANE = D2LevelIds.LEVEL_ARCANESANCTUARY;

  private Act2ArcaneD2MOOLayoutBridge() {}

  /**
   * Generates only Level 74 with D2MOO's maze code and projects its native
   * RoomEx rectangles/preset ids into an existing Zone.  Tile rendering keeps
   * the current Zone pipeline until the exported DS1 tile bridge is enabled.
   */
  public static boolean populateZone(int gameSeed, int diff, Zone zone) {
    if (zone == null || zone.levelId() != LEVEL_ARCANE || D2MOOLayoutContext.mpqs() == null) {
      return false;
    }
    D2DrlgStrc drlg = null;
    try {
      DataTbls.setLevelDefBinCache(
          Act1D2MOOLayoutBridge.buildLevelDefCache(diff, -1));
      DataTbls.setLevelTypesTxtCache(Act1D2MOOLayoutBridge.buildLevelTypesCache());
      D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
      DataTbls.loadLvlPrestTxt(archive, 0);
      DataTbls.loadLvlSubTxt(archive);
      DataTbls.loadLvlMazeTxt(archive);
      DataTbls.loadLvlWarpTxt(archive);

      D2DrlgAct act = new D2DrlgAct();
      act.setAct(D2C_Acts.ACT_II);
      act.setTownId(D2LevelIds.LEVEL_LUTGHOLEIN);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_II, archive, gameSeed,
          D2LevelIds.LEVEL_LUTGHOLEIN, 0, null, (byte) diff, null, null);
      if (drlg == null) return false;
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, LEVEL_ARCANE);
      if (level == null) return false;
      DrlgDrlg.initLevel(level);
      if (level.getFirstRoomEx() == null || level.getLevelCoords() == null) return false;

      projectRooms(level, zone);
      D2MooTileApplier applier = new D2MooTileApplier();
      TileGrid grid = new TileGrid(level.getLevelCoords().getNWidth(),
          level.getLevelCoords().getNHeight());
      applier.putGrid(LEVEL_ARCANE, grid);
      int exportedFloors = com.d2moo.common.drlg.DrlgExport.exportLevelTiles(
          drlg, LEVEL_ARCANE, applier);
      int dt1Mask = com.d2moo.common.drlg.DrlgExport.collectLevelDt1Mask(
          drlg, LEVEL_ARCANE);
      if (exportedFloors > 0) zone.setNativeTileGrid(grid, dt1Mask);
      com.riiablo.map.NativeMonsterSpawnExclusion.capture(level, zone);
      com.badlogic.gdx.utils.Array<Map.NativeMonster> exportedMonsters =
          new com.badlogic.gdx.utils.Array<>();
      int[] nativeWarpUnits = {0, 0};
      int[] objectCounts = {0, 0};
      int[] monsterCounts = {0, 0};
      int[] exportedVis = com.d2moo.common.drlg.DrlgDrlgRoom.getVisArrayFromLevelId(drlg, LEVEL_ARCANE);
      int[] exportedWarps = com.d2moo.common.drlg.DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, LEVEL_ARCANE);
      int presetUnits = com.d2moo.common.drlg.DrlgExport.exportLevelPresetUnits(
          drlg, LEVEL_ARCANE,
          (exportLevelId, roomId, unitType, index, mode, x, y, ds1Raw, spawned,
              externalEntity, sourceFile) -> {
            if (unitType == D2UnitTypes.UNIT_TILE) {
              int slot = NativeWarpSlotResolver.resolve(level, exportedVis, exportedWarps, roomId, index);
              if (slot >= 0) {
                zone.addNativeWarpMarker(slot, x, y);
                nativeWarpUnits[0]++;
              }
              nativeWarpUnits[1]++;
              return;
            }
            if (unitType == D2UnitTypes.UNIT_MONSTER) {
              monsterCounts[0]++;
              if (!externalEntity || x < 0 || y < 0
                  || x >= grid.width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                  || y >= grid.height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                if (x < 0 || y < 0
                    || x >= grid.width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                    || y >= grid.height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
                  monsterCounts[1]++;
                }
                return;
              }
              NativeMonsterResolution resolved = resolveNativeMonster(index, ds1Raw);
              exportedMonsters.add(new Map.NativeMonster(roomId, resolved.monsterId, mode,
                  x, y, resolved.superUniqueId, resolved.superUniqueKey, spawned));
              return;
            }
            if (unitType != D2UnitTypes.UNIT_OBJECT) return;
            objectCounts[0]++;
            if (x < 0 || y < 0
                || x >= grid.width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                || y >= grid.height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE) {
              objectCounts[1]++;
              return;
            }
            zone.getNativeObjects().add(new Map.NativeObject(roomId, index, mode, x, y,
                ds1Raw, spawned, sourceFile, externalEntity));
          });
      if (exportedFloors > 0) {
        queueNativeMonsters(zone, exportedMonsters);
        zone.getNativeMonsters().addAll(exportedMonsters);
      }
      if (Gdx.app != null) {
        Gdx.app.log(TAG, String.format(
            "Projected native Arcane RoomEx/tiles: seed=%d levelSeed=%d rooms=%d topology=%s floors=%d walls=%d shadows=%d dt1Mask=0x%X presetUnits=%d objects=%d/%d monsters=%d/%d unitTileWarp=%d/%d",
            gameSeed, zone.levelSeed(), zone.getRoomsEx().size, zone.hasNativeRoomTopology(),
            applier.getLastExportedFloorCount(), applier.getExportedWallCount(),
            applier.getExportedShadowCount(), dt1Mask, presetUnits, objectCounts[0],
            objectCounts[1], monsterCounts[0], monsterCounts[1], nativeWarpUnits[0],
            nativeWarpUnits[1]));
      }
      return !zone.getRoomsEx().isEmpty() && exportedFloors > 0;
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "Native Arcane generation unavailable; keeping compatibility Zone", t);
      return false;
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  public static boolean populateZone(D2MOOLayoutContext context, int gameSeed, int diff, Zone zone) {
    try (D2MOOLayoutContext.Scope ignored = context.activate()) {
      return populateZone(gameSeed, diff, zone);
    }
  }

  private static void projectRooms(D2DrlgLevel level, Zone zone) {
    int levelX = level.getLevelCoords().getNPosX();
    int levelY = level.getLevelCoords().getNPosY();
    zone.getRoomsEx().clear();
    IdentityHashMap<D2DrlgRoom, Integer> ids = new IdentityHashMap<>();
    java.util.ArrayList<D2DrlgRoom> nativeRooms = new java.util.ArrayList<>();
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      ids.put(room, nativeRooms.size());
      nativeRooms.add(room);
      int localTileX = room.getNTileXPos() - levelX;
      int localTileY = room.getNTileYPos() - levelY;
      Map.RoomEx projected = zone.addRoomEx(
          zone.x() + localTileX * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          zone.y() + localTileY * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          room.getNTileWidth() * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          room.getNTileHeight() * com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
      projected.setNativePopulationMetadata(room);
      Object maze = room.getMazeOrOutdoor();
      if (maze instanceof D2DrlgPresetRoomStrc) {
        D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) maze;
        projected.setPreset(preset.getNLevelPrest(), preset.getNPickedFile());
      }
    }
    for (int i = 0; i < nativeRooms.size(); i++) {
      D2DrlgRoom room = nativeRooms.get(i);
      D2DrlgRoom[] near = room.getPpRoomsNear();
      int count = near == null ? 0 : Math.min(room.getNRoomsNear(), near.length);
      com.badlogic.gdx.utils.IntArray adjacent = new com.badlogic.gdx.utils.IntArray(count);
      for (int j = 0; j < count; j++) {
        Integer id = near[j] == null ? null : ids.get(near[j]);
        if (id != null && id != i) adjacent.add(id);
      }
      zone.getRoomsEx().get(i).setAdjacentRoomIds(adjacent.toArray());
    }
  }

  static int queueNativeMonsters(Zone zone,
      com.badlogic.gdx.utils.Array<Map.NativeMonster> monsters) {
    if (zone == null || zone.isTown() || monsters == null) return 0;
    int queued = 0;
    for (Map.NativeMonster monster : monsters) {
      if (monster == null || monster.spawned || monster.monsterId < 0) continue;
      Map.RoomEx room = monster.roomId >= 0 && monster.roomId < zone.getRoomsEx().size
          ? zone.getRoomsEx().get(monster.roomId) : null;
      float worldX = zone.x() + monster.x;
      float worldY = zone.y() + monster.y;
      if (room == null || !room.contains(worldX, worldY)) room = zone.findRoomEx(worldX, worldY);
      if (room == null) continue;
      room.addMonsterSpawn(monster.monsterId, worldX, worldY, -1, false,
          monster.superUniqueId, monster.superUniqueKey);
      queued++;
    }
    return queued;
  }

  /** Resolves Act II MonPreset indices and encoded SuperUnique ids. */
  static NativeMonsterResolution resolveNativeMonster(int index, boolean ds1Raw) {
    if (D2MOOLayoutContext.files() == null || D2MOOLayoutContext.files().monstats == null) {
      return new NativeMonsterResolution(index, -1, null);
    }
    String placement = null;
    if (ds1Raw && D2MOOLayoutContext.files().MonPreset != null) {
      try {
        placement = D2MOOLayoutContext.files().MonPreset.getPlace(2, index);
      } catch (RuntimeException ignored) {}
    }
    MonStats.Entry monster = Act1MapBuilderD2MOD.resolveD2MooPresetMonster(index, placement);
    SuperUniques.Entry superUnique = placement == null || D2MOOLayoutContext.files().SuperUniques == null
        ? null : D2MOOLayoutContext.files().SuperUniques.get(placement);
    if (monster == null && superUnique != null) monster = D2MOOLayoutContext.files().monstats.get(superUnique.MonClass);
    if (monster == null && superUnique == null && D2MOOLayoutContext.files().SuperUniques != null) {
      int suIndex = index - D2MOOLayoutContext.files().monstats.size();
      superUnique = D2MOOLayoutContext.files().SuperUniques.get(suIndex);
      if (superUnique != null) monster = D2MOOLayoutContext.files().monstats.get(superUnique.MonClass);
    }
    return new NativeMonsterResolution(monster == null ? index : monster.hcIdx,
        superUnique == null ? -1 : superUnique.hcIdx,
        superUnique == null ? null : superUnique.Superunique);
  }

  static final class NativeMonsterResolution {
    final int monsterId;
    final int superUniqueId;
    final String superUniqueKey;
    NativeMonsterResolution(int monsterId, int superUniqueId, String superUniqueKey) {
      this.monsterId = monsterId;
      this.superUniqueId = superUniqueId;
      this.superUniqueKey = superUniqueKey;
    }
  }

  /** Avoids touching MPQ handles from a headless test JVM. */
  private static final class RiiabloAccess {
    static boolean mpqsMissing() {
      return D2MOOLayoutContext.mpqs() == null;
    }
  }
}
